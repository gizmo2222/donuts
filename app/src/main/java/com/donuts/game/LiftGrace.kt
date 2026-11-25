package com.donuts.game

/**
 * Lift grace: when the finger leaves the board mid-chain, the chain waits a moment for it to come back
 * before it is released. A chain that would pop waits only long enough for a bounced touch, so a clear
 * never feels late; two donuts (which would only boop) wait longer; a tap or a closed ring goes at once.
 * Whether a new touch is close enough to take the chain back is [HitZone.target] from the chain's end.
 */
object LiftGrace {
    /** A chain that would pop: covers a finger that skips off the glass, short enough not to feel slow. */
    const val SHORT_MS = 120L
    /** A chain that would not pop yet: nothing is lost by waiting, so give a small finger time to come back. */
    const val LONG_MS = 450L

    /** How long a chain of [size] cells waits after the lift; 0 means release it now. */
    fun graceMs(size: Int, looped: Boolean, pops: Boolean): Long = when {
        size <= 1 || looped -> 0L
        pops -> SHORT_MS
        else -> LONG_MS
    }

    /** True once a chain lifted at [liftMs] (negative when none is waiting) has waited its [graceMs]. */
    fun expired(liftMs: Long, graceMs: Long, now: Long): Boolean = liftMs >= 0 && now - liftMs >= graceMs
}
