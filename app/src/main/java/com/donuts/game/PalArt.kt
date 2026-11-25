package com.donuts.game

import android.graphics.*
import kotlin.math.*

// The donut pal: Steven's buddy who stands beside the counter, holding up the number.
// Part of GameView: these are extension functions on it, split out of GameView.kt.

private val PAL_INK = Color.rgb(28, 12, 0)
private val PAL_DOUGH = Color.rgb(240, 170, 85)
private val PAL_GLAZE = Color.rgb(255, 140, 35)
private val PAL_SHOE = Color.rgb(235, 45, 55)

/** How far the pal reaches either side of its centre, as a share of its body radius (arms included). */
internal const val PAL_HALF_W = 1.30f
/** How tall the pal stands, feet to the top of its outline, as a share of its body radius. */
internal const val PAL_TALL = 2.30f

private val palSprX = floatArrayOf(-0.58f, -0.30f, 0.30f, 0.58f, -0.66f, 0.66f)
private val palSprY = floatArrayOf(-0.30f, -0.70f, -0.70f, -0.30f, 0.22f, 0.22f)
private val palSprRot = floatArrayOf(40f, -25f, 30f, -45f, -60f, 55f)
private val PAL_SPR = intArrayOf(
    Color.WHITE, Color.rgb(60, 190, 255), Color.rgb(255, 225, 40),
    Color.WHITE, Color.rgb(110, 225, 70), Color.rgb(60, 190, 255))

/**
 * Paints the pal standing with its feet on [footY], centred on [cx], body radius [r], in [pose]
 * (see [PalMotion]). The pose bobs and squashes it about its feet, so it never sinks below [footY].
 */
internal fun GameView.drawPal(canvas: Canvas, cx: Float, footY: Float, r: Float, pose: PalPose) {
    if (r <= 0f) return
    // Floor shadow: shrinks a little as the pal hops up
    val shadowW = r * (0.95f - pose.bob)
    fillPaint.color = Color.argb(55, 0, 0, 0)
    canvas.drawOval(scratchRectF.apply { set(cx - shadowW, footY - r * 0.10f, cx + shadowW, footY + r * 0.10f) }, fillPaint)

    canvas.save()
    canvas.scale(pose.squashX, pose.squashY, cx, footY)
    canvas.translate(0f, -pose.bob * r)
    val cy = footY - r * 1.12f

    // Sneakers, then the waving arms, both tucked behind the body
    for (side in intArrayOf(-1, 1)) {
        val fx = cx + side * r * 0.42f
        val rect = scratchRectF.apply { set(fx - r * 0.34f, footY - r * 0.30f, fx + r * 0.34f, footY) }
        fillPaint.color = PAL_SHOE
        canvas.drawRoundRect(rect, r * 0.16f, r * 0.16f, fillPaint)
        outlinePaint.color = PAL_INK; outlinePaint.strokeWidth = max(2f, r * 0.10f)
        canvas.drawRoundRect(rect, r * 0.16f, r * 0.16f, outlinePaint)
    }
    for (side in intArrayOf(-1, 1)) {
        val ax = cx + side * r * 0.94f; val ay = cy + r * 0.05f
        canvas.save()
        canvas.rotate(side * 35f, ax, ay)
        val rect = scratchRectF.apply { set(ax - r * 0.15f, ay - r * 0.42f, ax + r * 0.15f, ay + r * 0.12f) }
        fillPaint.color = PAL_DOUGH
        canvas.drawRoundRect(rect, r * 0.15f, r * 0.15f, fillPaint)
        outlinePaint.color = PAL_INK; outlinePaint.strokeWidth = max(2f, r * 0.10f)
        canvas.drawRoundRect(rect, r * 0.15f, r * 0.15f, outlinePaint)
        canvas.restore()
    }

    // Body: the dough ring with a drippy orange glaze, sprinkles, and the hole up top
    ringBase(canvas, cx, cy, r, PAL_DOUGH, false, 255)
    scratchPath2.rewind(); scratchPath2.addCircle(cx, cy, r * 0.80f, Path.Direction.CW)
    for (deg in floatArrayOf(60f, 95f, 128f)) {
        val a = Math.toRadians(deg.toDouble())
        scratchPath2.addCircle(cx + r * 0.84f * cos(a).toFloat(), cy + r * 0.84f * sin(a).toFloat(), r * 0.12f, Path.Direction.CW)
    }
    fillPaint.color = PAL_GLAZE
    canvas.drawPath(scratchPath2, fillPaint)
    canvas.save(); canvas.clipPath(scratchPath2)
    addSheen(canvas, cx, cy, r * 0.84f, 255, sheenAlpha = 0.28f)
    canvas.restore()
    val sw = r * 0.22f; val sh = r * 0.09f
    for (i in palSprX.indices) {
        val sx = cx + r * palSprX[i]; val sy = cy + r * palSprY[i]
        canvas.save(); canvas.rotate(palSprRot[i], sx, sy)
        fillPaint.color = PAL_SPR[i]
        canvas.drawRoundRect(scratchRectF.apply { set(sx - sw / 2f, sy - sh / 2f, sx + sw / 2f, sy + sh / 2f) }, sh / 2f, sh / 2f, fillPaint)
        canvas.restore()
    }
    drawHole(canvas, cx, cy - r * 0.52f, r * 0.19f, 255)

    // Face: big eyes that glance about and blink, rosy cheeks, an open grin
    val ey = cy + r * 0.02f; val er = r * 0.21f
    val lookX = 0.12f * pose.look
    for (side in intArrayOf(-1, 1)) {
        val ex = cx + side * r * 0.34f
        canvas.save()
        canvas.scale(1f, 1f - 0.9f * pose.blink, ex, ey)
        friendEye(canvas, ex, ey, er, lookX, 0.08f)
        canvas.restore()
    }
    friendCheeks(canvas, cx, cy + r * 0.30f, r)
    friendGrin(canvas, cx, cy + r * 0.38f, r * 0.28f, r * 0.22f, tongueIn = true)
    canvas.restore()
    fillPaint.alpha = 255; outlinePaint.alpha = 255
}
