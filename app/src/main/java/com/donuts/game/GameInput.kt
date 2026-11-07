package com.donuts.game

import android.graphics.*
import android.os.SystemClock
import android.view.MotionEvent
import kotlin.math.*

// Touch handling: panel hits, dragging a chain, releasing it, New game.
// Part of GameView: these are extension functions on it, split out of GameView.kt.

// Panel hit ids: 0-5 option buttons (sound, hints, size), 10-11 buzz, 6 New game, 9 Reset stickers, 7 Done, 8 outside the panel,
// -1 the panel itself (not a button)
internal fun GameView.settingsHit(x: Float, y: Float): Int {
    val opts = arrayOf(soundRects[0], soundRects[1], hintRects[0], hintRects[1], gridRects[0], gridRects[1],
                       buzzRects[0], buzzRects[1])
    for (i in opts.indices) if (opts[i].contains(x, y)) return if (i < 6) i else i + 4
    return when {
        settingsResetRect.contains(x, y) -> 6
        settingsStickersRect.contains(x, y) -> 9
        settingsCloseRect.contains(x, y) -> 7
        !panelRect.contains(x, y)        -> 8
        else -> -1
    }
}

internal fun GameView.stickersHit(x: Float, y: Float): Int = when {
    stickersCloseRect.contains(x, y) -> 7
    !stickerPanelRect.contains(x, y) -> 8
    else -> -1
}

/** The button a hit id refers to in whichever panel is open, for press feedback. */
internal fun GameView.panelHitRect(hit: Int): RectF? {
    val inSettings = settingsOpen || settingsAnim > 0f
    return when (hit) {
        0 -> soundRects[0]; 1 -> soundRects[1]; 2 -> hintRects[0]; 3 -> hintRects[1]
        4 -> gridRects[0];  5 -> gridRects[1];  6 -> settingsResetRect
        9 -> settingsStickersRect; 10 -> buzzRects[0]; 11 -> buzzRects[1]
        7 -> if (inSettings) settingsCloseRect else stickersCloseRect
        else -> null
    }
}

internal fun GameView.handleSettingsTap(hit: Int) {
    val now = SystemClock.elapsedRealtime()
    when (hit) {
        0    -> prefs.soundEnabled = true
        1    -> prefs.soundEnabled = false
        10   -> { prefs.hapticEnabled = true; hapticEngine.tick() }
        11   -> prefs.hapticEnabled = false
        2, 3 -> prefs.hintDelayMs = hintOptions[hit - 2]
        4, 5 -> {
            val size = gridOptions[hit - 4]
            if (prefs.gridSize != size) { prefs.gridSize = size; rebuildBoard() }
        }
        6 -> {
            stickersConfirm.cancel()
            if (newGameConfirm.tap(now)) { startOver(); closeSettings() }
        }
        9 -> {
            newGameConfirm.cancel()
            if (stickersConfirm.tap(now)) resetStickers()
        }
        7, 8 -> closeSettings()
    }
}

/** Back to 0 of 12 stickers and an empty donut box. The board is banked and cleared first so nothing from it counts again. */
internal fun GameView.resetStickers() {
    startOver()
    prefs.lifetimeDonuts = 0; prefs.highScore6x6 = 0; prefs.highScore8x8 = 0
    prefs.bestChainLength = 0; prefs.sessionCount = 0; prefs.goldenPopped = false
    sessionSaved = 0; earnedMask = 0; prefs.stickersAnnounced = 0; boxesSeen = -1; allStickersParty = false
    closeSettings()
}

/** Shut the settings panel and disarm both two-tap confirms. */
internal fun GameView.closeSettings() { settingsOpen = false; newGameConfirm.cancel(); stickersConfirm.cancel() }

/** Fresh board and counter. Session stats are saved first so stickers keep counting. */
internal fun GameView.startOver() {
    val now = SystemClock.elapsedRealtime()
    synchronized(holder) {
        settleBoard()      // apply the in-flight chain first so its donuts are banked
        saveSession()
        sessionSaved = 0
        prefs.savedBoard = null
        board.reset()
        resetPlayState()
        resetFlashMs     = now
        lastActionMs     = now
        boardEntryMs     = now
    }
}

/** A finger that went down mid-animation and is still down: start its drag where it is now. */
internal fun GameView.startHeldDrag() {
    if (!fingerHeld) return
    fingerHeld = false
    if (settingsOpen || stickersOpen) return
    handleDown(heldX, heldY)
}

internal fun GameView.handleDown(x: Float, y: Float) {
    lastActionMs = SystemClock.elapsedRealtime(); hintCells = emptyList()
    dragChain.clear(); chainPings.clear()
    val col = cellCol(x); val row = cellRow(y)
    if (inBounds(row, col)) {
        val now2 = SystemClock.elapsedRealtime()
        dragChain.add(Pair(row, col))
        chainPings[Pair(row, col)] = now2
        centerPingMs = now2
    }
}

internal fun GameView.handleMove(x: Float, y: Float) {
    val col = cellCol(x); val row = cellRow(y)
    if (!inBounds(row, col)) return
    val cell = Pair(row, col)
    if (dragChain.size >= 2 && dragChain[dragChain.size - 2] == cell) {
        chainPings.remove(dragChain.last())
        dragChain.removeAt(dragChain.size - 1)
        val now2 = SystemClock.elapsedRealtime()
        centerPingMs = now2
        return
    }
    if (cell != rejectedCell) rejectedCell = null
    if (cell in dragChain) return
    val last = dragChain.lastOrNull() ?: return
    if (!adjacent8(last.first, last.second, row, col)) return
    // Golden cells are wild; an all-golden chain takes any type (see ChainRules).
    val next = board.grid[row][col]
    val accepted = ChainRules.accepts(
        dragChain.map { (r, c) -> board.grid[r][c].type },
        dragChain.map { (r, c) -> board.grid[r][c].isGolden },
        next.type, next.isGolden)
    if (!accepted) {
        // Wrong flavour: that donut shakes its head (once per visit, not every move event)
        if (rejectedCell != cell) {
            rejectedCell = cell
            shakeCell = cell; shakeMs = SystemClock.elapsedRealtime()
            if (prefs.hapticEnabled) hapticEngine.tick()
        }
        return
    }
    val now2 = SystemClock.elapsedRealtime()
    dragChain.add(cell)
    chainPings[cell] = now2
    centerPingMs = now2
    if (prefs.soundEnabled)  soundEngine.playConnectBlip(dragChain.size)
    if (prefs.hapticEnabled) hapticEngine.tick()
}

internal fun GameView.handleUp() {
    val chain = dragChain.toList()
    if (chain.size >= 3) {
        val result = board.peekChainClear(chain)
        if (result != null) {
            pendingResult = result
            isCascade     = false
            cascadeCount  = 0

            // The chain and any power-up bonus cells all pop; bonus cells wait their turn.
            popCells.clear()
            val allPop = result.chainCells + result.bonusCells
            val delays = PopTiming.delays(result)
            popCells.addAll(allPop.map { (r, c) ->
                AnimCell(r, c, board.grid[r][c].type, isGolden = board.grid[r][c].isGolden,
                    delayMs = delays[Pair(r, c)] ?: 0L)
            })

            val now = SystemClock.elapsedRealtime()
            powerFx = if (result.powerUp == PowerUp.NONE) null else {
                val (orr, occ) = PopTiming.origin(result)
                PowerUpFx(result.powerUp, orr, occ, result.chainType.bodyColor,
                    PopTiming.totalMs(delays, POP_MS))
            }
            animStartMs = now; animPhase = AnimPhase.POPPING
            lastActionMs = now; hintCells = emptyList()
            // First chain ever made: the guided demo has done its job
            if (tutorialActive) { tutorialActive = false; prefs.tutorialSeen = true; celebrate(now, "YAY!") }

            val chainLen = result.chainCells.size
            if (chainLen > prefs.bestChainLength) prefs.bestChainLength = chainLen
            if (chainLen >= 6) {
                chainFlashMs    = now
                chainFlashColor = result.chainType.bodyColor
            }
            if (prefs.soundEnabled)  soundEngine.playPopClear()
            if (prefs.hapticEnabled) hapticEngine.pop()

            // Floating label for big chains
            // Chains of 5+ always fire a power-up, so those get the sound-word
            val label = when {
                result.powerUp == PowerUp.COLOR_BURST -> "KABOOM!"
                result.powerUp == PowerUp.ROW_BLAST   -> "WHOOSH!"
                result.powerUp == PowerUp.BOMB        -> "BOOM!"
                chainLen == 4 -> "NICE!"
                else          -> null
            }
            label?.let { addFloatLabelAt(allPop, it, result.chainType.labelColor, now) }
        }
    } else if (chain.isNotEmpty()) {
        // Too short to pop: the donuts wobble with a "boop" and a tick, so a tap is never dead.
        // Two in a row also get a "3!" so the rule is taught by the board itself.
        val now = SystemClock.elapsedRealtime()
        wobbleCells = chain; wobbleMs = now
        if (chain.size == 2) addFloatLabelAt(chain, "3!", Color.WHITE, now)
        if (prefs.soundEnabled)  soundEngine.playBoop()
        if (prefs.hapticEnabled) hapticEngine.tick()
    }
    dragChain.clear(); chainPings.clear(); rejectedCell = null
}

/** Every touch on the game screen: finger ownership, the open panel, the HUD buttons, a held finger, then the board. */
internal fun GameView.handleTouch(event: MotionEvent): Boolean {
    val act = event.actionMasked
    if (act != MotionEvent.ACTION_DOWN && act != MotionEvent.ACTION_MOVE &&
        act != MotionEvent.ACTION_UP && act != MotionEvent.ACTION_CANCEL &&
        act != MotionEvent.ACTION_POINTER_DOWN && act != MotionEvent.ACTION_POINTER_UP) return true

    // One finger owns the touch: later fingers are ignored, and lifting the owner releases.
    var action = act
    if (act == MotionEvent.ACTION_DOWN) chainPointer.onDown(event.getPointerId(0))
    if (act == MotionEvent.ACTION_POINTER_DOWN) return true   // extra fingers are ignored; the first owns the chain
    if (act == MotionEvent.ACTION_POINTER_UP) {
        if (!chainPointer.onPointerUp(event.getPointerId(event.actionIndex))) return true
        action = MotionEvent.ACTION_UP
    } else if (act == MotionEvent.ACTION_UP || act == MotionEvent.ACTION_CANCEL) {
        if (chainPointer.activeId == ChainPointer.NONE) return true
    }
    val pi = event.findPointerIndex(chainPointer.activeId).let { if (it < 0) event.actionIndex else it }
    val px = event.getX(pi); val py = event.getY(pi)
    if (act == MotionEvent.ACTION_UP || act == MotionEvent.ACTION_CANCEL) chainPointer.reset()

    synchronized(holder) {
        // When settings or stickers is open or animating, consume touch
        if (settingsOpen || settingsAnim > 0f || stickersOpen || stickersAnim > 0f) {
            // Buttons press in on finger-down and act on finger-up over the same button,
            // so a finger sliding off a button cancels it, like any other button.
            val inSettings = settingsOpen || settingsAnim > 0f
            val slideY = if (inSettings) settingsSlideY() else stickersSlideY()
            val x = px; val y = LayoutMath.panelLocalY(py, slideY)
            val hit = if (inSettings) settingsHit(x, y) else stickersHit(x, y)
            when (action) {
                MotionEvent.ACTION_DOWN -> {
                    panelDownHit = hit
                    panelHitRect(hit)?.let { optPressRect = it; optPressMs = SystemClock.elapsedRealtime() }
                }
                MotionEvent.ACTION_UP -> {
                    if (hit != -1 && hit == panelDownHit) {
                        if (inSettings) handleSettingsTap(hit) else { stickersOpen = false }
                    }
                    panelDownHit = -2
                }
                MotionEvent.ACTION_CANCEL -> panelDownHit = -2
            }
            return true
        }

        if (action == MotionEvent.ACTION_DOWN) {
            val now = SystemClock.elapsedRealtime()
            if (stickersBtnRect.contains(px, py)) { stickersPressMs = now; stickersOpen = true; return true }
            if (settingsBtnRect.contains(px, py)) { settingsPressMs = now; settingsOpen = true; return true }
        }

        // Board still popping or dropping: hold the finger instead of dropping it, so a
        // drag started early begins the moment the pieces land (see startHeldDrag).
        if (animPhase != AnimPhase.IDLE) {
            when (action) {
                MotionEvent.ACTION_DOWN -> { fingerHeld = true; heldX = px; heldY = py }
                MotionEvent.ACTION_MOVE -> if (fingerHeld) { heldX = px; heldY = py }
                else -> fingerHeld = false
            }
            return true
        }

        when (action) {
            MotionEvent.ACTION_DOWN   -> handleDown(px, py)
            MotionEvent.ACTION_MOVE   -> {
                startHeldDrag()
                // A fast swipe batches several samples into one event; feed the real ones in order.
                if (act == MotionEvent.ACTION_MOVE) {
                    for (h in 0 until event.historySize) handleMove(event.getHistoricalX(pi, h), event.getHistoricalY(pi, h))
                }
                handleMove(px, py)
            }
            MotionEvent.ACTION_UP     -> { fingerHeld = false; handleUp() }
            MotionEvent.ACTION_CANCEL -> { fingerHeld = false; dragChain.clear() }
        }
    }
    return true
}

internal fun GameView.cellCol(x: Float) = LayoutMath.cellIndex(x, boardLeft, cellSize)

internal fun GameView.cellRow(y: Float) = LayoutMath.cellIndex(y, boardTop, cellSize)

internal fun GameView.inBounds(r: Int, c: Int) = r in 0 until board.rows && c in 0 until board.cols

internal fun GameView.adjacent8(r1: Int, c1: Int, r2: Int, c2: Int) =
    abs(r1 - r2) <= 1 && abs(c1 - c2) <= 1 && !(r1 == r2 && c1 == c2)

// System Back: close an open panel first; false means nothing was open
internal fun GameView.onBackPressed(): Boolean {
    synchronized(holder) {
        if (settingsOpen) { closeSettings(); return true }
        if (stickersOpen) { stickersOpen = false; return true }
    }
    return false
}
