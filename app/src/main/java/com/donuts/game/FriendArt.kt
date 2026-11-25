package com.donuts.game

import android.graphics.*
import kotlin.math.*

// The donut friends: one silly character each, drawn as vectors (big eyes, a grin, one funny feature).
// Part of GameView: these are extension functions on it, split out of GameView.kt.

/** Friends that have art. FriendRules.next is passed this so no friend without a picture is revealed. */
const val DRAWN = 12

private val INK = Color.rgb(28, 12, 0)
private val DOUGH = Color.rgb(240, 170, 85)
private val MOUTH = Color.rgb(125, 20, 35)
private val TONGUE = Color.rgb(255, 105, 125)

// A ring friend's hole sits high so the face has room below it
private const val HOLE_Y = -0.50f
private const val HOLE_R = 0.20f

/** Paints friend [idx] centred on ([cx], [cy]) with body radius [r]. Friends past [DRAWN] paint nothing. */
internal fun GameView.drawFriend(canvas: Canvas, idx: Int, cx: Float, cy: Float, r: Float) {
    if (r <= 0f) return
    when (idx) {
        0 -> drawSprinkleFriend(canvas, cx, cy, r)
        1 -> drawJellyFriend(canvas, cx, cy, r)
        2 -> drawBouncyFriend(canvas, cx, cy, r)
        3 -> drawSwirlFriend(canvas, cx, cy, r)
        4 -> drawChocoFriend(canvas, cx, cy, r)
        5 -> drawKickyFriend(canvas, cx, cy, r)
        6 -> drawBerryFriend(canvas, cx, cy, r)
        7 -> drawGloopFriend(canvas, cx, cy, r)
        8 -> drawZoomyFriend(canvas, cx, cy, r)
        9 -> drawPuffFriend(canvas, cx, cy, r)
        10 -> drawFizzFriend(canvas, cx, cy, r)
        11 -> drawCaptainFriend(canvas, cx, cy, r)
    }
    fillPaint.alpha = 255; strokePaint.alpha = 255; outlinePaint.alpha = 255
}

// Sprinkle: a pink frosted ring with rainbow sprinkles, and a tongue poking out
private val sprX   = floatArrayOf(-0.66f, -0.40f, -0.12f, 0.30f, 0.52f, 0.70f, -0.62f, 0.62f)
private val sprY   = floatArrayOf(-0.20f, -0.60f, -0.78f, -0.74f, -0.48f, -0.16f, 0.40f, 0.42f)
private val sprRot = floatArrayOf(35f, -30f, 80f, -20f, 60f, -40f, -55f, 50f)
private val SPR_COLORS = intArrayOf(
    Color.WHITE, Color.rgb(255, 225, 40), Color.rgb(60, 190, 255), Color.rgb(110, 225, 70),
    Color.rgb(170, 110, 255), Color.rgb(255, 140, 30), Color.rgb(60, 190, 255), Color.rgb(255, 225, 40))

private val dripDeg = floatArrayOf(48f, 76f, 104f, 133f)
private val dripR   = floatArrayOf(0.12f, 0.10f, 0.13f, 0.10f)

private fun GameView.drawSprinkleFriend(canvas: Canvas, cx: Float, cy: Float, r: Float) {
    ringBase(canvas, cx, cy, r, DOUGH, false, 255)
    // Frosting: a round top with four fat drips running down toward the edge
    scratchPath2.rewind(); scratchPath2.addCircle(cx, cy, r * 0.80f, Path.Direction.CW)
    for (i in dripDeg.indices) {
        val a = Math.toRadians(dripDeg[i].toDouble())
        scratchPath2.addCircle(cx + r * 0.84f * cos(a).toFloat(), cy + r * 0.84f * sin(a).toFloat(), r * dripR[i], Path.Direction.CW)
    }
    fillPaint.color = Color.rgb(255, 95, 170)
    canvas.drawPath(scratchPath2, fillPaint)
    canvas.save(); canvas.clipPath(scratchPath2)
    addSheen(canvas, cx, cy, r * 0.84f, 255, sheenAlpha = 0.26f)
    canvas.restore()
    val sw = r * 0.22f; val sh = r * 0.085f
    for (i in sprX.indices) {
        val sx = cx + r * sprX[i]; val sy = cy + r * sprY[i]
        canvas.save(); canvas.rotate(sprRot[i], sx, sy)
        fillPaint.color = SPR_COLORS[i]
        canvas.drawRoundRect(scratchRectF.apply { set(sx - sw / 2f, sy - sh / 2f, sx + sw / 2f, sy + sh / 2f) }, sh / 2f, sh / 2f, fillPaint)
        canvas.restore()
    }
    drawHole(canvas, cx, cy + r * HOLE_Y, r * HOLE_R, 255)
    friendEye(canvas, cx - r * 0.33f, cy + r * 0.02f, r * 0.2f, 0.12f, 0.1f)
    friendEye(canvas, cx + r * 0.33f, cy + r * 0.02f, r * 0.2f, -0.12f, 0.1f)
    friendGrin(canvas, cx, cy + r * 0.36f, r * 0.30f, r * 0.22f, tongueIn = false)
    friendTongueOut(canvas, cx + r * 0.06f, cy + r * 0.36f, r * 0.30f, r * 0.22f)
}

private val sugX = floatArrayOf(-0.62f, -0.40f, -0.70f, 0.55f, 0.70f, 0.48f, -0.20f)
private val sugY = floatArrayOf(-0.42f, -0.62f, -0.12f, -0.50f, -0.22f, -0.72f, -0.40f)

// Jelly: a round jelly bun with jam bursting out of the top, and rosy cheeks
private fun GameView.drawJellyFriend(canvas: Canvas, cx: Float, cy: Float, r: Float) {
    ringBase(canvas, cx, cy, r, Color.rgb(250, 185, 90), false, 255)
    scratchPath2.rewind(); scratchPath2.addCircle(cx, cy, r, Path.Direction.CW)
    canvas.save(); canvas.clipPath(scratchPath2)
    fillPaint.color = Color.rgb(225, 140, 55)            // a toasty band round the middle
    canvas.drawOval(scratchRectF.apply { set(cx - r * 1.2f, cy + r * 0.55f, cx + r * 1.2f, cy + r * 1.6f) }, fillPaint)
    addSheen(canvas, cx, cy, r, 255, sheenAlpha = 0.28f)
    canvas.restore()
    // Jam: a blob on top with a drip down the front, outlined in ink
    val jx = cx + r * 0.08f; val jy = cy - r * 0.78f; val jr = r * 0.30f
    val ow = r * 0.07f
    fillPaint.color = INK
    canvas.drawCircle(jx, jy, jr + ow, fillPaint)
    canvas.drawCircle(jx - jr * 0.95f, jy + jr * 0.35f, jr * 0.55f + ow, fillPaint)
    canvas.drawRoundRect(scratchRectF.apply { set(jx + jr * 0.2f - ow, jy, jx + jr * 0.75f + ow, jy + jr * 1.55f + ow) }, jr, jr, fillPaint)
    fillPaint.color = Color.rgb(235, 25, 65)
    canvas.drawCircle(jx, jy, jr, fillPaint)
    canvas.drawCircle(jx - jr * 0.95f, jy + jr * 0.35f, jr * 0.55f, fillPaint)
    canvas.drawRoundRect(scratchRectF.apply { set(jx + jr * 0.2f, jy, jx + jr * 0.75f, jy + jr * 1.55f) }, jr, jr, fillPaint)
    fillPaint.color = Color.argb(200, 255, 255, 255)
    canvas.drawCircle(jx - jr * 0.35f, jy - jr * 0.35f, jr * 0.22f, fillPaint)
    // Powdered sugar dusted round the jam
    fillPaint.color = Color.argb(230, 255, 255, 255)
    for (i in sugX.indices) canvas.drawCircle(cx + r * sugX[i], cy + r * sugY[i], r * 0.055f, fillPaint)
    friendEye(canvas, cx - r * 0.34f, cy + r * 0.0f, r * 0.21f, 0.1f, 0.12f)
    friendEye(canvas, cx + r * 0.34f, cy + r * 0.0f, r * 0.21f, -0.1f, 0.12f)
    friendCheeks(canvas, cx, cy + r * 0.3f, r)
    friendGrin(canvas, cx, cy + r * 0.38f, r * 0.30f, r * 0.24f, tongueIn = true)
}

// Bouncy: a basketball with big bouncy eyebrows
private fun GameView.drawBouncyFriend(canvas: Canvas, cx: Float, cy: Float, r: Float) {
    ringBase(canvas, cx, cy, r, Color.rgb(255, 125, 25), false, 255)
    scratchPath2.rewind(); scratchPath2.addCircle(cx, cy, r, Path.Direction.CW)
    canvas.save(); canvas.clipPath(scratchPath2)
    canvas.rotate(-18f, cx, cy)
    outlinePaint.color = Color.rgb(120, 45, 5); outlinePaint.strokeWidth = r * 0.06f
    canvas.drawLine(cx - r, cy, cx + r, cy, outlinePaint)
    canvas.drawLine(cx, cy - r, cx, cy + r, outlinePaint)
    canvas.drawArc(scratchRectF.apply { set(cx - r * 1.55f, cy - r * 0.95f, cx - r * 0.45f, cy + r * 0.95f) }, -70f, 140f, false, outlinePaint)
    canvas.drawArc(scratchRectF.apply { set(cx + r * 0.45f, cy - r * 0.95f, cx + r * 1.55f, cy + r * 0.95f) }, 110f, 140f, false, outlinePaint)
    canvas.restore()
    addSheen(canvas, cx, cy, r, 255, sheenAlpha = 0.24f, specAlpha = 0.8f)
    val ey = cy - r * 0.08f; val er = r * 0.22f
    friendEye(canvas, cx - r * 0.34f, ey, er, 0.14f, -0.05f)
    friendEye(canvas, cx + r * 0.34f, ey, er, 0.14f, -0.05f)
    // Eyebrows raised high and tilted, as if he just did a big jump
    outlinePaint.color = INK; outlinePaint.strokeWidth = r * 0.09f
    canvas.drawArc(scratchRectF.apply { set(cx - r * 0.56f, ey - er * 2.35f, cx - r * 0.12f, ey - er * 1.05f) }, 205f, 120f, false, outlinePaint)
    canvas.drawArc(scratchRectF.apply { set(cx + r * 0.12f, ey - er * 2.35f, cx + r * 0.56f, ey - er * 1.05f) }, 215f, 120f, false, outlinePaint)
    friendGrin(canvas, cx, cy + r * 0.34f, r * 0.36f, r * 0.30f, tongueIn = true)
}

// Swirl: a ring with a purple swirl glaze, and a wink
private fun GameView.drawSwirlFriend(canvas: Canvas, cx: Float, cy: Float, r: Float) {
    ringBase(canvas, cx, cy, r, DOUGH, false, 255)
    scratchPath2.rewind(); scratchPath2.addCircle(cx, cy, r * 0.84f, Path.Direction.CW)

    fillPaint.color = Color.rgb(150, 70, 235)
    canvas.drawPath(scratchPath2, fillPaint)
    canvas.save(); canvas.clipPath(scratchPath2)
    // The swirl: a spiral of lighter glaze winding out from the hole
    val hx = cx; val hy = cy + r * HOLE_Y
    scratchPath.rewind()
    val turns = 3.2f; val steps = 90
    for (s in 0..steps) {
        val a = s.toFloat() / steps * turns * 2f * PI.toFloat()
        val d = r * (HOLE_R + 0.05f) + r * 0.62f * a / (turns * 2f * PI.toFloat()) * 1.9f
        val x = hx + d * cos(a); val y = hy + d * sin(a)
        if (s == 0) scratchPath.moveTo(x, y) else scratchPath.lineTo(x, y)
    }
    strokePaint.color = Color.rgb(205, 160, 255); strokePaint.strokeWidth = r * 0.10f
    strokePaint.strokeCap = Paint.Cap.ROUND
    canvas.drawPath(scratchPath, strokePaint)
    strokePaint.strokeCap = Paint.Cap.BUTT
    addSheen(canvas, cx, cy, r * 0.84f, 255, sheenAlpha = 0.24f)
    canvas.restore()
    drawHole(canvas, hx, hy, r * HOLE_R, 255)
    val ey = cy + r * 0.04f
    friendEye(canvas, cx - r * 0.33f, ey, r * 0.2f, 0.1f, 0.1f)
    friendWink(canvas, cx + r * 0.33f, ey, r * 0.2f)
    friendCheeks(canvas, cx, ey + r * 0.28f, r)
    friendGrin(canvas, cx - r * 0.03f, cy + r * 0.38f, r * 0.30f, r * 0.22f, tongueIn = true)
}

// -----------------------------------------------------------------------
// Face parts shared by every friend (FriendArt2.kt uses them too)
// -----------------------------------------------------------------------

// A big round eye: ink rim, white, a pupil looking toward ([lookX], [lookY]) and two shines
internal fun GameView.friendEye(canvas: Canvas, x: Float, y: Float, er: Float, lookX: Float, lookY: Float) {
    val ew = er; val eh = er * 1.12f
    fillPaint.color = INK
    canvas.drawOval(scratchRectF.apply { set(x - ew * 1.2f, y - eh * 1.18f, x + ew * 1.2f, y + eh * 1.18f) }, fillPaint)
    fillPaint.color = Color.WHITE
    canvas.drawOval(scratchRectF.apply { set(x - ew, y - eh, x + ew, y + eh) }, fillPaint)
    val px = x + er * lookX * 2f; val py = y + er * lookY * 2f
    fillPaint.color = INK
    canvas.drawCircle(px, py, er * 0.62f, fillPaint)
    fillPaint.color = Color.WHITE
    canvas.drawCircle(px - er * 0.22f, py - er * 0.24f, er * 0.24f, fillPaint)
    canvas.drawCircle(px + er * 0.22f, py + er * 0.24f, er * 0.10f, fillPaint)
}

// A closed happy eye, curved up like a rainbow
internal fun GameView.friendWink(canvas: Canvas, x: Float, y: Float, er: Float) {
    outlinePaint.color = INK; outlinePaint.strokeWidth = er * 0.42f
    canvas.drawArc(scratchRectF.apply { set(x - er * 0.95f, y - er * 0.55f, x + er * 0.95f, y + er * 0.95f) }, 200f, 140f, false, outlinePaint)
}

// Two soft pink cheeks either side of the mouth, [cy] at their centre
internal fun GameView.friendCheeks(canvas: Canvas, cx: Float, cy: Float, r: Float) {
    fillPaint.color = Color.argb(170, 255, 90, 120)
    canvas.drawOval(scratchRectF.apply { set(cx - r * 0.70f, cy - r * 0.08f, cx - r * 0.42f, cy + r * 0.08f) }, fillPaint)
    canvas.drawOval(scratchRectF.apply { set(cx + r * 0.42f, cy - r * 0.08f, cx + r * 0.70f, cy + r * 0.08f) }, fillPaint)
    fillPaint.alpha = 255
}

// An open grin: a smile-curved top and a round bottom, [w] and [h] its half width and depth
internal fun GameView.friendGrin(canvas: Canvas, mx: Float, my: Float, w: Float, h: Float, tongueIn: Boolean) {
    scratchPath.rewind()
    scratchPath.moveTo(mx - w, my)
    scratchPath.quadTo(mx, my + h * 0.3f, mx + w, my)
    scratchPath.arcTo(scratchRectF.apply { set(mx - w, my - h, mx + w, my + h) }, 0f, 180f, false)
    scratchPath.close()
    fillPaint.color = MOUTH
    canvas.drawPath(scratchPath, fillPaint)
    if (tongueIn) {
        canvas.save(); canvas.clipPath(scratchPath)
        fillPaint.color = TONGUE
        canvas.drawOval(scratchRectF.apply { set(mx - w * 0.55f, my + h * 0.45f, mx + w * 0.55f, my + h * 1.5f) }, fillPaint)
        canvas.restore()
    }
    outlinePaint.color = INK; outlinePaint.strokeWidth = max(2f, w * 0.17f)
    canvas.drawPath(scratchPath, outlinePaint)
}

// A tongue poking out over the bottom lip of the grin at ([mx], [my])
internal fun GameView.friendTongueOut(canvas: Canvas, mx: Float, my: Float, w: Float, h: Float) {
    val tw = w * 0.42f; val top = my + h * 0.45f; val bottom = my + h * 1.75f
    scratchPath.rewind()
    scratchPath.moveTo(mx - tw, top)
    scratchPath.lineTo(mx - tw, bottom - tw)
    scratchPath.arcTo(scratchRectF.apply { set(mx - tw, bottom - 2f * tw, mx + tw, bottom) }, 180f, -180f, false)
    scratchPath.lineTo(mx + tw, top)
    fillPaint.color = TONGUE
    canvas.drawPath(scratchPath, fillPaint)
    outlinePaint.color = INK; outlinePaint.strokeWidth = max(2f, w * 0.15f)
    canvas.drawPath(scratchPath, outlinePaint)
    outlinePaint.color = Color.rgb(215, 55, 85); outlinePaint.strokeWidth = max(1f, w * 0.07f)
    canvas.drawLine(mx, top + h * 0.2f, mx, bottom - tw * 0.9f, outlinePaint)
}
