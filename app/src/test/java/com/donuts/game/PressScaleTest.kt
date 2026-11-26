package com.donuts.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PressScaleTest {
    @Test
    fun theDipStartsLowAndEasesBackToOne() {
        assertEquals(PressScale.LOW, PressScale.at(0, 150), 1e-6f)
        assertEquals(1f, PressScale.at(150, 150), 1e-6f)
        assertEquals(1f, PressScale.at(10_000, 150), 1e-6f)
        assertTrue("fast start", PressScale.at(75, 150) > 0.98f)
        var last = 0f
        for (ms in 0..150L) {
            val s = PressScale.at(ms, 150)
            assertTrue("only rises at $ms", s >= last); last = s
        }
    }

    @Test
    fun anUnpressedButtonAndTimeBeforeTheTapStayPut() {
        assertEquals(1f, PressScale.at(0, 150, pressed = false), 0f)
        assertEquals(PressScale.LOW, PressScale.at(-20, 150), 1e-6f)
    }

    @Test
    fun theDipIsTheQuintEaseOut() {
        for (ms in listOf(0L, 30L, 90L, 149L)) {
            val x = 1f - ms / 150f
            assertEquals(0.86f + 0.14f * (1f - x * x * x * x * x), PressScale.at(ms, 150), 0f)
        }
    }
}
