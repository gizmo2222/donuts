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

    private fun paint(scene: ShopScene, earned: Int, t: Float = 0f, still: Boolean = true, pop: Float = 1f): Bitmap =
        Bitmap.createBitmap(540, 240, Bitmap.Config.ARGB_8888).also { scene.draw(Canvas(it), box, earned, t, still, pop) }

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

    /** A piece popping in, with sparkles or (reduced motion) a glow, never paints outside the scene box. */
    @Test fun aPoppingPieceStaysInsideTheBox() {
        val scene = ShopScene(Typeface.DEFAULT_BOLD)
        for (n in 1..ShopRules.COUNT) for (still in listOf(false, true)) for (step in 0..10)
            assertInside(paint(scene, n, step * 0.2f, still, step / 10f), box, "piece $n still=$still pop ${step / 10f}")
    }

    /** Mid-pop the new piece is part drawn: it differs both from the shop without it and from the settled shop. */
    @Test fun aPoppingPieceIsBetweenAbsentAndSettled() {
        val scene = ShopScene(Typeface.DEFAULT_BOLD)
        for (still in listOf(false, true)) {
            val without = paint(scene, 6, still = still); val settled = paint(scene, 7, still = still)
            val mid = paint(scene, 7, still = still, pop = 0.3f)
            assertTrue("still=$still", diff(mid, without, box) > 100)
            assertTrue("still=$still", diff(mid, settled, box) > 50)
            assertEquals(0, diff(paint(scene, 7, still = still, pop = 1f), settled, box))
        }
    }

    private fun diff(a: Bitmap, b: Bitmap, r: RectF): Int {
        var n = 0
        for (y in r.top.toInt() until r.bottom.toInt()) for (x in r.left.toInt() until r.right.toInt())
            if (x in 0 until a.width && y in 0 until a.height && a.getPixel(x, y) != b.getPixel(x, y)) n++
        return n
    }

    /** On the home screen a newly earned piece waits, pops in, then is saved as seen; a seen piece shows at once. */
    @Test fun aNewPiecePopsInOnTheHomeScreenAndIsSavedOnceSettled() {
        val (w, h) = 1080 to 2400
        fun shot(mv: MainView) = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { mv.draw(Canvas(it)) }
        prefs().also { it.shopEarned = 6; it.shopSeen = 6 }
        val six = mainView(w, h)
        val spot = ShopScene(Typeface.DEFAULT_BOLD).spot(RectF(six.shopRect), ShopRules.PIECES[6])
        val without = shot(six)

        val p = prefs().also { it.shopEarned = 7; it.shopSeen = 6 }
        val mv = mainView(w, h)
        assertEquals(6, mv.shopSeen)
        assertEquals("waits before popping", 0, diff(shot(mv), without, spot))
        TestViews.advance(ShopRules.REVEAL_DELAY_MS + ShopRules.REVEAL_MS / 2)
        assertTrue("half popped", diff(shot(mv), without, spot) > 100)
        assertEquals(6, p.shopSeen)
        TestViews.advance(ShopRules.REVEAL_MS)
        val settled = shot(mv)
        assertTrue(diff(settled, without, spot) > 100)
        assertEquals(7, mv.shopSeen)
        assertEquals(7, p.shopSeen)

        // Coming back, the piece is already there and nothing pops again
        val again = mainView(w, h)
        assertEquals(7, again.shopSeen)
        assertEquals(0, diff(shot(again), settled, spot))
    }

    /** Leaving mid-reveal keeps only the settled pieces as seen, so the rest pop in next time. */
    @Test fun leavingMidRevealReplaysTheUnseenPieces() {
        val p = prefs().also { it.shopEarned = 9; it.shopSeen = 6 }
        val mv = mainView(1080, 2400)
        val c = Canvas(Bitmap.createBitmap(1080, 2400, Bitmap.Config.ARGB_8888))
        TestViews.advance(ShopRules.REVEAL_DELAY_MS + ShopRules.REVEAL_MS + 10)
        mv.draw(c)
        assertEquals(7, p.shopSeen)
        mv.refreshShop()                                       // what onResume does after a game
        assertEquals(7, mv.shopSeen)
        TestViews.advance(ShopRules.REVEAL_DELAY_MS + 2 * ShopRules.REVEAL_MS)
        mv.draw(c)
        assertEquals(9, p.shopSeen)
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
                prefs().also { it.shopEarned = earned; it.shopSeen = earned }
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
