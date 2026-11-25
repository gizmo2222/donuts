package com.donuts.game

import android.graphics.Bitmap
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

/** The toppings tab and picker, wearing a topping, the reveal, and the baked art. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ToppingsPageTest : GameInputTestBase() {

    private fun openWith(friends: Int, toppings: Int): GameView {
        val v = planted()
        v.prefs.friendsMet = friends
        v.prefs.toppingsEarned = toppings
        openStickers(v)
        return v
    }

    private fun paint(v: GameView) = TestViews.frames(v, TestViews.canvas(v), 2)

    private fun pixels(b: Bitmap): IntArray { val p = IntArray(b.width * b.height); b.getPixels(p, 0, b.width, 0, 0, b.width, b.height); return p }

    @Test fun theToppingsTabShowsWithToppingsAndNoFriends() {
        val v = openWith(0, 2)
        assertEquals(listOf(0, 2), v.panelTabPages())
        tap(v, v.toppingsTabRect)
        assertEquals(2, v.stickersPage)
        paint(v)
        tap(v, v.stickersTabRect)
        assertEquals(0, v.stickersPage)
    }

    @Test fun threeTabsShareTheRowWithoutOverlap() {
        val v = openWith(3, 3)
        assertEquals(listOf(0, 1, 2), v.panelTabPages())
        v.placePanelTabs()
        assertTrue(v.stickersTabRect.right <= v.friendsTabRect.left)
        assertTrue(v.friendsTabRect.right <= v.toppingsTabRect.left)
        assertTrue(v.toppingsTabRect.right <= v.stickerPanelRect.right)
        tap(v, v.toppingsTabRect); assertEquals(2, v.stickersPage)
        tap(v, v.friendsTabRect); assertEquals(1, v.stickersPage)
        paint(v)
    }

    @Test fun noToppingsEarnedMeansNoToppingsTab() {
        val v = openWith(2, 0)
        assertEquals(listOf(0, 1), v.panelTabPages())
        v.stickersPage = 2
        assertEquals(0, v.panelPageNow())
    }

    @Test fun aTileTapWearsThatToppingAndAnEmptyTileDoesNothing() {
        val v = openWith(0, 2)
        tap(v, v.toppingsTabRect)
        tap(v, v.stickerRects[1])
        assertEquals(Topping.SPRINKLES, v.topping)
        assertEquals("sprinkles", v.prefs.topping)
        assertEquals(1, v.toppingTapIdx)
        paint(v)
        tap(v, v.stickerRects[2]); assertEquals(Topping.CHOC_CHIPS, v.topping)
        tap(v, v.stickerRects[0]); assertEquals(Topping.PLAIN, v.topping)
        tap(v, v.stickerRects[3])
        assertEquals(Topping.PLAIN, v.topping)
        assertTrue(v.stickersOpen)
    }

    @Test fun aSavedButUnearnedKeyDrawsPlain() {
        val v = TestViews.quietView()
        v.prefs.topping = "rainbow"
        assertEquals(Topping.PLAIN, ToppingRules.chosen(v.prefs.topping, v.prefs.toppingsEarned))
        assertEquals(Topping.PLAIN, TestViews.gameView().topping)
    }

    @Test fun fiftyDonutsStartsOneRevealEarnsOneAndWearsIt() {
        val v = TestViews.quietView()
        val now = TestViews.now()
        v.prefs.lifetimeDonuts = 49
        v.checkToppings(now); assertEquals(-1L, v.toppingFlyMs)
        v.prefs.lifetimeDonuts = 50
        v.checkToppings(now + 16)
        assertEquals(now + 16, v.toppingFlyMs)
        assertEquals(1, v.prefs.toppingsEarned)
        assertEquals(Topping.SPRINKLES, v.topping)
        v.checkToppings(now + 32)
        assertEquals(1, v.prefs.toppingsEarned)
        TestViews.frames(v, TestViews.canvas(v), 3)
    }

    @Test fun aBigJumpRevealsOneAtATime() {
        val v = TestViews.quietView()
        val now = TestViews.now()
        v.prefs.lifetimeDonuts = 400
        v.checkToppings(now); assertEquals(Topping.SPRINKLES, v.toppingFlyTop)
        v.toppingFlyMs = -1L
        v.checkToppings(now + 100); assertEquals(Topping.CHOC_CHIPS, v.toppingFlyTop)
        v.toppingFlyMs = -1L
        v.checkToppings(now + 200); assertEquals(Topping.STARS, v.toppingFlyTop)
        v.toppingFlyMs = -1L
        v.checkToppings(now + 300); assertEquals(-1L, v.toppingFlyMs)
        assertEquals(3, v.prefs.toppingsEarned)
    }

    @Test fun theRevealWaitsForAFriendFlyingAndAPanel() {
        val v = TestViews.quietView()
        val now = TestViews.now()
        v.prefs.lifetimeDonuts = 50
        v.friendFlyMs = now
        v.checkToppings(now); assertEquals(-1L, v.toppingFlyMs)
        v.friendFlyMs = -1L; v.stickersAnim = 0.5f
        v.checkToppings(now); assertEquals(-1L, v.toppingFlyMs)
    }

    @Test fun everyToppingBakesDifferentPixelsFromPlain() {
        val v = TestViews.gameView()
        val i = DonutType.STRAWBERRY.ordinal * 2
        v.wearTopping(Topping.PLAIN)
        val plain = pixels(v.pieceSprites[i]!!)
        for (t in Topping.values().filter { it != Topping.PLAIN }) {
            v.wearTopping(t)
            assertFalse(t.name, pixels(v.pieceSprites[i]!!).contentEquals(plain))
        }
        // the golden ball never wears one
        v.wearTopping(Topping.PLAIN)
        val ball = pixels(v.ballSprites[0]!!)
        v.wearTopping(Topping.HEARTS)
        assertTrue(pixels(v.ballSprites[0]!!).contentEquals(ball))
    }

    @Test fun everyToppingPaintsAtBothBoardSizes() {
        for (grid in intArrayOf(6, 8)) {
            val v = TestViews.gameView(grid = grid)
            for (t in Topping.values()) { v.wearTopping(t); TestViews.frames(v, TestViews.canvas(v), 1) }
        }
        assertEquals("Mini balls", toppingName(Topping.MINI_BALLS))
    }
}
