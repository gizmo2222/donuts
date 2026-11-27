package com.donuts.game

import android.view.MotionEvent
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class FriendsPageTest : GameInputTestBase() {

    private fun openWith(met: Int): GameView {
        val v = planted()
        v.prefs.friendsMet = met
        openStickers(v)
        return v
    }

    private fun paint(v: GameView) = TestViews.frames(v, TestViews.canvas(v), 2)

    @Test
    fun noTabsAndATitleTapDoNothingWithNoFriends() {
        val v = openWith(0)
        tap(v, v.friendsTabRect)
        assertEquals(0, v.stickersPage)
        assertTrue(v.stickersOpen)
        paint(v)
    }

    @Test
    fun theFriendsTabSwitchesPageAndBack() {
        val v = openWith(2)
        tap(v, v.friendsTabRect)
        assertEquals(1, v.stickersPage)
        paint(v)
        tap(v, v.stickersTabRect)
        assertEquals(0, v.stickersPage)
        assertTrue(v.stickersOpen)
    }

    @Test
    fun aTabActsOnReleaseOverTheSameButtonOnly() {
        val v = openWith(2)
        TestViews.touch(v, DOWN, v.friendsTabRect.centerX(), v.friendsTabRect.centerY())
        TestViews.touch(v, UP, v.stickersTabRect.centerX(), v.stickersTabRect.centerY())
        assertEquals(0, v.stickersPage)
        TestViews.touch(v, DOWN, v.friendsTabRect.centerX(), v.friendsTabRect.centerY())
        TestViews.touch(v, MotionEvent.ACTION_CANCEL, v.friendsTabRect.centerX(), v.friendsTabRect.centerY())
        TestViews.touch(v, UP, v.friendsTabRect.centerX(), v.friendsTabRect.centerY())
        assertEquals(0, v.stickersPage)
    }

    @Test
    fun thePageSurvivesClosingAndReopening() {
        val v = openWith(1)
        tap(v, v.friendsTabRect)
        tap(v, v.stickersCloseRect)
        assertFalse(v.stickersOpen)
        v.stickersAnim = 0f
        openStickers(v)
        assertEquals(1, v.stickersPage)
    }

    @Test
    fun tappingAMetFriendHopsAndAnEmptyTileDoesNothing() {
        val v = openWith(2)
        tap(v, v.friendsTabRect)
        tap(v, v.stickerRects[1])
        assertEquals(1, v.friendTapIdx)
        assertTrue(v.friendTapMs >= 0)
        paint(v)
        v.friendTapIdx = -1; v.friendTapMs = -1L
        tap(v, v.stickerRects[2])                    // the tile just after the last friend met
        assertEquals(-1, v.friendTapIdx)
        tap(v, v.stickerRects[3])
        assertEquals(-1, v.friendTapIdx)
        assertTrue(v.stickersOpen)
    }

    @Test
    fun aTileTapOnTheStickersPageStartsNoHop() {
        val v = openWith(2)
        tap(v, v.stickerRects[0])
        assertEquals(-1, v.friendTapIdx)
    }

    @Test
    fun twelveMetFriendsAllPaintAndEveryTileHops() {
        val v = openWith(12)
        tap(v, v.friendsTabRect)
        for (i in 0 until FriendRules.FRIEND_COUNT) {
            tap(v, v.stickerRects[i])
            assertEquals(i, v.friendTapIdx)
        }
        paint(v)
    }

    @Test
    fun theFriendsPagePaintsMoreThanTheBareBoard() {
        val v = openWith(4)
        tap(v, v.friendsTabRect)
        paint(v)
        val c = TestViews.canvas(v)
        TestViews.frames(v, c, 1)
        val r = v.stickerRects[0]
        assertTrue(r.width() > 0f)
    }
}
