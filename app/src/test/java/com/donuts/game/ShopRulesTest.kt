package com.donuts.game

import org.junit.Assert.*
import org.junit.Test

/** Which shop pieces lifetime donuts build, that a built piece is never taken away, and where each one sits. */
class ShopRulesTest {

    @Test fun piecesComeInOrderAtRisingLifetimes() {
        val at = ShopRules.PIECES.map { it.unlockAt }
        assertEquals(10, ShopRules.COUNT)
        assertEquals(at.sorted(), at)
        assertEquals(at.size, at.toSet().size)
        assertTrue(at.first() > 0)
        assertEquals(ShopRules.COUNT, ShopRules.PIECES.map { it.key }.toSet().size)
    }

    @Test fun dueCountsThePiecesReachedAndNothingBefore() {
        assertEquals(0, ShopRules.due(0))
        for ((i, piece) in ShopRules.PIECES.withIndex()) {
            assertEquals(i, ShopRules.due(piece.unlockAt - 1))
            assertEquals(i + 1, ShopRules.due(piece.unlockAt))
        }
        assertEquals(ShopRules.COUNT, ShopRules.due(Int.MAX_VALUE))
    }

    @Test fun keptNeverDropsBelowWhatWasSavedAndIgnoresBadSavedValues() {
        assertEquals(2, ShopRules.kept(ShopPiece.AWNING.unlockAt, 0))
        assertEquals(4, ShopRules.kept(0, 4))                         // Reset stickers zeroed lifetime: the shop stays
        assertEquals(3, ShopRules.kept(ShopPiece.WINDOW_DONUTS.unlockAt, 1))
        assertEquals(0, ShopRules.kept(0, -5))
        assertEquals(ShopRules.COUNT, ShopRules.kept(0, 99))
    }

    @Test fun shownAndNextFollowTheEarnedCount() {
        assertTrue(ShopRules.shown(0).isEmpty())
        assertTrue(ShopRules.shown(-3).isEmpty())
        assertEquals(listOf(ShopPiece.SIGN, ShopPiece.AWNING), ShopRules.shown(2))
        assertEquals(ShopRules.PIECES, ShopRules.shown(99))
        assertTrue(ShopRules.isShown(ShopPiece.SIGN, 1))
        assertFalse(ShopRules.isShown(ShopPiece.AWNING, 1))
        assertFalse(ShopRules.isShown(ShopPiece.SIGN, 0))
        assertEquals(ShopPiece.SIGN, ShopRules.next(0))
        assertEquals(ShopPiece.SIGN, ShopRules.next(-1))
        assertEquals(ShopPiece.SUN, ShopRules.next(ShopRules.COUNT - 1))
        assertNull(ShopRules.next(ShopRules.COUNT))
    }

    @Test fun everySpotLiesInTheSceneAndNoTwoPiecesShareAnyOfIt() {
        for (p in ShopRules.PIECES) {
            assertTrue(p.name, 0f <= p.left && p.left < p.right && p.right <= 1f)
            assertTrue(p.name, 0f <= p.top && p.top < p.bottom && p.bottom <= 1f)
        }
        // The lamp, the balls, the balloons and the sun are beside the shop, never on it
        for (p in listOf(ShopPiece.LAMP, ShopPiece.BALL, ShopPiece.BASKETBALL, ShopPiece.BALLOONS, ShopPiece.SUN))
            assertTrue(p.name, p.right <= ShopRules.SHOP_LEFT || p.left >= ShopRules.SHOP_RIGHT)
        // The sign, awning, window donuts and flower box are on the shop front; the roof donut stands on the roof
        for (p in listOf(ShopPiece.SIGN, ShopPiece.AWNING, ShopPiece.WINDOW_DONUTS, ShopPiece.FLOWERS))
            assertTrue(p.name, p.left >= ShopRules.SHOP_LEFT - 0.03f && p.right <= ShopRules.SHOP_RIGHT + 0.03f &&
                p.top >= ShopRules.SHOP_TOP && p.bottom <= ShopRules.GROUND_TOP)
        assertTrue(ShopPiece.ROOF_DONUT.bottom <= ShopRules.SHOP_TOP + 0.02f)
        val ps = ShopRules.PIECES
        for (i in ps.indices) for (j in i + 1 until ps.size) {
            val a = ps[i]; val b = ps[j]
            val overlap = a.left < b.right && b.left < a.right && a.top < b.bottom && b.top < a.bottom
            assertFalse("${a.name} and ${b.name}", overlap)
        }
    }

    @Test fun nothingPopsInWhenEverythingWasSeenOrBeforeTheDelay() {
        assertEquals(ShopReveal(4, 1f), ShopRules.revealAt(4, 4, 0))
        assertEquals(ShopReveal(4, 1f), ShopRules.revealAt(4, 4, 99_999))
        assertEquals(ShopReveal(2, 1f), ShopRules.revealAt(2, 4, ShopRules.REVEAL_DELAY_MS - 1))
        assertTrue(ShopRules.revealDone(4, 4, 0))
        assertFalse(ShopRules.revealDone(2, 4, 0))
    }

    @Test fun newPiecesPopInOneAtATimeAndSettleInTurn() {
        val d = ShopRules.REVEAL_DELAY_MS; val m = ShopRules.REVEAL_MS
        val first = ShopRules.revealAt(2, 4, d)
        assertEquals(3, first.shown); assertEquals(0f, first.pop, 1e-6f); assertEquals(2, first.settled)
        val mid = ShopRules.revealAt(2, 4, d + m / 2)
        assertEquals(3, mid.shown); assertEquals(0.5f, mid.pop, 0.01f); assertEquals(2, mid.settled)
        val second = ShopRules.revealAt(2, 4, d + m + m / 4)
        assertEquals(4, second.shown); assertEquals(0.25f, second.pop, 0.01f); assertEquals(3, second.settled)
        val done = ShopRules.revealAt(2, 4, d + 2 * m)
        assertEquals(ShopReveal(4, 1f), done); assertEquals(4, done.settled)
        assertFalse(ShopRules.revealDone(2, 4, d + 2 * m - 1))
        assertTrue(ShopRules.revealDone(2, 4, d + 2 * m))
        // The pop only ever rises while one piece arrives
        var last = -1f
        for (ms in d until d + m step 50) { val r = ShopRules.revealAt(2, 3, ms); assertEquals(3, r.shown); assertTrue(r.pop > last); last = r.pop }
    }

    @Test fun badSeenAndEarnedValuesAreClamped() {
        assertEquals(ShopReveal(3, 1f), ShopRules.revealAt(9, 3, ShopRules.REVEAL_DELAY_MS))   // seen above earned
        assertEquals(1, ShopRules.revealAt(-4, 2, ShopRules.REVEAL_DELAY_MS).shown)            // negative seen is 0
        assertEquals(ShopReveal(ShopRules.COUNT, 1f), ShopRules.revealAt(ShopRules.COUNT, 99, 0))
        assertEquals(ShopReveal(0, 1f), ShopRules.revealAt(0, -1, 5_000))
    }
}
