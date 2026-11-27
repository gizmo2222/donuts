package com.donuts.game

import kotlin.math.max

// Frame pacing for the render thread: one frame every 1/60 s against a nanosecond deadline. A frame that runs
// long moves the deadline to now, so the thread never bursts frames to catch up (pure, unit tested).

internal object FramePacer {
    const val FRAME_NS = 16_666_667L

    /** The deadline for the frame after one whose deadline was [deadline], measured at [now] (both nanoTime). */
    fun next(deadline: Long, now: Long): Long {
        val n = deadline + FRAME_NS
        return if (n - now > 0) n else now
    }

    /** How long to sleep from [now] until [deadline]; none when it has passed. */
    fun waitNs(deadline: Long, now: Long): Long = max(0L, deadline - now)
}
