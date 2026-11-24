package com.donuts.game

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class FriendRulesTest {

    @Test fun twelveFriendsWithUniqueNamesAndThreeBalls() {
        assertEquals(12, FriendRules.FRIENDS.size)
        assertEquals(FriendRules.FRIEND_COUNT, FriendRules.FRIENDS.size)
        assertEquals(12, FriendRules.FRIENDS.map { it.name }.toSet().size)
        assertEquals(listOf("Bouncy", "Kicky", "Zoomy"), FriendRules.FRIENDS.filter { it.isBall }.map { it.name })
        assertEquals("Sprinkle", FriendRules.FRIENDS.first().name)
        assertEquals("Captain Glaze", FriendRules.FRIENDS.last().name)
    }

    @Test fun revealAtStartsAtOneAndStrictlyRises() {
        assertEquals(12, FriendRules.REVEAL_AT.size)
        assertEquals(1, FriendRules.REVEAL_AT[0])
        for (i in 1 until 12) assertTrue(FriendRules.REVEAL_AT[i] > FriendRules.REVEAL_AT[i - 1])
    }

    @Test fun dueAtEachBoundary() {
        assertEquals(0, FriendRules.due(0))
        assertEquals(0, FriendRules.due(9))
        assertEquals(1, FriendRules.due(10))
        assertEquals(1, FriendRules.due(19))
        assertEquals(2, FriendRules.due(20))
        assertEquals(11, FriendRules.due(419))
        assertEquals(12, FriendRules.due(420))
        assertEquals(12, FriendRules.due(Int.MAX_VALUE))
        assertEquals(0, FriendRules.due(-5))
        assertEquals(0, FriendRules.slotsEver(-1))
    }

    @Test fun everyRevealLandsOnASlotFill() {
        for ((i, t) in FriendRules.REVEAL_AT.withIndex()) {
            assertTrue(StickerRules.slotJustFilled(t * 10 - 1, t * 10))
            assertEquals(i + 1, FriendRules.due(t * 10))
            assertEquals(i, FriendRules.due(t * 10 - 1))
        }
    }

    @Test fun nextGoesInOrderOneAtATime() {
        assertNull(FriendRules.next(0, 0))
        assertEquals(0, FriendRules.next(10, 0))
        assertNull(FriendRules.next(10, 1))
        // a big jump still reveals one at a time, in order
        assertEquals(0, FriendRules.next(200, 0))
        assertEquals(1, FriendRules.next(200, 1))
        assertEquals(7, FriendRules.next(200, 7))
        assertNull(FriendRules.next(200, 8))
        assertNull(FriendRules.next(-10, -3))
        assertEquals(0, FriendRules.next(10, -3))
    }

    @Test fun nothingAfterAllTwelve() {
        assertEquals(11, FriendRules.next(10_000, 11))
        assertNull(FriendRules.next(10_000, 12))
        assertNull(FriendRules.next(10_000, 99))
    }

    @Test fun friendsMetRoundTripsAndClamps() {
        val prefs = Prefs(TestViews.app())
        assertEquals(0, prefs.friendsMet)
        prefs.friendsMet = 7
        assertEquals(7, Prefs(TestViews.app()).friendsMet)
        prefs.friendsMet = 99
        assertEquals(12, prefs.friendsMet)
        prefs.friendsMet = -4
        assertEquals(0, prefs.friendsMet)
    }
}
