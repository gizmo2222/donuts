package com.donuts.game

import kotlin.math.abs
import kotlin.math.hypot

/**
 * Forgiving hit zones for a drag chain (pure, unit tested). The finger is in cell units: u along the columns,
 * v along the rows, so cell (r, c) spans u in [c, c+1) and v in [r, r+1).
 *
 * The cell the finger aims at is the nearest of the chain's last cell and its eight neighbours, measured from a
 * point led a little ahead along the drag ([LEAD]). Diagonal neighbours count as nearer by [diagWeight], so a
 * finger cutting a corner reaches the diagonal cell instead of clipping the straight one beside it. Between the
 * last cell and a straight neighbour the line stays at the shared edge, so straight steps and the backtrack
 * undo behave as before.
 */
object HitZone {
    /** How far ahead of the finger, in cells, the aim point sits along the drag direction. */
    const val LEAD = 0.15f
    /** The finger must move this far, in cells, before the drag direction is updated (jitter does not flip it). */
    const val AIM_STEP = 0.12f
    /** Diagonal weight on 6x6 and 7x7. */
    const val DIAG_WEIGHT = 1.25f
    /** Diagonal weight on 8x8, where the cells are smaller under the same finger. */
    const val DIAG_WEIGHT_BIG = 1.35f

    fun diagWeight(gridSize: Int): Float = if (gridSize >= 8) DIAG_WEIGHT_BIG else DIAG_WEIGHT

    /**
     * The cell (row, col) the finger at ([u], [v]) aims at from the chain's last cell ([lastRow], [lastCol]):
     * the last cell itself, one of its in-bounds neighbours, or null when the finger is beyond the 3x3 block.
     * ([aimU], [aimV]) is the unit drag direction, or zero before the finger has moved.
     */
    fun target(
        u: Float, v: Float, lastRow: Int, lastCol: Int, rows: Int, cols: Int,
        aimU: Float, aimV: Float, diagWeight: Float
    ): Pair<Int, Int>? {
        if (abs(u - (lastCol + 0.5f)) > 1.5f || abs(v - (lastRow + 0.5f)) > 1.5f) return null
        val pu = u + aimU * LEAD
        val pv = v + aimV * LEAD
        var best = Pair(lastRow, lastCol)
        var bestScore = hypot(pu - (lastCol + 0.5f), pv - (lastRow + 0.5f))
        for (dr in -1..1) for (dc in -1..1) {
            if (dr == 0 && dc == 0) continue
            val r = lastRow + dr; val c = lastCol + dc
            if (r !in 0 until rows || c !in 0 until cols) continue
            val w = if (dr != 0 && dc != 0) diagWeight else 1f
            val score = hypot(pu - (c + 0.5f), pv - (r + 0.5f)) / w
            if (score < bestScore) { bestScore = score; best = Pair(r, c) }
        }
        return best
    }

    /**
     * The new drag direction after the finger reached ([u], [v]) from the anchor ([anchorU], [anchorV]), as
     * (aimU, aimV, moved): unit vector and true when it moved at least [AIM_STEP]; otherwise the old aim and false.
     */
    fun aim(u: Float, v: Float, anchorU: Float, anchorV: Float, aimU: Float, aimV: Float): Triple<Float, Float, Boolean> {
        val du = u - anchorU; val dv = v - anchorV
        val d = hypot(du, dv)
        return if (d < AIM_STEP) Triple(aimU, aimV, false) else Triple(du / d, dv / d, true)
    }
}
