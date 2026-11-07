package com.donuts.game

import android.content.Intent
import android.graphics.RectF
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows

@RunWith(RobolectricTestRunner::class)
class ActivityTest {

    private fun contentChild(a: android.app.Activity): View =
        a.findViewById<ViewGroup>(android.R.id.content).getChildAt(0)

    private fun gameViewOf(a: GameActivity) = contentChild(a) as GameView

    private fun startGame() = Robolectric.buildActivity(GameActivity::class.java).create().start().resume()

    /** A resumed MainActivity whose view is laid out at 1080x2400, with the play button's rect. */
    private fun laidOutMain(): Triple<org.robolectric.android.controller.ActivityController<MainActivity>, View, RectF> {
        val c = Robolectric.buildActivity(MainActivity::class.java).create().start().resume()
        val view = contentChild(c.get())
        view.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(2400, View.MeasureSpec.EXACTLY))
        view.layout(0, 0, 1080, 2400)
        val f = view.javaClass.getDeclaredField("playRect").apply { isAccessible = true }
        return Triple(c, view, f.get(view) as RectF)
    }

    private fun dispatch(view: View, t: Long, action: Int, x: Float, y: Float) {
        val e = MotionEvent.obtain(t, t, action, x, y, 0)
        view.dispatchTouchEvent(e); e.recycle()
    }

    @Test
    fun gameActivityShowsAGameView() {
        val c = startGame()
        assertTrue(contentChild(c.get()) is GameView)
        c.pause().stop().destroy()
    }

    @Test
    fun coldStartCountsAVisitAndStartsFresh() {
        val c = Robolectric.buildActivity(GameActivity::class.java).create()
        assertEquals(1, Prefs(TestViews.app()).sessionCount)
        assertEquals(0, TestViews.total(gameViewOf(c.get())))
    }

    @Test
    fun recreateWithSavedStateKeepsTheBoardAndTheVisitCount() {
        val first = startGame()
        val board = gameViewOf(first.get()).board
        board.donutsCleared[DonutType.values()[0]] = 7
        val bundle = Bundle()
        first.saveInstanceState(bundle)
        val expected = board.cellCodes()
        val expectedCleared = board.clearedCounts()
        assertEquals(1, Prefs(TestViews.app()).sessionCount)
        // Nothing in prefs could explain a restored board, so it must come from the bundle
        Prefs(TestViews.app()).savedBoard = null

        val second = Robolectric.buildActivity(GameActivity::class.java).create(bundle)
        val restored = gameViewOf(second.get()).board
        assertArrayEquals(expected, restored.cellCodes())
        assertArrayEquals(expectedCleared, restored.clearedCounts())
        // A restart is still the same visit
        assertEquals(1, Prefs(TestViews.app()).sessionCount)
    }

    @Test
    fun coldStartRestoresTheBoardKeptByOnPause() {
        val first = startGame()
        val board = gameViewOf(first.get()).board
        board.donutsCleared[DonutType.values()[1]] = 4
        first.pause()
        assertNotNull(Prefs(TestViews.app()).savedBoard)
        val expected = board.cellCodes()
        first.stop().destroy()

        val second = Robolectric.buildActivity(GameActivity::class.java).create()
        val restored = gameViewOf(second.get()).board
        assertArrayEquals(expected, restored.cellCodes())
        assertEquals(4, restored.donutsCleared[DonutType.values()[1]])
        assertEquals(2, Prefs(TestViews.app()).sessionCount)
    }

    @Test
    fun onPauseBanksDonutsAndWritesTheBoardToPrefs() {
        val c = startGame()
        val v = gameViewOf(c.get())
        v.board.donutsCleared[DonutType.values()[0]] = 5
        assertNull(Prefs(TestViews.app()).savedBoard)
        c.pause()
        val prefs = Prefs(TestViews.app())
        assertEquals(5, prefs.lifetimeDonuts)
        assertEquals(5, prefs.highScore6x6)
        val saved = SavedBoard.decode(prefs.savedBoard)
        assertNotNull(saved)
        assertArrayEquals(v.board.cellCodes(), saved!!.cells)
        // Pausing again banks nothing twice
        c.resume().pause()
        assertEquals(5, Prefs(TestViews.app()).lifetimeDonuts)
    }

    @Test
    fun backPressClosesAnOpenSettingsPanelFirst() {
        val c = startGame()
        val v = gameViewOf(c.get())
        v.settingsOpen = true
        c.get().onBackPressedDispatcher.onBackPressed()
        assertFalse(v.settingsOpen)
        assertFalse(c.get().isFinishing)
    }

    @Test
    fun backPressClosesAnOpenStickersPanelFirst() {
        val c = startGame()
        val v = gameViewOf(c.get())
        v.stickersOpen = true
        c.get().onBackPressedDispatcher.onBackPressed()
        assertFalse(v.stickersOpen)
        assertFalse(c.get().isFinishing)
    }

    @Test
    fun backPressWithNoPanelFinishesTheActivity() {
        val c = startGame()
        c.get().onBackPressedDispatcher.onBackPressed()
        assertTrue(c.get().isFinishing)
    }

    @Test
    fun secondBackPressAfterClosingAPanelFinishes() {
        val c = startGame()
        val v = gameViewOf(c.get())
        v.settingsOpen = true
        c.get().onBackPressedDispatcher.onBackPressed()
        assertFalse(c.get().isFinishing)
        c.get().onBackPressedDispatcher.onBackPressed()
        assertTrue(c.get().isFinishing)
    }

    @Test
    fun mainActivityStartsGameActivityWhenPlayIsTapped() {
        val (c, view, r) = laidOutMain()
        assertFalse(r.isEmpty)
        val shadow = Shadows.shadowOf(c.get())
        assertNull(shadow.peekNextStartedActivity())

        val t = TestViews.now()
        fun send(action: Int, x: Float, y: Float) {
            dispatch(view, t, action, x, y)
        }
        // Press the button but release outside it: nothing starts
        send(MotionEvent.ACTION_DOWN, r.centerX(), r.centerY())
        send(MotionEvent.ACTION_UP, 1f, 1f)
        assertNull(shadow.peekNextStartedActivity())

        send(MotionEvent.ACTION_DOWN, r.centerX(), r.centerY())
        send(MotionEvent.ACTION_UP, r.centerX(), r.centerY())
        val started: Intent = shadow.nextStartedActivity
        assertEquals(GameActivity::class.java.name, started.component!!.className)
        assertNull(shadow.peekNextStartedActivity())
    }

    @Test
    fun secondQuickPlayTapStartsOnlyOneGameUntilResume() {
        val (c, view, r) = laidOutMain()
        val shadow = Shadows.shadowOf(c.get())
        val t = TestViews.now()
        fun tap() {
            for (a in intArrayOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
                dispatch(view, t, a, r.centerX(), r.centerY())
            }
        }
        tap(); tap()
        assertNotNull(shadow.nextStartedActivity)
        assertNull(shadow.peekNextStartedActivity())
        // Coming back from the game re-arms the button
        c.pause().resume()
        tap()
        assertNotNull(shadow.nextStartedActivity)
    }

    @Test
    fun mainActivityResumeAndPauseDoNotThrow() {
        Robolectric.buildActivity(MainActivity::class.java).create().start().resume().pause().resume()
    }

    @Test
    fun surfaceDestroyedBanksLifetimeDonutsOnce() {
        val v = TestViews.gameView()
        v.board.donutsCleared[DonutType.values()[0]] = 6
        v.surfaceDestroyed(v.holder)
        assertEquals(6, v.prefs.lifetimeDonuts)
        assertEquals(6, v.sessionSaved)
        v.surfaceDestroyed(v.holder)
        assertEquals(6, v.prefs.lifetimeDonuts)
        v.board.donutsCleared[DonutType.values()[0]] = 9
        v.surfaceDestroyed(v.holder)
        assertEquals(9, v.prefs.lifetimeDonuts)
    }

    @Test(timeout = 20000)
    fun surfaceDestroyedStopsAndClearsTheRenderThread() {
        val v = TestViews.gameView()
        v.surfaceCreated(v.holder)
        val t = v.renderThread
        assertNotNull(t)
        v.surfaceDestroyed(v.holder)
        assertNull(v.renderThread)
        assertFalse(t!!.running)
        t.join(5000L)
        assertFalse(t.isAlive)
    }

    @Test
    fun surfaceChangedLaysTheBoardOut() {
        val v = TestViews.gameView(w = 1080, h = 2400)
        v.surfaceChanged(v.holder, 0, 720, 1280)
        assertEquals(720, v.surfaceW)
        assertEquals(1280, v.surfaceH)
        assertTrue(v.cellSize > 0f)
        assertTrue(v.boardLeft >= 0f)
    }
}
