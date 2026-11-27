package com.donuts.game

import kotlin.math.min

/**
 * The grown-ups' rest reminder (off unless a parent picks a time). Play time only counts while
 * Steven is actually playing: a frame's gap is capped, and nothing counts once the board has
 * been left alone for a minute. When the time is up the game waits for a quiet moment and shows
 * one warm "time for a rest?" card; a tap closes it and play goes on. It never locks the game.
 */
internal object WindDown {
    /** The choices on the Grown-ups page, in minutes; 0 is off (the default). */
    val OPTIONS = intArrayOf(0, 15, 30)
    val LABELS  = arrayOf("Off", "15 min", "30 min")
    const val STEP_CAP_MS   = 250L            // a stalled or paused frame adds at most this
    const val ACTIVE_MS     = 60_000L         // no touch for this long: the clock stops
    const val AWAY_RESET_MS = 20L * 60_000L   // away from the game this long: a fresh start

    /** Play time after a frame at [now], the last one at [lastFrameMs] (-1 before the first). */
    fun step(playedMs: Long, lastFrameMs: Long, now: Long, lastActionMs: Long): Long {
        if (lastFrameMs < 0 || now <= lastFrameMs || now - lastActionMs > ACTIVE_MS) return playedMs
        return playedMs + min(now - lastFrameMs, STEP_CAP_MS)
    }

    /** Whether the rest card is due: a time is set, it has passed, and it has not shown yet. */
    fun due(playedMs: Long, limitMin: Int, shown: Boolean): Boolean =
        !shown && limitMin > 0 && playedMs >= limitMin * 60_000L

    /** Whether time away (surface gone to back) is long enough to start the count again. */
    fun freshAfter(awayMs: Long): Boolean = awayMs >= AWAY_RESET_MS
}
