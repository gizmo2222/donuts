package com.donuts.game

import android.graphics.*
import kotlin.math.*

// The toppings: small chunky pieces drawn over a donut's glaze ring, fixed positions so the sprite is the same every time.
// Part of GameView: these are extension functions on it, split out of GameView.kt.

private val TOP_ANG = floatArrayOf(20f, 80f, 140f, 200f, 260f, 320f)          // degrees, clockwise from three o'clock
private val TOP_RAD = floatArrayOf(0.58f, 0.55f, 0.60f, 0.56f, 0.60f, 0.55f)  // share of r: between hole (0.34) and rim
private val TOP_TILT = floatArrayOf(30f, 100f, -20f, 70f, 10f, 120f)
private val ROD_COLORS = intArrayOf(
    Color.rgb(255, 235, 60), Color.rgb(60, 200, 255), Color.rgb(255, 255, 255),
    Color.rgb(120, 230, 90), Color.rgb(255, 150, 30), Color.rgb(190, 110, 255)
)
private val BAND_COLORS = intArrayOf(
    Color.rgb(255, 50, 60), Color.rgb(255, 160, 30), Color.rgb(255, 235, 60),
    Color.rgb(70, 210, 90), Color.rgb(60, 140, 255)
)

/** What the picker and the reveal call a topping. */
fun toppingName(t: Topping): String = when (t) {
    Topping.PLAIN -> "Plain"
    Topping.SPRINKLES -> "Sprinkles"
    Topping.CHOC_CHIPS -> "Choc chips"
    Topping.STARS -> "Stars"
    Topping.HEARTS -> "Hearts"
    Topping.MINI_BALLS -> "Mini balls"
    Topping.RAINBOW -> "Rainbow"
}

/** Draws [t] over the glaze ring of a donut of radius [r] centred on [cx], [cy]. PLAIN draws nothing. */
internal fun GameView.drawTopping(canvas: Canvas, cx: Float, cy: Float, r: Float, t: Topping, alpha: Int) {
    if (t == Topping.PLAIN) return
    if (t == Topping.RAINBOW) { drawRainbowBands(canvas, cx, cy, r, alpha); return }
    val ow = r * 0.055f                       // outline thickness around each piece
    for (i in TOP_ANG.indices) {
        val a = Math.toRadians(TOP_ANG[i].toDouble())
        val px = cx + r * TOP_RAD[i] * cos(a).toFloat()
        val py = cy + r * TOP_RAD[i] * sin(a).toFloat()
        canvas.save(); canvas.rotate(TOP_TILT[i], px, py)
        when (t) {
            Topping.SPRINKLES -> {
                val w = r * 0.20f; val h = r * 0.075f
                fillPaint.color = Color.argb(alpha, 28, 12, 0)
                canvas.drawRoundRect(scratchRectF.apply { set(px - w - ow, py - h - ow, px + w + ow, py + h + ow) }, h + ow, h + ow, fillPaint)
                fillPaint.color = ROD_COLORS[i]; fillPaint.alpha = alpha
                canvas.drawRoundRect(scratchRectF.apply { set(px - w, py - h, px + w, py + h) }, h, h, fillPaint)
            }
            Topping.CHOC_CHIPS -> {
                val w = r * 0.15f; val h = r * 0.11f
                fillPaint.color = Color.argb(alpha, 28, 12, 0)
                canvas.drawOval(scratchRectF.apply { set(px - w - ow, py - h - ow, px + w + ow, py + h + ow) }, fillPaint)
                fillPaint.color = Color.rgb(112, 62, 28); fillPaint.alpha = alpha
                canvas.drawOval(scratchRectF.apply { set(px - w, py - h, px + w, py + h) }, fillPaint)
                fillPaint.color = Color.argb(alpha * 2 / 3, 190, 130, 80)
                canvas.drawCircle(px - w * 0.3f, py - h * 0.35f, h * 0.32f, fillPaint)
            }
            Topping.STARS -> starPiece(canvas, px, py, r * 0.21f, ow, Color.rgb(255, 220, 30), alpha)
            Topping.HEARTS -> heartPiece(canvas, px, py, r * 0.19f, ow, if (i % 2 == 0) Color.rgb(255, 70, 110) else Color.rgb(255, 130, 190), alpha)
            else -> miniBall(canvas, px, py, r * 0.17f, ow, alpha)
        }
        canvas.restore()
    }
    fillPaint.alpha = 255
}

private fun GameView.starPiece(canvas: Canvas, x: Float, y: Float, s: Float, ow: Float, color: Int, alpha: Int) {
    scratchPath2.rewind()
    for (k in 0 until 10) {
        val rad = if (k % 2 == 0) s else s * 0.46f
        val a = (-90.0 + k * 36.0) * PI / 180.0
        val px = x + rad * cos(a).toFloat(); val py = y + rad * sin(a).toFloat()
        if (k == 0) scratchPath2.moveTo(px, py) else scratchPath2.lineTo(px, py)
    }
    scratchPath2.close()
    fillPaint.color = color; fillPaint.alpha = alpha
    canvas.drawPath(scratchPath2, fillPaint)
    outlineOf(canvas, ow, alpha)
}

private fun GameView.heartPiece(canvas: Canvas, x: Float, y: Float, s: Float, ow: Float, color: Int, alpha: Int) {
    scratchPath2.rewind()
    scratchPath2.moveTo(x, y + s * 0.95f)
    scratchPath2.cubicTo(x - s * 1.6f, y - s * 0.1f, x - s * 0.85f, y - s * 1.2f, x, y - s * 0.4f)
    scratchPath2.cubicTo(x + s * 0.85f, y - s * 1.2f, x + s * 1.6f, y - s * 0.1f, x, y + s * 0.95f)
    scratchPath2.close()
    fillPaint.color = color; fillPaint.alpha = alpha
    canvas.drawPath(scratchPath2, fillPaint)
    outlineOf(canvas, ow, alpha)
}

private fun GameView.outlineOf(canvas: Canvas, ow: Float, alpha: Int) {
    strokePaint.color = Color.argb(alpha, 28, 12, 0); strokePaint.strokeWidth = ow * 1.6f
    strokePaint.strokeJoin = Paint.Join.ROUND
    canvas.drawPath(scratchPath2, strokePaint)
    strokePaint.alpha = 255
}

private fun GameView.miniBall(canvas: Canvas, x: Float, y: Float, s: Float, ow: Float, alpha: Int) {
    fillPaint.color = Color.argb(alpha, 28, 12, 0)
    canvas.drawCircle(x, y, s + ow, fillPaint)
    fillPaint.color = Color.WHITE; fillPaint.alpha = alpha
    canvas.drawCircle(x, y, s, fillPaint)
    fillPaint.color = Color.argb(alpha, 28, 12, 0)
    canvas.drawCircle(x, y, s * 0.46f, fillPaint)
    for (k in 0 until 3) {
        val a = (30.0 + k * 120.0) * PI / 180.0
        canvas.drawCircle(x + s * 0.82f * cos(a).toFloat(), y + s * 0.82f * sin(a).toFloat(), s * 0.2f, fillPaint)
    }
}

// Rainbow: five colour bands curving over the top of the ring, and a shorter set low down
private fun GameView.drawRainbowBands(canvas: Canvas, cx: Float, cy: Float, r: Float, alpha: Int) {
    val bw = r * 0.07f
    fun arcs(start: Float, sweep: Float) {
        val inner = r * 0.45f
        strokePaint.strokeCap = Paint.Cap.ROUND
        strokePaint.color = Color.argb(alpha, 28, 12, 0); strokePaint.strokeWidth = bw * 5f + r * 0.04f
        val mid = inner + bw * 2f
        canvas.drawArc(scratchRectF.apply { set(cx - mid, cy - mid, cx + mid, cy + mid) }, start, sweep, false, strokePaint)
        strokePaint.strokeWidth = bw
        for (k in 0 until 5) {
            val rad = inner + bw * k
            strokePaint.color = BAND_COLORS[k]; strokePaint.alpha = alpha
            canvas.drawArc(scratchRectF.apply { set(cx - rad, cy - rad, cx + rad, cy + rad) }, start, sweep, false, strokePaint)
        }
        strokePaint.strokeCap = Paint.Cap.BUTT; strokePaint.alpha = 255
    }
    arcs(190f, 160f)
    arcs(25f, 130f)
}
