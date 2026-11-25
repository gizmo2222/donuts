package com.donuts.game

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.RectF
import android.graphics.Typeface
import android.view.View
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The donut shop on the home screen: under PLAY at every gallery size, each piece painted in its spot, kept forever. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ShopSceneTest {

    private val gallery = listOf(1080 to 2400, 2400 to 1080, 720 to 1280, 2560 to 1600, 1600 to 2560)
    private val box = RectF(20f, 20f, 520f, 220f)

    private fun prefs(): Prefs {
        TestViews.app().getSharedPreferences("donuts", 0).edit().clear().commit()
        return Prefs(TestViews.app())
    }

    private fun mainView(w: Int, h: Int): MainView {
        val mv = MainView(TestViews.app()) {}
        mv.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY))
        mv.layout(0, 0, w, h)
        return mv
    }

    private fun checkLayout(sizes: List<Pair<Int, Int>>) {
        for ((w, h) in sizes) {
            prefs()
            val mv = mainView(w, h)
            val u = UiScale(TestViews.app()).also { it.update(w, h) }.u
            val shop = RectF(mv.shopRect)
            val tag = "${w}x$h shop $shop play ${mv.playRect}"
            assertFalse(tag, shop.isEmpty)
            assertTrue(tag, shop.left >= 0f && shop.right <= w && shop.bottom <= h)
            assertTrue(tag, shop.height() >= 48f * u)
            assertTrue(tag, shop.top >= mv.playRect.bottom + 10f * u)
            assertEquals(tag, MainView.SHOP_ASPECT, shop.width() / shop.height(), 0.01f)
        }
    }

    @Test fun theShopFitsUnderPlayAtEveryGallerySize() = checkLayout(gallery)

    @Test @Config(qualifiers = "xxhdpi")
    fun theShopFitsUnderPlayOnARealDensityPhone() = checkLayout(listOf(1080 to 2400, 2400 to 1080))

    private fun paint(scene: ShopScene, earned: Int, t: Float = 0f, still: Boolean = true): Bitmap =
        Bitmap.createBitmap(540, 240, Bitmap.Config.ARGB_8888).also { scene.draw(Canvas(it), box, earned, t, still) }

    private fun assertInside(bmp: Bitmap, r: RectF, tag: String) {
        for (y in 0 until bmp.height) for (x in 0 until bmp.width)
            if (Color.alpha(bmp.getPixel(x, y)) != 0)
                assertTrue("$tag painted $x,$y outside $r", x + 1 >= r.left && x - 1 <= r.right && y + 1 >= r.top && y - 1 <= r.bottom)
    }

    /** Adding each piece changes paint only inside that piece's spot, and the bare shop stays inside its box. */
    @Test fun eachPiecePaintsOnlyInsideItsSpot() {
        val scene = ShopScene(Typeface.DEFAULT_BOLD)
        var before = paint(scene, 0)
        assertInside(before, box, "bare shop")
        for ((i, piece) in ShopRules.PIECES.withIndex()) {
            val after = paint(scene, i + 1)
            val spot = scene.spot(box, piece)
            var changed = 0
            for (y in 0 until after.height) for (x in 0 until after.width) {
                if (after.getPixel(x, y) == before.getPixel(x, y)) continue
                changed++
                assertTrue("$piece changed $x,$y outside $spot",
                    x + 2 >= spot.left && x - 2 <= spot.right && y + 2 >= spot.top && y - 2 <= spot.bottom)
            }
            assertTrue("$piece painted only $changed pixels", changed > 150)
            before = after
        }
    }

    /** The lamp breathes and the roof donut bobs, but every frame stays in the scene box. */
    @Test fun aMovingFullShopStaysInsideItsBox() {
        val scene = ShopScene(Typeface.DEFAULT_BOLD)
        for (step in 0..24) assertInside(paint(scene, ShopRules.COUNT, step * 0.13f, still = false), box, "step $step")
    }

    @Test fun lifetimeDonutsBuildTheShopAndResetStickersCannotTakeItDown() {
        val p = prefs()
        assertEquals(0, mainView(1080, 2400).shopEarned)
        p.lifetimeDonuts = ShopPiece.WINDOW_DONUTS.unlockAt
        val mv = mainView(1080, 2400)
        assertEquals(3, mv.shopEarned)
        assertEquals(3, p.shopEarned)
        p.lifetimeDonuts = 0                                  // what Reset stickers does
        mv.refreshShop()
        assertEquals(3, mv.shopEarned)
        p.lifetimeDonuts = ShopPiece.LAMP.unlockAt
        mv.refreshShop()
        assertEquals(4, mv.shopEarned)
        assertEquals(4, p.shopEarned)
    }

    /** A full shop paints its pieces on the home screen at every size, inside the shop's box. */
    @Test fun aFullShopShowsOnTheHomeScreen() {
        for ((w, h) in gallery) {
            fun shot(earned: Int): Pair<Bitmap, RectF> {
                prefs().shopEarned = earned
                val mv = mainView(w, h)
                assertEquals(earned, mv.shopEarned)
                val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                mv.draw(Canvas(bmp))
                return bmp to RectF(mv.shopRect)
            }
            val (bare, s) = shot(0)
            val (full, _) = shot(ShopRules.COUNT)
            var changed = 0
            for (y in 0 until h step 2) for (x in 0 until w step 2) {
                if (bare.getPixel(x, y) == full.getPixel(x, y)) continue
                assertTrue("${w}x$h changed $x,$y outside $s", s.contains(x.toFloat(), y.toFloat()))
                changed++
            }
            assertTrue("${w}x$h only $changed changed", changed > 500)
        }
    }
}
