package com.donuts.game

import android.graphics.Bitmap
import android.graphics.Canvas
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

/** Meeting the donut friends: one reveal per box slot, in order, waiting for anything else on screen. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FriendRevealTest {

    private fun distinctColors(bmp: Bitmap): Int {
        val px = IntArray(bmp.width * bmp.height)
        bmp.getPixels(px, 0, bmp.width, 0, 0, bmp.width, bmp.height)
        return px.toHashSet().size
    }

    @Test fun noFriendBeforeTheFirstSlotThenFriendZeroIsSavedAtTheStart() {
        val v = TestViews.quietView()
        val now = TestViews.now()
        v.prefs.lifetimeDonuts = 9
        v.checkFriends(now)
        assertEquals(-1L, v.friendFlyMs)
        assertEquals(0, v.prefs.friendsMet)
        v.prefs.lifetimeDonuts = 10
        v.checkFriends(now + 16)
        assertEquals(now + 16, v.friendFlyMs)
        assertEquals(0, v.friendFlyIdx)
        assertEquals(1, v.prefs.friendsMet)      // saved as the fly starts, so a quit mid-fly never repeats it
    }

    @Test fun friendsComeOneAtATimeAndStopAtTheDrawnOnes() {
        val v = TestViews.quietView()
        val now = TestViews.now()
        v.prefs.lifetimeDonuts = 10_000           // every friend is due
        for (i in 0 until DRAWN) {
            v.checkFriends(now + i * 100L)
            assertEquals(i, v.friendFlyIdx)
            v.checkFriends(now + i * 100L + 16)   // no second reveal while this one flies
            assertEquals(i + 1, v.prefs.friendsMet)
            v.friendFlyMs = -1L
        }
        v.checkFriends(now + 10_000)
        assertEquals(-1L, v.friendFlyMs)
        assertEquals(DRAWN, v.prefs.friendsMet)
    }

    @Test fun theRevealWaitsForPanelsFliesCardsAndTheTutorial() {
        val v = TestViews.quietView()
        val now = TestViews.now()
        v.prefs.lifetimeDonuts = 10
        v.stickersAnim = 0.5f
        v.checkFriends(now); assertEquals(-1L, v.friendFlyMs)
        v.stickersAnim = 0f
        v.stickerFlyMs = now
        v.checkFriends(now); assertEquals(-1L, v.friendFlyMs)
        v.stickerFlyMs = -1L
        v.showSomeoneMs = now
        v.checkFriends(now); assertEquals(-1L, v.friendFlyMs)
        v.showSomeoneMs = -1L
        v.showSomeonePending = SHOW_BOX
        v.checkFriends(now); assertEquals(-1L, v.friendFlyMs)
        v.showSomeonePending = SHOW_NONE
        v.tutorialActive = true
        v.checkFriends(now); assertEquals(-1L, v.friendFlyMs)
        assertEquals(0, v.prefs.friendsMet)
        v.tutorialActive = false
        v.checkFriends(now); assertEquals(now, v.friendFlyMs)
    }

    @Test fun aStickerFlyAndTheShowSomeoneCardWaitForTheFriend() {
        val v = TestViews.quietView()
        val now = TestViews.now()
        v.checkStickers(now)                      // seeds the announced mask
        v.friendFlyMs = now; v.friendFlyIdx = 0
        v.prefs.lifetimeDonuts = 20               // sticker 0 is now earned
        v.checkStickers(now + 16)
        assertEquals(-1L, v.stickerFlyMs)
        v.showSomeonePending = SHOW_BOX
        v.advanceShowSomeone(now + 32)
        assertEquals(-1L, v.showSomeoneMs)
        v.friendFlyMs = -1L
        v.advanceShowSomeone(now + 48)
        assertEquals(now + 48, v.showSomeoneMs)
    }

    @Test fun theFlyRunsToTheStarAndEndsWithAPulse() {
        for (reduced in listOf(false, true)) {
            val v = TestViews.quietView()
            v.reducedMotion = reduced
            val c = TestViews.canvas(v)
            v.prefs.lifetimeDonuts = 10
            v.prefs.friendsMet = 0                // prefs outlive the view within one test
            v.drawFrame(c)
            assertEquals(0, v.friendFlyIdx)
            assertTrue(v.friendFlyMs >= 0)
            TestViews.frames(v, c, (v.FRIEND_FLY_MS / 16 + 2).toInt())
            assertEquals("reduced=$reduced", -1L, v.friendFlyMs)
            assertTrue(v.starPulseMs >= 0)
        }
    }

    @Test fun eachDrawnFriendPaints() {
        val v = TestViews.quietView()
        for (i in 0 until DRAWN) {
            val bmp = Bitmap.createBitmap(200, 200, Bitmap.Config.ARGB_8888)
            v.drawFriend(Canvas(bmp), i, 100f, 100f, 70f)
            assertTrue("friend $i looks blank", distinctColors(bmp) > 8)
        }
        val blank = Bitmap.createBitmap(50, 50, Bitmap.Config.ARGB_8888)
        v.drawFriend(Canvas(blank), DRAWN, 25f, 25f, 20f)
        assertEquals(1, distinctColors(blank))    // a friend without art paints nothing
    }

    @Test fun everyFriendPaintsWithSeveralColoursAndNoTwoLookTheSame() {
        val v = TestViews.quietView()
        val seen = HashSet<List<Int>>()
        for (i in 0 until FriendRules.FRIEND_COUNT) {
            val bmp = Bitmap.createBitmap(200, 200, Bitmap.Config.ARGB_8888)
            v.drawFriend(Canvas(bmp), i, 100f, 100f, 60f)
            assertTrue("friend $i looks blank", distinctColors(bmp) > 8)
            val px = IntArray(200 * 200); bmp.getPixels(px, 0, 200, 0, 0, 200, 200)
            assertTrue("friend $i paints too little", px.count { it != 0 } > 200 * 200 / 5)
            assertTrue("friend $i paints the same as another", seen.add(px.toList()))
        }
    }

    @Test fun theFriendsAfterTheFirstFourAreMetInOrder() {
        val v = TestViews.quietView()
        val now = TestViews.now()
        v.prefs.friendsMet = 4
        v.prefs.lifetimeDonuts = 10_000
        for (i in 4 until FriendRules.FRIEND_COUNT) {
            v.checkFriends(now + i * 100L)
            assertEquals(i, v.friendFlyIdx)
            assertEquals(i + 1, v.prefs.friendsMet)
            v.friendFlyMs = -1L
        }
        v.checkFriends(now + 10_000)
        assertEquals(-1L, v.friendFlyMs)
        assertEquals(FriendRules.FRIEND_COUNT, v.prefs.friendsMet)
    }
}