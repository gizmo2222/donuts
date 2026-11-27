package com.donuts.game

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Bundle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * The saveState race (B36): the activity saves on the main thread while the render thread draws and
 * advances the board. Saving takes the holder lock, so it never writes a half-cleared board.
 */
@RunWith(RobolectricTestRunner::class)
class SaveRaceTest {

    private fun blockedUntilReleased(v: GameView, save: () -> Unit) {
        val done = CountDownLatch(1)
        val failed = AtomicReference<Throwable?>(null)
        val t = Thread { try { save() } catch (e: Throwable) { failed.set(e) } finally { done.countDown() } }
        synchronized(v.holder) {
            t.start()
            assertFalse("the save ran while the frame lock was held", done.await(150, TimeUnit.MILLISECONDS))
        }
        assertTrue(done.await(5, TimeUnit.SECONDS))
        failed.get()?.let { throw it }
    }

    @Test(timeout = 20000)
    fun saveStateWaitsForTheFrameLock() {
        val v = TestViews.quietView()
        TestViews.playHintChain(v)                         // a chain is mid-pop when the save comes
        val out = Bundle()
        blockedUntilReleased(v) { v.saveState(out) }
        assertNotNull(out.getIntArray("board.cells"))
        assertEquals(TestViews.total(v), out.getInt("board.sessionSaved"))
    }

    @Test(timeout = 20000)
    fun saveToPrefsWaitsForTheFrameLock() {
        val v = TestViews.quietView()
        TestViews.playHintChain(v)
        blockedUntilReleased(v) { v.saveToPrefs() }
        assertNotNull(SavedBoard.decode(v.prefs.savedBoard))
        assertEquals(TestViews.total(v), v.prefs.lifetimeDonuts)
    }

    @Test(timeout = 60000)
    fun savesWhileTheRenderThreadDrawsAreAlwaysWholeSettledBoards() {
        val v = TestViews.quietView()
        v.surfaceW = 540; v.surfaceH = 1200; v.computeLayout()
        val frame = Canvas(Bitmap.createBitmap(540, 1200, Bitmap.Config.ARGB_8888))
        val crash = AtomicReference<Throwable?>(null)
        val t = RenderThread(v)
        t.lock = { frame }; t.post = { }
        t.setUncaughtExceptionHandler { _, e -> crash.set(e) }
        t.start()
        var lastSaved = 0
        try {
            repeat(40) {
                synchronized(v.holder) {
                    if (!v.board.hasValidMoves()) v.board.shuffle()
                    TestViews.playHintChain(v)
                }
                val out = Bundle()
                v.saveState(out)
                val saved = out.getInt("board.sessionSaved")
                assertTrue("the banked count never goes back", saved >= lastSaved)
                lastSaved = saved
                val fresh = TestViews.quietView(seed = 2)
                assertTrue("every save restores", fresh.restoreState(out))
                assertTrue("no half-cleared board is saved", fresh.board.findMatches().isEmpty())
                assertEquals(saved, fresh.board.totalCleared)
            }
        } finally { t.running = false; t.join(5000L) }
        crash.get()?.let { throw AssertionError("the render thread crashed", it) }
        assertTrue(lastSaved >= 40 * 3)
        assertEquals("every donut is banked once", lastSaved, v.prefs.lifetimeDonuts)
    }
}
