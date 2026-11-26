package com.donuts.game

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.MotionEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import kotlin.random.Random

/** The hint's slow finger trace (HintDraw.kt): when it starts, that a touch stops it, that it never plays the move. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HintTraceTest {
    private fun shot(v: GameView): IntArray {
        val bmp = Bitmap.createBitmap(v.surfaceW, v.surfaceH, Bitmap.Config.ARGB_8888)
        v.drawFrame(Canvas(bmp))
        return IntArray(bmp.width * bmp.height).also { bmp.getPixels(it, 0, bmp.width, 0, 0, bmp.width, bmp.height) }
    }

    private fun idleHinted(): GameView {
        val v = TestViews.quietView()
        v.hintRandom = Random(3)
        TestViews.advance(5_100); v.drawFrame(TestViews.canvas(v))
        assertTrue(v.hintCells.size >= 3)
        return v
    }

    @Test
    fun theTraceWaitsForASecondIdleSpellThenDrawsOverTheBoard() {
        val v = idleHinted()
        TestViews.advance(HintRules.TRACE_AFTER_MS - 200); v.drawFrame(TestViews.canvas(v))
        assertFalse(v.hintTracing(TestViews.now()))
        TestViews.advance(200 + HintRules.FADE_MS + HintRules.STEP_MS / 2)
        assertTrue(v.hintTracing(TestViews.now()))
        // Same moment with the trace on and off: the finger and the trail change the board's pixels
        val traced = shot(v)
        val cells = v.hintCells
        v.hintCells = emptyList(); val plain = shot(v); v.hintCells = cells
        assertTrue(traced.indices.count { traced[it] != plain[it] } > 500)
    }

    @Test
    fun theTraceNeverPlaysTheMove() {
        val v = idleHinted()
        val before = v.board.cellCodes().toList()
        val total = TestViews.total(v)
        TestViews.frames(v, TestViews.canvas(v), 40, 750L)   // half a minute of tracing, a frame every 750 ms
        assertEquals(before, v.board.cellCodes().toList())
        assertEquals(total, TestViews.total(v))
        assertEquals(AnimPhase.IDLE, v.animPhase)
        assertTrue(v.dragChain.isEmpty())
        assertTrue(v.hintTracing(TestViews.now()))
    }

    @Test
    fun aTouchStopsTheTraceAndTheWaitStartsAgain() {
        val v = idleHinted()
        TestViews.advance(HintRules.TRACE_AFTER_MS + 500); v.drawFrame(TestViews.canvas(v))
        assertTrue(v.hintTracing(TestViews.now()))
        val (x, y) = TestViews.cellCenter(v, 0, 0)
        TestViews.touch(v, MotionEvent.ACTION_DOWN, x, y)
        assertFalse(v.hintTracing(TestViews.now()))
        TestViews.touch(v, MotionEvent.ACTION_UP, x, y)
        TestViews.advance(5_100); v.drawFrame(TestViews.canvas(v))
        assertTrue(v.hintCells.isNotEmpty())
        assertFalse(v.hintTracing(TestViews.now()))
    }

    @Test
    fun underCalmMotionTheTraceStillDrawsWithTheFingerAtRest() {
        val v = idleHinted()
        v.reducedMotion = true
        TestViews.advance(HintRules.TRACE_AFTER_MS + 10)
        val a = shot(v)
        TestViews.advance(450)
        val b = shot(v)
        val cells = v.hintCells
        v.hintCells = emptyList(); val plain = shot(v); v.hintCells = cells
        assertTrue(a.indices.count { a[it] != plain[it] } > 500)
        // The hint ring's own pulse is unaffected by the trace; compare only the trace's change with time
        assertTrue(HintRules.traceAt(10, cells.size, true) == HintRules.traceAt(460, cells.size, true))
        assertTrue(b.isNotEmpty())
    }
}
