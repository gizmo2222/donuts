package com.donuts.game

import android.graphics.Canvas
import android.graphics.Color
import kotlin.math.PI
import kotlin.math.sin

// The bubble donut (B37, BubbleRules): a shiny soap bubble around a donut that pops when Steven taps it.
// Part of GameView: extension functions on it, drawn over the donut by the cell, drop and pop loops in BoardDraw.

/** How long a new bubble takes to spring in once the drop has landed. */
internal const val BUBBLE_IN_MS = 320L

/**
 * The bubble around the donut at [cx],[cy] of radius [r] (cell [row],[col]): a sky-blue skin with a thick dark
 * rim and a bright shine. It breathes gently under full motion and stays still under reduced motion. The newest
 * bubble ([GameView.bubbleBornAt]) waits for the drop and then springs in with an overshoot.
 */
internal fun GameView.drawBubble(canvas: Canvas, cx: Float, cy: Float, r: Float, now: Long,
                                 row: Int, col: Int, alpha: Int = 255) {
    var scale = 1f
    if (bubbleBornAt == Pair(row, col)) {
        val t = (now - bubbleBornMs).toFloat() / BUBBLE_IN_MS
        if (t < 0f) return
        if (t >= 1f) bubbleBornAt = null
        else if (!reducedMotion) scale = easeOutBack(t).coerceAtLeast(0f)
    }
    if (!reducedMotion) scale *= 1f + 0.045f * sin((now % 1500L) / 1500f * 2f * PI.toFloat() + col * 0.9f + row * 0.6f)
    val br = r * 1.13f * scale
    val a = alpha / 255f
    val sky = GameTheme.bubble
    fillPaint.color = Color.argb((70 * a).toInt(), Color.red(sky), Color.green(sky), Color.blue(sky))
    canvas.drawCircle(cx, cy, br, fillPaint)
    strokePaint.strokeWidth = r * 0.16f
    strokePaint.color = ink((200 * a).toInt())
    canvas.drawCircle(cx, cy, br, strokePaint)
    strokePaint.strokeWidth = r * 0.09f
    strokePaint.color = Color.argb((235 * a).toInt(), Color.red(sky), Color.green(sky), Color.blue(sky))
    canvas.drawCircle(cx, cy, br - r * 0.11f, strokePaint)
    // The shine: a white arc high on the left and a dot beside it
    strokePaint.color = Color.argb((235 * a).toInt(), 255, 255, 255)
    strokePaint.strokeWidth = r * 0.13f
    strokePaint.strokeCap = android.graphics.Paint.Cap.ROUND
    scratchRectF.set(cx - br * 0.72f, cy - br * 0.72f, cx + br * 0.72f, cy + br * 0.72f)
    canvas.drawArc(scratchRectF, 195f, 55f, false, strokePaint)
    strokePaint.strokeCap = android.graphics.Paint.Cap.BUTT
    fillPaint.color = Color.argb((235 * a).toInt(), 255, 255, 255)
    canvas.drawCircle(cx - br * 0.18f, cy - br * 0.66f, r * 0.08f, fillPaint)
    strokePaint.alpha = 255; fillPaint.alpha = 255
}
