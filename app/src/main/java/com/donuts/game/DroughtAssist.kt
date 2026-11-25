package com.donuts.game

/**
 * The quiet drought assist (pure, unit tested). After each of Steven's chains, once the board has stopped
 * moving, the game asks whether a long chain ([LONG] donuts, a bomb) could be dragged anywhere. When
 * [DRY_CHAINS] chains in a row have left no such chain, the refill is nudged: each new donut has a [NUDGE]
 * chance to copy a neighbour (never making a straight three, so nothing pops by itself), which grows the
 * groups already there. The first board with a long chain on it ends the nudge. Nothing on screen says so.
 */
internal object DroughtAssist {
    /** The chain length that counts as long: a bomb. */
    const val LONG = 5
    /** Chains in a row without a long chain possible before the refill is nudged. */
    const val DRY_CHAINS = 3
    /** Chance that a refilled donut copies a neighbour while the nudge is on. */
    const val NUDGE = 0.75

    /** The dry count after a chain, given whether a [LONG] chain is now possible. Saturates at [DRY_CHAINS]. */
    fun after(dry: Int, longPossible: Boolean): Int =
        if (longPossible) 0 else (dry.coerceAtLeast(0) + 1).coerceAtMost(DRY_CHAINS)

    /** The refill nudge for dry count [dry]: [NUDGE] once the drought has lasted [DRY_CHAINS] chains, else 0. */
    fun nudge(dry: Int): Double = if (dry >= DRY_CHAINS) NUDGE else 0.0
}
