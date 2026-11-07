package com.donuts.game

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

/** Reduced motion swaps the refusal shake for a still, fading ring. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RefusedCueTest {

    private fun blank(v: GameView) =
        Bitmap.createBitmap(v.surfaceW, v.surfaceH, Bitmap.Config.ARGB_8888)

    private fun ringAlpha(v: GameView, bmp: Bitmap): Int {
        val x = (v.boardLeft + 2.5f * v.cellSize + v.cellSize * 0.46f).toInt()
        val y = (v.boardTop + 1.5f * v.cellSize).toInt()
        return Color.alpha(bmp.getPixel(x, y))
    }

    @Test fun reducedMotionDrawsAFadingRingThenExpires() {
        val v = TestViews.gameView()
        v.reducedMotion = true
        v.shakeCell = 1 to 2; v.shakeMs = 1000L
        val early = blank(v); v.drawRefusedCue(Canvas(early), 1000L)
        val late = blank(v);  v.drawRefusedCue(Canvas(late), 1000L + v.SHAKE_MS * 3 / 4)
        assertTrue(ringAlpha(v, early) > 150)
        assertTrue(ringAlpha(v, late) in 1 until ringAlpha(v, early))
        assertEquals(1000L, v.shakeMs)
        v.drawRefusedCue(Canvas(blank(v)), 1000L + v.SHAKE_MS)
        assertEquals(-1L, v.shakeMs)
        assertNull(v.shakeCell)
    }

    @Test fun withoutReducedMotionNothingIsDrawn() {
        val v = TestViews.gameView()
        v.reducedMotion = false
        v.shakeCell = 1 to 2; v.shakeMs = 1000L
        val bmp = blank(v); v.drawRefusedCue(Canvas(bmp), 1000L)
        assertEquals(0, ringAlpha(v, bmp))
        assertEquals(1000L, v.shakeMs)
    }
}
