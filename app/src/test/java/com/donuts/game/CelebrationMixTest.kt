package com.donuts.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** CelebrationMix: no look twice in a row, a rare golden shower, a rare giggle, all from a seedable Random. */
class CelebrationMixTest {

    private fun run(seed: Int, n: Int): List<CelebrationLook> {
        val rnd = Random(seed)
        var last: CelebrationLook? = null
        return List(n) { CelebrationMix.next(rnd, last).also { last = it } }
    }

    @Test
    fun theSameSeedGivesTheSameLooks() {
        assertEquals(run(5, 50), run(5, 50))
        assertNotEquals(run(5, 50), run(6, 50))
    }

    @Test
    fun theStyleAlwaysChangesAndTheBannerChangesUnlessTheShowerWearsGold() {
        for (seed in 0 until 20) run(seed, 200).zipWithNext().forEach { (a, b) ->
            assertNotEquals(a.style, b.style)
            if (!b.golden) assertNotEquals(a.banner, b.banner)
            assertTrue(b.banner in 0 until CelebrationMix.BANNER_COLOURS)
        }
    }

    @Test
    fun everyStyleAndBannerColourTurnsUp() {
        val looks = run(1, 300)
        assertEquals(ConfettiStyle.values().toSet(), looks.map { it.style }.toSet())
        assertEquals((0 until CelebrationMix.BANNER_COLOURS).toSet(), looks.map { it.banner }.toSet())
    }

    @Test
    fun theGoldenShowerIsRareNeverTwiceInARowAndAlwaysGold() {
        val looks = run(2, 10_000)
        val share = looks.count { it.golden } / 10_000.0
        assertTrue("golden share $share", share in 0.06..0.12)
        looks.zipWithNext().forEach { (a, b) -> assertFalse(a.golden && b.golden) }
        assertTrue(looks.filter { it.golden }.all { it.banner == 0 })
    }

    @Test
    fun theGiggleIsAboutOneTapInTwelve() {
        val rnd = Random(3)
        val share = (0 until 12_000).count { CelebrationMix.giggle(rnd) } / 12_000.0
        assertTrue("giggle share $share", share in 0.06..0.11)
        assertTrue(CelebrationMix.giggle(TestViews.fixedRandom(0)))
        assertFalse(CelebrationMix.giggle(TestViews.fixedRandom(1)))
    }

    @Test
    fun aDieThatAlwaysRollsZeroGivesAGoldenShowerFirstThenAPlainOne() {
        val zero = TestViews.fixedRandom(0)
        val first = CelebrationMix.next(zero, null)
        assertEquals(CelebrationLook(ConfettiStyle.CANNONS, 0, golden = true), first)
        val second = CelebrationMix.next(zero, first)
        assertFalse(second.golden)
        assertEquals(ConfettiStyle.FOUNTAIN, second.style)
        assertEquals(1, second.banner)
    }
}
