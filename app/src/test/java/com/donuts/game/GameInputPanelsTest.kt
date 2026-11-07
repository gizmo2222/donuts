package com.donuts.game

import android.view.MotionEvent
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class GameInputPanelsTest : GameInputTestBase() {
    // ---- panel buttons ----

    @Test
    fun gearOpensSettingsAndStarOpensStickers() {
        val v = planted()
        tap(v, v.settingsBtnRect)
        assertTrue(v.settingsOpen)
        assertFalse(v.stickersOpen)
        assertTrue(v.dragChain.isEmpty())

        val w = planted()
        tap(w, w.stickersBtnRect)
        assertTrue(w.stickersOpen)
        assertFalse(w.settingsOpen)
    }

    @Test
    fun panelButtonsWorkWhileTheBoardIsAnimating() {
        val v = planted()
        drag(v, 0 to 0, 0 to 1, 0 to 2)
        assertEquals(AnimPhase.POPPING, v.animPhase)
        tap(v, v.settingsBtnRect)
        assertTrue(v.settingsOpen)
    }

    @Test
    fun boardTouchesAreSwallowedWhileAPanelIsOpen() {
        val v = planted()
        openSettings(v)
        drag(v, 0 to 0, 0 to 1, 0 to 2)
        assertTrue(v.dragChain.isEmpty())
        assertEquals(AnimPhase.IDLE, v.animPhase)
    }

    @Test
    fun stickersCloseButtonAndOutsideTapBothClose() {
        val v = planted()
        openStickers(v)
        tap(v, v.stickersCloseRect)
        assertFalse(v.stickersOpen)

        val w = planted()
        openStickers(w)
        tap(w, 1f, 1f)
        assertFalse(w.stickersOpen)
    }

    @Test
    fun tapInsideStickersPanelBodyKeepsItOpen() {
        val v = planted()
        openStickers(v)
        val r = v.stickerPanelRect
        var x = r.left + 4f; var y = r.top + 4f
        if (v.stickersCloseRect.contains(x, y)) { x = r.right - 4f; y = r.top + 4f }
        tap(v, x, y)
        assertTrue(v.stickersOpen)
    }

    @Test
    fun settingsSoundHintBuzzButtonsChangePrefs() {
        val v = planted()
        openSettings(v)
        tap(v, v.soundRects[1]); assertFalse(v.prefs.soundEnabled)
        tap(v, v.soundRects[0]); assertTrue(v.prefs.soundEnabled)
        tap(v, v.buzzRects[1]);  assertFalse(v.prefs.hapticEnabled)
        tap(v, v.buzzRects[0]);  assertTrue(v.prefs.hapticEnabled)
        tap(v, v.hintRects[1]);  assertEquals(v.hintOptions[1], v.prefs.hintDelayMs)
        tap(v, v.hintRects[0]);  assertEquals(v.hintOptions[0], v.prefs.hintDelayMs)
        assertTrue(v.settingsOpen)
    }

    @Test
    fun settingsGridButtonRebuildsTheBoard() {
        val v = planted()
        openSettings(v)
        tap(v, v.gridRects[1])
        assertEquals(8, v.prefs.gridSize)
        assertEquals(8, v.board.rows)
        assertEquals(8, v.board.cols)
        assertTrue(v.settingsOpen)
        tap(v, v.gridRects[0])
        assertEquals(6, v.board.rows)
    }

    @Test
    fun settingsGridSameSizeKeepsTheBoard() {
        val v = planted()
        openSettings(v)
        val board = v.board
        tap(v, v.gridRects[0])
        assertSame(board, v.board)
    }

    @Test
    fun buttonActsOnReleaseOverTheSameButtonOnly() {
        val v = planted()
        openSettings(v)
        v.prefs.soundEnabled = true
        // press sound-off, slide onto sound-on, release: nothing happens
        TestViews.touch(v, DOWN, v.soundRects[1].centerX(), v.soundRects[1].centerY())
        TestViews.touch(v, UP, v.soundRects[0].centerX(), v.soundRects[0].centerY())
        assertTrue(v.prefs.soundEnabled)
        // press and release on sound-off: it acts
        tap(v, v.soundRects[1])
        assertFalse(v.prefs.soundEnabled)
        // a cancelled press does nothing
        TestViews.touch(v, DOWN, v.soundRects[0].centerX(), v.soundRects[0].centerY())
        TestViews.touch(v, MotionEvent.ACTION_CANCEL, v.soundRects[0].centerX(), v.soundRects[0].centerY())
        TestViews.touch(v, UP, v.soundRects[0].centerX(), v.soundRects[0].centerY())
        assertFalse(v.prefs.soundEnabled)
    }

    @Test
    fun settingsDoneAndOutsideTapClose() {
        val v = planted()
        openSettings(v)
        tap(v, v.settingsCloseRect)
        assertFalse(v.settingsOpen)

        val w = planted()
        openSettings(w)
        tap(w, 1f, 1f)
        assertFalse(w.settingsOpen)
    }

    @Test
    fun newGameNeedsTwoTapsAndBanksTheSession() {
        // the seeded board, not planted(): its SSS row would be cleared and banked by settle() at New game
        val v = TestViews.gameView()
        v.board.donutsCleared[DonutType.STRAWBERRY] = 5
        v.displayedCount = 5
        openSettings(v)
        tap(v, v.settingsResetRect)
        assertTrue(v.settingsOpen)
        assertEquals(5, TestViews.total(v))
        assertEquals(0, v.prefs.lifetimeDonuts)
        assertTrue(v.newGameConfirm.isArmed(TestViews.now()))
        tap(v, v.settingsResetRect)
        assertFalse(v.settingsOpen)
        assertEquals(0, TestViews.total(v))
        assertEquals(5, v.prefs.lifetimeDonuts)
        assertEquals(0, v.displayedCount)
        assertFalse(v.newGameConfirm.isArmed(TestViews.now()))
    }

    @Test
    fun newGameConfirmTimesOut() {
        val v = planted()
        v.board.donutsCleared[DonutType.CHOCOLATE] = 4
        openSettings(v)
        tap(v, v.settingsResetRect)
        TestViews.advance(2000)
        tap(v, v.settingsResetRect)               // only re-arms
        assertTrue(v.settingsOpen)
        assertEquals(4, TestViews.total(v))
    }

    @Test
    fun tappingResetStickersDisarmsNewGame() {
        val v = planted()
        v.board.donutsCleared[DonutType.CHOCOLATE] = 4
        openSettings(v)
        tap(v, v.settingsResetRect)
        tap(v, v.settingsStickersRect)            // cancels the new-game arm
        tap(v, v.settingsResetRect)               // so this only arms again
        assertTrue(v.settingsOpen)
        assertEquals(4, TestViews.total(v))
    }

    @Test
    fun resetStickersNeedsTwoTapsAndZeroesProgress() {
        val v = planted()
        openSettings(v)
        v.prefs.lifetimeDonuts = 50; v.prefs.highScore6x6 = 20; v.prefs.highScore8x8 = 9
        v.prefs.bestChainLength = 7; v.prefs.sessionCount = 3; v.prefs.goldenPopped = true
        tap(v, v.settingsStickersRect)
        assertEquals(50, v.prefs.lifetimeDonuts)
        assertTrue(v.settingsOpen)
        tap(v, v.settingsStickersRect)
        assertEquals(0, v.prefs.lifetimeDonuts)
        assertEquals(0, v.prefs.highScore6x6)
        assertEquals(0, v.prefs.highScore8x8)
        assertEquals(0, v.prefs.bestChainLength)
        assertEquals(0, v.prefs.sessionCount)
        assertFalse(v.prefs.goldenPopped)
        assertFalse(v.settingsOpen)
    }

    @Test
    fun closingSettingsDisarmsBothConfirms() {
        val v = planted()
        openSettings(v)
        tap(v, v.settingsResetRect)
        tap(v, v.settingsCloseRect)
        assertFalse(v.newGameConfirm.isArmed(TestViews.now()))
        assertFalse(v.stickersConfirm.isArmed(TestViews.now()))
    }
}
