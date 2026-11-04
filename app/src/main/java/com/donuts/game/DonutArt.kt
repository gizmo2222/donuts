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

// The six donuts and the ball: vector art and the sprite cache.
// Part of GameView: these are extension functions on it, split out of GameView.kt.

internal fun GameView.buildSprites() {
    val r = cellSize * 0.43f
    if (r <= 0f) return
    spriteR    = r
    spriteHalf = ceil(r * 1.55f).toInt()              // outline, drips and the selection halo all fit
    val side   = spriteHalf * 2
    val c      = Canvas()
    val mid    = spriteHalf.toFloat()
    for (t in DonutType.values()) for (sel in 0..1) {
        pieceSprites[t.ordinal * 2 + sel]?.recycle()
        val bmp = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888)
        c.setBitmap(bmp)
        drawPieceVector(c, mid, mid, r, t, sel == 1, 255)
        pieceSprites[t.ordinal * 2 + sel] = bmp
    }
    for (sel in 0..1) {
        ballSprites[sel]?.recycle()
        val bmp = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888)
        c.setBitmap(bmp)
        drawBallVector(c, mid, mid, r, sel == 1, 255)
        ballSprites[sel] = bmp
    }
    c.setBitmap(null)
}

internal fun GameView.blit(canvas: Canvas, bmp: Bitmap, cx: Float, cy: Float, radius: Float, alpha: Int) {
    val sc = radius / spriteR
    bitmapPaint.alpha = alpha
    if (abs(sc - 1f) < 0.002f) {
        // Fast path: unscaled blit
        canvas.drawBitmap(bmp, cx - spriteHalf, cy - spriteHalf, bitmapPaint)
        return
    }
    canvas.save()
    canvas.scale(sc, sc, cx, cy)
    canvas.drawBitmap(bmp, cx - spriteHalf, cy - spriteHalf, bitmapPaint)
    canvas.restore()
}

internal fun GameView.drawPiece(
    canvas: Canvas, cx: Float, cy: Float,
    radius: Float, type: DonutType, selected: Boolean, alpha: Int = 255
) {
    val bmp = pieceSprites[type.ordinal * 2 + (if (selected) 1 else 0)]
    if (bmp == null || spriteR <= 0f) drawPieceVector(canvas, cx, cy, radius, type, selected, alpha)
    else blit(canvas, bmp, cx, cy, radius, alpha)
}

internal fun GameView.drawBall(canvas: Canvas, cx: Float, cy: Float, r: Float, selected: Boolean, alpha: Int) {
    val bmp = ballSprites[if (selected) 1 else 0]
    if (bmp == null || spriteR <= 0f) drawBallVector(canvas, cx, cy, r, selected, alpha)
    else blit(canvas, bmp, cx, cy, r, alpha)
}

internal fun GameView.drawPieceVector(
    canvas: Canvas, cx: Float, cy: Float,
    radius: Float, type: DonutType, selected: Boolean, alpha: Int
) {
    when (type) {
        DonutType.STRAWBERRY -> drawDrippyRing(canvas, cx, cy, radius, type, selected, alpha)
        DonutType.CHOCOLATE  -> drawStripedRing(canvas, cx, cy, radius, type, selected, alpha)
        DonutType.BLUEBERRY  -> drawJellyBun(canvas, cx, cy, radius, type, selected, alpha)
        DonutType.VANILLA    -> drawFlowerRing(canvas, cx, cy, radius, type, selected, alpha)
        DonutType.MATCHA     -> drawHalfDipRing(canvas, cx, cy, radius, type, selected, alpha)
        DonutType.CARAMEL    -> drawSquareDonut(canvas, cx, cy, radius, type, selected, alpha)
    }
}

// Shared round base: selection halo, dark outline, drop shadow, dough body.
internal fun GameView.ringBase(canvas: Canvas, cx: Float, cy: Float, r: Float, body: Int, selected: Boolean, alpha: Int) {
    val ow = r * 0.18f
    if (selected) {
        fillPaint.color = Color.argb(alpha, 255, 255, 255)
        canvas.drawCircle(cx, cy, r + ow + cellSize * 0.07f, fillPaint)
    }
    fillPaint.color = Color.argb(alpha, 28, 12, 0)
    canvas.drawCircle(cx, cy, r + ow, fillPaint)
    fillPaint.color = Color.argb(alpha / 3, 0, 0, 0)
    canvas.drawCircle(cx + r * 0.06f, cy + r * 0.12f, r, fillPaint)
    fillPaint.color = body; fillPaint.alpha = alpha
    canvas.drawCircle(cx, cy, r, fillPaint)
}

internal fun GameView.drawHole(canvas: Canvas, cx: Float, cy: Float, hr: Float, alpha: Int) {
    fillPaint.color = theme.holeColor; fillPaint.alpha = alpha
    canvas.drawCircle(cx, cy, hr, fillPaint)
    outlinePaint.color = Color.argb(alpha / 2, 255, 255, 255); outlinePaint.strokeWidth = hr * 0.15f
    canvas.drawCircle(cx, cy, hr, outlinePaint)
    outlinePaint.color = Color.argb(alpha / 2, 28, 12, 0)
    canvas.drawCircle(cx, cy, hr * 1.06f, outlinePaint)
    fillPaint.alpha = 255
}

// Strawberry: pink ring, red glaze that drips at the bottom, rainbow sprinkles.
internal fun GameView.drawDrippyRing(canvas: Canvas, cx: Float, cy: Float, r: Float, type: DonutType, selected: Boolean, alpha: Int) {
    ringBase(canvas, cx, cy, r, type.bodyColor, selected, alpha)
    scratchPath2.rewind()
    for (i in 0 until 24) {
        val gr = r * 0.80f * dripMul[i]
        val x = cx + gr * c24[i]; val y = cy + gr * s24[i]
        if (i == 0) scratchPath2.moveTo(x, y) else scratchPath2.lineTo(x, y)
    }
    scratchPath2.close()
    fillPaint.color = type.glazeColor; fillPaint.alpha = alpha
    canvas.drawPath(scratchPath2, fillPaint)
    canvas.save(); canvas.clipPath(scratchPath2)
    addSheen(canvas, cx, cy, r * 0.82f, alpha)
    canvas.restore()
    val sw = r * 0.24f; val sh = r * 0.09f
    for (i in 0 until 6) {
        val sx = cx + r * sprinkleD[i] * c24[sprinkleA[i]]
        val sy = cy + r * sprinkleD[i] * s24[sprinkleA[i]]
        canvas.save(); canvas.rotate(sprinkleRot[i], sx, sy)
        fillPaint.color = SPRINKLE_COLORS[i]; fillPaint.alpha = alpha
        canvas.drawRoundRect(scratchRectF.apply { set(sx - sw / 2f, sy - sh / 2f, sx + sw / 2f, sy + sh / 2f) }, sh / 2f, sh / 2f, fillPaint)
        canvas.restore()
    }
    drawHole(canvas, cx, cy, r * 0.34f, alpha)
}

// Chocolate: dark glaze with three cream stripes.
internal fun GameView.drawStripedRing(canvas: Canvas, cx: Float, cy: Float, r: Float, type: DonutType, selected: Boolean, alpha: Int) {
    ringBase(canvas, cx, cy, r, type.bodyColor, selected, alpha)
    fillPaint.color = type.glazeColor; fillPaint.alpha = alpha
    canvas.drawCircle(cx, cy, r * 0.82f, fillPaint)
    scratchPath2.rewind(); scratchPath2.addCircle(cx, cy, r * 0.82f, Path.Direction.CW)
    canvas.save(); canvas.clipPath(scratchPath2)
    strokePaint.color = Color.argb(alpha, 255, 236, 200); strokePaint.strokeWidth = r * 0.13f
    for (i in -1..1) {
        val off = i * r * 0.40f
        canvas.drawLine(cx - r + off, cy - r * 0.35f - off, cx + r * 0.35f + off, cy + r - off, strokePaint)
    }
    addSheen(canvas, cx, cy, r * 0.82f, alpha, sheenAlpha = 0.22f)
    canvas.restore()
    strokePaint.alpha = 255
    drawHole(canvas, cx, cy, r * 0.34f, alpha)
}

// Blueberry: a filled bun with no hole, a purple jam spot and powdered sugar.
internal fun GameView.drawJellyBun(canvas: Canvas, cx: Float, cy: Float, r: Float, type: DonutType, selected: Boolean, alpha: Int) {
    ringBase(canvas, cx, cy, r, type.bodyColor, selected, alpha)
    scratchPath2.rewind(); scratchPath2.addCircle(cx, cy, r, Path.Direction.CW)
    canvas.save(); canvas.clipPath(scratchPath2)
    fillPaint.color = type.glazeColor; fillPaint.alpha = alpha
    canvas.drawCircle(cx + r * 0.62f, cy + r * 0.18f, r * 0.36f, fillPaint)
    fillPaint.color = Color.argb((alpha * 0.55f).toInt(), 255, 255, 255)
    canvas.drawCircle(cx + r * 0.54f, cy + r * 0.06f, r * 0.09f, fillPaint)
    addSheen(canvas, cx, cy, r, alpha, sheenAlpha = 0.30f)
    canvas.restore()
    fillPaint.color = Color.argb((alpha * 0.85f).toInt(), 255, 255, 255)
    for (i in 0 until 7) canvas.drawCircle(cx + r * sugarX[i], cy + r * sugarY[i], r * sugarR[i], fillPaint)
    fillPaint.alpha = 255
}

internal fun GameView.buildScallop(path: Path, cx: Float, cy: Float, rr: Float) {
    path.rewind()
    for (i in 0 until 48) {
        val d = rr * scallopMul[i]
        val x = cx + d * c48[i]; val y = cy + d * s48[i]
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
}

// Vanilla: an eight-lobed flower ring with lemon glaze.
internal fun GameView.drawFlowerRing(canvas: Canvas, cx: Float, cy: Float, r: Float, type: DonutType, selected: Boolean, alpha: Int) {
    val ow = r * 0.16f
    if (selected) {
        fillPaint.color = Color.argb(alpha, 255, 255, 255)
        canvas.drawCircle(cx, cy, r * 1.32f, fillPaint)
    }
    buildScallop(scratchPath, cx, cy, r)
    outlinePaint.color = Color.argb(alpha, 28, 12, 0); outlinePaint.strokeWidth = ow * 2f
    canvas.drawPath(scratchPath, outlinePaint)
    canvas.save(); canvas.translate(r * 0.06f, r * 0.12f)
    fillPaint.color = Color.argb(alpha / 3, 0, 0, 0)
    canvas.drawPath(scratchPath, fillPaint)
    canvas.restore()
    fillPaint.color = type.bodyColor; fillPaint.alpha = alpha
    canvas.drawPath(scratchPath, fillPaint)
    buildScallop(scratchPath2, cx, cy, r * 0.80f)
    fillPaint.color = type.glazeColor; fillPaint.alpha = alpha
    canvas.drawPath(scratchPath2, fillPaint)
    canvas.save(); canvas.clipPath(scratchPath2)
    addSheen(canvas, cx, cy, r * 0.80f, alpha)
    canvas.restore()
    drawHole(canvas, cx, cy, r * 0.30f, alpha)
}

// Matcha: green ring dipped halfway in lime glaze, sesame seeds on top.
internal fun GameView.drawHalfDipRing(canvas: Canvas, cx: Float, cy: Float, r: Float, type: DonutType, selected: Boolean, alpha: Int) {
    ringBase(canvas, cx, cy, r, type.bodyColor, selected, alpha)
    scratchPath2.rewind(); scratchPath2.addCircle(cx, cy, r * 0.82f, Path.Direction.CW)
    canvas.save(); canvas.clipPath(scratchPath2)
    scratchPath.rewind()
    scratchPath.moveTo(cx - r, cy - r)
    scratchPath.lineTo(cx + r, cy - r)
    scratchPath.lineTo(cx + r, cy + r * 0.10f)
    val seg = r * 0.5f
    for (i in 0 until 4) {
        val x0 = cx + r - seg * i
        val ctrlY = cy + (if (i % 2 == 0) r * 0.42f else -r * 0.18f)
        scratchPath.quadTo(x0 - seg / 2f, ctrlY, x0 - seg, cy + r * 0.10f)
    }
    scratchPath.close()
    fillPaint.color = type.glazeColor; fillPaint.alpha = alpha
    canvas.drawPath(scratchPath, fillPaint)
    addSheen(canvas, cx, cy, r * 0.82f, alpha, sheenAlpha = 0.24f)
    canvas.restore()
    fillPaint.color = Color.argb(alpha, 60, 40, 20)
    for (i in 0 until 5) {
        val sx = cx + r * seedX[i]; val sy = cy + r * seedY[i]
        canvas.save(); canvas.rotate(seedRot[i], sx, sy)
        canvas.drawOval(scratchRectF.apply { set(sx - r * 0.075f, sy - r * 0.04f, sx + r * 0.075f, sy + r * 0.04f) }, fillPaint)
        canvas.restore()
    }
    drawHole(canvas, cx, cy, r * 0.34f, alpha)
}

// Caramel: a square donut with a round hole and dark caramel drizzle.
internal fun GameView.drawSquareDonut(canvas: Canvas, cx: Float, cy: Float, r: Float, type: DonutType, selected: Boolean, alpha: Int) {
    val ow = r * 0.18f; val side = r * 0.90f; val cr = r * 0.34f
    if (selected) {
        fillPaint.color = Color.argb(alpha, 255, 255, 255)
        canvas.drawCircle(cx, cy, r * 1.34f, fillPaint)
    }
    fillPaint.color = Color.argb(alpha, 28, 12, 0)
    canvas.drawRoundRect(scratchRectF.apply { set(cx - side - ow, cy - side - ow, cx + side + ow, cy + side + ow) }, cr + ow, cr + ow, fillPaint)
    fillPaint.color = Color.argb(alpha / 3, 0, 0, 0)
    canvas.drawRoundRect(scratchRectF.apply { set(cx - side + r * 0.06f, cy - side + r * 0.12f, cx + side + r * 0.06f, cy + side + r * 0.12f) }, cr, cr, fillPaint)
    fillPaint.color = type.bodyColor; fillPaint.alpha = alpha
    canvas.drawRoundRect(scratchRectF.apply { set(cx - side, cy - side, cx + side, cy + side) }, cr, cr, fillPaint)
    val g = side * 0.78f
    scratchRectF.set(cx - g, cy - g, cx + g, cy + g)
    fillPaint.color = type.glazeColor; fillPaint.alpha = alpha
    canvas.drawRoundRect(scratchRectF, cr * 0.8f, cr * 0.8f, fillPaint)
    scratchPath2.rewind(); scratchPath2.addRoundRect(scratchRectF, cr * 0.8f, cr * 0.8f, Path.Direction.CW)
    canvas.save(); canvas.clipPath(scratchPath2)
    strokePaint.color = Color.argb(alpha, 110, 55, 10); strokePaint.strokeWidth = r * 0.07f
    for (i in 0 until 3) {
        val yy = cy - g + g * (0.35f + 0.65f * i)
        canvas.drawLine(cx - g, yy - r * 0.14f, cx + g, yy + r * 0.14f, strokePaint)
    }
    addSheen(canvas, cx, cy, g, alpha, sheenAlpha = 0.22f)
    canvas.restore()
    strokePaint.alpha = 255
    drawHole(canvas, cx, cy, r * 0.30f, alpha)
}

// The wildcard is a sports ball: matches any donut, and echoes the title screen.
internal fun GameView.drawBallVector(canvas: Canvas, cx: Float, cy: Float, r: Float, selected: Boolean, alpha: Int) {
    val ow = r * 0.18f
    if (selected) {
        fillPaint.color = Color.argb(alpha, 255, 255, 255)
        canvas.drawCircle(cx, cy, r + ow + cellSize * 0.07f, fillPaint)
    }
    fillPaint.color = Color.argb(alpha, 28, 12, 0)
    canvas.drawCircle(cx, cy, r + ow, fillPaint)
    fillPaint.color = Color.argb(alpha / 3, 0, 0, 0)
    canvas.drawCircle(cx + r * 0.06f, cy + r * 0.12f, r, fillPaint)
    fillPaint.color = Color.argb(alpha, 255, 255, 255)
    canvas.drawCircle(cx, cy, r, fillPaint)
    scratchPath2.rewind(); scratchPath2.addCircle(cx, cy, r, Path.Direction.CW)
    canvas.save(); canvas.clipPath(scratchPath2)
    fillPaint.color = Color.argb(alpha, 22, 22, 22)
    drawPentagon(canvas, cx, cy, r * 0.34f, -90f)
    for (i in 0 until 5) {
        val a = i * 72f - 90f
        val rad = Math.toRadians(a.toDouble())
        drawPentagon(canvas, cx + (r * 0.80f * cos(rad)).toFloat(), cy + (r * 0.80f * sin(rad)).toFloat(), r * 0.28f, a + 180f)
    }
    canvas.restore()
    addSheen(canvas, cx, cy, r, alpha, sheenAlpha = 0.28f, specAlpha = 0.9f)
    fillPaint.alpha = 255
}

internal fun GameView.drawPentagon(canvas: Canvas, cx: Float, cy: Float, r: Float, startDeg: Float) {
    scratchPath.rewind()
    for (i in 0 until 5) {
        val a = Math.toRadians((startDeg + i * 72f).toDouble())
        val x = cx + (r * cos(a)).toFloat(); val y = cy + (r * sin(a)).toFloat()
        if (i == 0) scratchPath.moveTo(x, y) else scratchPath.lineTo(x, y)
    }
    scratchPath.close()
    canvas.drawPath(scratchPath, fillPaint)
}

// -----------------------------------------------------------------------
// 3D sheen helper — upper-left crescent highlight + specular dot
// -----------------------------------------------------------------------
/**
 * Adds a 3D-style highlight to any piece. Clips to an upper-left oval,
 * paints a semi-transparent white sheen, then drops a bright specular dot.
 * Call AFTER drawing the main body, BEFORE any hole/overlay elements.
 */
internal fun GameView.addSheen(canvas: Canvas, cx: Float, cy: Float, r: Float, alpha: Int,
                     sheenAlpha: Float = 0.32f, specAlpha: Float = 0.75f) {
    // Soft top-left crescent via clipPath — reuse scratchPath (callers use scratchPath2
    // for their own outer clip, so there is no aliasing conflict here).
    scratchPath.rewind()
    scratchPath.addOval(
        scratchRectF.apply { set(cx - r * 0.88f, cy - r * 1.05f, cx + r * 0.52f, cy + r * 0.05f) },
        Path.Direction.CW
    )
    canvas.save()
    canvas.clipPath(scratchPath)
    fillPaint.color = Color.argb((alpha * sheenAlpha).toInt(), 255, 255, 255)
    canvas.drawRect(cx - r * 2f, cy - r * 2f, cx + r * 2f, cy + r * 2f, fillPaint)
    canvas.restore()
    // Bright specular dot upper-left
    fillPaint.color = Color.argb((alpha * specAlpha).toInt(), 255, 255, 255)
    canvas.drawCircle(cx - r * 0.30f, cy - r * 0.44f, r * 0.16f, fillPaint)
    fillPaint.alpha = 255
}
