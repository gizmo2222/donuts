package com.donuts.game

import android.view.MotionEvent
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.math.PI

/** SoundEngine, HapticEngine and RenderThread smoke tests (moved from ActivityTest). */
@RunWith(RobolectricTestRunner::class)
class EnginesTest {

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
