package com.donuts.game

import android.graphics.Bitmap
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

/** Seasonal looks: the once-a-year reveal, the picker tile, taking the look off, and the season ending. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SeasonLookTest : GameInputTestBase() {

    private val sprite = DonutType.STRAWBERRY.ordinal * 2

    private fun pixels(b: Bitmap): IntArray { val p = IntArray(b.width * b.height); b.getPixels(p, 0, b.width, 0, 0, b.width, b.height); return p }

    /** Moves the view's calendar to [date] and makes the next check look at it at once. */
    private fun setDate(v: GameView, date: LocalDate) { v.today = { date }; v.seasonCheckMs = -1L }

    @Test fun theFirstSnowyDayRevealsTheLookOnceAndPutsItOn() {
        val v = TestViews.quietView()
        val now = TestViews.now()
        v.checkSeason(now); assertNull(v.season); assertEquals(-1L, v.toppingFlyMs)
        setDate(v, LocalDate.of(2026, 12, 3))
        v.checkSeason(now + 16)
        assertEquals(now + 16, v.toppingFlyMs)
        assertEquals(Season.SNOW, v.toppingFlySeason)
        assertEquals(Season.SNOW, v.seasonWorn)
        assertEquals("snow-2026", v.prefs.seasonSeen)
        assertEquals("snow", v.prefs.seasonLook)
        assertEquals(Topping.PLAIN, v.topping)
        TestViews.frames(v, TestViews.canvas(v), 3)
        v.toppingFlyMs = -1L
        v.checkSeason(now + 100); assertEquals(-1L, v.toppingFlyMs)
    }

    @Test fun januaryIsTheSameWinterAndNextDecemberIsANewOne() {
        val v = TestViews.quietView()
        v.prefs.seasonSeen = "snow-2026"
        setDate(v, LocalDate.of(2027, 1, 4))
        v.checkSeason(TestViews.now()); assertEquals(-1L, v.toppingFlyMs)
        setDate(v, LocalDate.of(2027, 12, 1))
        v.checkSeason(TestViews.now()); assertEquals(Season.SNOW, v.toppingFlySeason)
        assertEquals("snow-2027", v.prefs.seasonSeen)
    }

    @Test fun theRevealWaitsForAPanelAndAToppingFly() {
        val v = TestViews.quietView()
        val now = TestViews.now()
        setDate(v, LocalDate.of(2026, 10, 2))
        v.stickersAnim = 0.5f
        v.checkSeason(now); assertEquals(-1L, v.toppingFlyMs); assertNull(v.prefs.seasonSeen)
        v.stickersAnim = 0f; v.toppingFlyMs = now; v.toppingFlySeason = null
        v.checkSeason(now + 16); assertNull(v.toppingFlySeason)
        v.toppingFlyMs = -1L
        v.checkSeason(now + 32); assertEquals(Season.LEAVES, v.toppingFlySeason)
    }

    @Test fun theTabShowsInSeasonAndTilesTakeTheLookOffAndPutItBack() {
        val v = planted()
        v.prefs.seasonSeen = "flowers-2026"; v.prefs.seasonLook = "flowers"
        setDate(v, LocalDate.of(2026, 4, 1))
        v.checkSeason(TestViews.now())
        assertEquals(Season.FLOWERS, v.seasonWorn)
        assertEquals(listOf(0, 2), v.panelTabPages())
        assertEquals(2, v.pickerTileCount())
        openStickers(v)
        tap(v, v.toppingsTabRect); assertEquals(2, v.stickersPage)
        TestViews.frames(v, TestViews.canvas(v), 2)
        tap(v, v.stickerRects[0])
        assertNull(v.seasonWorn); assertEquals("", v.prefs.seasonLook); assertEquals(Topping.PLAIN, v.topping)
        tap(v, v.stickerRects[1])
        assertEquals(Season.FLOWERS, v.seasonWorn); assertEquals("flowers", v.prefs.seasonLook); assertEquals(1, v.toppingTapIdx)
        tap(v, v.stickerRects[2])
        assertEquals(Season.FLOWERS, v.seasonWorn)
        TestViews.frames(v, TestViews.canvas(v), 2)
    }

    @Test fun outOfSeasonTheTabNeedsAnEarnedTopping() {
        val v = TestViews.quietView()
        assertNull(v.season)
        assertEquals(listOf(0), v.panelTabPages())
        assertEquals(1, v.pickerTileCount())
    }

    @Test fun whenTheSeasonEndsTheDonutsGoBackToHisTopping() {
        val v = TestViews.quietView()
        v.prefs.toppingsEarned = 1
        v.wearTopping(Topping.SPRINKLES)
        val sprinkles = pixels(v.pieceSprites[sprite]!!)
        v.prefs.seasonSeen = "snow-2026"; v.prefs.seasonLook = "snow"
        setDate(v, LocalDate.of(2027, 1, 6))
        v.checkSeason(TestViews.now())
        assertEquals(Season.SNOW, v.seasonWorn)
        assertFalse(pixels(v.pieceSprites[sprite]!!).contentEquals(sprinkles))
        v.today = { LocalDate.of(2027, 1, 7) }
        v.checkSeason(v.seasonCheckMs + 59_000); assertEquals(Season.SNOW, v.seasonWorn)   // not looked at yet
        v.checkSeason(v.seasonCheckMs + 60_000)
        assertNull(v.season); assertNull(v.seasonWorn)
        assertEquals(Topping.SPRINKLES, v.topping)
        assertTrue(pixels(v.pieceSprites[sprite]!!).contentEquals(sprinkles))
    }

    @Test fun aNewlyEarnedToppingTakesTheLookOff() {
        val v = TestViews.quietView()
        v.prefs.seasonSeen = "suns-2026"; v.prefs.seasonLook = "suns"
        setDate(v, LocalDate.of(2026, 7, 4))
        v.checkSeason(TestViews.now()); assertEquals(Season.SUNSHINE, v.seasonWorn)
        v.prefs.lifetimeDonuts = 50
        v.checkToppings(TestViews.now())
        assertNull(v.toppingFlySeason); assertEquals(Topping.SPRINKLES, v.toppingFlyTop)
        assertNull(v.seasonWorn); assertEquals("", v.prefs.seasonLook)
        TestViews.frames(v, TestViews.canvas(v), 3)
    }

    @Test fun everyLookBakesItsOwnPixelsAndPaintsAtBothSizes() {
        for (grid in intArrayOf(6, 8)) {
            val v = TestViews.gameView(grid = grid)
            val seen = ArrayList<IntArray>()
            seen.add(pixels(v.pieceSprites[sprite]!!))
            for (s in SeasonRules.SEASONS) {
                v.wearSeason(s)
                val p = pixels(v.pieceSprites[sprite]!!)
                for (q in seen) assertFalse(s.name, p.contentEquals(q))
                seen.add(p)
                TestViews.frames(v, TestViews.canvas(v), 1)
            }
        }
    }
}
