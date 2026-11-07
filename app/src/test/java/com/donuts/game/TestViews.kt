package com.donuts.game

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.SystemClock
import android.view.MotionEvent
import org.robolectric.RuntimeEnvironment
import org.robolectric.shadows.ShadowSystemClock
import java.time.Duration
import kotlin.random.Random

/**
 * Shared set-up for the Robolectric tests (annotate the class with @RunWith(RobolectricTestRunner::class)).
 * Robolectric gives each test a fresh Application, so prefs start empty. Time only moves when [advance] is called.
 */
internal object TestViews {
    fun app(): Application = RuntimeEnvironment.getApplication()

    /** A laid-out GameView with a seeded board. The tutorial is marked seen unless [tutorialSeen] is false. */
    fun gameView(
        w: Int = 1080, h: Int = 2400, grid: Int = 6, seed: Int = 1, tutorialSeen: Boolean = true
    ): GameView {
        val prefs = Prefs(app())
        prefs.gridSize = grid
        prefs.tutorialSeen = tutorialSeen
        val v = GameView(app(), GameBoard(grid, grid, Random(seed)), prefs)
        v.surfaceW = w; v.surfaceH = h
        v.computeLayout()
        return v
    }

    fun canvas(v: GameView): Canvas =
        Canvas(Bitmap.createBitmap(v.surfaceW, v.surfaceH, Bitmap.Config.ARGB_8888))

    fun now(): Long = SystemClock.elapsedRealtime()

    fun advance(ms: Long) { ShadowSystemClock.advanceBy(Duration.ofMillis(ms)) }

    /** Moves the clock [stepMs] and draws a frame, [count] times. */
    fun frames(v: GameView, c: Canvas, count: Int, stepMs: Long = 16L) {
        repeat(count) { advance(stepMs); v.drawFrame(c) }
    }

    /** Screen point at the centre of a board cell. */
    fun cellCenter(v: GameView, row: Int, col: Int): Pair<Float, Float> =
        (v.boardLeft + (col + 0.5f) * v.cellSize) to (v.boardTop + (row + 0.5f) * v.cellSize)

    /** Sends one single-finger MotionEvent (ACTION_DOWN, ACTION_MOVE, ACTION_UP ...) to the view. */
    fun touch(v: GameView, action: Int, x: Float, y: Float): Boolean {
        val t = now()
        val e = MotionEvent.obtain(t, t, action, x, y, 0)
        try { return v.onTouchEvent(e) } finally { e.recycle() }
    }

    // Shared by the GameAnimation tests
    fun quietView(seed: Int = 1, tutorialSeen: Boolean = true): GameView {
        val v = TestViews.gameView(seed = seed, tutorialSeen = tutorialSeen)
        v.prefs.soundEnabled = false
        v.prefs.hapticEnabled = false
        return v
    }

    fun total(v: GameView) = v.board.donutsCleared.values.sum()

    /** Drags the first hint trio (in whatever order is a legal path) and releases it. */
    fun playHintChain(v: GameView) {
        val hint = v.board.findHint()
        org.junit.Assert.assertEquals(3, hint.size)
        val orders = listOf(
            listOf(0, 1, 2), listOf(0, 2, 1), listOf(1, 0, 2),
            listOf(1, 2, 0), listOf(2, 0, 1), listOf(2, 1, 0)
        )
        val path = orders.map { o -> o.map { hint[it] } }.first { v.board.peekChainClear(it) != null }
        v.dragChain.clear(); v.dragChain.addAll(path)
        v.handleUp()
    }
}
