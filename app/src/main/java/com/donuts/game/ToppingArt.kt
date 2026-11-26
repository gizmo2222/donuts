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
                fillPaint.color = ink(alpha)
                canvas.drawRoundRect(scratchRectF.apply { set(px - w - ow, py - h - ow, px + w + ow, py + h + ow) }, h + ow, h + ow, fillPaint)
                fillPaint.color = ROD_COLORS[i]; fillPaint.alpha = alpha
                canvas.drawRoundRect(scratchRectF.apply { set(px - w, py - h, px + w, py + h) }, h, h, fillPaint)
            }
            Topping.CHOC_CHIPS -> {
                val w = r * 0.15f; val h = r * 0.11f
                fillPaint.color = ink(alpha)
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
    strokePaint.color = ink(alpha); strokePaint.strokeWidth = ow * 1.6f
    strokePaint.strokeJoin = Paint.Join.ROUND
    canvas.drawPath(scratchPath2, strokePaint)
    strokePaint.alpha = 255
}

private fun GameView.miniBall(canvas: Canvas, x: Float, y: Float, s: Float, ow: Float, alpha: Int) {
    fillPaint.color = ink(alpha)
    canvas.drawCircle(x, y, s + ow, fillPaint)
    fillPaint.color = Color.WHITE; fillPaint.alpha = alpha
    canvas.drawCircle(x, y, s, fillPaint)
    fillPaint.color = ink(alpha)
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
        strokePaint.color = ink(alpha); strokePaint.strokeWidth = bw * 5f + r * 0.04f
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

// ---- Seasonal looks: drawn at the topping spots instead of the topping while one is worn ----

private val FLOWER_COLORS = intArrayOf(Color.rgb(255, 120, 185), Color.rgb(255, 225, 60), Color.rgb(185, 130, 255))
private val LEAF_COLORS = intArrayOf(Color.rgb(235, 70, 40), Color.rgb(255, 150, 30), Color.rgb(240, 200, 40))

/** What the picker calls a seasonal look. */
fun seasonName(s: Season): String = when (s) {
    Season.SNOW -> "Snowflakes"
    Season.FLOWERS -> "Flowers"
    Season.SUNSHINE -> "Suns"
    Season.LEAVES -> "Leaves"
}

/** The shout over a season's reveal. */
fun seasonCheer(s: Season): String = when (s) {
    Season.SNOW -> "SNOWY DONUTS!"
    Season.FLOWERS -> "FLOWER DONUTS!"
    Season.SUNSHINE -> "SUNNY DONUTS!"
    Season.LEAVES -> "LEAFY DONUTS!"
}

/** Draws seasonal look [s] over the glaze ring of a donut of radius [r] centred on [cx], [cy]. */
internal fun GameView.drawSeasonLook(canvas: Canvas, cx: Float, cy: Float, r: Float, s: Season, alpha: Int) {
    val ow = r * 0.055f
    for (i in TOP_ANG.indices) {
        val a = Math.toRadians(TOP_ANG[i].toDouble())
        val px = cx + r * TOP_RAD[i] * cos(a).toFloat()
        val py = cy + r * TOP_RAD[i] * sin(a).toFloat()
        canvas.save(); canvas.rotate(TOP_TILT[i], px, py)
        when (s) {
            Season.SNOW -> snowflake(canvas, px, py, r * 0.19f, ow, if (i % 2 == 0) Color.WHITE else Color.rgb(190, 235, 255), alpha)
            Season.FLOWERS -> flower(canvas, px, py, r * 0.10f, ow, FLOWER_COLORS[i % FLOWER_COLORS.size], alpha)
            Season.SUNSHINE -> sun(canvas, px, py, r * 0.10f, ow, alpha)
            Season.LEAVES -> leaf(canvas, px, py, r * 0.20f, ow, LEAF_COLORS[i % LEAF_COLORS.size], alpha)
        }
        canvas.restore()
    }
    fillPaint.alpha = 255; strokePaint.alpha = 255
    strokePaint.strokeCap = Paint.Cap.BUTT
}

private fun GameView.snowflake(canvas: Canvas, x: Float, y: Float, s: Float, ow: Float, color: Int, alpha: Int) {
    strokePaint.strokeCap = Paint.Cap.ROUND
    for (pass in 0..1) {
        strokePaint.color = if (pass == 0) ink(alpha) else color
        if (pass == 1) strokePaint.alpha = alpha
        strokePaint.strokeWidth = if (pass == 0) s * 0.26f + ow * 1.6f else s * 0.26f
        for (k in 0 until 3) {
            val a = (k * 60.0) * PI / 180.0
            val dx = s * cos(a).toFloat(); val dy = s * sin(a).toFloat()
            canvas.drawLine(x - dx, y - dy, x + dx, y + dy, strokePaint)
        }
    }
    fillPaint.color = ink(alpha)
    canvas.drawCircle(x, y, s * 0.12f, fillPaint)
}

private fun GameView.flower(canvas: Canvas, x: Float, y: Float, s: Float, ow: Float, color: Int, alpha: Int) {
    fillPaint.color = ink(alpha)
    for (k in 0 until 5) {
        val a = (-90.0 + k * 72.0) * PI / 180.0
        canvas.drawCircle(x + s * cos(a).toFloat(), y + s * sin(a).toFloat(), s * 0.78f + ow, fillPaint)
    }
    fillPaint.color = color; fillPaint.alpha = alpha
    for (k in 0 until 5) {
        val a = (-90.0 + k * 72.0) * PI / 180.0
        canvas.drawCircle(x + s * cos(a).toFloat(), y + s * sin(a).toFloat(), s * 0.78f, fillPaint)
    }
    fillPaint.color = ink(alpha)
    canvas.drawCircle(x, y, s * 0.55f + ow, fillPaint)
    fillPaint.color = Color.rgb(255, 160, 30); fillPaint.alpha = alpha
    canvas.drawCircle(x, y, s * 0.55f, fillPaint)
}

private fun GameView.sun(canvas: Canvas, x: Float, y: Float, s: Float, ow: Float, alpha: Int) {
    strokePaint.strokeCap = Paint.Cap.ROUND
    for (pass in 0..1) {
        strokePaint.color = if (pass == 0) ink(alpha) else Color.rgb(255, 150, 20)
        if (pass == 1) strokePaint.alpha = alpha
        strokePaint.strokeWidth = if (pass == 0) s * 0.32f + ow * 1.6f else s * 0.32f
        for (k in 0 until 8) {
            val a = (k * 45.0) * PI / 180.0
            val c = cos(a).toFloat(); val sn = sin(a).toFloat()
            canvas.drawLine(x + c * s * 1.25f, y + sn * s * 1.25f, x + c * s * 1.75f, y + sn * s * 1.75f, strokePaint)
        }
    }
    fillPaint.color = ink(alpha)
    canvas.drawCircle(x, y, s + ow, fillPaint)
    fillPaint.color = Color.rgb(255, 225, 40); fillPaint.alpha = alpha
    canvas.drawCircle(x, y, s, fillPaint)
    fillPaint.color = Color.argb(alpha * 2 / 3, 255, 250, 200)
    canvas.drawCircle(x - s * 0.35f, y - s * 0.35f, s * 0.3f, fillPaint)
}

private fun GameView.leaf(canvas: Canvas, x: Float, y: Float, s: Float, ow: Float, color: Int, alpha: Int) {
    scratchPath2.rewind()
    scratchPath2.moveTo(x - s, y)
    scratchPath2.quadTo(x, y - s * 0.85f, x + s, y)
    scratchPath2.quadTo(x, y + s * 0.85f, x - s, y)
    scratchPath2.close()
    fillPaint.color = color; fillPaint.alpha = alpha
    canvas.drawPath(scratchPath2, fillPaint)
    outlineOf(canvas, ow, alpha)
    strokePaint.color = Color.argb(alpha * 3 / 4, 120, 40, 10); strokePaint.strokeWidth = ow
    canvas.drawLine(x - s * 0.75f, y, x + s * 0.6f, y, strokePaint)
    strokePaint.alpha = 255
}
