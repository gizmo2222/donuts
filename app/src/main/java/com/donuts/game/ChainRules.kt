package com.donuts.game

/** Which cell may join a drag chain next. Pure, no Android types. */
object ChainRules {
    /**
     * True when a cell of [nextType] ([nextGolden] = wild) may join a chain whose cells
     * have [chainTypes] and [chainGolden]. A golden cell joins anything, and a chain of
     * only golden cells (or an empty one) accepts any type.
     */
    fun accepts(
        chainTypes: List<DonutType>,
        chainGolden: List<Boolean>,
        nextType: DonutType,
        nextGolden: Boolean
    ): Boolean {
        if (nextGolden) return true
        val i = chainGolden.indexOfFirst { !it }
        if (i < 0 || i >= chainTypes.size) return true
        return chainTypes[i] == nextType
    }
}
