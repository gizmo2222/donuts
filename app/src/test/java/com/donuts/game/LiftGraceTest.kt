package com.donuts.game

import org.junit.Assert.*
import org.junit.Test

/** Unit tests for [LiftGrace] (no Android needed). */
class LiftGraceTest {
    @Test fun aTapAndAClosedRingAreReleasedAtOnce() {
        assertEquals(0L, LiftGrace.graceMs(0, looped = false, pops = false))
        assertEquals(0L, LiftGrace.graceMs(1, looped = false, pops = false))
        assertEquals(0L, LiftGrace.graceMs(6, looped = true, pops = true))
    }

    @Test fun twoDonutsWaitLongAndAPoppingChainWaitsShort() {
        assertEquals(LiftGrace.LONG_MS, LiftGrace.graceMs(2, looped = false, pops = false))
        assertEquals(LiftGrace.SHORT_MS, LiftGrace.graceMs(3, looped = false, pops = true))
        assertEquals(LiftGrace.SHORT_MS, LiftGrace.graceMs(9, looped = false, pops = true))
        // a long chain that would not pop is treated like two donuts: nothing is lost by waiting
        assertEquals(LiftGrace.LONG_MS, LiftGrace.graceMs(4, looped = false, pops = false))
    }

    @Test fun theShortWaitIsShorterAndNeitherFeelsLikeAStall() {
        assertTrue(LiftGrace.SHORT_MS < LiftGrace.LONG_MS)
        assertTrue(LiftGrace.SHORT_MS in 80L..150L)
        assertTrue(LiftGrace.LONG_MS in 300L..600L)
    }

    @Test fun theWaitExpiresExactlyAtItsLengthAndNeverWithoutALift() {
        assertFalse(LiftGrace.expired(1000L, 120L, 1119L))
        assertTrue(LiftGrace.expired(1000L, 120L, 1120L))
        assertTrue(LiftGrace.expired(0L, 120L, 5000L))
        assertFalse(LiftGrace.expired(-1L, 120L, 5000L))
        assertFalse(LiftGrace.expired(-1L, 0L, 0L))
    }
}
