package com.donuts.game

import android.view.MotionEvent
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** SoundEngine, HapticEngine and RenderThread smoke tests (moved from ActivityTest). */
@RunWith(RobolectricTestRunner::class)
class EnginesTest {

    // ---- SoundEngine ----

    @Test
    fun everySoundMethodRunsWithoutThrowing() {
        val s = SoundEngine()
        s.playConnectBlip(0); s.playConnectBlip(3); s.playConnectBlip(100)
        s.playPopClear(); s.playBoop(); s.playDropLand(); s.playMilestone(); s.playShuffle(); s.playFriend(-1)
        s.playMilestone(1, golden = true); s.playMilestone(-1); s.playGiggle(); s.playPromptDone()
        for (i in 0 until FriendRules.FRIEND_COUNT + 1) s.playFriend(i)     // a jingle for every friend, and past the end
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
