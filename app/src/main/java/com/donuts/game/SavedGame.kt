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
    val size    = state.getInt(KEY_SIZE)
    if (size != prefs.gridSize) return false
    val saved = GameBoard(rows = size, cols = size)
    if (!saved.restore(cells, cleared)) return false
    synchronized(holder) {
        board = saved
        val total = saved.donutsCleared.values.sum()
        sessionSaved = state.getInt(KEY_SAVED).coerceIn(0, total)
        // Show the counter where it was: no count-up from zero, no second milestone party
        displayedCount = total; prevDisplayCount = total
        lastMilestone = MILESTONES.lastOrNull { it <= total } ?: 0
        if (!saved.hasValidMoves()) noMovesWarningMs = SystemClock.elapsedRealtime()
    }
    return true
}
