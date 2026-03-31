package com.donuts.game

/**
 * Represents a single cell in the game grid.
 *
 * @param type      The donut flavor/color in this cell.
 * @param row       Grid row (0 = top).
 * @param col       Grid column (0 = left).
 * @param drawOffY  Vertical pixel offset used for fall animations (0 = settled).
 */
data class GameCell(
    var type: DonutType,
    val row: Int,
    val col: Int,
    var drawOffY: Float = 0f
)
