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

    @Test fun theReactionGrowsWithWhatStevenDid() {
        assertEquals(PalReaction.NONE, PalMotion.reactionFor(1, PowerUp.NONE))
        assertEquals(PalReaction.NONE, PalMotion.reactionFor(2, PowerUp.NONE))
        assertEquals(PalReaction.SMILE, PalMotion.reactionFor(3, PowerUp.NONE))
        assertEquals(PalReaction.CHEER, PalMotion.reactionFor(4, PowerUp.NONE))
        for (p in PowerUp.values().filter { it != PowerUp.NONE }) assertEquals(PalReaction.WOW, PalMotion.reactionFor(6, p))
        val order = PalReaction.values().toList()
        for (i in 1 until order.size) assertTrue(order[i].jump > order[i - 1].jump && order[i].ms > order[i - 1].ms)
    }

    @Test fun aWeakerReactionNeverCutsAStrongerOneShort() {
        assertTrue(PalMotion.replaces(PalReaction.NONE, -1L, PalReaction.SMILE, 0L))
        assertTrue(PalMotion.replaces(PalReaction.SMILE, 0L, PalReaction.WOW, 100L))
        assertTrue(PalMotion.replaces(PalReaction.CHEER, 0L, PalReaction.CHEER, 100L))
        assertFalse(PalMotion.replaces(PalReaction.PARTY, 0L, PalReaction.WOW, 100L))
        assertTrue(PalMotion.replaces(PalReaction.PARTY, 0L, PalReaction.SMILE, PalReaction.PARTY.ms))
        assertFalse(PalMotion.replaces(PalReaction.NONE, -1L, PalReaction.NONE, 0L))
    }

    @Test fun aReactionHopsHigherThrowsTheArmsUpAndSettlesBackToIdle() {
        val start = 7_000L
        for (r in PalReaction.values().filter { it != PalReaction.NONE }) {
            var top = 0f; var arms = 0f; var joy = 0f
            for (t in start until start + r.ms step 5L) {
                val p = PalMotion.at(t, false, r, start)
                assertTrue("$r bob $t", p.bob in 0f..PalMotion.MAX_LIFT)
                assertEquals("$r area $t", 1f, p.squashX * p.squashY, 0.015f)
                assertTrue("$r stretch $t", p.squashY <= PalMotion.MAX_STRETCH + 1e-4f)
                assertTrue("$r tilt $t", Math.abs(p.tilt) <= PalMotion.MAX_TILT + 1e-4f)
                assertTrue("$r arms $t", p.arms in 0f..1f && p.joy in 0f..1f)
                top = maxOf(top, p.bob); arms = maxOf(arms, p.arms); joy = maxOf(joy, p.joy)
            }
            assertTrue("$r hops above the idle bob", top > PalMotion.BOB_HEIGHT && top <= r.jump + 1e-4f)
            assertEquals("$r arms", r.arms, arms, 0.02f)
            assertEquals("$r smile", r.joy, joy, 0.02f)
            // It starts from the idle pose and hands back to it, so nothing jumps
            assertEquals(PalMotion.at(start, false).bob, PalMotion.at(start, false, r, start).bob, 1e-4f)
            assertEquals(PalMotion.at(start + r.ms, false), PalMotion.at(start + r.ms, false, r, start))
            assertEquals(PalMotion.at(start - 1, false), PalMotion.at(start - 1, false, r, start))
        }
        assertTrue(PalMotion.at(start + 750L, false, PalReaction.PARTY, start).joy > PalMotion.HAPPY_EYES)
    }

    @Test fun reducedMotionHoldsAStillCheerWhileTheReactionLasts() {
        assertTrue(PalMotion.STILL.look > 0f && PalMotion.STILL.joy > 0f && PalMotion.STILL.blink == 0f)
        for (t in 1_000L until 1_000L + PalReaction.WOW.ms step 50L) {
            assertEquals(PalMotion.STILL.copy(arms = 1f, joy = 1f), PalMotion.at(t, true, PalReaction.WOW, 1_000L))
        }
        assertEquals(PalMotion.STILL, PalMotion.at(1_000L + PalReaction.WOW.ms, true, PalReaction.WOW, 1_000L))
    }
}
