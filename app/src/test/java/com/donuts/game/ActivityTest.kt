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
import kotlin.math.PI

@RunWith(RobolectricTestRunner::class)
class ActivityTest {

    private fun contentChild(a: android.app.Activity): View =
        a.findViewById<ViewGroup>(android.R.id.content).getChildAt(0)

    private fun gameViewOf(a: GameActivity) = contentChild(a) as GameView

    @Test
    fun gameActivityShowsAGameView() {
        val c = Robolectric.buildActivity(GameActivity::class.java).create().start().resume()
        assertTrue(contentChild(c.get()) is GameView)
        c.pause().stop().destroy()
    }

    @Test
    fun coldStartCountsAVisitAndStartsFresh() {
        val c = Robolectric.buildActivity(GameActivity::class.java).create()
        assertEquals(1, Prefs(TestViews.app()).sessionCount)
        assertEquals(0, gameViewOf(c.get()).board.donutsCleared.values.sum())
    }

    @Test
    fun recreateWithSavedStateKeepsTheBoardAndTheVisitCount() {
        val first = Robolectric.buildActivity(GameActivity::class.java).create().start().resume()
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
        val first = Robolectric.buildActivity(GameActivity::class.java).create().start().resume()
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
        val c = Robolectric.buildActivity(GameActivity::class.java).create().start().resume()
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
        val c = Robolectric.buildActivity(GameActivity::class.java).create().start().resume()
        val v = gameViewOf(c.get())
        v.settingsOpen = true
        c.get().onBackPressedDispatcher.onBackPressed()
        assertFalse(v.settingsOpen)
        assertFalse(c.get().isFinishing)
    }

    @Test
    fun backPressClosesAnOpenStickersPanelFirst() {
        val c = Robolectric.buildActivity(GameActivity::class.java).create().start().resume()
        val v = gameViewOf(c.get())
        v.stickersOpen = true
        c.get().onBackPressedDispatcher.onBackPressed()
        assertFalse(v.stickersOpen)
        assertFalse(c.get().isFinishing)
    }

    @Test
    fun backPressWithNoPanelFinishesTheActivity() {
        val c = Robolectric.buildActivity(GameActivity::class.java).create().start().resume()
        c.get().onBackPressedDispatcher.onBackPressed()
        assertTrue(c.get().isFinishing)
    }

    @Test
    fun secondBackPressAfterClosingAPanelFinishes() {
        val c = Robolectric.buildActivity(GameActivity::class.java).create().start().resume()
        val v = gameViewOf(c.get())
        v.settingsOpen = true
        c.get().onBackPressedDispatcher.onBackPressed()
        assertFalse(c.get().isFinishing)
        c.get().onBackPressedDispatcher.onBackPressed()
        assertTrue(c.get().isFinishing)
    }

    @Test
    fun mainActivityStartsGameActivityWhenPlayIsTapped() {
        val c = Robolectric.buildActivity(MainActivity::class.java).create().start().resume()
        val view = contentChild(c.get())
        view.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(2400, View.MeasureSpec.EXACTLY))
        view.layout(0, 0, 1080, 2400)
        val f = view.javaClass.getDeclaredField("playRect").apply { isAccessible = true }
        val r = f.get(view) as RectF
        assertFalse(r.isEmpty)
        val shadow = Shadows.shadowOf(c.get())
        assertNull(shadow.peekNextStartedActivity())

        val t = TestViews.now()
        fun send(action: Int, x: Float, y: Float) {
            val e = MotionEvent.obtain(t, t, action, x, y, 0)
            view.dispatchTouchEvent(e); e.recycle()
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
        val c = Robolectric.buildActivity(MainActivity::class.java).create().start().resume()
        val view = contentChild(c.get())
        view.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(2400, View.MeasureSpec.EXACTLY))
        view.layout(0, 0, 1080, 2400)
        val f = view.javaClass.getDeclaredField("playRect").apply { isAccessible = true }
        val r = f.get(view) as RectF
        val shadow = Shadows.shadowOf(c.get())
        val t = TestViews.now()
        fun tap() {
            for (a in intArrayOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
                val e = MotionEvent.obtain(t, t, a, r.centerX(), r.centerY(), 0)
                view.dispatchTouchEvent(e); e.recycle()
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

    // ---- SoundEngine ----

    private fun waveSample(phase: Double): Double {
        val m = SoundEngine::class.java.getDeclaredMethod("waveSample", Double::class.javaPrimitiveType)
        m.isAccessible = true
        return m.invoke(SoundEngine(), phase) as Double
    }

    @Test
    fun waveSampleIsASineAtQuarterPoints() {
        assertEquals(0.0, waveSample(0.0), 1e-9)
        assertEquals(1.0, waveSample(2 * PI * 0.25), 1e-9)
        assertEquals(0.0, waveSample(2 * PI * 0.5), 1e-9)
        assertEquals(-1.0, waveSample(2 * PI * 0.75), 1e-9)
        assertEquals(0.0, waveSample(2 * PI), 1e-9)
    }

    @Test
    fun waveSampleStaysInRangeOverManyPhases() {
        for (i in 0..2000) assertTrue(waveSample(i * 0.01) in -1.0..1.0)
    }

    @Test
    fun everySoundMethodRunsWithoutThrowing() {
        val s = SoundEngine()
        s.playConnectBlip(0); s.playConnectBlip(3); s.playConnectBlip(100)
        s.playPopClear(); s.playBoop(); s.playDropLand(); s.playMilestone(); s.playShuffle()
    }

    @Test
    fun playingWithSoundAndHapticsOffAndOnWorks() {
        for (enabled in listOf(false, true)) {
            val v = TestViews.gameView()
            v.prefs.soundEnabled = enabled
            v.prefs.hapticEnabled = enabled
            val c = TestViews.canvas(v)
            val (x, y) = TestViews.cellCenter(v, 0, 0)
            TestViews.touch(v, MotionEvent.ACTION_DOWN, x, y)
            TestViews.touch(v, MotionEvent.ACTION_UP, x, y)
            TestViews.frames(v, c, 30)
            assertEquals(enabled, v.prefs.soundEnabled)
        }
    }

    // ---- HapticEngine ----

    @Test
    fun hapticMethodsDoNotThrow() {
        val h = HapticEngine(TestViews.app())
        h.tick(); h.pop(); h.milestone(); h.shuffle()
    }

    // ---- RenderThread ----

    @Test(timeout = 20000)
    fun renderThreadStartsAndStopsWithoutAValidSurface() {
        val t = RenderThread(TestViews.gameView())
        assertTrue(t.running)
        t.start()
        Thread.sleep(100)
        t.running = false
        t.join(10000L)
        assertFalse(t.isAlive)
    }

    @Test(timeout = 20000)
    fun renderThreadStoppedBeforeItRunsExitsAtOnce() {
        val t = RenderThread(TestViews.gameView())
        t.running = false
        t.start()
        t.join(10000L)
        assertFalse(t.isAlive)
    }
}
