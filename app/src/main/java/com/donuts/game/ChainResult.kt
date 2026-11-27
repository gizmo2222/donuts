package com.donuts.game

/**
 * Describes the full outcome of a player-drawn chain clear before it is applied.
 *
 * @param chainCells  The cells the player explicitly connected.
 * @param bonusCells  Extra cells demolished by the power-up (empty when [powerUp] == NONE).
 * @param powerUp     Which power-up tier fired.
 * @param chainType   The donut type of the chain (first non-golden cell's type).
 * @param bursting    Bubble cells ([BubbleRules]) this clear bursts, first one first; each also clears its 3×3.
 * @param bubbleAt    Where a new bubble is left once the clear has dropped, or null.
 */
data class ChainResult(
    val chainCells: List<Pair<Int, Int>>,
    val bonusCells: List<Pair<Int, Int>>,
    val powerUp: PowerUp,
    val chainType: DonutType,
    val bursting: List<Pair<Int, Int>> = emptyList(),
    val bubbleAt: Pair<Int, Int>? = null,
)

/** The middle cell of a chain: where a bomb or row blast is centred and its pop ripple starts. */
internal fun midCell(cells: List<Pair<Int, Int>>): Pair<Int, Int> = cells[cells.size / 2]
