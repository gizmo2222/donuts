package com.donuts.game

import android.graphics.*
import kotlin.math.*

// Donut friends 4 to 11: Choco, Kicky, Berry, Gloop, Zoomy, Puff, Fizz and Captain Glaze. Same style as
// FriendArt.kt (big eyes, a grin, one funny feature) and its face helpers. Extension functions on GameView.

private val DOUGH = Color.rgb(240, 170, 85)

// A ring friend's hole sits high so the face has room below it (as in FriendArt.kt)
private const val HOLE_Y = -0.50f
private const val HOLE_R = 0.20f

/** A frosted top of radius [gr] in [color] over the dough, with the sheen clipped to it. Leaves scratchPath2 as the clip shape. */
private fun GameView.glazeTop(canvas: Canvas, cx: Float, cy: Float, gr: Float, color: Int, sheen: Float = 0.24f) {
    scratchPath2.rewind(); scratchPath2.addCircle(cx, cy, gr, Path.Direction.CW)
    fillPaint.color = color
    canvas.drawPath(scratchPath2, fillPaint)
    canvas.save(); canvas.clipPath(scratchPath2)
    addSheen(canvas, cx, cy, gr, 255, sheenAlpha = sheen)
    canvas.restore()
}

// Choco: milk-chocolate glaze with a white drizzle, and a curly chocolate moustache
internal fun GameView.drawChocoFriend(canvas: Canvas, cx: Float, cy: Float, r: Float) {
    ringBase(canvas, cx, cy, r, DOUGH, false, 255)
    glazeTop(canvas, cx, cy, r * 0.84f, Color.rgb(165, 88, 38), sheen = 0.20f)
    canvas.save(); canvas.clipPath(scratchPath2)
    scratchPath.rewind()
    for (k in 0..5) {
        val x = cx + r * (-0.85f + k * 0.34f); val y = cy + r * (if (k % 2 == 0) -0.80f else -0.24f)
        if (k == 0) scratchPath.moveTo(x, y) else scratchPath.lineTo(x, y)
    }
    outlinePaint.color = Color.rgb(255, 240, 222); outlinePaint.strokeWidth = r * 0.08f
    canvas.drawPath(scratchPath, outlinePaint)
    canvas.restore()
    drawHole(canvas, cx, cy + r * HOLE_Y, r * HOLE_R, 255)
    friendEye(canvas, cx - r * 0.33f, cy + r * 0.0f, r * 0.2f, 0.08f, 0.1f)
    friendEye(canvas, cx + r * 0.33f, cy + r * 0.0f, r * 0.2f, -0.08f, 0.1f)
    friendGrin(canvas, cx, cy + r * 0.48f, r * 0.27f, r * 0.21f, tongueIn = true)
    // The moustache: two curls from the middle, swept out and up at the tips
    val my = cy + r * 0.38f
    scratchPath.rewind()
    for (s in intArrayOf(-1, 1)) {
        scratchPath.moveTo(cx, my)
        scratchPath.quadTo(cx + s * r * 0.30f, my + r * 0.14f, cx + s * r * 0.50f, my + r * 0.01f)
        scratchPath.quadTo(cx + s * r * 0.64f, my - r * 0.08f, cx + s * r * 0.55f, my - r * 0.17f)
    }
    outlinePaint.color = INK; outlinePaint.strokeWidth = r * 0.21f
    canvas.drawPath(scratchPath, outlinePaint)
    outlinePaint.color = Color.rgb(70, 30, 10); outlinePaint.strokeWidth = r * 0.13f
    canvas.drawPath(scratchPath, outlinePaint)
    fillPaint.color = Color.rgb(70, 30, 10)
    canvas.drawCircle(cx, my + r * 0.02f, r * 0.09f, fillPaint)
    outlinePaint.color = Color.rgb(140, 78, 40); outlinePaint.strokeWidth = r * 0.035f
    canvas.drawLine(cx - r * 0.40f, my + r * 0.03f, cx - r * 0.18f, my + r * 0.06f, outlinePaint)
    canvas.drawLine(cx + r * 0.18f, my + r * 0.06f, cx + r * 0.40f, my + r * 0.03f, outlinePaint)
}

// Kicky: a soccer ball in a red sweatband, its knot tails flapping out to one side
internal fun GameView.drawKickyFriend(canvas: Canvas, cx: Float, cy: Float, r: Float) {
    val red = Color.rgb(235, 40, 50)
    for (tail in 0..1) {                          // behind the ball, so its outline covers where they tie on
        val tx = cx + r * (if (tail == 0) 1.14f else 1.10f); val ty = cy + r * (if (tail == 0) -0.78f else -0.42f)
        canvas.save(); canvas.rotate(if (tail == 0) -28f else 22f, tx, ty)
        val hw = r * 0.27f; val hh = r * 0.10f; val ow = r * 0.06f
        fillPaint.color = INK
        canvas.drawRoundRect(scratchRectF.apply { set(tx - hw - ow, ty - hh - ow, tx + hw + ow, ty + hh + ow) }, hh + ow, hh + ow, fillPaint)
        fillPaint.color = red
        canvas.drawRoundRect(scratchRectF.apply { set(tx - hw, ty - hh, tx + hw, ty + hh) }, hh, hh, fillPaint)
        canvas.restore()
    }
    ringBase(canvas, cx, cy, r, Color.rgb(250, 250, 245), false, 255)
    scratchPath2.rewind(); scratchPath2.addCircle(cx, cy, r, Path.Direction.CW)
    canvas.save(); canvas.clipPath(scratchPath2)
    fillPaint.color = Color.rgb(35, 35, 42)
    for (i in 0 until 5) {
        val a = i * 72f - 90f
        val rad = Math.toRadians(a.toDouble())
        drawPentagon(canvas, cx + (r * 0.88f * cos(rad)).toFloat(), cy + (r * 0.88f * sin(rad)).toFloat(), r * 0.30f, a + 180f)
    }
    // The sweatband round his forehead, with a white stripe
    val bt = cy - r * 0.74f; val bb = cy - r * 0.46f
    fillPaint.color = red
    canvas.drawRect(cx - r, bt, cx + r, bb, fillPaint)
    fillPaint.color = Color.WHITE
    canvas.drawRect(cx - r, bt + (bb - bt) * 0.40f, cx + r, bt + (bb - bt) * 0.60f, fillPaint)
    outlinePaint.color = INK; outlinePaint.strokeWidth = r * 0.06f
    canvas.drawLine(cx - r, bt, cx + r, bt, outlinePaint)
    canvas.drawLine(cx - r, bb, cx + r, bb, outlinePaint)
    canvas.restore()
    addSheen(canvas, cx, cy, r, 255, sheenAlpha = 0.20f, specAlpha = 0.85f)
    friendEye(canvas, cx - r * 0.34f, cy - r * 0.04f, r * 0.21f, 0.14f, 0.04f)
    friendEye(canvas, cx + r * 0.34f, cy - r * 0.04f, r * 0.21f, 0.14f, 0.04f)
    friendGrin(canvas, cx, cy + r * 0.34f, r * 0.34f, r * 0.28f, tongueIn = true)
}

// Berry: blueberry glaze, and a big strawberry worn as a hat, tip up and leaves for a brim
internal fun GameView.drawBerryFriend(canvas: Canvas, cx: Float, cy: Float, r: Float) {
    ringBase(canvas, cx, cy, r, DOUGH, false, 255)
    glazeTop(canvas, cx, cy, r * 0.84f, Color.rgb(70, 110, 235))
    fillPaint.color = Color.rgb(150, 180, 255)
    for (i in 0 until 6) {
        val a = Math.toRadians((i * 60 + 20).toDouble())
        canvas.drawCircle(cx + r * 0.66f * cos(a).toFloat(), cy + r * 0.06f + r * 0.60f * sin(a).toFloat(), r * 0.045f, fillPaint)
    }
    drawHole(canvas, cx, cy + r * HOLE_Y, r * HOLE_R, 255)
    friendEye(canvas, cx - r * 0.33f, cy + r * 0.04f, r * 0.2f, 0.1f, 0.08f)
    friendEye(canvas, cx + r * 0.33f, cy + r * 0.04f, r * 0.2f, -0.1f, 0.08f)
    friendCheeks(canvas, cx, cy + r * 0.32f, r)
    friendGrin(canvas, cx, cy + r * 0.40f, r * 0.30f, r * 0.24f, tongueIn = true)
    // The hat, tilted a little; built about its brim centre
    val hx = cx + r * 0.12f; val hy = cy - r * 0.86f
    canvas.save(); canvas.rotate(14f, hx, hy)
    scratchPath.rewind()
    scratchPath.moveTo(hx - r * 0.42f, hy)
    scratchPath.cubicTo(hx - r * 0.46f, hy - r * 0.32f, hx - r * 0.12f, hy - r * 0.58f, hx, hy - r * 0.62f)
    scratchPath.cubicTo(hx + r * 0.12f, hy - r * 0.58f, hx + r * 0.46f, hy - r * 0.32f, hx + r * 0.42f, hy)
    scratchPath.quadTo(hx, hy + r * 0.10f, hx - r * 0.42f, hy)
    scratchPath.close()
    outlinePaint.color = INK; outlinePaint.strokeWidth = r * 0.10f
    canvas.drawPath(scratchPath, outlinePaint)
    fillPaint.color = Color.rgb(240, 35, 55)
    canvas.drawPath(scratchPath, fillPaint)
    fillPaint.color = Color.rgb(255, 225, 60)
    for (k in 0 until 7) {
        val sx = hx + r * floatArrayOf(-0.24f, 0f, 0.24f, -0.12f, 0.12f, 0f, -0.30f)[k]
        val sy = hy - r * floatArrayOf(0.14f, 0.12f, 0.14f, 0.30f, 0.30f, 0.46f, 0.02f)[k]
        canvas.drawOval(scratchRectF.apply { set(sx - r * 0.03f, sy - r * 0.045f, sx + r * 0.03f, sy + r * 0.045f) }, fillPaint)
    }
    fillPaint.color = Color.argb(170, 255, 255, 255)
    canvas.drawCircle(hx - r * 0.18f, hy - r * 0.36f, r * 0.06f, fillPaint)
    // A curly stem on the tip and a ring of pointed leaves along the brim
    outlinePaint.color = Color.rgb(40, 140, 40); outlinePaint.strokeWidth = r * 0.07f
    canvas.drawArc(scratchRectF.apply { set(hx, hy - r * 0.80f, hx + r * 0.20f, hy - r * 0.60f) }, 90f, 220f, false, outlinePaint)
    for (k in -2..2) {
        val lx = hx + k * r * 0.18f
        scratchPath.rewind()
        scratchPath.moveTo(lx - r * 0.11f, hy - r * 0.03f)
        scratchPath.lineTo(lx + k * r * 0.04f, hy + r * 0.20f)
        scratchPath.lineTo(lx + r * 0.11f, hy - r * 0.03f)
        scratchPath.close()
        fillPaint.color = Color.rgb(70, 195, 60)
        canvas.drawPath(scratchPath, fillPaint)
        outlinePaint.color = INK; outlinePaint.strokeWidth = r * 0.045f
        canvas.drawPath(scratchPath, outlinePaint)
    }
    canvas.restore()
}

private val gooX = floatArrayOf(-0.60f, -0.20f, 0.28f, 0.64f)
private val gooY = floatArrayOf(1.00f, 1.20f, 1.08f, 0.92f)

// Gloop: green goo glaze that drips right over his edge, with bubbles in it
internal fun GameView.drawGloopFriend(canvas: Canvas, cx: Float, cy: Float, r: Float) {
    ringBase(canvas, cx, cy, r, DOUGH, false, 255)
    val gr = r * 0.86f; val dw = r * 0.10f; val ball = r * 0.14f
    for (pass in 0..1) {                          // ink first, a little bigger, then the goo over it
        val ow = if (pass == 0) r * 0.07f else 0f
        fillPaint.color = if (pass == 0) INK else Color.rgb(110, 220, 50)
        canvas.drawCircle(cx, cy, gr + ow, fillPaint)
        for (i in gooX.indices) {
            val x = cx + r * gooX[i]; val y = cy + r * gooY[i]
            canvas.drawRect(x - dw - ow, cy + r * 0.3f, x + dw + ow, y, fillPaint)
            canvas.drawCircle(x, y, ball + ow, fillPaint)
        }
    }
    scratchPath2.rewind(); scratchPath2.addCircle(cx, cy, gr, Path.Direction.CW)
    canvas.save(); canvas.clipPath(scratchPath2)
    addSheen(canvas, cx, cy, gr, 255, sheenAlpha = 0.26f)
    canvas.restore()
    fillPaint.color = Color.rgb(190, 248, 130)
    canvas.drawCircle(cx - r * 0.62f, cy - r * 0.10f, r * 0.07f, fillPaint)
    canvas.drawCircle(cx + r * 0.58f, cy - r * 0.40f, r * 0.05f, fillPaint)
    canvas.drawCircle(cx + r * 0.66f, cy + r * 0.12f, r * 0.08f, fillPaint)
    for (i in gooX.indices) canvas.drawCircle(cx + r * gooX[i] - ball * 0.35f, cy + r * gooY[i] - ball * 0.35f, ball * 0.28f, fillPaint)
    drawHole(canvas, cx, cy + r * HOLE_Y, r * HOLE_R, 255)
    friendEye(canvas, cx - r * 0.33f, cy + r * 0.02f, r * 0.21f, 0.06f, 0.14f)
    friendEye(canvas, cx + r * 0.33f, cy + r * 0.02f, r * 0.21f, -0.06f, 0.14f)
    friendGrin(canvas, cx, cy + r * 0.40f, r * 0.32f, r * 0.26f, tongueIn = true)
}

// Zoomy: a tennis ball in flying goggles, with speed lines streaming behind
internal fun GameView.drawZoomyFriend(canvas: Canvas, cx: Float, cy: Float, r: Float) {
    for (k in 0..2) {
        val y = cy + r * (-0.42f + k * 0.42f); val x0 = cx - r * (if (k == 1) 1.78f else 1.62f)
        outlinePaint.color = INK; outlinePaint.strokeWidth = r * 0.15f
        canvas.drawLine(x0, y, cx - r * 1.24f, y, outlinePaint)
        outlinePaint.color = Color.WHITE; outlinePaint.strokeWidth = r * 0.07f
        canvas.drawLine(x0, y, cx - r * 1.24f, y, outlinePaint)
    }
    ringBase(canvas, cx, cy, r, Color.rgb(215, 240, 40), false, 255)
    scratchPath2.rewind(); scratchPath2.addCircle(cx, cy, r, Path.Direction.CW)
    canvas.save(); canvas.clipPath(scratchPath2)
    canvas.rotate(35f, cx, cy)
    outlinePaint.color = Color.WHITE; outlinePaint.strokeWidth = r * 0.10f
    canvas.drawArc(scratchRectF.apply { set(cx - r * 1.70f, cy - r * 0.95f, cx - r * 0.45f, cy + r * 0.95f) }, -70f, 140f, false, outlinePaint)
    canvas.drawArc(scratchRectF.apply { set(cx + r * 0.45f, cy - r * 0.95f, cx + r * 1.70f, cy + r * 0.95f) }, 110f, 140f, false, outlinePaint)
    canvas.rotate(-35f, cx, cy)
    // The goggle strap round his middle
    val ey = cy - r * 0.08f
    fillPaint.color = Color.rgb(120, 65, 25)
    canvas.drawRect(cx - r, ey - r * 0.11f, cx + r, ey + r * 0.11f, fillPaint)
    canvas.restore()
    addSheen(canvas, cx, cy, r, 255, sheenAlpha = 0.22f, specAlpha = 0.8f)
    for (s in intArrayOf(-1, 1)) {
        val lx = cx + s * r * 0.34f
        fillPaint.color = INK; canvas.drawCircle(lx, ey, r * 0.34f, fillPaint)
        fillPaint.color = Color.rgb(250, 200, 50); canvas.drawCircle(lx, ey, r * 0.29f, fillPaint)
        fillPaint.color = Color.rgb(160, 220, 255); canvas.drawCircle(lx, ey, r * 0.23f, fillPaint)
        friendEye(canvas, lx, ey, r * 0.15f, 0.12f, 0f)
        outlinePaint.color = Color.argb(200, 255, 255, 255); outlinePaint.strokeWidth = r * 0.04f
        canvas.drawArc(scratchRectF.apply { set(lx - r * 0.19f, ey - r * 0.19f, lx + r * 0.19f, ey + r * 0.19f) }, 200f, 60f, false, outlinePaint)
    }
    friendGrin(canvas, cx, cy + r * 0.40f, r * 0.32f, r * 0.27f, tongueIn = true)
}

private val sugarX = floatArrayOf(-0.60f, -0.30f, 0.00f, 0.30f, 0.60f, -0.45f, -0.15f, 0.15f, 0.45f)
private val sugarY = floatArrayOf(-0.78f, -0.90f, -0.94f, -0.90f, -0.78f, -0.62f, -0.70f, -0.70f, -0.62f)

// Puff: a cherry bun dusted with powdered sugar, cheeks puffed right out
internal fun GameView.drawPuffFriend(canvas: Canvas, cx: Float, cy: Float, r: Float) {
    val body = Color.rgb(250, 85, 80)
    val chx = r * 0.80f; val chy = cy + r * 0.24f; val chr = r * 0.38f
    fillPaint.color = INK
    canvas.drawCircle(cx - chx, chy, chr + r * 0.14f, fillPaint)
    canvas.drawCircle(cx + chx, chy, chr + r * 0.14f, fillPaint)
    ringBase(canvas, cx, cy, r, body, false, 255)
    fillPaint.color = body
    canvas.drawCircle(cx - chx, chy, chr, fillPaint)
    canvas.drawCircle(cx + chx, chy, chr, fillPaint)
    scratchPath2.rewind(); scratchPath2.addCircle(cx, cy, r, Path.Direction.CW)
    canvas.save(); canvas.clipPath(scratchPath2)
    fillPaint.color = Color.rgb(255, 248, 240)       // the powdered sugar cap
    for (i in sugarX.indices) canvas.drawCircle(cx + r * sugarX[i], cy + r * sugarY[i], r * 0.24f, fillPaint)
    addSheen(canvas, cx, cy, r, 255, sheenAlpha = 0.20f)
    canvas.restore()
    fillPaint.color = Color.argb(230, 255, 255, 255)
    canvas.drawCircle(cx - r * 0.50f, cy - r * 0.30f, r * 0.05f, fillPaint)
    canvas.drawCircle(cx + r * 0.56f, cy - r * 0.26f, r * 0.04f, fillPaint)
    canvas.drawCircle(cx + r * 0.10f, cy - r * 0.40f, r * 0.04f, fillPaint)
    // A pink blush and a shine on each big cheek
    for (s in intArrayOf(-1, 1)) {
        val x = cx + s * chx
        fillPaint.color = Color.argb(200, 255, 170, 175)
        canvas.drawOval(scratchRectF.apply { set(x - chr * 0.55f, chy - chr * 0.30f, x + chr * 0.55f, chy + chr * 0.30f) }, fillPaint)
        fillPaint.color = Color.argb(200, 255, 255, 255)
        canvas.drawCircle(x - s * chr * 0.30f, chy - chr * 0.50f, chr * 0.16f, fillPaint)
    }
    friendEye(canvas, cx - r * 0.30f, cy - r * 0.10f, r * 0.20f, 0.08f, 0.08f)
    friendEye(canvas, cx + r * 0.30f, cy - r * 0.10f, r * 0.20f, -0.08f, 0.08f)
    friendGrin(canvas, cx, cy + r * 0.36f, r * 0.24f, r * 0.22f, tongueIn = true)
}

private val bubX = floatArrayOf(-0.44f, 0.04f, 0.44f, 0.80f)
private val bubY = floatArrayOf(-1.12f, -1.24f, -1.10f, -0.92f)
private val bubR = floatArrayOf(0.14f, 0.10f, 0.12f, 0.08f)

// Fizz: fizzy soda glaze, with bubbles popping up off the top of his head
internal fun GameView.drawFizzFriend(canvas: Canvas, cx: Float, cy: Float, r: Float) {
    ringBase(canvas, cx, cy, r, DOUGH, false, 255)
    glazeTop(canvas, cx, cy, r * 0.84f, Color.rgb(25, 195, 220))
    canvas.save(); canvas.clipPath(scratchPath2)
    outlinePaint.color = Color.argb(200, 225, 255, 255); outlinePaint.strokeWidth = r * 0.035f
    canvas.drawCircle(cx - r * 0.62f, cy - r * 0.05f, r * 0.08f, outlinePaint)
    canvas.drawCircle(cx + r * 0.60f, cy - r * 0.42f, r * 0.06f, outlinePaint)
    canvas.drawCircle(cx + r * 0.66f, cy + r * 0.10f, r * 0.05f, outlinePaint)
    canvas.drawCircle(cx - r * 0.40f, cy - r * 0.62f, r * 0.05f, outlinePaint)
    canvas.restore()
    drawHole(canvas, cx, cy + r * HOLE_Y, r * HOLE_R, 255)
    for (i in bubX.indices) {
        val x = cx + r * bubX[i]; val y = cy + r * bubY[i]; val br = r * bubR[i]
        fillPaint.color = INK; canvas.drawCircle(x, y, br + r * 0.05f, fillPaint)
        fillPaint.color = Color.rgb(170, 240, 255); canvas.drawCircle(x, y, br, fillPaint)
        fillPaint.color = Color.WHITE; canvas.drawCircle(x - br * 0.35f, y - br * 0.35f, br * 0.30f, fillPaint)
    }
    friendEye(canvas, cx - r * 0.33f, cy + r * 0.04f, r * 0.2f, 0.06f, -0.16f)    // looking up at the bubbles
    friendEye(canvas, cx + r * 0.33f, cy + r * 0.04f, r * 0.2f, 0.10f, -0.16f)
    friendGrin(canvas, cx, cy + r * 0.40f, r * 0.30f, r * 0.24f, tongueIn = true)
}

// Captain Glaze: shiny honey glaze, and a sailor captain's cap with a gold badge
internal fun GameView.drawCaptainFriend(canvas: Canvas, cx: Float, cy: Float, r: Float) {
    ringBase(canvas, cx, cy, r, Color.rgb(215, 130, 50), false, 255)
    glazeTop(canvas, cx, cy, r * 0.86f, Color.rgb(255, 200, 85), sheen = 0.34f)
    friendEye(canvas, cx - r * 0.33f, cy + r * 0.08f, r * 0.2f, 0.1f, 0.06f)
    friendEye(canvas, cx + r * 0.33f, cy + r * 0.08f, r * 0.2f, 0.1f, 0.06f)
    friendCheeks(canvas, cx, cy + r * 0.36f, r)
    friendGrin(canvas, cx, cy + r * 0.44f, r * 0.30f, r * 0.24f, tongueIn = true)
    val hx = cx; val hy = cy - r * 0.64f               // where the band meets the visor
    canvas.save(); canvas.rotate(-7f, hx, hy)
    val ow = r * 0.09f
    // Crown, then band, then visor: each ink first, a little bigger, then its colour
    fillPaint.color = INK
    canvas.drawOval(scratchRectF.apply { set(hx - r * 0.82f - ow, hy - r * 0.60f - ow, hx + r * 0.82f + ow, hy - r * 0.12f + ow) }, fillPaint)
    canvas.drawRect(hx - r * 0.62f - ow, hy - r * 0.30f - ow, hx + r * 0.62f + ow, hy + ow, fillPaint)
    canvas.drawArc(scratchRectF.apply { set(hx - r * 0.70f - ow, hy - r * 0.22f - ow, hx + r * 0.70f + ow, hy + r * 0.30f + ow) }, 0f, 180f, true, fillPaint)
    fillPaint.color = Color.WHITE
    canvas.drawOval(scratchRectF.apply { set(hx - r * 0.82f, hy - r * 0.60f, hx + r * 0.82f, hy - r * 0.12f) }, fillPaint)
    fillPaint.color = Color.rgb(30, 50, 140)
    canvas.drawRect(hx - r * 0.62f, hy - r * 0.30f, hx + r * 0.62f, hy, fillPaint)
    fillPaint.color = Color.rgb(30, 30, 36)
    canvas.drawArc(scratchRectF.apply { set(hx - r * 0.70f, hy - r * 0.22f, hx + r * 0.70f, hy + r * 0.30f) }, 0f, 180f, true, fillPaint)
    outlinePaint.color = Color.argb(140, 255, 255, 255); outlinePaint.strokeWidth = r * 0.04f
    canvas.drawArc(scratchRectF.apply { set(hx - r * 0.55f, hy - r * 0.12f, hx + r * 0.55f, hy + r * 0.20f) }, 30f, 60f, false, outlinePaint)
    fillPaint.color = Color.argb(200, 255, 255, 255)
    canvas.drawOval(scratchRectF.apply { set(hx - r * 0.55f, hy - r * 0.55f, hx - r * 0.15f, hy - r * 0.41f) }, fillPaint)
    // The gold badge on the band's top edge
    val bx = hx; val by = hy - r * 0.30f
    fillPaint.color = INK; canvas.drawCircle(bx, by, r * 0.19f, fillPaint)
    fillPaint.color = Color.rgb(255, 205, 40); canvas.drawCircle(bx, by, r * 0.14f, fillPaint)
    fillPaint.color = Color.rgb(255, 245, 190); canvas.drawCircle(bx - r * 0.04f, by - r * 0.04f, r * 0.05f, fillPaint)
    canvas.restore()
}
