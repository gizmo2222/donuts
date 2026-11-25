package com.donuts.game

import android.graphics.Bitmap
import android.graphics.Canvas
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

/** The settings pictures paint, show their choice, and the tap-twice icons pulse only with motion on. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SettingsIconsTest {

    private val v = TestViews.gameView()

    private fun pixels(draw: (Canvas) -> Unit): IntArray {
        val b = Bitmap.createBitmap(v.surfaceW, v.surfaceH, Bitmap.Config.ARGB_8888)
        draw(Canvas(b))
        val px = IntArray(b.width * b.height)
        b.getPixels(px, 0, b.width, 0, 0, b.width, b.height)
        return px
    }

    private fun icon(which: Int, on: Boolean) =
        pixels { v.drawSettingsIcon(it, v.settingsIconRects[which], which, on, "Word") }

    @Test fun everyPicturePaintsAndLooksDifferentOnAndOff() {
        for (which in 0 until 4) {
            val on = icon(which, true); val off = icon(which, false)
            assertTrue("icon $which on is blank", on.toHashSet().size > 4)
            assertTrue("icon $which off is blank", off.toHashSet().size > 4)
            assertFalse("icon $which looks the same both ways", on.contentEquals(off))
        }
    }

    @Test fun picturesStayInsideTheirColumn() {
        for (which in 0 until 4) for (on in listOf(true, false)) {
            val r = v.settingsIconRects[which]
            val px = icon(which, on)
            for (i in px.indices) if (px[i] != 0) {
                val x = i % v.surfaceW; val y = i / v.surfaceW
                assertTrue("icon $which on=$on paints outside at $x,$y",
                    x >= r.left - 2 && x <= r.right + 2 && y >= r.top - 2 && y <= r.bottom + 2)
            }
        }
    }

    @Test fun anArmedConfirmIconPulsesOnlyWithMotionOn() {
        fun at(now: Long, restart: Boolean) =
            pixels { v.drawConfirmIcon(it, now, 100f, 100f, 30f, restart, armed = true) }
        for (restart in listOf(true, false)) {
            v.reducedMotion = false
            assertFalse("restart=$restart should pulse", at(0L, restart).contentEquals(at(220L, restart)))
            v.reducedMotion = true
            assertTrue("restart=$restart should hold still", at(0L, restart).contentEquals(at(220L, restart)))
        }
        v.reducedMotion = false
    }
}
