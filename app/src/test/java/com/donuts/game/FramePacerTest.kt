package com.donuts.game

import org.junit.Assert.assertEquals
import org.junit.Test

/** The render thread's frame pacing: a deadline every 1/60 s, never a burst to catch up (B36). */
class FramePacerTest {
    private val f = FramePacer.FRAME_NS

    @Test fun aFrameIsASixtiethOfASecond() {
        assertEquals(16_666_667L, f)
        assertEquals(1_000_000_000L, f * 60 - 20)        // sixty frames are a second, to the nanosecond
    }

    @Test fun anOnTimeFrameMovesTheDeadlineOneFrameOnAndWaitsTheRest() {
        val start = 5_000_000_000L
        val next = FramePacer.next(start, start + 4_000_000L)        // the frame took 4 ms
        assertEquals(start + f, next)
        assertEquals(f - 4_000_000L, FramePacer.waitNs(next, start + 4_000_000L))
    }

    @Test fun theDeadlineKeepsItsBeatInsteadOfDriftingByTheDrawTime() {
        var d = 0L
        for (i in 1..600) d = FramePacer.next(d, d + 10_000_000L)    // ten seconds of 10 ms frames
        assertEquals(600 * f, d)
    }

    @Test fun aLongFrameResyncsToNowSoNoFramesBurstAfterIt() {
        val late = 3 * f                                               // the frame ran three frames long
        assertEquals(late, FramePacer.next(0L, late))
        assertEquals(0L, FramePacer.waitNs(late, late))
        assertEquals(late + f, FramePacer.next(late, late + 1L))     // the frame after it is paced again
    }

    @Test fun aFrameEndingExactlyOnTheNextDeadlineDoesNotWait() {
        assertEquals(f, FramePacer.next(0L, f))
        assertEquals(0L, FramePacer.waitNs(f, f))
        assertEquals(f - 1L, FramePacer.waitNs(f, 1L))
        assertEquals(0L, FramePacer.waitNs(f, f + 5L))                // never a negative sleep
    }
}
