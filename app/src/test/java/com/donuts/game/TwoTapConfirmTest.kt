package com.donuts.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TwoTapConfirmTest {
    @Test fun firstTapArmsOnly() {
        val c = TwoTapConfirm(1500)
        assertFalse(c.tap(1000))
        assertTrue(c.isArmed(1000))
    }

    @Test fun secondTapInsideWindowConfirmsThenDisarms() {
        val c = TwoTapConfirm(1500)
        c.tap(1000)
        assertTrue(c.tap(2000))
        assertFalse(c.isArmed(2000))
        assertFalse(c.tap(2100))   // a third tap arms again
    }

    @Test fun secondTapAfterWindowRearmsInstead() {
        val c = TwoTapConfirm(1500)
        c.tap(1000)
        assertFalse(c.isArmed(2500))
        assertFalse(c.tap(2500))
        assertTrue(c.isArmed(2600))
        assertTrue(c.tap(2700))
    }

    @Test fun cancelDisarms() {
        val c = TwoTapConfirm(1500)
        c.tap(1000)
        c.cancel()
        assertFalse(c.isArmed(1100))
        assertFalse(c.tap(1200))
    }

    @Test fun progressRunsFromOneToZero() {
        val c = TwoTapConfirm(1000)
        assertEquals(0f, c.progress(0), 0f)
        c.tap(100)
        assertEquals(1f, c.progress(100), 0.001f)
        assertEquals(0.5f, c.progress(600), 0.001f)
        assertEquals(0f, c.progress(1100), 0f)
        assertEquals(0f, c.progress(5000), 0f)
    }
}
