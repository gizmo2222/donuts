package com.donuts.game

import android.graphics.*
import android.os.SystemClock
import android.view.MotionEvent
import kotlin.math.*

// Touch handling: panel hits, dragging a chain, releasing it, New game.
// Part of GameView: these are extension functions on it, split out of GameView.kt.

// Panel hit ids: 0-5 option buttons (sound, hints, size), 10-11 buzz, 6 New game, 9 Reset stickers, 7 Done,
// 8 outside the panel, 14 Grown-ups (Steven's page), 15 Back (the gate), 30+d keypad digit d, 40-41 Bouncy/Calm,
// -1 the panel itself (not a button). Only the shown page's buttons can be hit.
internal fun GameView.settingsHit(x: Float, y: Float): Int {
    if (!panelRect.contains(x, y)) return 8
    when (settingsPage) {
        SETTINGS_KID -> {
            val opts = arrayOf(soundRects[0], soundRects[1], hintRects[0], hintRects[1], buzzRects[0], buzzRects[1])
            val ids  = intArrayOf(0, 1, 2, 3, 10, 11)
            for (i in opts.indices) if (opts[i].contains(x, y)) return ids[i]
            if (settingsGrownRect.contains(x, y)) return 14
            if (settingsCloseRect.contains(x, y)) return 7
        }
        SETTINGS_GATE -> {
            for (d in 0..9) if (gateKeyRects[d].contains(x, y)) return 30 + d
            if (settingsCloseRect.contains(x, y)) return 15
        }
        else -> {
            if (gridRects[0].contains(x, y)) return 4
            if (gridRects[1].contains(x, y)) return 5
            if (motionRects[0].contains(x, y)) return 40
            if (motionRects[1].contains(x, y)) return 41
            if (settingsResetRect.contains(x, y)) return 6
            if (settingsStickersRect.contains(x, y)) return 9
            if (settingsCloseRect.contains(x, y)) return 7
        }
    }
    return -1
}

// Stickers panel hit ids: 7 Done, 8 outside, 12 Stickers tab, 13 Friends tab, 20+i a met friend's tile, -1 the panel itself
internal fun GameView.stickersHit(x: Float, y: Float): Int {
    placePanelTabs()
    val tabs = panelTabPages().size > 1
    val page = panelPageNow()
    return when {
        stickersCloseRect.contains(x, y) -> 7
        !stickerPanelRect.contains(x, y) -> 8
        tabs && stickersTabRect.contains(x, y) -> 12
        tabs && prefs.friendsMet > 0 && friendsTabRect.contains(x, y) -> 13
        tabs && toppingsTabRect.contains(x, y) && 2 in panelTabPages() -> 16
        tabs && page == 2 -> (0 until pickerTileCount()).firstOrNull { stickerRects[it].contains(x, y) }?.let { 60 + it } ?: -1
        tabs && page == 1 -> (0 until min(prefs.friendsMet, DRAWN)).firstOrNull { stickerRects[it].contains(x, y) }
            ?.let { 20 + it } ?: -1
        else -> -1
    }
}

/** A release over a stickers-panel button: close, switch page, or tap a friend (a happy hop and a blip). */
internal fun GameView.handleStickersTap(hit: Int) {
    when {
        hit == 7 || hit == 8 -> stickersOpen = false
        hit == 12 -> stickersPage = 0
        hit == 13 -> stickersPage = 1
        hit == 16 -> stickersPage = 2
        hit >= 60 -> tapToppingTile(hit - 60)
        hit >= 20 -> {
            friendTapIdx = hit - 20; friendTapMs = SystemClock.elapsedRealtime()
            if (prefs.soundEnabled) soundEngine.playFriend(hit - 20)
        }
    }
}

/** The button a hit id refers to in whichever panel is open, for press feedback. */
internal fun GameView.panelHitRect(hit: Int): RectF? {
    val inSettings = settingsOpen || settingsAnim > 0f
    return when (hit) {
        0 -> soundRects[0]; 1 -> soundRects[1]; 2 -> hintRects[0]; 3 -> hintRects[1]
        4 -> gridRects[0];  5 -> gridRects[1];  6 -> settingsResetRect
        9 -> settingsStickersRect; 10 -> buzzRects[0]; 11 -> buzzRects[1]
        7 -> if (inSettings) settingsCloseRect else stickersCloseRect
        12 -> stickersTabRect; 13 -> friendsTabRect; 16 -> toppingsTabRect
        14 -> settingsGrownRect; 15 -> settingsCloseRect
        in 30..39 -> gateKeyRects[hit - 30]
        else -> null
    }
}

/** Turn the settings panel to [page] with a little pop, disarming both two-tap confirms. */
internal fun GameView.turnSettingsPage(page: Int) {
    newGameConfirm.cancel(); stickersConfirm.cancel()
    if (page == SETTINGS_GATE) { parentGate.next(); gateWrongMs = -1L }
    showSettingsPage(page)
    settingsPageMs = SystemClock.elapsedRealtime()
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
        40, 41 -> { prefs.calmMotion = hit == 41; reducedMotion = systemReducedMotion || prefs.calmMotion }
        6 -> {
            stickersConfirm.cancel()
            if (newGameConfirm.tap(now)) { startOver(); closeSettings() }
        }
        9 -> {
            newGameConfirm.cancel()
            if (stickersConfirm.tap(now)) resetStickers()
        }
        14 -> turnSettingsPage(SETTINGS_GATE)
        15 -> turnSettingsPage(SETTINGS_KID)
        in 30..39 -> when (parentGate.press(hit - 30)) {
            ParentGate.OPEN  -> turnSettingsPage(SETTINGS_GROWN)
            ParentGate.WRONG -> { gateWrongMs = now; if (prefs.soundEnabled) soundEngine.playBoop() }
        }
        7, 8 -> closeSettings()
    }
}

/** Back to 0 of 12 stickers and an empty donut box. The board is banked and cleared first so nothing from it counts again. */
internal fun GameView.resetStickers() {
    startOver()
    // Donut friends (prefs.friendsMet) and toppings (prefs.toppingsEarned, prefs.topping) are kept forever:
    // a sticker reset leaves them alone on purpose.
    prefs.lifetimeDonuts = 0; prefs.highScore6x6 = 0; prefs.highScore8x8 = 0
    prefs.bestChainLength = 0; prefs.sessionCount = 0; prefs.goldenPopped = false
    sessionSaved = 0; earnedMask = 0; prefs.stickersAnnounced = 0; boxesSeen = -1; allStickersParty = false
    showSomeonePending = SHOW_NONE; showSomeoneKind = SHOW_NONE; showSomeoneMs = -1L
    setParty(PartyState()); partyShownFill = -1f
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
        if (FlashGate.allows(lastFullFlashMs, now)) { resetFlashMs = now; lastFullFlashMs = now }
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
    dragChain.clear(); chainPings.clear(); chainLooped = false
    aimU = 0f; aimV = 0f; aimAnchorU = cellU(x); aimAnchorV = cellV(y)
    val col = cellCol(x); val row = cellRow(y)
    if (inBounds(row, col)) {
        val now2 = SystemClock.elapsedRealtime()
        dragChain.add(Pair(row, col))
        chainPings[Pair(row, col)] = now2
        centerPingMs = now2
    }
}

internal fun GameView.handleMove(x: Float, y: Float) {
    val u = cellU(x); val v = cellV(y)
    val (au, av, moved) = HitZone.aim(u, v, aimAnchorU, aimAnchorV, aimU, aimV)
    if (moved) { aimU = au; aimV = av; aimAnchorU = u; aimAnchorV = v }
    val end = dragChain.lastOrNull() ?: return
    // Forgiving zones: the cell aimed at from the chain's end, not just the square under the finger
    val cell = HitZone.target(u, v, end.first, end.second, board.rows, board.cols, aimU, aimV,
        HitZone.diagWeight(board.cols)) ?: return
    val row = cell.first; val col = cell.second
    // A closed ring holds until the finger goes back onto the last cell, which opens it again
    if (chainLooped) {
        if (cell == dragChain.last()) { chainLooped = false; centerPingMs = SystemClock.elapsedRealtime() }
        return
    }
    if (LoopRules.closes(dragChain, cell)) {
        val now2 = SystemClock.elapsedRealtime()
        chainLooped = true
        chainPings[cell] = now2
        centerPingMs = now2
        if (prefs.soundEnabled)  soundEngine.playConnectBlip(dragChain.size + 1)
        if (prefs.hapticEnabled) hapticEngine.milestone()
        return
    }
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
        val result = board.peekChainClear(chain, looped = chainLooped)
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
            if (chainLen >= 6 && FlashGate.allows(lastFullFlashMs, now)) {
                chainFlashMs    = now; lastFullFlashMs = now
                chainFlashColor = result.chainType.bodyColor
            }
            if (prefs.soundEnabled)  soundEngine.playPopClear()
            if (prefs.hapticEnabled) hapticEngine.pop()

            // Float word that names the act: power-ups get their sound word, a golden cell its own
            val hasGolden = allPop.any { (r, c) -> board.grid[r][c].isGolden }
            val label = Praise.chainLabel(result.powerUp, chainLen, hasGolden)
            val labelColor = if (label == "GOLDEN!") GameTheme.goldenLabel else result.chainType.labelColor
            label?.let { addFloatLabelAt(allPop, it, labelColor, now) }
            tallyChain(chainLen, result.powerUp, hasGolden)
            palReact(PalMotion.reactionFor(chainLen, result.powerUp), now)
        }
    } else if (chain.isNotEmpty()) {
        // Too short to pop: the donuts wobble with a "boop" and a tick, so a tap is never dead.
        // Two in a row also get a "3!" so the rule is taught by the board itself. Now and then a lone tap
        // giggles instead: a bigger wiggle, "HEE HEE!" and a giggle sound.
        val now = SystemClock.elapsedRealtime()
        wobbleCells = chain; wobbleMs = now
        wobbleGiggle = chain.size == 1 && CelebrationMix.giggle(fxRandom)
        if (chain.size == 2) addFloatLabelAt(chain, "3!", Color.WHITE, now)
        if (wobbleGiggle) addFloatLabelAt(chain, Praise.GIGGLE, Color.WHITE, now)
        if (prefs.soundEnabled) { if (wobbleGiggle) soundEngine.playGiggle() else soundEngine.playBoop() }
        if (prefs.hapticEnabled) hapticEngine.tick()
        tallyChain(chain.size)
    }
    dragChain.clear(); chainPings.clear(); rejectedCell = null; chainLooped = false
}

/** Counts a released chain in [PlayTally] and saves it to Prefs (on this phone only). */
internal fun GameView.tallyChain(length: Int, powerUp: PowerUp = PowerUp.NONE, hadGolden: Boolean = false) {
    playTally.record(length, powerUp, hadGolden)
    prefs.playTally = playTally.encode()
    tallyLines = playTally.lines()
}

/** The chain's finger lifted: release the chain now, or let it wait [LiftGrace] for the finger to come back. */
internal fun GameView.beginLift() {
    val pops = dragChain.size >= 3 && board.peekChainClear(dragChain.toList(), looped = chainLooped) != null
    val grace = LiftGrace.graceMs(dragChain.size, chainLooped, pops)
    if (grace == 0L) { handleUp(); return }
    liftMs = SystemClock.elapsedRealtime(); liftGraceMs = grace
}

/** Releases a lifted chain that is still waiting (pops it or wobbles it, as [handleUp] does). */
internal fun GameView.finishLift() {
    if (liftMs < 0) return
    liftMs = -1L
    handleUp()
}

/** Each frame: a lifted chain whose wait is over is released. */
internal fun GameView.advanceLift(now: Long) {
    if (LiftGrace.expired(liftMs, liftGraceMs, now)) finishLift()
}

/**
 * A finger came down while a lifted chain waits. Within reach of the chain's end (the [HitZone] block)
 * it takes the chain back and carries on as a drag; anywhere else the chain is released first. True
 * when the chain was taken back.
 */
internal fun GameView.resumeLift(x: Float, y: Float): Boolean {
    if (liftMs < 0) return false
    val end = dragChain.lastOrNull()
    val u = cellU(x); val v = cellV(y)
    val onBoard = inBounds(cellRow(y), cellCol(x))   // a button beside the board never takes the chain back
    val cell = if (!onBoard) null else end?.let { HitZone.target(u, v, it.first, it.second, board.rows, board.cols, aimU, aimV,
        HitZone.diagWeight(board.cols)) }
    if (cell == null) { finishLift(); return false }
    val now = SystemClock.elapsedRealtime()
    liftMs = -1L
    lastActionMs = now; centerPingMs = now          // the count badge pings: got it back
    aimAnchorU = u; aimAnchorV = v                   // the jump back is not a drag direction
    if (prefs.hapticEnabled) hapticEngine.tick()
    handleMove(x, y)
    return true
}

/** Every touch on the game screen: finger ownership, the open panel, the HUD buttons, a held finger, then the board. */
internal fun GameView.handleTouch(event: MotionEvent): Boolean {
    val act = event.actionMasked
    if (act != MotionEvent.ACTION_DOWN && act != MotionEvent.ACTION_MOVE &&
        act != MotionEvent.ACTION_UP && act != MotionEvent.ACTION_CANCEL &&
        act != MotionEvent.ACTION_POINTER_DOWN && act != MotionEvent.ACTION_POINTER_UP) return true

    // One finger owns the touch: later fingers are ignored, and lifting the owner releases.
    if (showSomeoneMs >= 0) {
        // The card swallows every touch; a tap after the guard closes it
        val now = SystemClock.elapsedRealtime()
        if (act == MotionEvent.ACTION_DOWN && now - showSomeoneMs >= SHOW_CARD_GUARD_MS) closeShowSomeone(now)
        return true
    }
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
        // A lifted chain still waiting: this finger may take it back (or it is released first)
        if (action == MotionEvent.ACTION_DOWN && resumeLift(px, py)) return true

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
                        if (inSettings) handleSettingsTap(hit) else handleStickersTap(hit)
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
            if (settingsBtnRect.contains(px, py)) { settingsPressMs = now; showSettingsPage(SETTINGS_KID); settingsOpen = true; return true }
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
                if (liftMs >= 0) return true    // no finger owns a waiting chain; a stray move must not grow it
                startHeldDrag()
                // A fast swipe batches several samples into one event; feed the real ones in order.
                if (act == MotionEvent.ACTION_MOVE) {
                    for (h in 0 until event.historySize) handleMove(event.getHistoricalX(pi, h), event.getHistoricalY(pi, h))
                }
                handleMove(px, py)
            }
            MotionEvent.ACTION_UP     -> { fingerHeld = false; beginLift() }
            MotionEvent.ACTION_CANCEL -> { fingerHeld = false; liftMs = -1L; dragChain.clear(); chainLooped = false }
        }
    }
    return true
}

internal fun GameView.cellCol(x: Float) = LayoutMath.cellIndex(x, boardLeft, cellSize)

internal fun GameView.cellRow(y: Float) = LayoutMath.cellIndex(y, boardTop, cellSize)

/** The finger in cell units along the columns (u) and rows (v), for [HitZone]. */
internal fun GameView.cellU(x: Float) = if (cellSize > 0f) (x - boardLeft) / cellSize else 0f

internal fun GameView.cellV(y: Float) = if (cellSize > 0f) (y - boardTop) / cellSize else 0f

internal fun GameView.inBounds(r: Int, c: Int) = r in 0 until board.rows && c in 0 until board.cols

internal fun GameView.adjacent8(r1: Int, c1: Int, r2: Int, c2: Int) =
    abs(r1 - r2) <= 1 && abs(c1 - c2) <= 1 && !(r1 == r2 && c1 == c2)

// System Back: close an open panel first; false means nothing was open
internal fun GameView.onBackPressed(): Boolean {
    synchronized(holder) {
        if (showSomeoneMs >= 0) { closeShowSomeone(SystemClock.elapsedRealtime()); return true }
        if (settingsOpen) { closeSettings(); return true }
        if (stickersOpen) { stickersOpen = false; return true }
    }
    return false
}
