package com.donuts.game

import android.graphics.RectF
import androidx.core.view.ViewCompat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.math.abs

@RunWith(RobolectricTestRunner::class)
class GameLayoutTest {
    private val eps = 0.01f

    private val surfaces = listOf(
        "phone" to (1080 to 2400),
        "small phone" to (720 to 1280),
        "tablet" to (1600 to 2560),
        "landscape" to (2400 to 1080)
    )
    private val grids = listOf(6, 8)

    private fun inside(outer: RectF, r: RectF, tol: Float = eps) =
        r.left >= outer.left - tol && r.top >= outer.top - tol &&
            r.right <= outer.right + tol && r.bottom <= outer.bottom + tol

    private fun surfaceRect(v: GameView) = RectF(0f, 0f, v.surfaceW.toFloat(), v.surfaceH.toFloat())
    private fun boardRect(v: GameView): RectF {
        val side = v.cellSize * v.board.cols
        return RectF(v.boardLeft, v.boardTop, v.boardLeft + side, v.boardTop + side)
    }
    private fun overlap(a: RectF, b: RectF) =
        a.left < b.right - eps && b.left < a.right - eps && a.top < b.bottom - eps && b.top < a.bottom - eps

    private fun forAll(block: (String, GameView) -> Unit) {
        for ((name, wh) in surfaces) for (g in grids) {
            block("$name ${wh.first}x${wh.second} grid $g", TestViews.gameView(wh.first, wh.second, g))
        }
    }

    private fun assertNoOverlap(label: String, rects: List<Pair<String, RectF>>) {
        for (i in rects.indices) for (j in i + 1 until rects.size) {
            assertTrue("$label: ${rects[i].first} overlaps ${rects[j].first}", !overlap(rects[i].second, rects[j].second))
        }
    }

    @Test fun boardIsSquareCelledAndInsideTheSurface() = forAll { n, v ->
        assertTrue("$n: cellSize", v.cellSize > 0f)
        assertEquals("$n: board is square", v.board.rows, v.board.cols)
        assertTrue("$n: board inside surface", inside(surfaceRect(v), boardRect(v)))
    }

    @Test fun boardIsCentredHorizontally() = forAll { n, v ->
        val b = boardRect(v)
        assertEquals("$n: centred", v.surfaceW / 2f, (b.left + b.right) / 2f, 1f)
    }

    @Test fun boardDoesNotOverlapHudButtonsOrCounter() = forAll { n, v ->
        val b = boardRect(v)
        assertTrue("$n: stickers button clear", !overlap(b, v.stickersBtnRect))
        assertTrue("$n: gear button clear", !overlap(b, v.settingsBtnRect))
        assertTrue("$n: stickers btn above board", v.stickersBtnRect.bottom <= b.top + eps)
        // the counter sits between the board and the box strip
        assertTrue("$n: counter below board", v.boxStrip.rect.top >= b.bottom + v.counterH - eps)
        assertTrue("$n: counter has height", v.counterH > 0f)
        assertTrue("$n: strip inside surface", inside(surfaceRect(v), v.boxStrip.rect))
        assertTrue("$n: strip under board", !overlap(b, v.boxStrip.rect))
    }

    @Test fun hudButtonsAreInsideAndBigEnough() = forAll { n, v ->
        val minPx = 48f * v.uiScale.density
        for ((name, r) in listOf("stickers" to v.stickersBtnRect, "settings" to v.settingsBtnRect)) {
            assertTrue("$n: $name inside", inside(surfaceRect(v), r))
            assertTrue("$n: $name width ${r.width()}", r.width() >= minPx)
            assertTrue("$n: $name height ${r.height()}", r.height() >= minPx)
        }
        assertTrue("$n: buttons apart", !overlap(v.stickersBtnRect, v.settingsBtnRect))
        assertTrue("$n: star left of gear", v.stickersBtnRect.right <= v.settingsBtnRect.left)
    }

    @Test fun settingsPanelButtonsFitAndDoNotOverlap() = forAll { n, v ->
        assertTrue("$n: panel inside", inside(surfaceRect(v), v.panelRect))
        val all = listOf(
            "sound0" to v.soundRects[0], "sound1" to v.soundRects[1],
            "buzz0" to v.buzzRects[0], "buzz1" to v.buzzRects[1],
            "hint0" to v.hintRects[0], "hint1" to v.hintRects[1],
            "grid0" to v.gridRects[0], "grid1" to v.gridRects[1],
            "reset" to v.settingsResetRect, "stickers" to v.settingsStickersRect,
            "close" to v.settingsCloseRect
        )
        for ((name, r) in all) {
            assertTrue("$n: $name inside panel", inside(v.panelRect, r))
            assertTrue("$n: $name has size", r.width() > 0f && r.height() > 0f)
        }
        assertNoOverlap(n, all)
    }

    @Test fun stickersPanelTilesAndButtonsFitAndDoNotOverlap() = forAll { n, v ->
        assertTrue("$n: panel inside", inside(surfaceRect(v), v.stickerPanelRect))
        val all = v.stickerRects.mapIndexed { i, r -> "tile$i" to r } +
            listOf("box" to v.donutBoxRect, "close" to v.stickersCloseRect)
        for ((name, r) in all) assertTrue("$n: $name inside panel", inside(v.stickerPanelRect, r))
        assertNoOverlap(n, all)
        val side = v.stickerRects[0].width()
        for (r in v.stickerRects) {
            assertEquals("$n: tiles square", r.width(), r.height(), eps)
            assertEquals("$n: tiles equal", side, r.width(), eps)
        }
    }

    @Test fun panelsAreCentredHorizontally() = forAll { n, v ->
        assertEquals("$n: settings", v.surfaceW / 2f, v.panelRect.centerX(), 1f)
        assertEquals("$n: stickers", v.surfaceW / 2f, v.stickerPanelRect.centerX(), 1f)
    }

    @Test fun buttonRowsAndStripHaveTheDesignedSizesAndGaps() = forAll { n, v ->
        val b = boardRect(v)
        val s = v.boxStrip.rect
        assertEquals("$n: strip left", b.left, s.left, eps)
        assertEquals("$n: strip right", b.right, s.right, eps)
        assertEquals("$n: strip top", b.bottom + v.counterH, s.top, eps)
        assertTrue("$n: strip height ${s.height()}", s.height() >= 64f * v.u - eps)

        val k = v.u * v.settingsSc
        val pad = 16f * k
        val p = v.panelRect
        val reset = v.settingsResetRect; val stk = v.settingsStickersRect; val close = v.settingsCloseRect
        assertEquals("$n: reset left", p.left + pad, reset.left, eps)
        assertEquals("$n: reset height", 56f * k, reset.height(), eps)
        assertEquals("$n: reset width = stickers width", reset.width(), stk.width(), eps)
        assertEquals("$n: gap between", pad, stk.left - reset.right, eps)
        assertEquals("$n: stickers right", p.right - pad, stk.right, eps)
        assertEquals("$n: close height", 56f * k, close.height(), eps)
        assertEquals("$n: close left", p.left + pad, close.left, eps)
        assertEquals("$n: close right", p.right - pad, close.right, eps)
        assertEquals("$n: close bottom", p.bottom - 24f * k - pad, close.bottom, eps)

        val sp = v.stickerPanelRect; val sc = v.stickersCloseRect
        assertEquals("$n: sticker close height", 56f * v.u, sc.height(), eps)
        assertEquals("$n: sticker close bottom", sp.bottom - 10f * v.u, sc.bottom, eps)
        assertEquals("$n: sticker close left", sp.left + 10f * v.u, sc.left, eps)
        assertEquals("$n: sticker close right", sp.right - 10f * v.u, sc.right, eps)
    }

    @Test fun panelsAreCentredVertically() = forAll { n, v ->
        assertEquals("$n: settings", v.surfaceH / 2f, v.panelRect.centerY(), 1f)
        assertEquals("$n: stickers", v.surfaceH / 2f, v.stickerPanelRect.centerY(), 1f)
    }
    @Test @Config(qualifiers = "xxhdpi")
    fun realDensityPhoneStaysInsideAndKeepsTouchTargets() {
        for (g in grids) {
            val v = TestViews.gameView(1080, 2400, g)
            assertEquals(3f, v.uiScale.density, 0.01f)
            assertTrue(inside(surfaceRect(v), boardRect(v)))
            assertTrue(v.stickersBtnRect.width() >= 48f * 3f)
            assertTrue(inside(surfaceRect(v), v.panelRect))
            assertTrue(inside(surfaceRect(v), v.stickerPanelRect))
            assertEquals(1f, v.settingsSc, 0f)
        }
    }

    @Test @Config(qualifiers = "xxhdpi")
    fun shortLandscapeShrinksTheSettingsPanelToFit() {
        val v = TestViews.gameView(2400, 1080, 6)
        assertTrue("scaled down: ${v.settingsSc}", v.settingsSc < 1f)
        assertTrue(inside(surfaceRect(v), v.panelRect))
        assertTrue(inside(v.panelRect, v.settingsCloseRect))
        assertTrue(inside(v.panelRect, v.gridRects[1]))
        assertTrue(inside(surfaceRect(v), v.stickerPanelRect))
        assertTrue(inside(surfaceRect(v), boardRect(v)))
    }

    @Test fun insetsKeepEverythingInTheSafeArea() {
        val v = TestViews.gameView(1080, 2400, 6)
        v.insetL = 60; v.insetR = 40; v.insetT = 120; v.insetB = 90
        v.computeLayout()
        val safe = RectF(60f, 120f, 1080f - 40f, 2400f - 90f)
        assertTrue(inside(safe, boardRect(v)))
        assertTrue(inside(safe, v.stickersBtnRect))
        assertTrue(inside(safe, v.settingsBtnRect))
        assertTrue(inside(safe, v.boxStrip.rect))
        assertTrue(inside(safe, v.panelRect))
        assertTrue(inside(safe, v.stickerPanelRect))
        assertEquals((safe.left + safe.right) / 2f, boardRect(v).centerX(), 1f)
    }

    @Test fun zeroSizeLeavesTheLayoutUntouched() {
        for ((w, h) in listOf(0 to 2400, 1080 to 0, 0 to 0)) {
            val v = TestViews.gameView()
            val before = snapshot(v)
            val uBefore = v.u
            v.surfaceW = w; v.surfaceH = h
            v.computeLayout()
            assertEquals("$w x $h", before, snapshot(v))
            assertEquals(uBefore, v.u, 0f)
        }
    }

    @Test fun computeLayoutIsIdempotent() = forAll { n, v ->
        val first = snapshot(v)
        v.computeLayout()
        assertEquals(n, first, snapshot(v))
        v.computeLayout()
        assertEquals(n, first, snapshot(v))
    }

    @Test fun biggerSurfaceGivesBiggerBoard() {
        val small = TestViews.gameView(720, 1280)
        val big = TestViews.gameView(1600, 2560)
        assertTrue(big.cellSize > small.cellSize)
        assertTrue(big.u >= small.u)   // at mdpi both are already at the 1.5x cap
    }

    @Test fun eightByEightCellsAreSmallerThanSixBySix() {
        val six = TestViews.gameView(1080, 2400, 6)
        val eight = TestViews.gameView(1080, 2400, 8)
        assertEquals(boardRect(six).width(), boardRect(eight).width(), 0.5f)
        assertTrue(eight.cellSize < six.cellSize)
    }

    @Test @Config(sdk = [29])
    fun backSwipeIsExcludedBesideTheBoardOnApi29() = checkExclusion(expected = true)

    @Test @Config(sdk = [34])
    fun backSwipeIsExcludedBesideTheBoardOnApi34() = checkExclusion(expected = true)

    @Test @Config(sdk = [28])
    fun backSwipeIsNotExcludedBelowApi29() = checkExclusion(expected = false)

    private fun checkExclusion(expected: Boolean) {
        val v = TestViews.gameView(1080, 2400, 6)
        val rects = ViewCompat.getSystemGestureExclusionRects(v)
        if (!expected) { assertTrue(rects.isEmpty()); return }
        assertEquals(2, rects.size)
        val strip = (24f * v.u).toInt()
        val left = rects.first { it.left == 0 }
        val right = rects.first { it.right == v.surfaceW }
        assertEquals(strip, left.right)
        assertEquals(v.surfaceW - strip, right.left)
        for (r in rects) {
            assertTrue(r.top >= v.boardTop.toInt() - 1)
            assertTrue(r.bottom <= (v.boardTop + v.cellSize * v.board.cols).toInt() + 1)
            assertTrue(r.height() > 0)
        }
        assertEquals(left.top, right.top)
        assertEquals(left.bottom, right.bottom)
        assertTrue("tall strip capped at 200dp", left.height() <= (200f * v.u).toInt() + 1)
        // vertically centred on the board
        val mid = (left.top + left.bottom) / 2f
        assertTrue(abs(mid - (v.boardTop + v.cellSize * v.board.cols / 2f)) < 2f)
    }

    private fun snapshot(v: GameView): List<Any> {
        val rects = mutableListOf<RectF>()
        rects += listOf(v.stickersBtnRect, v.settingsBtnRect, v.panelRect, v.stickerPanelRect,
            v.settingsCloseRect, v.settingsResetRect, v.settingsStickersRect, v.stickersCloseRect,
            v.donutBoxRect, v.boxStrip.rect)
        rects += v.soundRects + v.buzzRects + v.hintRects + v.gridRects + v.stickerRects
        return listOf(v.cellSize, v.boardLeft, v.boardTop, v.counterH, v.u, v.settingsSc) +
            rects.map { it.toShortString() }
    }
}
