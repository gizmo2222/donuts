package com.donuts.game

import android.graphics.Bitmap
import android.graphics.Canvas
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.concurrent.atomic.AtomicInteger
import kotlin.random.Random

/** The render loop with a bitmap in place of the surface: its pacing, the holder lock, a failed lock or post (B36). */
@RunWith(RobolectricTestRunner::class)
class RenderThreadTest {

    /** A view that is not laid out, so drawFrame only paints the cream background and costs almost nothing. */
    private fun bareView(): GameView {
        val prefs = Prefs(TestViews.app())
        return GameView(TestViews.app(), GameBoard(6, 6, Random(1)), prefs)
    }

    private fun bitmapCanvas() = Canvas(Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888))

    private fun waitFor(ms: Long, what: () -> Boolean): Boolean {
        val end = System.currentTimeMillis() + ms
        while (System.currentTimeMillis() < end) { if (what()) return true; Thread.sleep(5) }
        return what()
    }

    private fun stop(t: RenderThread) { t.running = false; t.join(5000L); assertTrue(!t.isAlive) }

    @Test(timeout = 20000)
    fun framesArePacedToAboutSixtyASecondNotAsFastAsTheyDraw() {
        val v = bareView(); val c = bitmapCanvas()
        val posts = AtomicInteger()
        val t = RenderThread(v)
        t.lock = { c }; t.post = { posts.incrementAndGet() }
        val t0 = System.nanoTime()
        t.start()
        Thread.sleep(600)
        val n = posts.get(); val ms = (System.nanoTime() - t0) / 1_000_000L
        stop(t)
        val paced = ms * 60 / 1000
        assertTrue("$n frames in $ms ms is faster than 60 fps", n <= paced + 3)
        assertTrue("$n frames in $ms ms is far too slow", n >= paced / 4)
    }

    @Test(timeout = 20000)
    fun everyFrameIsDrawnUnderTheHolderLockAndPostedOutsideIt() {
        val v = bareView(); val c = bitmapCanvas()
        val posts = AtomicInteger(); val postedLocked = AtomicInteger()
        val t = RenderThread(v)
        t.lock = { c }
        t.post = { if (Thread.holdsLock(v.holder)) postedLocked.incrementAndGet(); posts.incrementAndGet() }
        synchronized(v.holder) {
            t.start()
            Thread.sleep(150)
            assertEquals("no frame is drawn while the lock is held", 0, posts.get())
        }
        assertTrue(waitFor(3000) { posts.get() >= 3 })
        stop(t)
        assertEquals(0, postedLocked.get())
    }

    @Test(timeout = 20000)
    fun noCanvasMeansNoDrawAndNoPostUntilTheSurfaceIsBack() {
        val v = bareView(); val c = bitmapCanvas()
        val locks = AtomicInteger(); val posts = AtomicInteger()
        val t = RenderThread(v)
        t.lock = { if (locks.incrementAndGet() <= 4) null else c }
        t.post = { posts.incrementAndGet() }
        t.start()
        assertTrue(waitFor(3000) { posts.get() >= 2 })
        stop(t)
        assertTrue(locks.get() >= 6)
        assertTrue("a post per canvas, none for a missing one", posts.get() <= locks.get() - 4)
    }

    @Test(timeout = 20000)
    fun aSurfaceThatVanishesMidPostDoesNotStopTheLoop() {
        val v = bareView(); val c = bitmapCanvas()
        val posts = AtomicInteger()
        val t = RenderThread(v)
        t.lock = { c }
        t.post = { posts.incrementAndGet(); throw IllegalStateException("surface gone") }
        t.start()
        assertTrue(waitFor(3000) { posts.get() >= 4 })
        assertTrue(t.isAlive)
        stop(t)
    }
}
