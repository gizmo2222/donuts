package com.donuts.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The sprinkle party meter's rules: what fills it, when a party starts and ends, what it does to golden. */
class PartyMeterTest {

    @Test fun everyClearPoursItsDonutsInShortChainsAndCascadesToo() {
        var s = PartyState()
        s = PartyMeter.after(s, 3, playerClear = true)
        s = PartyMeter.after(s, 4, playerClear = false)
        assertEquals(PartyState(7, 0), s)
        assertFalse(s.partyOn)
    }

    @Test fun reachingFillEmptiesTheMeterAndStartsAPartyOfFourChains() {
        val before = PartyState(PartyMeter.FILL - 2, 0)
        val after = PartyMeter.after(before, 5, playerClear = true)
        assertEquals(PartyState(0, PartyMeter.CHAINS), after)
        assertTrue(PartyMeter.started(before, after))
        assertFalse(PartyMeter.started(after, PartyMeter.after(after, 3, true)))
    }

    @Test fun exactlyFillStartsTheParty() {
        assertTrue(PartyMeter.after(PartyState(PartyMeter.FILL - 3, 0), 3, false).partyOn)
        assertFalse(PartyMeter.after(PartyState(PartyMeter.FILL - 4, 0), 3, false).partyOn)
    }

    @Test fun aCascadeCanStartThePartyToo() {
        val before = PartyState(PartyMeter.FILL - 1, 0)
        assertTrue(PartyMeter.started(before, PartyMeter.after(before, 3, playerClear = false)))
    }

    @Test fun onlyThePlayersChainsUseUpThePartyAndItEndsAfterFour() {
        var s = PartyState(0, PartyMeter.CHAINS)
        s = PartyMeter.after(s, 9, playerClear = false)
        assertEquals("a cascade costs no party chain", PartyMeter.CHAINS, s.left)
        repeat(PartyMeter.CHAINS) { s = PartyMeter.after(s, 3, playerClear = true) }
        assertEquals(PartyState(0, 0), s)
        assertFalse(s.partyOn)
    }

    @Test fun theMeterDoesNotFillDuringAParty() {
        val s = PartyMeter.after(PartyState(0, 2), 50, playerClear = true)
        assertEquals(0, s.meter)
    }

    @Test fun aNegativeCountPoursNothing() {
        assertEquals(PartyState(5, 0), PartyMeter.after(PartyState(5, 0), -4, true))
    }

    @Test fun goldenChanceIsRaisedOnlyDuringTheParty() {
        assertEquals(GameBoard.GOLDEN_CHANCE, PartyMeter.goldenChance(PartyState(30, 0)), 0.0)
        assertEquals(PartyMeter.GOLDEN, PartyMeter.goldenChance(PartyState(0, 1)), 0.0)
        assertTrue(PartyMeter.GOLDEN > GameBoard.GOLDEN_CHANCE * 5)
    }

    @Test fun fillShowsDonutsPouredOrPartyChainsLeft() {
        assertEquals(0f, PartyMeter.fill(PartyState()), 0f)
        assertEquals(0.5f, PartyMeter.fill(PartyState(PartyMeter.FILL / 2, 0)), 1e-6f)
        assertEquals(1f, PartyMeter.fill(PartyState(0, PartyMeter.CHAINS)), 0f)
        assertEquals(0.25f, PartyMeter.fill(PartyState(0, 1)), 1e-6f)
    }

    @Test fun restoreClampsBadSaves() {
        assertEquals(PartyState(PartyMeter.FILL - 1, PartyMeter.CHAINS), PartyMeter.restore(9999, 99))
        assertEquals(PartyState(0, 0), PartyMeter.restore(-5, -1))
        assertEquals(PartyState(12, 2), PartyMeter.restore(12, 2))
    }

    @Test fun thePartyLabelFitsTheBannerAndRainsUnderItsOwnColour() {
        assertEquals(ConfettiStyle.RAIN, PartyMeter.LOOK.style)
        assertTrue(PartyMeter.LOOK.banner in 0 until CelebrationMix.BANNER_COLOURS)
        assertFalse(PartyMeter.LOOK.golden)
    }
}
