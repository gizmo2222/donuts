package com.donuts.game

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs

/** The sprite cache against the vector painters it stands in for, and the board drawn from it (B36). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SpriteTest {

    private fun blank(side: Int) = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888)
    private fun pixels(b: Bitmap) = IntArray(b.width * b.height).also { b.getPixels(it, 0, b.width, 0, 0, b.width, b.height) }

    private fun far(a: Int, b: Int, tol: Int) =
        abs(Color.alpha(a) - Color.alpha(b)) > tol || abs(Color.red(a) - Color.red(b)) > tol ||
            abs(Color.green(a) - Color.green(b)) > tol || abs(Color.blue(a) - Color.blue(b)) > tol

    /** Share of pixels, among those painted in either, that differ by more than [tol] in some channel. */
    private fun diff(a: IntArray, b: IntArray, tol: Int = 40): Double {
        var painted = 0; var off = 0
        for (i in a.indices) {
            if (Color.alpha(a[i]) == 0 && Color.alpha(b[i]) == 0) continue
            painted++
            if (far(a[i], b[i], tol)) off++
        }
        return if (painted == 0) 0.0 else off.toDouble() / painted
    }

    private fun every(v: GameView) = v.pieceSprites.toList() + v.ballSprites.toList()

    @Test fun everySpriteIsPaintedAtTheCellRadiusWithRoomToSpare() {
        val v = TestViews.gameView()
        assertEquals(v.cellSize * 0.43f, v.spriteR, 0.001f)
        for ((i, bmp) in every(v).withIndex()) {
            assertNotNull("sprite $i", bmp)
            assertEquals(v.spriteHalf * 2, bmp!!.width); assertEquals(v.spriteHalf * 2, bmp.height)
            val px = pixels(bmp); val w = bmp.width
            assertTrue("sprite $i is painted", px.count { Color.alpha(it) > 200 } > w * w / 5)
            for (k in 0 until w) for (p in listOf(px[k], px[(w - 1) * w + k], px[k * w], px[k * w + w - 1]))
                assertEquals("sprite $i reaches its edge, so it is clipped", 0, Color.alpha(p))
        }
    }

    @Test fun theSixDonutsAndTheirSelectedLooksAreAllDifferent() {
        val v = TestViews.gameView()
        val px = every(v).map { pixels(it!!) }
        for (i in px.indices) for (j in i + 1 until px.size)
            assertTrue("sprites $i and $j look the same", diff(px[i], px[j]) > 0.05)
    }

    @Test fun anUnscaledBlitIsTheVectorDonutAndAScaledOneIsCloseToIt() {
        val v = TestViews.gameView()
        val side = v.spriteHalf * 2; val mid = v.spriteHalf.toFloat()
        for (t in DonutType.values()) for (sel in listOf(false, true)) {
            val fast = blank(side); v.drawPiece(Canvas(fast), mid, mid, v.spriteR, t, sel)
            val vec = blank(side); v.drawPieceVector(Canvas(vec), mid, mid, v.spriteR, t, sel, 255)
            assertTrue("$t sel=$sel blit", diff(pixels(fast), pixels(vec)) < 0.01)
        }
        val big = v.spriteR * 1.25f; val bigSide = (side * 1.3f).toInt(); val bigMid = bigSide / 2f
        for (t in DonutType.values()) {
            val scaled = blank(bigSide); v.drawPiece(Canvas(scaled), bigMid, bigMid, big, t, false)
            val vec = blank(bigSide); v.drawPieceVector(Canvas(vec), bigMid, bigMid, big, t, false, 255)
            assertTrue("$t scaled", diff(pixels(scaled), pixels(vec), 64) < 0.10)
        }
        val ball = blank(side); v.drawBall(Canvas(ball), mid, mid, v.spriteR, false, 255)
        val ballVec = blank(side); v.drawBallVector(Canvas(ballVec), mid, mid, v.spriteR, false, 255)
        assertTrue("ball blit", diff(pixels(ball), pixels(ballVec)) < 0.01)
    }

    @Test fun withNoSpritesTheVectorPainterDrawsInstead() {
        val v = TestViews.gameView()
        val side = v.spriteHalf * 2; val mid = v.spriteHalf.toFloat(); val r = v.spriteR
        v.spriteR = 0f
        val t = DonutType.values()[2]
        val fallback = blank(side); v.drawPiece(Canvas(fallback), mid, mid, r, t, false)
        val vec = blank(side); v.drawPieceVector(Canvas(vec), mid, mid, r, t, false, 255)
        assertEquals(0.0, diff(pixels(fallback), pixels(vec), 0), 0.0)
    }

    @Test fun aNewLayoutRepaintsTheSpritesAndFreesTheOldOnes() {
        val v = TestViews.gameView()
        val old = every(v).map { it!! }
        val oldHalf = v.spriteHalf
        v.surfaceW = 720; v.surfaceH = 1600; v.computeLayout()
        assertTrue(v.spriteHalf < oldHalf)
        assertEquals(v.cellSize * 0.43f, v.spriteR, 0.001f)
        for ((i, bmp) in every(v).withIndex()) {
            assertNotSame(old[i], bmp)
            assertTrue("old sprite $i is recycled", old[i].isRecycled)
            assertEquals(v.spriteHalf * 2, bmp!!.width)
        }
    }

    @Test fun wearingAToppingRepaintsEveryDonutSprite() {
        val v = TestViews.gameView()
        val plain = v.pieceSprites.map { pixels(it!!) }
        v.wearTopping(Topping.SPRINKLES)
        for ((i, bmp) in v.pieceSprites.withIndex())
            assertTrue("sprite $i wears the sprinkles", diff(plain[i], pixels(bmp!!), 24) > 0.01)
    }

    @Test fun theStillBoardDrawsEachCellsOwnDonutAtItsCentre() {
        val v = TestViews.gameView()
        v.reducedMotion = true; v.boardEntryMs = -1L
        val w = v.surfaceW; val h = v.surfaceH
        val board = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        v.drawCellsInner(Canvas(board), 100_000L)
        val types = DonutType.values()
        fun expected(swap: Pair<Int, Int>?): Bitmap {
            val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888); val c = Canvas(b)
            for (r in 0 until v.board.rows) for (col in 0 until v.board.cols) {
                var t = v.board.grid[r][col].type
                if (swap == r to col) t = types[(t.ordinal + 1) % types.size]
                v.drawPieceVector(c, v.boardLeft + (col + 0.5f) * v.cellSize, v.boardTop + (r + 0.5f) * v.cellSize,
                    v.spriteR, t, false, 255)
            }
            return b
        }
        fun cellDiff(a: Bitmap, b: Bitmap, r: Int, col: Int): Double {
            val x = (v.boardLeft + col * v.cellSize).toInt(); val y = (v.boardTop + r * v.cellSize).toInt()
            val s = v.cellSize.toInt()
            val pa = IntArray(s * s); a.getPixels(pa, 0, s, x, y, s, s)
            val pb = IntArray(s * s); b.getPixels(pb, 0, s, x, y, s, s)
            return diff(pa, pb)
        }
        val want = expected(null)
        for (r in 0 until v.board.rows) for (col in 0 until v.board.cols)
            assertTrue("cell $r,$col", cellDiff(board, want, r, col) < 0.03)
        val wrong = expected(2 to 3)
        assertTrue("a different donut in a cell is caught", cellDiff(board, wrong, 2, 3) > 0.10)
    }
}
