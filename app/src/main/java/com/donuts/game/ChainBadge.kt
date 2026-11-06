package com.donuts.game

// Where the chain-count badge sits: on a chain cell next to the last one, so it never
// hides a donut that is not part of the chain.
object ChainBadge {
    // (dRow, dCol) in order of preference: up, up-left, up-right, left, right, down-left, down-right, down
    private val PREFERENCE = listOf(
        -1 to 0, -1 to -1, -1 to 1, 0 to -1, 0 to 1, 1 to -1, 1 to 1, 1 to 0
    )

    fun spot(chain: List<Pair<Int, Int>>): Pair<Int, Int>? {
        if (chain.size < 2) return null
        val (lr, lc) = chain.last()
        val others = chain.subList(0, chain.size - 1)
        for ((dr, dc) in PREFERENCE) {
            val cell = Pair(lr + dr, lc + dc)
            if (cell in others) return cell
        }
        return null
    }
}