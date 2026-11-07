package com.donuts.game

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import kotlin.math.PI
import kotlin.math.sin

/** Part 1: every drawing state runs without throwing and paints something. Part 2: the small pure helpers. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DrawSmokeTest {

    private class Scene(val v: GameView) {
        val bmp: Bitmap = Bitmap.createBitmap(v.surfaceW, v.surfaceH, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        fun frames(n: Int, step: Long = 16L) = TestViews.frames(v, c, n, step)
    }

    private fun scene(tutorialSeen: Boolean = true, grid: Int = 6, seed: Int = 1) =
        Scene(TestViews.gameView(grid = grid, seed = seed, tutorialSeen = tutorialSeen))

    private fun distinctColors(bmp: Bitmap): Int {
        val px = IntArray(bmp.width * bmp.height)
        bmp.getPixels(px, 0, bmp.width, 0, 0, bmp.width, bmp.height)
        return px.toHashSet().size
    }

    private fun assertPainted(s: Scene, what: String) =
        assertTrue("$what: bitmap looks blank", distinctColors(s.bmp) > 8)

    /** A snake of [n] cells: row 0 left to right, then row 1 right to left. */
    private fun snake(n: Int): List<Pair<Int, Int>> {
        val out = mutableListOf<Pair<Int, Int>>()
        for (c in 0 until 6) out.add(0 to c)
        for (c in 5 downTo 0) out.add(1 to c)
        return out.take(n)
    }

    private fun dragSnake(s: Scene, n: Int) {
        val v = s.v
        snake(n).forEach { (r, c) -> v.board.grid[r][c].type = DonutType.STRAWBERRY }
        val path = snake(n).map { (r, c) -> TestViews.cellCenter(v, r, c) }
        TestViews.touch(v, MotionEvent.ACTION_DOWN, path[0].first, path[0].second)
        for (p in path.drop(1)) TestViews.touch(v, MotionEvent.ACTION_MOVE, p.first, p.second)
    }

    private fun release(s: Scene) {
        val last = TestViews.cellCenter(s.v, 0, 0)
        TestViews.touch(s.v, MotionEvent.ACTION_UP, last.first, last.second)
    }

    // ---------------- Part 1: smoke ----------------

    @Test fun idleBoardPaints() {
        val s = scene(); s.frames(10); assertPainted(s, "idle")
    }

    @Test fun eightByEightBoardPaints() {
        val s = scene(grid = 8); s.frames(5); assertPainted(s, "8x8")
    }

    @Test fun tutorialPaintsAndStaysActiveUntilAChain() {
        val s = scene(tutorialSeen = false)
        assertTrue(s.v.tutorialActive)
        s.frames(60, 50L)
        assertPainted(s, "tutorial")
        assertTrue(s.v.tutorialActive)
        assertTrue(s.v.tutorialCells.size >= 3)
    }

    @Test fun midDragShowsAChain() {
        val s = scene(); s.frames(3)
        dragSnake(s, 4)
        assertEquals(snake(4), s.v.dragChain.toList())
        s.frames(10); assertPainted(s, "mid-drag")
    }

    @Test fun popAndDropAfterAClear() {
        val s = scene(); s.frames(3)
        dragSnake(s, 3); release(s)
        assertEquals(AnimPhase.POPPING, s.v.animPhase)
        s.frames(10)
        assertPainted(s, "popping")
        s.frames(60, 40L)
        assertEquals(AnimPhase.IDLE, s.v.animPhase)
        assertTrue(s.v.board.donutsCleared.values.sum() >= 3)
    }

    private fun powerUpCase(len: Int, expected: PowerUp) {
        val s = scene(); s.frames(3)
        dragSnake(s, len); release(s)
        assertEquals(expected, s.v.powerFx?.kind)
        s.frames(15, 40L)
        assertPainted(s, "$expected")
        s.frames(80, 40L)
        assertEquals(AnimPhase.IDLE, s.v.animPhase)
        assertTrue(s.v.board.donutsCleared.values.sum() >= len)
    }

    @Test fun bombEffect() = powerUpCase(5, PowerUp.BOMB)
    @Test fun rowBlastEffect() = powerUpCase(7, PowerUp.ROW_BLAST)
    @Test fun colourBurstEffect() = powerUpCase(9, PowerUp.COLOR_BURST)

    @Test fun goldenCellsPaint() {
        val s = scene()
        val b = s.v.board
        for ((r, c) in listOf(0 to 0, 2 to 3, 4 to 5)) b.grid[r][c] = GameCell(b.grid[r][c].type, r, c, isGolden = true)
        s.frames(10); assertPainted(s, "golden")
    }

    @Test fun celebrationBannerAndConfetti() {
        val s = scene(); s.frames(2)
        s.v.celebrate(TestViews.now(), "WOW!")
        assertTrue(s.v.celebrateMs >= 0)
        assertTrue(s.v.particles.isNotEmpty())
        s.frames(30, 50L)
        assertPainted(s, "celebration")
        s.frames(60, 50L)
        assertTrue("banner should end", s.v.celebrateMs < 0)
    }

    @Test fun stickerFlyRunsWhenAStickerIsEarned() {
        val s = scene()
        s.v.prefs.stickersAnnounced = 0
        s.v.prefs.bestChainLength = 4          // earns sticker 3
        s.frames(2)
        assertEquals(3, s.v.stickerFlyIdx)
        s.frames(20, 40L); assertPainted(s, "sticker fly")
        s.frames(40, 40L)
        assertTrue(s.v.stickerFlyMs < 0)
    }

    @Test fun stickersPanelWithEarnedStickersAndBox() {
        val s = scene()
        val p = s.v.prefs
        p.lifetimeDonuts = 75; p.bestChainLength = 8; p.highScore6x6 = 70; p.goldenPopped = true
        p.stickersAnnounced = 0xFFF
        s.frames(2)
        s.v.stickersOpen = true
        s.frames(30, 20L)
        assertTrue(s.v.stickersAnim > 0.99f)
        assertPainted(s, "stickers panel")
    }

    @Test fun stickersPanelEmptyAndFullBoxes() {
        for (life in intArrayOf(0, 119, 120, 5000)) {
            val s = scene()
            s.v.prefs.lifetimeDonuts = life; s.v.prefs.stickersAnnounced = 0
            s.frames(2); s.v.stickersOpen = true; s.frames(25, 20L)
            assertPainted(s, "box $life")
        }
    }

    @Test fun settingsPanelWithConfirmArmed() {
        val s = scene(); s.frames(2)
        s.v.settingsOpen = true
        s.frames(30, 20L)
        assertTrue(s.v.settingsAnim > 0.99f)
        s.v.newGameConfirm.tap(TestViews.now())
        assertTrue(s.v.newGameConfirm.isArmed(TestViews.now()))
        s.frames(5); assertPainted(s, "settings")
        s.v.newGameConfirm.cancel(); s.v.stickersConfirm.tap(TestViews.now())
        s.frames(5); assertPainted(s, "settings stickers confirm")
    }

    @Test fun noMovesWarning() {
        val s = scene(); s.frames(2)
        s.v.noMovesWarningMs = TestViews.now()
        s.frames(20, 50L); assertPainted(s, "no moves")
    }

    @Test fun boxStripPops() {
        val s = scene(); s.frames(2)
        s.v.boxStrip.slotPopMs = TestViews.now(); s.v.boxStrip.boxPopMs = TestViews.now()
        s.v.starPulseMs = TestViews.now()
        s.frames(15); assertPainted(s, "box strip")
    }

    @Test fun shortChainWobbles() {
        val s = scene(); s.frames(2)
        val a = TestViews.cellCenter(s.v, 0, 0)
        TestViews.touch(s.v, MotionEvent.ACTION_DOWN, a.first, a.second)
        TestViews.touch(s.v, MotionEvent.ACTION_UP, a.first, a.second)
        assertTrue(s.v.wobbleMs >= 0)
        s.frames(10); assertPainted(s, "wobble")
    }

    @Test fun everyDonutAndTheBallDraw() {
        val s = scene()
        fun painted(draw: (Canvas) -> Unit): Boolean {
            val b = Bitmap.createBitmap(120, 120, Bitmap.Config.ARGB_8888)
            draw(Canvas(b)); return distinctColors(b) > 3
        }
        for (t in DonutType.values()) for (sel in listOf(false, true)) {
            assertTrue("$t sel=$sel vector blank", painted { s.v.drawPieceVector(it, 60f, 60f, 40f, t, sel, 255) })
            assertTrue("$t sel=$sel sprite blank", painted { s.v.drawPiece(it, 60f, 60f, 40f, t, sel) })
        }
        for (sel in listOf(false, true)) {
            assertTrue("ball vector blank", painted { s.v.drawBallVector(it, 60f, 60f, 40f, sel, 255) })
            assertTrue("ball sprite blank", painted { s.v.drawBall(it, 60f, 60f, 40f, sel, 255) })
        }
    }

    @Test fun everyStickerDraws() {
        val s = scene()
        for (i in 0 until StickerRules.STICKER_COUNT) {
            val b = Bitmap.createBitmap(200, 200, Bitmap.Config.ARGB_8888)
            s.v.drawStickerArt(Canvas(b), i, 100f, 100f, 180f)
            assertTrue("sticker $i blank", distinctColors(b) > 3)
        }
    }

    private fun tap(mv: MainView, x: Float, y: Float) {
        val t = TestViews.now()
        val d = MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, x, y, 0)
        mv.onTouchEvent(d); d.recycle()
        val u = MotionEvent.obtain(t, t, MotionEvent.ACTION_UP, x, y, 0)
        mv.onTouchEvent(u); u.recycle()
    }

    @Test fun mainViewDrawsAndPlayButtonFires() {
        var plays = 0
        val mv = MainView(TestViews.app()) { plays++ }
        mv.measure(
            View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(2400, View.MeasureSpec.EXACTLY))
        mv.layout(0, 0, 1080, 2400)
        val bmp = Bitmap.createBitmap(1080, 2400, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        repeat(5) { TestViews.advance(100); mv.draw(c) }
        assertTrue("title blank", distinctColors(bmp) > 8)
        // A touch in the corner does nothing.
        tap(mv, 5f, 5f)
        assertEquals(0, plays)
        // The play button sits somewhere on the centre line: sweep down it.
        var y = 600f
        while (y < 2300f && plays == 0) { tap(mv, 540f, y); y += 25f }
        assertEquals(1, plays)
    }

    // ---------------- Part 2: pure helpers ----------------

    private val tol = 1e-4f

    @Test fun buttonPressScaleTreatsPressAtTimeZeroAsAPress() {
        val v = TestViews.gameView()
        assertEquals(0.86f, v.buttonPressScale(0L, 0L), tol)   // only a negative stamp means "no press"
    }

    @Test fun buttonPressScaleBoundaries() {
        val v = TestViews.gameView()
        assertEquals(1f, v.buttonPressScale(1000L, -1L), 0f)
        assertEquals(0.86f, v.buttonPressScale(1000L, 1000L), tol)
        assertEquals(0.86f, v.buttonPressScale(900L, 1000L), tol)          // before the press: clamped to the dip
        assertEquals(1f, v.buttonPressScale(1000L + v.PRESS_MS, 1000L), tol)
        assertEquals(1f, v.buttonPressScale(5000L, 1000L), tol)
        val half = 0.86f + 0.14f * (1f - Math.pow(0.5, 5.0).toFloat())
        assertEquals(half, v.buttonPressScale(1000L + v.PRESS_MS / 2, 1000L), tol)
        // never below the dip, never above 1
        for (dt in 0L..200L step 10L) assertTrue(v.buttonPressScale(1000L + dt, 1000L) in 0.86f..1f)
    }

    @Test fun pressScaleForOnlyScalesTheTouchedRect() {
        val v = TestViews.gameView()
        val r = RectF(0f, 0f, 10f, 10f)
        v.optPressRect = r; v.optPressMs = 1000L
        assertEquals(0.86f, v.pressScaleFor(r, 1000L), tol)
        assertEquals(1f, v.pressScaleFor(RectF(0f, 0f, 10f, 10f), 1000L), 0f)   // equal but not the same rect
        v.optPressMs = -1L
        assertEquals(1f, v.pressScaleFor(r, 1000L), 0f)
        v.optPressRect = null
        assertEquals(1f, v.pressScaleFor(r, 1000L), 0f)
    }

    @Test fun nearMissOffsetWobble() {
        val v = TestViews.gameView()
        val cs = v.cellSize
        v.wobbleCells = listOf(2 to 2); v.wobbleMs = 1000L
        assertEquals(0f, v.nearMissOffset(2, 2, 1000L), tol)                       // t = 0
        val t = 42f / v.WOBBLE_MS
        val want = sin(t * PI.toFloat() * 5f) * (1f - t) * cs * 0.08f
        assertTrue(want > 0f)
        assertEquals(want, v.nearMissOffset(2, 2, 1042L), 1e-3f)
        assertEquals(0f, v.nearMissOffset(3, 3, 1042L), 0f)                       // not a wobbling cell
        assertTrue(v.wobbleMs >= 0)
        assertEquals(0f, v.nearMissOffset(2, 2, 1000L + v.WOBBLE_MS), 0f)         // t = 1 ends it
        assertEquals(-1L, v.wobbleMs)
    }

    @Test fun nearMissOffsetShake() {
        val v = TestViews.gameView()
        val cs = v.cellSize
        v.shakeCell = 1 to 4; v.shakeMs = 500L
        val t = 26f / v.SHAKE_MS
        val want = sin(t * PI.toFloat() * 4f) * (1f - t) * cs * 0.07f
        assertTrue(want > 0f)
        assertEquals(want, v.nearMissOffset(1, 4, 526L), 1e-3f)
        assertEquals(0f, v.nearMissOffset(4, 1, 526L), 0f)
        assertEquals(0f, v.nearMissOffset(1, 0, 526L), 0f)                        // same row only
        assertEquals(0f, v.nearMissOffset(3, 4, 526L), 0f)                        // same column only
        assertEquals(0f, v.nearMissOffset(1, 4, 500L + v.SHAKE_MS), 0f)
        assertEquals(-1L, v.shakeMs)
        assertNull(v.shakeCell)
    }

    @Test fun nearMissOffsetAddsWobbleAndShakeOnTheSameCell() {
        val v = TestViews.gameView()
        v.wobbleCells = listOf(0 to 0); v.wobbleMs = 0L
        val w = v.nearMissOffset(0, 0, 30L)
        v.wobbleMs = -1L
        v.shakeCell = 0 to 0; v.shakeMs = 0L
        val sh = v.nearMissOffset(0, 0, 30L)
        v.wobbleMs = 0L; v.shakeMs = 0L; v.shakeCell = 0 to 0
        assertTrue(w != 0f && sh != 0f)
        assertEquals(w + sh, v.nearMissOffset(0, 0, 30L), 1e-3f)
    }

    @Test fun nearMissOffsetIsZeroWhenIdle() {
        val v = TestViews.gameView()
        assertEquals(0f, v.nearMissOffset(0, 0, 12345L), 0f)
    }

    @Test fun chainOrderRejectsWhenOnlyOnePairIsAdjacent() {
        val v = TestViews.gameView()
        assertTrue(v.chainOrder(listOf(0 to 0, 5 to 5, 0 to 1)).isEmpty())   // only a-c touch
        assertTrue(v.chainOrder(listOf(0 to 0, 5 to 5, 5 to 6)).isEmpty())   // only b-c touch
    }

    @Test fun chainOrderNeedsThreeCells() {
        val v = TestViews.gameView()
        assertTrue(v.chainOrder(emptyList()).isEmpty())
        assertTrue(v.chainOrder(listOf(0 to 0, 0 to 1)).isEmpty())
    }

    @Test fun chainOrderPutsTheMiddleCellBetweenTheEnds() {
        val v = TestViews.gameView()
        val e1 = 0 to 0; val hub = 0 to 1; val e2 = 0 to 2
        assertEquals(listOf(e1, hub, e2), v.chainOrder(listOf(e1, hub, e2)))
        assertEquals(listOf(e1, hub, e2), v.chainOrder(listOf(hub, e1, e2)))
        assertEquals(listOf(e1, hub, e2), v.chainOrder(listOf(e1, e2, hub)))
        assertEquals(listOf(e2, hub, e1), v.chainOrder(listOf(e2, hub, e1)))
        assertEquals(listOf(e2, hub, e1), v.chainOrder(listOf(hub, e2, e1)))
        assertEquals(listOf(e2, hub, e1), v.chainOrder(listOf(e2, e1, hub)))
    }

    @Test fun chainOrderRejectsCellsThatCannotBeDragged() {
        val v = TestViews.gameView()
        assertTrue(v.chainOrder(listOf(0 to 0, 0 to 2, 0 to 4)).isEmpty())
        assertTrue(v.chainOrder(listOf(0 to 0, 2 to 0, 5 to 5)).isEmpty())
    }

    @Test fun chainOrderAcceptsDiagonalsAndOnlyLooksAtTheFirstThree() {
        val v = TestViews.gameView()
        assertEquals(listOf(0 to 0, 1 to 1, 2 to 2), v.chainOrder(listOf(0 to 0, 1 to 1, 2 to 2)))
        assertEquals(listOf(0 to 0, 1 to 1, 2 to 2), v.chainOrder(listOf(0 to 0, 1 to 1, 2 to 2, 5 to 5)))
    }

    @Test fun adjacent8Boundaries() {
        val v = TestViews.gameView()
        assertFalse(v.adjacent8(2, 2, 2, 2))
        assertTrue(v.adjacent8(2, 2, 2, 3)); assertTrue(v.adjacent8(2, 2, 1, 1)); assertTrue(v.adjacent8(2, 2, 3, 1))
        assertFalse(v.adjacent8(2, 2, 2, 4)); assertFalse(v.adjacent8(2, 2, 4, 2)); assertFalse(v.adjacent8(2, 2, 0, 0))
    }

    @Test fun popScaleKeyPoints() {
        val v = TestViews.gameView()
        assertEquals(0f, v.popScale(0f), tol)
        assertEquals(1.25f, v.popScale(0.6f), tol)
        assertEquals(1.125f, v.popScale(0.8f), tol)
        assertEquals(1f, v.popScale(1f), tol)
        // rising branch: easeOutBack(t / 0.6), capped at 1.1, scaled to a 1.25 peak
        val mid = 1.25f * v.easeOutBack(0.5f).coerceAtMost(1.1f) / 1.1f
        assertEquals(mid, v.popScale(0.3f), tol)
        // falling branch is linear from 1.25 to 1
        assertEquals(1.25f - 0.25f * ((0.61f - 0.6f) / 0.4f), v.popScale(0.61f), tol)
        assertTrue(v.popScale(0.59f) > 1.1f)
        assertTrue(v.popScale(0.61f) < 1.25f)
    }
}
