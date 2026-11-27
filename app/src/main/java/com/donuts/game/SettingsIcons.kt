package com.donuts.game

import android.graphics.*
import kotlin.math.*

// The settings pictures: a big icon over each settings word (a speaker, a buzzing phone, a
// bulb, a donut grid, a bouncing or resting donut and a moon), each showing its current choice, and the icons on the two
// tap-twice buttons. Part of GameView: these are extension functions on it.

private val OFF  = Color.rgb(195, 178, 160)   // a switched-off picture: same shape, no colour
private val GOLD = Color.rgb(255, 215, 50)

/** The settings rows' pictures, in [GameView.settingsIconRects] order. */
internal const val ICON_SOUND = 0
internal const val ICON_BUZZ  = 1
internal const val ICON_HINTS = 2
internal const val ICON_GRID  = 3
internal const val ICON_MOTION = 4
internal const val ICON_REST  = 5

/**
 * One settings row's left column: the picture, then its word underneath. [on] is the row's
 * current choice; for the grid it means big donuts (3 across) rather than small ones (4 across).
 */
internal fun GameView.drawSettingsIcon(canvas: Canvas, rect: RectF, which: Int, on: Boolean, word: String) {
    val k  = u * settingsSc
    val r  = 21f * k
    val cx = rect.centerX(); val cy = rect.top + 4f * k + r
    when (which) {
        ICON_SOUND -> drawSpeakerIcon(canvas, cx, cy, r, on)
        ICON_BUZZ  -> drawBuzzIcon(canvas, cx, cy, r, on)
        ICON_HINTS -> drawBulbIcon(canvas, cx, cy, r, on)
        ICON_MOTION -> drawMotionIcon(canvas, cx, cy, r, on)
        ICON_REST  -> drawMoonIcon(canvas, cx, cy, r, on)
        else       -> drawGridIcon(canvas, cx, cy, r, if (on) 3 else 4)
    }
    drawLabel(canvas, word, cx, rect.bottom - 7f * k, 16f * k, rect.width() - 4f * k, theme.textPrimary, outlined = false)
}

/** A chunky two-tone stroke: a dark line with a thinner coloured line on top. */
private fun GameView.inkLine(canvas: Canvas, path: Path, w: Float, color: Int) {
    outlinePaint.color = INK; outlinePaint.strokeWidth = w
    canvas.drawPath(path, outlinePaint)
    outlinePaint.color = color; outlinePaint.strokeWidth = w * 0.45f
    canvas.drawPath(path, outlinePaint)
}

private fun GameView.outlineAndFill(canvas: Canvas, path: Path, w: Float, color: Int) {
    outlinePaint.color = INK; outlinePaint.strokeWidth = w
    canvas.drawPath(path, outlinePaint)
    fillPaint.color = color; fillPaint.alpha = 255
    canvas.drawPath(path, fillPaint)
}

internal fun GameView.drawSpeakerIcon(canvas: Canvas, cx: Float, cy: Float, r: Float, on: Boolean) {
    val sx = cx - r * 0.35f
    scratchPath.rewind()
    scratchPath.moveTo(sx - r * 0.55f, cy - r * 0.3f)
    scratchPath.lineTo(sx - r * 0.15f, cy - r * 0.3f)
    scratchPath.lineTo(sx + r * 0.35f, cy - r * 0.72f)
    scratchPath.lineTo(sx + r * 0.35f, cy + r * 0.72f)
    scratchPath.lineTo(sx - r * 0.15f, cy + r * 0.3f)
    scratchPath.lineTo(sx - r * 0.55f, cy + r * 0.3f)
    scratchPath.close()
    outlineAndFill(canvas, scratchPath, r * 0.28f, if (on) Color.rgb(255, 150, 50) else OFF)
    if (on) {
        // Two sound waves
        for (wr in floatArrayOf(0.5f, 0.92f)) {
            scratchPath.rewind()
            scratchPath.addArc(sx + r * 0.35f - r * wr, cy - r * wr, sx + r * 0.35f + r * wr, cy + r * wr, -42f, 84f)
            inkLine(canvas, scratchPath, r * 0.3f, GOLD)
        }
    } else {
        // A red cross where the waves would be
        val xx = sx + r * 0.9f; val d = r * 0.28f
        scratchPath.rewind()
        scratchPath.moveTo(xx - d, cy - d); scratchPath.lineTo(xx + d, cy + d)
        scratchPath.moveTo(xx + d, cy - d); scratchPath.lineTo(xx - d, cy + d)
        inkLine(canvas, scratchPath, r * 0.32f, Color.rgb(230, 60, 50))
    }
}

internal fun GameView.drawBuzzIcon(canvas: Canvas, cx: Float, cy: Float, r: Float, on: Boolean) {
    scratchPath.rewind()
    scratchPath.addRoundRect(cx - r * 0.45f, cy - r * 0.78f, cx + r * 0.45f, cy + r * 0.78f, r * 0.2f, r * 0.2f, Path.Direction.CW)
    outlineAndFill(canvas, scratchPath, r * 0.26f, if (on) Color.rgb(80, 150, 230) else OFF)
    fillPaint.color = if (on) Color.rgb(215, 238, 255) else Color.rgb(235, 225, 212)
    canvas.drawRoundRect(scratchRectF.apply { set(cx - r * 0.3f, cy - r * 0.6f, cx + r * 0.3f, cy + r * 0.38f) }, r * 0.08f, r * 0.08f, fillPaint)
    fillPaint.color = INK
    canvas.drawCircle(cx, cy + r * 0.58f, r * 0.09f, fillPaint)
    if (!on) return
    // Zigzag buzz lines on both sides
    for (side in intArrayOf(-1, 1)) {
        val x = cx + side * r * 0.82f
        scratchPath.rewind()
        for (i in 0..4) {
            val px = x + (if (i % 2 == 0) -1 else 1) * r * 0.12f
            val py = cy - r * 0.48f + i * r * 0.24f
            if (i == 0) scratchPath.moveTo(px, py) else scratchPath.lineTo(px, py)
        }
        inkLine(canvas, scratchPath, r * 0.24f, GOLD)
    }
}

internal fun GameView.drawBulbIcon(canvas: Canvas, cx: Float, cy: Float, r: Float, on: Boolean) {
    val gy = cy - r * 0.15f; val gr = r * 0.58f
    if (on) {
        // Rays across the top half
        scratchPath.rewind()
        for (i in 0 until 5) {
            val a = Math.toRadians(-180.0 + 22.5 + i * 33.75)
            val c = cos(a).toFloat(); val s = sin(a).toFloat()
            scratchPath.moveTo(cx + c * r * 0.74f, gy + s * r * 0.74f)
            scratchPath.lineTo(cx + c * r * 0.92f, gy + s * r * 0.92f)
        }
        inkLine(canvas, scratchPath, r * 0.22f, GOLD)
    }
    // Screw base under the glass
    scratchPath.rewind()
    scratchPath.addRoundRect(cx - r * 0.28f, gy + gr * 0.6f, cx + r * 0.28f, cy + r * 0.8f, r * 0.1f, r * 0.1f, Path.Direction.CW)
    outlineAndFill(canvas, scratchPath, r * 0.24f, Color.rgb(170, 170, 185))
    // Glass
    scratchPath.rewind()
    scratchPath.addCircle(cx, gy, gr, Path.Direction.CW)
    outlineAndFill(canvas, scratchPath, r * 0.24f, if (on) GOLD else OFF)
    strokePaint.color = INK; strokePaint.strokeWidth = r * 0.08f; strokePaint.alpha = 255
    canvas.drawLine(cx - r * 0.24f, cy + r * 0.62f, cx + r * 0.24f, cy + r * 0.62f, strokePaint)
    if (on) {
        fillPaint.color = Color.argb(170, 255, 255, 255)
        canvas.drawCircle(cx - gr * 0.35f, gy - gr * 0.35f, gr * 0.2f, fillPaint)
        fillPaint.alpha = 255
    }
}

private val GRID_DONUTS = intArrayOf(
    Color.rgb(240, 110, 150), Color.rgb(255, 200, 60), Color.rgb(90, 170, 240), Color.rgb(110, 200, 110)
)

/** A little board of [n] by [n] donuts: big donuts for 6x6, small ones for 8x8. */
internal fun GameView.drawGridIcon(canvas: Canvas, cx: Float, cy: Float, r: Float, n: Int) {
    val h = r * 0.82f
    scratchPath.rewind()
    scratchPath.addRoundRect(cx - h, cy - h, cx + h, cy + h, r * 0.22f, r * 0.22f, Path.Direction.CW)
    outlineAndFill(canvas, scratchPath, r * 0.22f, theme.boardBg)
    val cell = h * 1.84f / n
    val x0 = cx - cell * n / 2f + cell / 2f
    for (i in 0 until n) for (j in 0 until n) {
        val x = x0 + j * cell; val y = x0 - cx + cy + i * cell
        fillPaint.color = INK
        canvas.drawCircle(x, y, cell * 0.42f, fillPaint)
        fillPaint.color = GRID_DONUTS[(i + j) % GRID_DONUTS.size]
        canvas.drawCircle(x, y, cell * 0.34f, fillPaint)
        fillPaint.color = theme.holeColor
        canvas.drawCircle(x, y, cell * 0.12f, fillPaint)
    }
    fillPaint.alpha = 255
}

/** Motion: a donut bouncing up off the ground with lines under it ([on], Bouncy), or resting on it (Calm). */
internal fun GameView.drawMotionIcon(canvas: Canvas, cx: Float, cy: Float, r: Float, on: Boolean) {
    val ground = cy + r * 0.9f
    val dr = r * 0.58f
    val dy = if (on) cy - r * 0.32f else ground - dr - r * 0.08f
    scratchPath.rewind()
    scratchPath.moveTo(cx - r * 0.95f, ground); scratchPath.lineTo(cx + r * 0.95f, ground)
    inkLine(canvas, scratchPath, r * 0.2f, theme.boardBg)
    if (on) for (i in -1..1) {
        scratchPath.rewind()
        val x = cx + i * r * 0.36f
        scratchPath.moveTo(x, dy + dr + r * 0.14f); scratchPath.lineTo(x, ground - r * 0.2f)
        inkLine(canvas, scratchPath, r * 0.16f, GOLD)
    }
    fillPaint.color = INK
    canvas.drawCircle(cx, dy, dr + r * 0.1f, fillPaint)
    fillPaint.color = GRID_DONUTS[0]
    canvas.drawCircle(cx, dy, dr, fillPaint)
    fillPaint.color = theme.holeColor
    canvas.drawCircle(cx, dy, dr * 0.36f, fillPaint)
    fillPaint.alpha = 255
}

/** The rest reminder: a crescent moon with a little star, gold when a rest time is set. */
internal fun GameView.drawMoonIcon(canvas: Canvas, cx: Float, cy: Float, r: Float, on: Boolean) {
    val mx = cx - r * 0.12f
    val moon = Path().apply { addCircle(mx, cy, r * 0.82f, Path.Direction.CW) }
    val bite = Path().apply { addCircle(mx + r * 0.42f, cy - r * 0.30f, r * 0.66f, Path.Direction.CW) }
    moon.op(bite, Path.Op.DIFFERENCE)
    outlineAndFill(canvas, moon, r * 0.2f, if (on) GOLD else OFF)
    val star = Path()
    for (i in 0 until 10) {
        val rr = if (i % 2 == 0) r * 0.32f else r * 0.14f
        val ang = Math.toRadians(-90.0 + i * 36.0)
        val x = cx + r * 0.66f + (rr * cos(ang)).toFloat(); val y = cy - r * 0.50f + (rr * sin(ang)).toFloat()
        if (i == 0) star.moveTo(x, y) else star.lineTo(x, y)
    }
    star.close()
    outlineAndFill(canvas, star, r * 0.12f, if (on) Color.WHITE else OFF)
}

/** A round arrow for New game. */
internal fun GameView.drawRestartIcon(canvas: Canvas, cx: Float, cy: Float, r: Float) {
    val ar = r * 0.62f
    scratchPath.rewind()
    scratchPath.addArc(cx - ar, cy - ar, cx + ar, cy + ar, 0f, 285f)
    inkLine(canvas, scratchPath, r * 0.5f, Color.WHITE)
    // Arrowhead at the end of the arc, pointing along it
    val a = Math.toRadians(285.0)
    val nx = cos(a).toFloat(); val ny = sin(a).toFloat()   // outward
    val tx = -ny; val ty = nx                              // along the arc, clockwise
    val ex = cx + nx * ar; val ey = cy + ny * ar
    scratchPath.rewind()
    scratchPath.moveTo(ex + tx * r * 0.5f, ey + ty * r * 0.5f)
    scratchPath.lineTo(ex + nx * r * 0.42f - tx * r * 0.1f, ey + ny * r * 0.42f - ty * r * 0.1f)
    scratchPath.lineTo(ex - nx * r * 0.42f - tx * r * 0.1f, ey - ny * r * 0.42f - ty * r * 0.1f)
    scratchPath.close()
    outlineAndFill(canvas, scratchPath, r * 0.2f, Color.WHITE)
}

/**
 * The icon on a tap-twice button: a round arrow (New game) or a star (Reset stickers). While
 * armed it pulses, unless reduced motion is on.
 */
internal fun GameView.drawConfirmIcon(canvas: Canvas, now: Long, cx: Float, cy: Float, r: Float, restart: Boolean, armed: Boolean) {
    val pulse = if (armed && !reducedMotion) 1f + 0.2f * abs(sin(now / 140.0)).toFloat() else 1f
    canvas.save(); canvas.scale(pulse, pulse, cx, cy)
    if (restart) drawRestartIcon(canvas, cx, cy, r) else drawStarIcon(canvas, cx, cy, r)
    canvas.restore()
}

/** A gold padlock for the Grown-ups button. */
internal fun GameView.drawLockIcon(canvas: Canvas, cx: Float, cy: Float, r: Float) {
    // Shackle: a thick arch above the body
    scratchPath.rewind()
    val sw = r * 0.5f
    scratchPath.addArc(cx - sw, cy - r * 0.9f, cx + sw, cy + r * 0.1f, 180f, 180f)
    scratchPath.moveTo(cx - sw, cy - r * 0.4f); scratchPath.lineTo(cx - sw, cy)
    scratchPath.moveTo(cx + sw, cy - r * 0.4f); scratchPath.lineTo(cx + sw, cy)
    inkLine(canvas, scratchPath, r * 0.34f, Color.rgb(200, 200, 215))
    // Body
    scratchPath.rewind()
    scratchPath.addRoundRect(cx - r * 0.8f, cy - r * 0.1f, cx + r * 0.8f, cy + r * 0.9f, r * 0.2f, r * 0.2f, Path.Direction.CW)
    outlineAndFill(canvas, scratchPath, r * 0.24f, GOLD)
    // Keyhole
    fillPaint.color = INK
    canvas.drawCircle(cx, cy + r * 0.32f, r * 0.14f, fillPaint)
    canvas.drawRect(cx - r * 0.06f, cy + r * 0.32f, cx + r * 0.06f, cy + r * 0.62f, fillPaint)
}
