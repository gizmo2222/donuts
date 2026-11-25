package com.donuts.game

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ToppingRulesTest {

    @Test fun plainComesFirstAndKeysAreUniqueAndThresholdsStrictlyRise() {
        val t = ToppingRules.TOPPINGS
        assertEquals(Topping.PLAIN, t.first())
        assertEquals(0, Topping.PLAIN.unlockAt)
        assertEquals(t.size - 1, ToppingRules.EARNABLE)
        assertEquals(t.size, t.map { it.key }.toSet().size)
        for (i in 1 until t.size) assertTrue(t[i].unlockAt > t[i - 1].unlockAt)
        // the first one comes early, well before the friends run out (420), the last one long after
        assertTrue(t[1].unlockAt <= 100)
        assertTrue(t.last().unlockAt > 420)
    }

    @Test fun dueAtEachBoundary() {
        assertEquals(0, ToppingRules.due(0))
        assertEquals(0, ToppingRules.due(-10))
        for ((i, t) in ToppingRules.TOPPINGS.withIndex()) {
            if (t == Topping.PLAIN) continue
            assertEquals(i - 1, ToppingRules.due(t.unlockAt - 1))
            assertEquals(i, ToppingRules.due(t.unlockAt))
        }
        assertEquals(ToppingRules.EARNABLE, ToppingRules.due(Int.MAX_VALUE))
    }

    @Test fun nextGoesInOrderOneAtATime() {
        assertNull(ToppingRules.next(0, 0))
        assertNull(ToppingRules.next(49, 0))
        assertEquals(Topping.SPRINKLES, ToppingRules.next(50, 0))
        assertNull(ToppingRules.next(50, 1))
        // a big jump still reveals one at a time, in order
        assertEquals(Topping.SPRINKLES, ToppingRules.next(5_000, 0))
        assertEquals(Topping.CHOC_CHIPS, ToppingRules.next(5_000, 1))
        assertEquals(Topping.RAINBOW, ToppingRules.next(5_000, ToppingRules.EARNABLE - 1))
        assertNull(ToppingRules.next(5_000, ToppingRules.EARNABLE))
        assertNull(ToppingRules.next(5_000, 99))
        assertEquals(Topping.SPRINKLES, ToppingRules.next(50, -3))
    }

    @Test fun earnedToppingsStayAfterLifetimeDrops() {
        // a sticker reset zeroes lifetime donuts; what was earned is still offered and nothing is revealed again
        assertNull(ToppingRules.next(0, 3))
        assertEquals(listOf(Topping.PLAIN, Topping.SPRINKLES, Topping.CHOC_CHIPS, Topping.STARS), ToppingRules.unlocked(3))
        assertEquals(Topping.STARS, ToppingRules.chosen("stars", 3))
    }

    @Test fun unlockedAlwaysHoldsPlainAndClamps() {
        assertEquals(listOf(Topping.PLAIN), ToppingRules.unlocked(0))
        assertEquals(listOf(Topping.PLAIN), ToppingRules.unlocked(-2))
        assertEquals(ToppingRules.TOPPINGS, ToppingRules.unlocked(99))
        assertTrue(ToppingRules.isUnlocked(Topping.PLAIN, 0))
        assertFalse(ToppingRules.isUnlocked(Topping.SPRINKLES, 0))
        assertTrue(ToppingRules.isUnlocked(Topping.SPRINKLES, 1))
        assertFalse(ToppingRules.isUnlocked(Topping.CHOC_CHIPS, 1))
        assertTrue(ToppingRules.isUnlocked(Topping.RAINBOW, 99))
    }

    @Test fun chosenFallsBackToPlainUnlessKnownAndEarned() {
        for (t in ToppingRules.TOPPINGS) {
            assertEquals(t, ToppingRules.chosen(ToppingRules.encode(t), ToppingRules.EARNABLE))
        }
        assertEquals(Topping.PLAIN, ToppingRules.chosen(null, ToppingRules.EARNABLE))
        assertEquals(Topping.PLAIN, ToppingRules.chosen("", ToppingRules.EARNABLE))
        assertEquals(Topping.PLAIN, ToppingRules.chosen("bacon", ToppingRules.EARNABLE))
        assertEquals(Topping.PLAIN, ToppingRules.chosen("STARS", ToppingRules.EARNABLE))   // keys, not enum names
        assertEquals(Topping.PLAIN, ToppingRules.chosen("hearts", 3))                      // not earned yet
        assertEquals(Topping.HEARTS, ToppingRules.chosen("hearts", 4))
    }

    @Test fun savedChoiceIsTheKeyNotTheOrdinal() {
        assertEquals("sprinkles", ToppingRules.encode(Topping.SPRINKLES))
        assertEquals("plain", ToppingRules.encode(Topping.PLAIN))
    }

    @Test fun prefsRoundTripAndClamp() {
        val prefs = Prefs(TestViews.app())
        assertEquals(0, prefs.toppingsEarned)
        assertNull(prefs.topping)
        prefs.toppingsEarned = 4
        prefs.topping = ToppingRules.encode(Topping.HEARTS)
        val again = Prefs(TestViews.app())
        assertEquals(4, again.toppingsEarned)
        assertEquals(Topping.HEARTS, ToppingRules.chosen(again.topping, again.toppingsEarned))
        prefs.toppingsEarned = 99
        assertEquals(ToppingRules.EARNABLE, prefs.toppingsEarned)
        prefs.toppingsEarned = -1
        assertEquals(0, prefs.toppingsEarned)
    }

    @Test fun resetStickersKeepsToppings() {
        val v = TestViews.gameView()
        v.prefs.toppingsEarned = 2
        v.prefs.topping = "chips"
        v.prefs.lifetimeDonuts = 400
        v.resetStickers()
        assertEquals(0, v.prefs.lifetimeDonuts)
        assertEquals(2, v.prefs.toppingsEarned)
        assertEquals(Topping.CHOC_CHIPS, ToppingRules.chosen(v.prefs.topping, v.prefs.toppingsEarned))
    }
}
