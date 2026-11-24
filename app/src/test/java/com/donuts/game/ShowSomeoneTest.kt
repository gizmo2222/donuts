package com.donuts.game

import android.view.MotionEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ShowSomeoneTest {

    private fun boxFull(v: GameView): Long {
        val now = TestViews.now()
        v.prefs.lifetimeDonuts = 119
        v.checkStickers(now)
        v.prefs.lifetimeDonuts = 120
        v.checkStickers(now + 16)
        return now + 16
    }

    @Test
    fun aFullBoxShowsTheCard() {
        val v = TestViews.quietView()
        val t = boxFull(v)
        assertEquals(-1L, v.showSomeoneMs)
        v.advanceShowSomeone(t + 16)
        assertEquals(t + 16, v.showSomeoneMs)
        assertEquals(SHOW_BOX, v.showSomeoneKind)
    }

    @Test
    fun aBigStickerShowsTheCardOnlyAfterTheFlyEnds() {
        val v = TestViews.quietView()
        val now = TestViews.now()
        v.checkStickers(now)
        v.earnedMask = 3; v.boxesSeen = 4     // stickers 0 and 1 already shown, boxes already seen
        v.prefs.lifetimeDonuts = 500          // sticker 2
        v.checkStickers(now + 16)
        assertEquals(2, v.stickerFlyIdx)
        v.advanceShowSomeone(now + 32)
        assertEquals(-1L, v.showSomeoneMs)
        v.stickerFlyMs = -1L
        v.advanceShowSomeone(now + 48)
        assertEquals(now + 48, v.showSomeoneMs)
        assertEquals(2, v.showSomeoneKind)
    }

    @Test
    fun aSmallStickerDoesNotShowTheCard() {
        val v = TestViews.quietView()
        val now = TestViews.now()
        v.checkStickers(now)
        v.prefs.lifetimeDonuts = 20           // sticker 0 only
        v.checkStickers(now + 16)
        assertEquals(0, v.stickerFlyIdx)
        v.stickerFlyMs = -1L
        v.advanceShowSomeone(now + 32)
        assertEquals(-1L, v.showSomeoneMs)
    }

    @Test
    fun theCardWaitsWhileAPanelIsOpen() {
        val v = TestViews.quietView()
        val t = boxFull(v)
        v.stickersOpen = true
        v.advanceShowSomeone(t + 16)
        assertEquals(-1L, v.showSomeoneMs)
        v.stickersOpen = false
        v.stickersAnim = 0f
        v.advanceShowSomeone(t + 32)
        assertTrue(v.showSomeoneMs >= 0)
    }

    @Test
    fun aTapClosesTheCardAndStartsNoChain() {
        val v = TestViews.quietView()
        val t = boxFull(v)
        v.advanceShowSomeone(t)
        v.showSomeoneMs = TestViews.now()
        val (x, y) = TestViews.cellCenter(v, 0, 0)
        TestViews.advance(100)                // inside the guard: ignored
        TestViews.touch(v, MotionEvent.ACTION_DOWN, x, y)
        TestViews.touch(v, MotionEvent.ACTION_UP, x, y)
        assertTrue(v.showSomeoneMs >= 0)
        TestViews.advance(500)
        TestViews.touch(v, MotionEvent.ACTION_DOWN, x, y)
        assertEquals(-1L, v.showSomeoneMs)
        assertTrue(v.dragChain.isEmpty())
        TestViews.touch(v, MotionEvent.ACTION_UP, x, y)
        assertTrue(v.dragChain.isEmpty())
    }

    @Test
    fun backClosesTheCardFirst() {
        val v = TestViews.quietView()
        v.showSomeoneMs = TestViews.now()
        v.settingsOpen = true
        assertTrue(v.onBackPressed())
        assertEquals(-1L, v.showSomeoneMs)
        assertTrue(v.settingsOpen)
    }

    @Test
    fun theCardDrawsForBothKindsAndHoldsTheHintBack() {
        val v = TestViews.quietView()
        val c = TestViews.canvas(v)
        TestViews.frames(v, c, 3)
        for (kind in listOf(SHOW_BOX, 2, 5, 8, 11)) {
            v.showSomeoneKind = kind; v.showSomeoneMs = TestViews.now()
            TestViews.frames(v, c, 30, 50L)
            assertTrue(v.hintCells.isEmpty())
        }
        assertFalse(v.showSomeoneMs < 0)
    }
}
