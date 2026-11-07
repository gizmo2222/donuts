package com.donuts.game

/**
 * Represents a single cell in the game grid.
 *
 * @param type      The donut flavor/color in this cell.
 * @param row       Grid row (0 = top).
 * @param col       Grid column (0 = left).
 * @param isGolden  When true this cell glows gold and can join any drag chain
 *                  regardless of its own type.  Golden cells spawn with a small
 *                  chance during gravity fills; the board never starts with them.
 */
data class GameCell(
    val type: DonutType,
    val row: Int,
    val col: Int,
    val isGolden: Boolean = false
)
