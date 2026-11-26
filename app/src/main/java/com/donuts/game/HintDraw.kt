package com.donuts.game

import android.graphics.Canvas
import android.graphics.Color

// The hint's gentle assist (B30): once the hint has rung alone for HintRules.TRACE_AFTER_MS, a slow
// finger traces its path, drawing the white trail behind it. It only draws: the move is Steven's.
// Part of GameView: these are extension functions on it, split out of GameView.kt.

/** True while the slow finger trace should show over the ringed hint. */
internal fun GameView.hintTracing(now: Long): Boolean =
    hintCells.size >= 3 && animPhase == AnimPhase.IDLE && dragChain.isEmpty() && !tutorialActive &&
        now - hintPulseMs >= HintRules.TRACE_AFTER_MS

internal fun GameView.drawHintTrace(canvas: Canvas, now: Long) {
    if (!hintTracing(now)) return
    val cells = hintCells
    val pose = HintRules.traceAt(now - hintPulseMs - HintRules.TRACE_AFTER_MS, cells.size, reducedMotion)
    if (pose.alpha <= 0f) return
    fun x(i: Int) = boardLeft + cells[i].second * cellSize + cellSize / 2f
    fun y(i: Int) = boardTop + cells[i].first * cellSize + cellSize / 2f

    val i0 = pose.seg.toInt().coerceAtMost(cells.size - 2)
    val frac = (pose.seg - i0).coerceIn(0f, 1f)
    val fx = x(i0) + (x(i0 + 1) - x(i0)) * frac
    val fy = y(i0) + (y(i0 + 1) - y(i0)) * frac

    // The trail so far: a white line on a dark rope, softer than a real chain
    if (pose.seg > 0f) {
        scratchPath.rewind()
        scratchPath.moveTo(x(0), y(0))
        for (i in 1..i0) scratchPath.lineTo(x(i), y(i))
        scratchPath.lineTo(fx, fy)
        val a = (pose.alpha * 200).toInt()
        chainOutlinePaint.strokeWidth = cellSize * 0.26f; chainOutlinePaint.color = ink(a * 3 / 4)
        canvas.drawPath(scratchPath, chainOutlinePaint)
        chainLinePaint.strokeWidth = cellSize * 0.13f; chainLinePaint.color = Color.argb(a, 255, 255, 255)
        canvas.drawPath(scratchPath, chainLinePaint)
    }

    // Under reduced motion the finger rests on the first donut; otherwise it rides the trail's end
    if (pose.still) drawFinger(canvas, x(0), y(0), 255)
    else drawFinger(canvas, fx, fy, (pose.alpha * 255).toInt())
}
