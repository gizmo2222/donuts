package com.donuts.game

import android.content.Context
import android.graphics.*
import android.os.SystemClock
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import kotlin.math.*

// The twelve sticker pictures.
// Part of GameView: these are extension functions on it, split out of GameView.kt.

// -----------------------------------------------------------------------
// Sticker art: drawn with the pieces' own sprites and outline style, no system emoji.
// -----------------------------------------------------------------------
internal fun GameView.drawStickerArt(canvas: Canvas, i: Int, cx: Float, cy: Float, size: Float) {
    val r = size * 0.30f
    when (i) {
        0  -> drawPiece(canvas, cx, cy, r * 1.15f, DonutType.STRAWBERRY, false)
        1  -> {
            drawPiece(canvas, cx - r * 0.72f, cy + r * 0.22f, r * 0.95f, DonutType.CHOCOLATE, false)
            drawPiece(canvas, cx + r * 0.72f, cy - r * 0.22f, r * 0.95f, DonutType.STRAWBERRY, false)
        }
        2  -> {
            drawPiece(canvas, cx, cy + r * 0.30f, r * 1.05f, DonutType.VANILLA, false)
            drawCrown(canvas, cx, cy - r * 1.05f, r * 1.0f)
        }
        3, 4, 5 -> drawChainArt(canvas, cx, cy, size, (4 + (i - 3) * 2).toString())
        6, 7, 8 -> drawBatchArt(canvas, cx, cy, size, intArrayOf(20, 60, 200)[i - 6].toString())
        9  -> {
            drawBall(canvas, cx, cy, r * 1.05f, false, 255)
            strokePaint.color = Color.argb(220, 255, 210, 30); strokePaint.strokeWidth = r * 0.16f
            scratchRectF.set(cx - r * 1.5f, cy - r * 1.5f, cx + r * 1.5f, cy + r * 1.5f)
            for (k in 0 until 4) canvas.drawArc(scratchRectF, k * 90f + 15f, 60f, false, strokePaint)
            strokePaint.alpha = 255
        }
        10 -> drawMiniBoard(canvas, cx, cy, size)
        else -> drawHeart(canvas, cx, cy + r * 0.1f, r * 1.25f)
    }
}

// A string with a donut at each end and the chain length in the middle
internal fun GameView.drawChainArt(canvas: Canvas, cx: Float, cy: Float, size: Float, label: String) {
    val half = size * 0.62f
    chainOutlinePaint.strokeWidth = size * 0.11f; chainOutlinePaint.color = Color.argb(230, 28, 12, 0)
    canvas.drawLine(cx - half, cy, cx + half, cy, chainOutlinePaint)
    chainLinePaint.strokeWidth = size * 0.05f; chainLinePaint.color = Color.WHITE
    canvas.drawLine(cx - half, cy, cx + half, cy, chainLinePaint)
    drawPiece(canvas, cx - half, cy, size * 0.20f, DonutType.MATCHA, false)
    drawPiece(canvas, cx + half, cy, size * 0.20f, DonutType.MATCHA, false)
    drawOutlinedText(canvas, label, cx, cy, size * 0.62f, Color.WHITE)
}

// A tray with a big number on it
internal fun GameView.drawBatchArt(canvas: Canvas, cx: Float, cy: Float, size: Float, label: String) {
    val w = size * 0.62f; val h = size * 0.42f; val bd = size * 0.045f
    fillPaint.color = Color.argb(255, 28, 12, 0)
    canvas.drawRoundRect(scratchRectF.apply { set(cx - w - bd, cy - h - bd, cx + w + bd, cy + h + bd) }, size * 0.16f, size * 0.16f, fillPaint)
    fillPaint.color = Color.rgb(245, 210, 165)
    canvas.drawRoundRect(scratchRectF.apply { set(cx - w, cy - h, cx + w, cy + h) }, size * 0.13f, size * 0.13f, fillPaint)
    drawPiece(canvas, cx - w + size * 0.05f, cy - h + size * 0.02f, size * 0.15f, DonutType.STRAWBERRY, false)
    drawOutlinedText(canvas, label, cx + size * 0.04f, cy + size * 0.02f, size * 0.50f, Color.WHITE)
}

internal fun GameView.drawCrown(canvas: Canvas, cx: Float, cy: Float, r: Float) {
    val w = r * 0.95f; val h = r * 0.45f
    scratchPath.rewind()
    scratchPath.moveTo(cx - w, cy + h)
    scratchPath.lineTo(cx - w, cy - h * 0.5f)
    scratchPath.lineTo(cx - w * 0.5f, cy + h * 0.05f)
    scratchPath.lineTo(cx, cy - h)
    scratchPath.lineTo(cx + w * 0.5f, cy + h * 0.05f)
    scratchPath.lineTo(cx + w, cy - h * 0.5f)
    scratchPath.lineTo(cx + w, cy + h)
    scratchPath.close()
    outlinePaint.color = Color.argb(255, 28, 12, 0); outlinePaint.strokeWidth = r * 0.22f
    canvas.drawPath(scratchPath, outlinePaint)
    fillPaint.color = Color.rgb(255, 215, 50); fillPaint.alpha = 255
    canvas.drawPath(scratchPath, fillPaint)
    fillPaint.color = Color.rgb(235, 25, 80)
    canvas.drawCircle(cx, cy + h * 0.45f, r * 0.14f, fillPaint)
}

internal fun GameView.drawMiniBoard(canvas: Canvas, cx: Float, cy: Float, size: Float) {
    val half = size * 0.50f; val bd = size * 0.05f
    fillPaint.color = Color.argb(255, 28, 12, 0)
    canvas.drawRoundRect(scratchRectF.apply { set(cx - half - bd, cy - half - bd, cx + half + bd, cy + half + bd) }, size * 0.16f, size * 0.16f, fillPaint)
    fillPaint.color = theme.boardBg
    canvas.drawRoundRect(scratchRectF.apply { set(cx - half, cy - half, cx + half, cy + half) }, size * 0.13f, size * 0.13f, fillPaint)
    val d = half * 0.5f; val pr = size * 0.19f
    drawPiece(canvas, cx - d, cy - d, pr, DonutType.STRAWBERRY, false)
    drawPiece(canvas, cx + d, cy - d, pr, DonutType.BLUEBERRY, false)
    drawPiece(canvas, cx - d, cy + d, pr, DonutType.VANILLA, false)
    drawPiece(canvas, cx + d, cy + d, pr, DonutType.MATCHA, false)
}

internal fun GameView.drawHeart(canvas: Canvas, cx: Float, cy: Float, r: Float) {
    scratchPath.rewind()
    scratchPath.moveTo(cx, cy + r * 0.95f)
    scratchPath.cubicTo(cx - r * 1.6f, cy - r * 0.1f, cx - r * 0.9f, cy - r * 1.15f, cx, cy - r * 0.45f)
    scratchPath.cubicTo(cx + r * 0.9f, cy - r * 1.15f, cx + r * 1.6f, cy - r * 0.1f, cx, cy + r * 0.95f)
    scratchPath.close()
    outlinePaint.color = Color.argb(255, 28, 12, 0); outlinePaint.strokeWidth = r * 0.22f
    canvas.drawPath(scratchPath, outlinePaint)
    fillPaint.color = Color.rgb(235, 25, 80); fillPaint.alpha = 255
    canvas.drawPath(scratchPath, fillPaint)
    fillPaint.color = Color.argb(160, 255, 255, 255)
    canvas.drawCircle(cx - r * 0.45f, cy - r * 0.45f, r * 0.18f, fillPaint)
    fillPaint.alpha = 255
}
