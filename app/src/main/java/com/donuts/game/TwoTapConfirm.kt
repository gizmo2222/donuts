package com.donuts.game

/** Two taps inside [windowMs] confirm; a stray single tap just arms and then times out. */
class TwoTapConfirm(val windowMs: Long) {
    private var armedAt = -1L

    /** True on the second tap inside the window (then disarms); otherwise arms and returns false. */
    fun tap(now: Long): Boolean {
        if (isArmed(now)) { armedAt = -1L; return true }
        armedAt = now
        return false
    }

    fun isArmed(now: Long): Boolean = armedAt >= 0 && (now - armedAt) < windowMs

    /** 1 at the first tap, falling to 0 when the window closes; 0 when not armed. */
    fun progress(now: Long): Float =
        if (!isArmed(now)) 0f else (1f - (now - armedAt).toFloat() / windowMs).coerceIn(0f, 1f)

    fun cancel() { armedAt = -1L }
}
