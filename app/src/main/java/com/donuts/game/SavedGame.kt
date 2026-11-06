package com.donuts.game

import android.os.Bundle
import android.os.SystemClock

// Keeping the game across an activity restart: a rotation, a window resize, a dark-mode
// switch, or the system reclaiming the app in the background.
// Part of GameView: these are extension functions on it.

private const val KEY_SIZE    = "board.size"
private const val KEY_CELLS   = "board.cells"
private const val KEY_CLEARED = "board.cleared"
private const val KEY_SAVED   = "board.sessionSaved"

/**
 * Finishes a pop or drop that is in flight (no animation), so the board is idle and every
 * donut of the chain is counted. Animation state itself is never saved.
 */
internal fun GameView.settleBoard() {
    pendingResult?.let { res ->
        if ((res.chainCells + res.bonusCells).any { (r, c) -> board.grid[r][c].isGolden }) prefs.goldenPopped = true
        board.clearChain(res)
    }
    pendingResult = null
    board.settle()
    animPhase = AnimPhase.IDLE
    popCells.clear(); dropCells.clear(); dropColMask.clear()
    cascadeCount = 0; isCascade = false
    dragChain.clear(); fingerHeld = false
    hintCells = emptyList()
    if (!board.hasValidMoves() && noMovesWarningMs < 0) noMovesWarningMs = SystemClock.elapsedRealtime()
}

/** Settles the board, banks its progress and writes the grid and the counter to [out]. */
internal fun GameView.saveState(out: Bundle) {
    synchronized(holder) {
        settleBoard()
        saveSession()
        out.putInt(KEY_SIZE, board.cols)
        out.putIntArray(KEY_CELLS, board.cellCodes())
        out.putIntArray(KEY_CLEARED, board.clearedCounts())
        out.putInt(KEY_SAVED, sessionSaved)
    }
}

/**
 * Puts back the board written by [saveState]. Returns false, leaving the fresh board in
 * place, when [state] holds no board or one of another size.
 */
internal fun GameView.restoreState(state: Bundle): Boolean {
    val cells   = state.getIntArray(KEY_CELLS) ?: return false
    val cleared = state.getIntArray(KEY_CLEARED) ?: return false
    return applySaved(SavedBoard(state.getInt(KEY_SIZE), cells, cleared, state.getInt(KEY_SAVED)))
}

/** Puts [s] on the board and the counter. False, changing nothing, when it does not fit. */
internal fun GameView.applySaved(s: SavedBoard): Boolean {
    if (s.size != prefs.gridSize) return false
    val saved = GameBoard(rows = s.size, cols = s.size)
    if (!saved.restore(s.cells, s.cleared)) return false
    synchronized(holder) {
        board = saved
        val total = saved.donutsCleared.values.sum()
        sessionSaved = s.sessionSaved.coerceIn(0, total)
        // Show the counter where it was: no count-up from zero, no second milestone party
        displayedCount = total; prevDisplayCount = total
        lastMilestone = MILESTONES.lastOrNull { it <= total } ?: 0
        if (!saved.hasValidMoves()) noMovesWarningMs = SystemClock.elapsedRealtime()
    }
    return true
}

/** Settles the board, banks its progress and keeps the board in [Prefs] for the next Play. */
internal fun GameView.saveToPrefs() {
    synchronized(holder) {
        settleBoard()
        saveSession()
        prefs.savedBoard = SavedBoard(board.cols, board.cellCodes(), board.clearedCounts(), sessionSaved).encode()
    }
}

/** Brings back the board kept by [saveToPrefs]. False when there is none or it is the other size. */
internal fun GameView.restoreFromPrefs(): Boolean {
    val s = SavedBoard.decode(prefs.savedBoard) ?: return false
    return applySaved(s)
}

// Session banking: fold this board's donuts into the lifetime total and high scores, or start a new board
internal fun GameView.saveSession() {
    val sessionTotal = board.donutsCleared.values.sum()
    val delta = sessionTotal - sessionSaved
    if (delta > 0) { prefs.lifetimeDonuts = prefs.lifetimeDonuts + delta; sessionSaved = sessionTotal }
    val hs = if (board.cols == 6) prefs.highScore6x6 else prefs.highScore8x8
    if (sessionTotal > hs) {
        if (board.cols == 6) prefs.highScore6x6 = sessionTotal
        else prefs.highScore8x8 = sessionTotal
    }
}

// Donuts popped ever: what is banked plus what this board has added since the last save
internal fun GameView.lifetimeDonuts(): Int = prefs.lifetimeDonuts + (board.donutsCleared.values.sum() - sessionSaved)

internal fun GameView.rebuildBoard() {
    saveSession()
    sessionSaved = 0
    prefs.savedBoard = null
    board = GameBoard(rows = prefs.gridSize, cols = prefs.gridSize)
    animPhase = AnimPhase.IDLE
    popCells.clear(); dropCells.clear()
    dragChain.clear(); fingerHeld = false
    pendingResult  = null
    cascadeCount   = 0
    isCascade      = false
    hintCells = emptyList()
    tutorialCells = emptyList()
    displayedCount = 0
    lastActionMs = SystemClock.elapsedRealtime()
    computeLayout()
    boardEntryMs = SystemClock.elapsedRealtime()
}
