package com.donuts.game

/**
 * Photosensitivity limit for the full-screen flashes (the New game burst and the big-chain tint), pure and
 * unit tested. Xbox guideline 118 and WCAG 2.3.1 fail more than three flashes a second over a large part of
 * the screen, with saturated red the riskiest. Both flashes share one clock: a new one starts only when the
 * last started at least [MIN_GAP_MS] ago, so any one second holds at most three. A refused flash is skipped;
 * the reset or the clear it belonged to still happens.
 */
object FlashGate {
    /** A third of a second, rounded up: starts this far apart fit at most three into any 1000 ms. */
    const val MIN_GAP_MS = 334L

    /** True when a full-screen flash may start at [now], the last one having started at [lastStartMs] (or -1 for none). */
    fun allows(lastStartMs: Long, now: Long): Boolean = lastStartMs < 0L || now - lastStartMs >= MIN_GAP_MS

    /** WCAG's "saturated red": red is 80% or more of R + G + B. No flash may use such a colour. */
    fun saturatedRed(r: Int, g: Int, b: Int): Boolean = r + g + b > 0 && r * 5 >= (r + g + b) * 4
}
