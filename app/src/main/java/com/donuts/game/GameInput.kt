package com.donuts.game

import android.content.Context
import android.graphics.*
import android.os.SystemClock
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
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

internal fun GameView.handleSettingsTouch(x: Float, y: Float) {
    val now = SystemClock.elapsedRealtime()
    when {
        soundRects[0].contains(x, y) -> prefs.soundEnabled = true
        soundRects[1].contains(x, y) -> prefs.soundEnabled = false
        buzzRects[0].contains(x, y)  -> { prefs.hapticEnabled = true; hapticEngine.tick() }
        buzzRects[1].contains(x, y)  -> prefs.hapticEnabled = false
        hintRects[0].contains(x, y)  -> prefs.hintDelayMs = hintOptions[0]
        hintRects[1].contains(x, y)  -> prefs.hintDelayMs = hintOptions[1]
        gridRects[0].contains(x, y) || gridRects[1].contains(x, y) -> {
            val size = if (gridRects[0].contains(x, y)) gridOptions[0] else gridOptions[1]
            if (prefs.gridSize != size) { prefs.gridSize = size; rebuildBoard() }
        }
        settingsResetRect.contains(x, y) -> {
            stickersConfirm.cancel()
            if (newGameConfirm.tap(now)) { startOver(); settingsOpen = false }
        }
        settingsStickersRect.contains(x, y) -> {
            newGameConfirm.cancel()
            if (stickersConfirm.tap(now)) resetStickers()
        }
        settingsCloseRect.contains(x, y) || !panelRect.contains(x, y) -> {
            newGameConfirm.cancel(); stickersConfirm.cancel()
            settingsOpen = false
        }
    }
}

internal fun GameView.handleStickersTouch(x: Float, y: Float) {
    if (stickersCloseRect.contains(x, y) || !stickerPanelRect.contains(x, y)) stickersOpen = false
}

/** Back to 0 of 12 stickers and an empty donut box. The board is banked and cleared first so nothing from it counts again. */
internal fun GameView.resetStickers() {
    startOver()
    prefs.lifetimeDonuts = 0; prefs.highScore6x6 = 0; prefs.highScore8x8 = 0
    prefs.bestChainLength = 0; prefs.sessionCount = 0; prefs.goldenPopped = false
    sessionSaved = 0; earnedMask = -1; boxesSeen = -1; allStickersParty = false
    newGameConfirm.cancel(); stickersConfirm.cancel()
    settingsOpen = false
}

/** Fresh board and counter. Session stats are saved first so stickers keep counting. */
internal fun GameView.startOver() {
    val now = SystemClock.elapsedRealtime()
    saveSession()
    sessionSaved = 0
    prefs.savedBoard = null
    board.reset()
    displayedCount   = 0
    prevDisplayCount = -1
    digitFlips.clear()
    lastMilestone    = 0
    celebrateMs      = -1L
    particles.clear()
    synchronized(floatLabels) { floatLabels.clear() }
    dragChain.clear(); chainPings.clear(); fingerHeld = false
    pendingResult    = null; powerFx = null
    cascadeCount     = 0
    isCascade        = false
    counterPulseMs   = -1L
    chainFlashMs     = -1L
    shuffleAnimMs    = -1L
    hintCells        = emptyList()
    tutorialCells    = emptyList()
    noMovesWarningMs = -1L
    resetFlashMs     = now
    lastActionMs     = now
    boardEntryMs     = now
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

internal fun GameView.handleMove(event: MotionEvent) {
    val col = cellCol(event.x); val row = cellRow(event.y)
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
    // Golden cells are wild — they join any chain regardless of type.
    // Non-golden cells must match the chain type.
    val chainType = dragChainType ?: return
    val cellIsGolden = board.grid[row][col].isGolden
    if (!cellIsGolden && board.grid[row][col].type != chainType) {
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
                PowerUpFx(result.powerUp, orr, occ, result.chainType.bodyColor, now,
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
            label?.let {
                val cx = allPop.map { (_, c) -> boardLeft + c * cellSize + cellSize / 2f }.average().toFloat()
                val cy = allPop.map { (r, _) -> boardTop  + r * cellSize + cellSize / 2f }.average().toFloat()
                val colors = intArrayOf(
                    Color.rgb(255, 80, 60), Color.rgb(255, 180, 0),
                    Color.rgb(80, 210, 80), Color.rgb(60, 160, 255), Color.rgb(200, 80, 255)
                )
                val col = colors[(chainLen - 4).coerceIn(0, colors.size - 1)]
                synchronized(floatLabels) { floatLabels.add(FloatLabel(it, cx, cy, col, now)) }
            }
        }
    } else if (chain.isNotEmpty()) {
        // Too short to pop: the donuts wobble. Two in a row also get a "boop" and a "3!"
        // so the rule is taught by the board itself, not by a text tutorial.
        val now = SystemClock.elapsedRealtime()
        wobbleCells = chain; wobbleMs = now
        if (chain.size == 2) {
            val cx = chain.map { (_, c) -> boardLeft + c * cellSize + cellSize / 2f }.average().toFloat()
            val cy = chain.map { (r, _) -> boardTop  + r * cellSize + cellSize / 2f }.average().toFloat()
            synchronized(floatLabels) { floatLabels.add(FloatLabel("3!", cx, cy, Color.WHITE, now)) }
            if (prefs.soundEnabled)  soundEngine.playBoop()
            if (prefs.hapticEnabled) hapticEngine.tick()
        }
    }
    dragChain.clear(); chainPings.clear(); rejectedCell = null
}

internal fun GameView.cellCol(x: Float) = ((x - boardLeft) / cellSize).toInt()

internal fun GameView.cellRow(y: Float) = ((y - boardTop)  / cellSize).toInt()

internal fun GameView.inBounds(r: Int, c: Int) = r in 0 until board.rows && c in 0 until board.cols

internal fun GameView.adjacent8(r1: Int, c1: Int, r2: Int, c2: Int) =
    abs(r1 - r2) <= 1 && abs(c1 - c2) <= 1 && !(r1 == r2 && c1 == c2)

// System Back: close an open panel first; false means nothing was open
internal fun GameView.onBackPressed(): Boolean {
    synchronized(holder) {
        if (settingsOpen) { settingsOpen = false; newGameConfirm.cancel(); stickersConfirm.cancel(); return true }
        if (stickersOpen) { stickersOpen = false; return true }
    }
    return false
}
