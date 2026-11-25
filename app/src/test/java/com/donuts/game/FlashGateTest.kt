package com.donuts.game

import org.junit.Assert.*
import org.junit.Test

/** Unit tests for [FlashGate] (no Android needed). */
class FlashGateTest {

    /** The start times a gate lets through when something asks for a flash every [everyMs] for [forMs]. */
    private fun starts(everyMs: Long, forMs: Long): List<Long> {
        val out = mutableListOf<Long>(); var last = -1L
        var t = 0L
        while (t <= forMs) { if (FlashGate.allows(last, t)) { out += t; last = t }; t += everyMs }
        return out
    }

    private fun mostInOneSecond(starts: List<Long>) =
        starts.maxOfOrNull { s -> starts.count { it >= s && it < s + 1000L } } ?: 0

    @Test fun theFirstFlashIsAlwaysAllowed() = assertTrue(FlashGate.allows(-1L, 0L))

    @Test fun aFlashInsideTheGapIsRefusedAndOneAtTheGapIsAllowed() {
        assertFalse(FlashGate.allows(1000L, 1000L + FlashGate.MIN_GAP_MS - 1))
        assertTrue(FlashGate.allows(1000L, 1000L + FlashGate.MIN_GAP_MS))
    }

    @Test fun askingEveryFrameForFiveSecondsGivesAtMostThreeASecond() {
        for (every in longArrayOf(1L, 16L, 50L, 111L, 167L, 250L, 333L)) {
            val s = starts(every, 5000L)
            assertTrue("every $every ms: ${mostInOneSecond(s)}", mostInOneSecond(s) <= 3)
        }
    }

    @Test fun slowerRequestsAreNeverHeldBack() = assertEquals(11, starts(500L, 5000L).size)

    @Test fun saturatedRedIsRedAtEightyPercentOrMore() {
        assertTrue(FlashGate.saturatedRed(255, 0, 0))
        assertTrue(FlashGate.saturatedRed(200, 25, 25))
        assertFalse(FlashGate.saturatedRed(200, 30, 30))
        assertFalse(FlashGate.saturatedRed(255, 255, 255))
        assertFalse(FlashGate.saturatedRed(0, 0, 0))
    }

    @Test fun noChainFlashColourIsSaturatedRed() {
        for (t in DonutType.values()) {
            val c = t.bodyColor
            assertFalse(t.name, FlashGate.saturatedRed((c shr 16) and 255, (c shr 8) and 255, c and 255))
        }
    }
}
