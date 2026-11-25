package com.donuts.game

import org.junit.Assert.*
import org.junit.Test

/** The donut pal's idle pose: a hop that squashes on landing, blinks now and then, and stands still under reduced motion. */
class PalMotionTest {

    @Test fun reducedMotionAlwaysStandsStill() {
        for (t in longArrayOf(0L, 350L, 700L, 1234L, 99_999L)) assertEquals(PalMotion.STILL, PalMotion.at(t, true))
        assertEquals(PalMotion.STILL, PalMotion.at(-5L, false))
    }

    @Test fun theHopStaysLowAndTheSquashKeepsItsSize() {
        for (t in 0L until 20_000L step 7L) {
            val p = PalMotion.at(t, false)
            assertTrue("bob $t", p.bob in 0f..PalMotion.BOB_HEIGHT)
            assertTrue("squash $t", p.squashX in 0.9f..1.1f && p.squashY in 0.9f..1.1f)
            assertEquals("area $t", 1f, p.squashX * p.squashY, 0.01f)
            assertTrue("blink $t", p.blink in 0f..1f)
            assertTrue("look $t", p.look in -1f..1f)
        }
    }

    @Test fun itLandsSquashedAndStretchesAtTheTop() {
        val land = PalMotion.at(0L, false)
        assertEquals(0f, land.bob, 1e-4f)
        assertTrue(land.squashX > 1.05f && land.squashY < 0.95f)
        val top = PalMotion.at(PalMotion.BOB_MS / 2, false)
        assertEquals(PalMotion.BOB_HEIGHT, top.bob, 1e-3f)
        assertTrue(top.squashX < 1f && top.squashY > 1f)
    }

    @Test fun itBlinksBrieflyOnceEveryFewSeconds() {
        var shut = 0; var blinks = 0; var wasShut = false
        val span = PalMotion.BLINK_EVERY_MS * 6
        for (t in 0L until span) {
            val s = PalMotion.at(t, false).blink > 0.5f
            if (s) shut++
            if (s && !wasShut) blinks++
            wasShut = s
        }
        assertEquals(6, blinks)
        assertTrue(shut in 1..(PalMotion.BLINK_MS * 6).toInt())
        assertEquals(1f, PalMotion.at(PalMotion.BLINK_MS / 2, false).blink, 0.01f)
    }

    @Test fun theGlanceSwingsBothWays() {
        val looks = (0L until PalMotion.LOOK_MS step 50L).map { PalMotion.at(it, false).look }
        assertTrue(looks.max() > 0.95f && looks.min() < -0.95f)
    }
}
