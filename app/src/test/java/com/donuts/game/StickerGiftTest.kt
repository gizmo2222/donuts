package com.donuts.game

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs

/** B11: a sticker not earned yet is a wrapped present, with no words to tick off. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class StickerGiftTest : GameInputTestBase() {

    private fun near(a: Int, b: Int, tol: Int = 30) =
        abs(Color.red(a) - Color.red(b)) <= tol && abs(Color.green(a) - Color.green(b)) <= tol &&
            abs(Color.blue(a) - Color.blue(b)) <= tol

    private fun shot(v: GameView): Bitmap {
        val bmp = Bitmap.createBitmap(v.surfaceW, v.surfaceH, Bitmap.Config.ARGB_8888)
        TestViews.frames(v, Canvas(bmp), 2)
        return bmp
    }

    /** A spot on tile [i]'s paper: below the ribbon, beside the left edge, clear of the paper dots. */
    private fun paper(v: GameView, bmp: Bitmap, i: Int): Int {
        val r = v.stickerRects[i]
        return bmp.getPixel((r.left + r.width() * 0.05f).toInt(), (r.top + r.height() * 0.75f).toInt())
    }

    @Test fun lockedTilesAreWrappedInTurnAndTheRibbonCrossesThem() {
        val v = planted()
        openStickers(v)
        val bmp = shot(v)
        for (i in 0 until 12) {
            assertTrue("tile $i paper", near(paper(v, bmp, i), GIFT_WRAP[i % GIFT_WRAP.size]))
            val r = v.stickerRects[i]
            val ribbon = bmp.getPixel(r.centerX().toInt(), (r.bottom - r.height() * 0.06f).toInt())
            assertTrue("tile $i ribbon", near(ribbon, Color.rgb(255, 208, 60)))
        }
    }

    @Test fun anEarnedStickerShowsItsOwnTileNotAPresent() {
        val v = planted()
        v.prefs.lifetimeDonuts = 10   // earns sticker 0, "Pop 10"
        openStickers(v)
        val bmp = shot(v)
        assertFalse(near(paper(v, bmp, 0), GIFT_WRAP[0]))
        assertTrue(near(paper(v, bmp, 1), GIFT_WRAP[1]))
    }

    @Test fun theStatsLineCountsStickersWithoutATotalToFinish() {
        assertEquals("0 donuts popped", StickerRules.statsLine(0, 0))
        assertEquals("12 donuts popped  ·  1 sticker", StickerRules.statsLine(12, 1))
        assertEquals("600 donuts popped  ·  12 stickers", StickerRules.statsLine(600, 12))
        assertFalse(StickerRules.statsLine(600, 12).contains("of"))
    }
}
