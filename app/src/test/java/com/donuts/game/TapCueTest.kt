package com.donuts.game

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.view.MotionEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

/** Under reduced motion the tap wobble is still, so a fading white ring shows the boop instead (B31). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TapCueTest {

    private fun blank(v: GameView) = Bitmap.createBitmap(v.surfaceW, v.surfaceH, Bitmap.Config.ARGB_8888)

    /** The pixel on the ring's right edge round cell ([row], [col]). */
    private fun ring(v: GameView, bmp: Bitmap, row: Int, col: Int): Int {
        val x = (v.boardLeft + (col + 0.5f) * v.cellSize + v.cellSize * 0.46f).toInt()
        val y = (v.boardTop + (row + 0.5f) * v.cellSize).toInt()
        return bmp.getPixel(x, y)
    }

    @Test fun aCalmTapRingsEveryTappedDonutThenFades() {
        val v = TestViews.gameView()
        v.reducedMotion = true
        v.wobbleCells = listOf(1 to 2, 1 to 3); v.wobbleMs = 1000L; v.wobbleGiggle = false
        val early = blank(v); v.drawTapCue(Canvas(early), 1000L)
        val late = blank(v);  v.drawTapCue(Canvas(late), 1000L + v.WOBBLE_MS * 3 / 4)
        for ((r, c) in v.wobbleCells) {
            val px = ring(v, early, r, c)
            assertTrue(Color.alpha(px) > 200)
            assertTrue("ring is white, not red", Color.red(px) > 200 && Color.green(px) > 200 && Color.blue(px) > 200)
            assertTrue(Color.alpha(ring(v, late, r, c)) in 1 until Color.alpha(px))
        }
        assertEquals(0, Color.alpha(ring(v, early, 4, 4)))
        assertEquals(1000L, v.wobbleMs)
        v.drawTapCue(Canvas(blank(v)), 1000L + v.WOBBLE_MS)
        assertEquals(-1L, v.wobbleMs)
    }

    @Test fun theGiggleRingLastsTwiceAsLong() {
        val v = TestViews.gameView()
        v.reducedMotion = true
        v.wobbleCells = listOf(2 to 2); v.wobbleMs = 1000L; v.wobbleGiggle = true
        val bmp = blank(v); v.drawTapCue(Canvas(bmp), 1000L + v.WOBBLE_MS * 3 / 2)
        assertTrue(Color.alpha(ring(v, bmp, 2, 2)) > 0)
        assertEquals(1000L, v.wobbleMs)
    }

    @Test fun withFullMotionTheWobbleIsTheCueAndNoRingIsDrawn() {
        val v = TestViews.gameView()
        v.reducedMotion = false
        v.wobbleCells = listOf(1 to 2); v.wobbleMs = 1000L
        val bmp = blank(v); v.drawTapCue(Canvas(bmp), 1000L)
        assertEquals(0, Color.alpha(ring(v, bmp, 1, 2)))
        assertEquals(1000L, v.wobbleMs)
    }

    @Test fun aRealCalmTapPaintsTheRingInTheFrame() {
        val v = TestViews.gameView()
        v.prefs.soundEnabled = false; v.prefs.hapticEnabled = false
        v.reducedMotion = true
        v.fxRandom = TestViews.fixedRandom(1)      // not the rare giggle
        val (x, y) = TestViews.cellCenter(v, 2, 2)
        TestViews.touch(v, MotionEvent.ACTION_DOWN, x, y)
        TestViews.touch(v, MotionEvent.ACTION_UP, x, y)
        assertEquals(listOf(2 to 2), v.wobbleCells)
        val bmp = blank(v); v.drawFrame(Canvas(bmp))
        val px = ring(v, bmp, 2, 2)
        assertTrue("a white ring shows the tap", Color.red(px) > 200 && Color.green(px) > 200 && Color.blue(px) > 200)
    }
}
