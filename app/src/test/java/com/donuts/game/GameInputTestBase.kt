package com.donuts.game

import android.graphics.RectF
import android.view.MotionEvent
import org.junit.Assert.*

/** Touch helpers shared by [GameInputTest] and [GameInputPanelsTest] (a planted 6x6 board, taps, drags, panel opening). */
abstract class GameInputTestBase {
    protected val DOWN = MotionEvent.ACTION_DOWN
    protected val MOVE = MotionEvent.ACTION_MOVE
    protected val UP = MotionEvent.ACTION_UP

    // S C B V M A. Row 0 starts with three strawberries; (0,3) and (1,0)-(1,2) are other flavours.
    protected val layout = listOf(
        "SSSCBV",
        "CBVMAC",
        "MAMBVC",
        "VCAVMB",
        "BMCABA",
        "AVBMCV"
    )

    protected fun typeOf(ch: Char) = when (ch) {
        'S' -> DonutType.STRAWBERRY; 'C' -> DonutType.CHOCOLATE; 'B' -> DonutType.BLUEBERRY
        'V' -> DonutType.VANILLA; 'M' -> DonutType.MATCHA; else -> DonutType.CARAMEL
    }

    /** A laid-out 6x6 view with a planted, idle board. */
    protected fun planted(rows: List<String> = layout): GameView {
        val v = TestViews.gameView()
        rows.forEachIndexed { r, line ->
            line.forEachIndexed { c, ch -> v.board.grid[r][c] = GameCell(typeOf(ch), r, c) }
        }
        return v
    }

    protected fun at(v: GameView, action: Int, row: Int, col: Int): Boolean {
        val (x, y) = TestViews.cellCenter(v, row, col)
        return TestViews.touch(v, action, x, y)
    }

    protected fun drag(v: GameView, vararg cells: Pair<Int, Int>, release: Boolean = true) {
        at(v, DOWN, cells[0].first, cells[0].second)
        for (i in 1 until cells.size) at(v, MOVE, cells[i].first, cells[i].second)
        if (release) at(v, UP, cells.last().first, cells.last().second)
    }

    protected fun tap(v: GameView, x: Float, y: Float) {
        TestViews.touch(v, DOWN, x, y); TestViews.touch(v, UP, x, y)
    }

    protected fun tap(v: GameView, r: RectF) = tap(v, r.centerX(), r.centerY())

    protected fun runUntilIdle(v: GameView) {
        val c = TestViews.canvas(v)
        var n = 0
        do { TestViews.frames(v, c, 1); n++ } while (v.animPhase != AnimPhase.IDLE && n < 1000)
        assertEquals(AnimPhase.IDLE, v.animPhase)
    }

    /** Steps only the panel slide to its end, then draws one frame (much cheaper than drawing every frame). */
    protected fun slidePanels(v: GameView) {
        repeat(60) { TestViews.advance(16); v.advanceSettingsAnim(TestViews.now()) }
        TestViews.frames(v, TestViews.canvas(v), 1)
    }

    protected fun openSettings(v: GameView) {
        tap(v, v.settingsBtnRect)
        assertTrue(v.settingsOpen)
        slidePanels(v)
        assertEquals(1f, v.settingsAnim, 0f)
    }

    protected fun openStickers(v: GameView) {
        tap(v, v.stickersBtnRect)
        assertTrue(v.stickersOpen)
        slidePanels(v)
        assertEquals(1f, v.stickersAnim, 0f)
    }

    protected fun pointers(action: Int, index: Int, vararg pts: Triple<Int, Float, Float>): MotionEvent {
        val props = Array(pts.size) { MotionEvent.PointerProperties().apply { id = pts[it].first; toolType = MotionEvent.TOOL_TYPE_FINGER } }
        val coords = Array(pts.size) { MotionEvent.PointerCoords().apply { x = pts[it].second; y = pts[it].third } }
        val t = TestViews.now()
        val a = if (action == MotionEvent.ACTION_POINTER_DOWN || action == MotionEvent.ACTION_POINTER_UP)
            action or (index shl MotionEvent.ACTION_POINTER_INDEX_SHIFT) else action
        return MotionEvent.obtain(t, t, a, pts.size, props, coords, 0, 0, 1f, 1f, 0, 0, 0, 0)
    }

    protected fun send(v: GameView, e: MotionEvent) { try { v.onTouchEvent(e) } finally { e.recycle() } }
}
