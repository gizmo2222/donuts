package com.donuts.game

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.RectF
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The donut pal beside the counter at the gallery sizes: on screen, in the counter row, clear of the chip and figures. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PalLayoutTest {

    private val gallery = listOf(1080 to 2400, 2400 to 1080, 720 to 1280, 2560 to 1600, 1600 to 2560)

    private fun check(sizes: List<Pair<Int, Int>>) {
        for ((w, h) in sizes) for (grid in listOf(6, 8)) for (count in listOf(0, 250, 9999)) {
            val v = TestViews.gameView(w = w, h = h, grid = grid)
            v.prefs.soundEnabled = false
            v.displayedCount = count
            TestViews.frames(v, TestViews.canvas(v), 1)
            val tag = "${w}x$h grid $grid count $count"
            val pal = RectF(v.palRect)
            val digits = RectF(v.counterDigits)
            val boardBottom = v.boardTop + v.board.rows * v.cellSize
            assertFalse(tag, pal.isEmpty)
            for (r in listOf(pal, digits)) {
                assertTrue(tag, r.left >= v.insetL && r.right <= v.surfaceW - v.insetR)
                assertTrue(tag, r.top >= boardBottom && r.bottom <= v.boxStrip.rect.top)
            }
            assertTrue(tag, pal.right <= digits.left)
            if (!v.promptRect.isEmpty) assertFalse(tag, RectF.intersects(pal, v.promptRect))
            assertTrue(tag, pal.height() >= 40f * v.u)            // a face Steven can read at a glance
            // The highest hop of any reaction, stretched, still stays under the board
            val r = pal.height() / PAL_TALL
            val hopTop = pal.bottom - (pal.height() + PalMotion.MAX_LIFT * r) * PalMotion.MAX_STRETCH
            assertTrue(tag, hopTop >= boardBottom)
        }
    }

    @Test fun thePalFitsTheCounterRowAtEveryGallerySize() = check(gallery)

    @Test @Config(qualifiers = "xxhdpi")
    fun thePalFitsOnARealDensityPhone() = check(listOf(1080 to 2400, 2400 to 1080))

    @Test @Config(qualifiers = "xhdpi")
    fun thePalFitsOnASmallPhone() = check(listOf(720 to 1280))

    @Test fun thePalPaintsOnlyInsideItsOwnBox() {
        val v = TestViews.gameView()
        val bmp = Bitmap.createBitmap(400, 400, Bitmap.Config.ARGB_8888)
        val r = 100f; val cx = 200f; val footY = 320f
        v.drawPal(Canvas(bmp), cx, footY, r, PalMotion.STILL)
        val box = RectF(cx - r * PAL_HALF_W, footY - r * PAL_TALL, cx + r * PAL_HALF_W, footY + r * 0.12f)
        var inside = 0
        val glaze = android.graphics.Color.rgb(255, 140, 35)
        var orange = 0
        var out = 0; var firstOut = ""
        for (y in 0 until 400) for (x in 0 until 400) {
            val p = bmp.getPixel(x, y)
            if (android.graphics.Color.alpha(p) == 0) continue
            if (!(x + 1 >= box.left && x - 1 <= box.right && y + 1 >= box.top && y - 1 <= box.bottom)) {
                out++; if (out == 1) firstOut = "$x,$y"
            }
            inside++
            if (p == glaze) orange++
        }
        assertEquals("painted outside the box, first at $firstOut", 0, out)
        assertTrue(inside > 15_000)
        assertTrue(orange > 2_000)                              // the orange glaze shows
    }
}
