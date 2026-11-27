package com.donuts.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WindDownTest {

    @Test
    fun aFrameAddsItsGapAndALongGapAddsOnlyTheCap() {
        assertEquals(16L, WindDown.step(0L, 1_000L, 1_016L, 1_000L))
        assertEquals(500L + WindDown.STEP_CAP_MS, WindDown.step(500L, 1_000L, 9_000L, 8_000L))
    }

    @Test
    fun theFirstFrameAndATimeThatGoesBackAddNothing() {
        assertEquals(40L, WindDown.step(40L, -1L, 1_000L, 1_000L))
        assertEquals(40L, WindDown.step(40L, 1_000L, 1_000L, 1_000L))
        assertEquals(40L, WindDown.step(40L, 1_000L, 900L, 900L))
    }

    @Test
    fun theClockStopsAMinuteAfterTheLastTouch() {
        val last = 10_000L
        val atMinute = last + WindDown.ACTIVE_MS
        assertEquals(16L, WindDown.step(0L, atMinute - 16, atMinute, last))
        assertEquals(0L, WindDown.step(0L, atMinute, atMinute + 16, last))
    }

    @Test
    fun theCardIsDueOnceTheTimeHasPassedUnlessOffOrShown() {
        val fifteen = 15 * 60_000L
        assertFalse(WindDown.due(fifteen - 1, 15, false))
        assertTrue(WindDown.due(fifteen, 15, false))
        assertFalse(WindDown.due(fifteen, 15, true))
        assertFalse(WindDown.due(Long.MAX_VALUE / 2, 0, false))
        assertFalse(WindDown.due(fifteen, 30, false))
    }

    @Test
    fun twentyMinutesAwayIsAFreshStart() {
        assertFalse(WindDown.freshAfter(WindDown.AWAY_RESET_MS - 1))
        assertTrue(WindDown.freshAfter(WindDown.AWAY_RESET_MS))
    }

    @Test
    fun theChoicesAreOffFifteenAndThirtyWithALabelEach() {
        assertEquals(listOf(0, 15, 30), WindDown.OPTIONS.toList())
        assertEquals(WindDown.OPTIONS.size, WindDown.LABELS.size)
        assertEquals("Off", WindDown.LABELS[0])
    }
}
