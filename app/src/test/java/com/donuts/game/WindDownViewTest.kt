package com.donuts.game

import android.os.Bundle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class WindDownViewTest : GameInputTestBase() {

    /** A quiet view with the rest reminder set to [min] and the count one frame short of it. */
    private fun almostDue(min: Int = 15): GameView {
        val v = TestViews.quietView()
        v.prefs.windDownMin = min
        v.windDownPlayedMs = min * 60_000L - 10L
        v.windDownFrameMs = TestViews.now()
        v.lastActionMs = TestViews.now()
        return v
    }

    @Test
    fun theReminderIsOffByDefaultAndNeverShowsTheCard() {
        val v = TestViews.quietView()
        assertEquals(0, v.prefs.windDownMin)
        v.windDownPlayedMs = 60 * 60_000L
        v.windDownFrameMs = TestViews.now()
        TestViews.frames(v, TestViews.canvas(v), 3)
        assertEquals(SHOW_NONE, v.showSomeonePending)
        assertEquals(-1L, v.showSomeoneMs)
        assertFalse(v.windDownShown)
    }

    @Test
    fun anOddSavedValueReadsAsOff() {
        val v = TestViews.quietView()
        v.prefs.windDownMin = 7
        assertEquals(0, v.prefs.windDownMin)
        v.prefs.windDownMin = 30
        assertEquals(30, v.prefs.windDownMin)
    }

    @Test
    fun onceTheTimeIsUpTheRestCardShowsQuietlyAtAnIdleBoard() {
        val v = almostDue()
        TestViews.frames(v, TestViews.canvas(v), 2)
        assertTrue(v.windDownShown)
        assertEquals(SHOW_REST, v.showSomeoneKind)
        assertTrue(v.showSomeoneMs >= 0)
    }

    @Test
    fun theCardWaitsForTheBoardAndAnOpenPanel() {
        val v = almostDue()
        v.animPhase = AnimPhase.POPPING
        val now = TestViews.now()
        v.advanceWindDown(now + 16)
        assertFalse(v.windDownShown)
        v.animPhase = AnimPhase.IDLE
        v.stickersOpen = true
        v.advanceWindDown(now + 32)
        assertFalse(v.windDownShown)
        v.stickersOpen = false; v.stickersAnim = 0f
        v.celebrateMs = now
        v.advanceWindDown(now + 48)
        assertFalse(v.windDownShown)
        v.celebrateMs = -1L
        v.advanceWindDown(now + 64)
        assertTrue(v.windDownShown)
        assertEquals(SHOW_REST, v.showSomeonePending)
    }

    @Test
    fun aTapClosesTheCardAndItDoesNotComeBack() {
        val v = almostDue()
        val c = TestViews.canvas(v)
        TestViews.frames(v, c, 2)
        assertTrue(v.showSomeoneMs >= 0)
        TestViews.advance(SHOW_CARD_GUARD_MS + 50)
        val (x, y) = TestViews.cellCenter(v, 2, 2)
        TestViews.touch(v, DOWN, x, y); TestViews.touch(v, UP, x, y)
        assertEquals(-1L, v.showSomeoneMs)
        v.windDownPlayedMs = 2 * 60 * 60_000L
        TestViews.frames(v, c, 5)
        assertEquals(-1L, v.showSomeoneMs)
        assertEquals(SHOW_NONE, v.showSomeonePending)
    }

    @Test
    fun theCountStopsWhenTheBoardIsLeftAlone() {
        val v = almostDue()
        v.windDownPlayedMs = 0L
        v.lastActionMs = TestViews.now() - WindDown.ACTIVE_MS - 1
        TestViews.frames(v, TestViews.canvas(v), 5)
        assertEquals(0L, v.windDownPlayedMs)
    }

    @Test
    fun theRestRowOnTheGrownUpsPageSetsTheTimeAndStartsTheCountAgain() {
        val v = planted()
        v.prefs.soundEnabled = false; v.prefs.hapticEnabled = false
        openGrownUps(v)
        v.windDownPlayedMs = 5 * 60_000L; v.windDownShown = true
        tap(v, v.restRects[1])
        assertEquals(15, v.prefs.windDownMin)
        assertEquals(0L, v.windDownPlayedMs)
        assertFalse(v.windDownShown)
        tap(v, v.restRects[2])
        assertEquals(30, v.prefs.windDownMin)
        tap(v, v.restRects[0])
        assertEquals(0, v.prefs.windDownMin)
        assertEquals(SETTINGS_GROWN, v.settingsPage)
    }

    @Test
    fun theRestRowIsNotOnStevensPage() {
        val v = planted()
        openSettings(v)
        assertEquals(SETTINGS_KID, v.settingsPage)
        tap(v, v.restRects[1])
        assertEquals(0, v.prefs.windDownMin)
    }

    @Test
    fun aTurnOfTheScreenKeepsTheCount() {
        val v = TestViews.quietView()
        v.windDownPlayedMs = 123_456L; v.windDownShown = true
        val out = Bundle()
        v.saveState(out)
        val w = TestViews.quietView()
        w.restoreState(out)
        assertEquals(123_456L, w.windDownPlayedMs)
        assertTrue(w.windDownShown)
    }

    @Test
    fun aLongTimeAwayStartsTheCountAgainButAShortOneDoesNot() {
        val v = TestViews.quietView()
        val now = TestViews.now()
        v.windDownPlayedMs = 600_000L; v.windDownShown = true
        v.windDownAwayMs = now
        v.wakeWindDown(now + 60_000L)
        assertEquals(600_000L, v.windDownPlayedMs)
        assertTrue(v.windDownShown)
        v.windDownAwayMs = now
        v.wakeWindDown(now + WindDown.AWAY_RESET_MS)
        assertEquals(0L, v.windDownPlayedMs)
        assertFalse(v.windDownShown)
        assertEquals(-1L, v.windDownAwayMs)
    }

    @Test
    fun theRestCardDrawsWithFullAndCalmMotion() {
        for (calm in listOf(false, true)) {
            val v = almostDue()
            v.reducedMotion = calm
            val c = TestViews.canvas(v)
            TestViews.frames(v, c, 2)
            TestViews.frames(v, c, 20, 50L)
            assertEquals(SHOW_REST, v.showSomeoneKind)
            assertTrue(v.hintCells.isEmpty())
        }
    }
}
