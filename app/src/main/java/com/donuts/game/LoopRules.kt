package com.donuts.game

/**
 * When a drag chain closes into a ring. Pure, no Android types.
 *
 * With 8-way links any 3-cell L or 2x2 block is already a loop, so a short loop would be free;
 * [MIN_LOOP] cells make him draw a real ring before it counts.
 */
object LoopRules {
    const val MIN_LOOP = 6

    /**
     * True when the finger entering [cell] closes [chain] into a ring: the chain has at least
     * [MIN_LOOP] cells, [cell] is its first cell, and its last cell touches the first (8-way).
     */
    fun closes(chain: List<Pair<Int, Int>>, cell: Pair<Int, Int>): Boolean {
        if (chain.size < MIN_LOOP || cell != chain.first()) return false
        val (lr, lc) = chain.last(); val (fr, fc) = chain.first()
        val dr = Math.abs(lr - fr); val dc = Math.abs(lc - fc)
        return dr <= 1 && dc <= 1 && (dr + dc) > 0
    }
}
