package com.donuts.game

import android.graphics.*
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Steven's donut shop on the home screen: the bare shop (wall, door, window, pavement) and the pieces lifetime
 * donuts have earned (see [ShopRules]), each drawn inside its [ShopPiece] spot of the scene box.
 * Thick brown outlines and warm saturated colours, like the rest of the game. Gentle motion unless [draw] is still.
 */
class ShopScene(private val typeface: Typeface) {

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeJoin = Paint.Join.ROUND; strokeCap = Paint.Cap.ROUND
    }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; this.typeface = typeface; isFakeBoldText = true }
    private val path = Path()
    private val r = RectF()

    private val outline = Color.rgb(60, 25, 0)
    private val wall = Color.rgb(255, 140, 105)
    private val roofBand = Color.rgb(120, 55, 20)
    private val door = Color.rgb(0, 165, 170)
    private val glass = Color.rgb(175, 230, 255)
    private val pavement = Color.rgb(235, 175, 110)
    private val signYellow = Color.rgb(255, 205, 40)
    private val awningRed = Color.rgb(230, 40, 85)
    private val potClay = Color.rgb(205, 95, 45)
    private val petals = intArrayOf(Color.rgb(255, 105, 160), Color.rgb(150, 80, 230), Color.rgb(255, 140, 0), Color.rgb(230, 40, 85))
    private val balloonColours = intArrayOf(Color.rgb(230, 40, 85), Color.rgb(255, 205, 40), Color.rgb(0, 160, 235))
    private val glazes = intArrayOf(Color.rgb(255, 105, 160), Color.rgb(125, 65, 30), Color.rgb(255, 200, 40))

    /**
     * Draws the shop with [earned] pieces into [box]. [t] is seconds for the motion; [still] holds every piece still.
     * While [pop] is below 1 the last piece is being revealed: it springs up out of its spot with a burst of
     * sparkles, or, when [still], stands at full size in a fading gold glow.
     */
    fun draw(c: Canvas, box: RectF, earned: Int, t: Float, still: Boolean, pop: Float = 1f) {
        if (box.width() <= 0f || box.height() <= 0f) return
        val h = box.height()
        ink.strokeWidth = h * 0.025f
        drawBase(c, box)
        val pieces = ShopRules.shown(earned)
        for ((i, piece) in pieces.withIndex()) {
            val s = spot(box, piece)
            if (pop < 1f && i == pieces.size - 1) {
                c.save(); c.clipRect(box)
                drawPopping(c, s, piece, t, still, pop.coerceAtLeast(0f))
                c.restore()
            } else drawPiece(c, s, piece, t, still)
        }
    }

    private fun drawPiece(c: Canvas, s: RectF, piece: ShopPiece, t: Float, still: Boolean) {
        when (piece) {
            ShopPiece.SIGN -> drawSign(c, s)
            ShopPiece.AWNING -> drawAwning(c, s)
            ShopPiece.WINDOW_DONUTS -> drawWindowDonuts(c, s)
            ShopPiece.LAMP -> drawLamp(c, s, if (still) 1f else 0.75f + 0.25f * sin(t * 2.4f))
            ShopPiece.BALL -> drawBall(c, s)
            ShopPiece.ROOF_DONUT -> drawRoofDonut(c, s, if (still) 0f else sin(t * 2f))
            ShopPiece.FLOWERS -> drawFlowers(c, s)
            ShopPiece.BASKETBALL -> drawBasketball(c, s)
            ShopPiece.BALLOONS -> drawBalloons(c, s, if (still) 0f else t)
            ShopPiece.SUN -> drawSun(c, s, if (still) 0f else t * 0.5f)
        }
    }

    /** A new piece arriving: a gold glow over its spot, the piece springing up from its foot, sparkles flying out. */
    private fun drawPopping(c: Canvas, s: RectF, piece: ShopPiece, t: Float, still: Boolean, pop: Float) {
        val sw = ink.strokeWidth
        r.set(s.left - sw, s.top - sw, s.right + sw, s.bottom + sw)
        fill.color = Color.argb((160 * (1f - pop)).toInt(), 255, 215, 60)
        c.drawOval(r, fill)
        if (still) { drawPiece(c, s, piece, t, true); return }
        val grow = easeOutBack((pop / 0.5f).coerceAtMost(1f))
        if (grow > 0.01f) {
            c.save()
            c.scale(grow, grow, s.centerX(), s.bottom)
            drawPiece(c, s, piece, t, false)
            c.restore()
        }
        val q = ((pop - 0.08f) / 0.92f).coerceIn(0f, 1f)
        if (q <= 0f || q >= 1f) return
        val k = 0.45f + 0.6f * q
        val size = max(min(s.width(), s.height()) * 0.16f, sw * 1.8f) * (1f - 0.4f * q)
        val alpha = (255 * (1f - q * q)).toInt()
        for (n in 0 until 8) {
            val a = (n * PI / 4 + 0.4).toFloat()
            sparkle(c, s.centerX() + cos(a) * s.width() / 2f * k, s.centerY() + sin(a) * s.height() / 2f * k,
                size, if (n % 2 == 0) signYellow else Color.WHITE, alpha)
        }
    }

    /** A chunky four-pointed sparkle. */
    private fun sparkle(c: Canvas, x: Float, y: Float, size: Float, color: Int, alpha: Int) {
        val n = size * 0.28f
        path.rewind()
        path.moveTo(x, y - size); path.quadTo(x + n, y - n, x + size, y); path.quadTo(x + n, y + n, x, y + size)
        path.quadTo(x - n, y + n, x - size, y); path.quadTo(x - n, y - n, x, y - size); path.close()
        fill.color = color; fill.alpha = alpha; c.drawPath(path, fill)
        val old = ink.strokeWidth; ink.strokeWidth = old * 0.5f
        ink.color = outline; ink.alpha = alpha; c.drawPath(path, ink)
        ink.strokeWidth = old; ink.alpha = 255; fill.alpha = 255
    }

    private fun easeOutBack(x: Float): Float {
        val k = 1.70158f; val y = x - 1f
        return 1f + (k + 1f) * y * y * y + k * y * y
    }

    /** [piece]'s spot in pixels inside [box]. */
    fun spot(box: RectF, piece: ShopPiece): RectF = RectF(
        box.left + box.width() * piece.left, box.top + box.height() * piece.top,
        box.left + box.width() * piece.right, box.top + box.height() * piece.bottom)

    private fun x(box: RectF, f: Float) = box.left + box.width() * f
    private fun y(box: RectF, f: Float) = box.top + box.height() * f

    private fun outlined(c: Canvas, rect: RectF, color: Int, corner: Float) {
        fill.color = color; c.drawRoundRect(rect, corner, corner, fill)
        ink.color = outline; c.drawRoundRect(rect, corner, corner, ink)
    }

    private fun drawBase(c: Canvas, box: RectF) {
        val h = box.height(); val sw = ink.strokeWidth
        // Pavement across the whole box
        r.set(box.left + sw, y(box, ShopRules.GROUND_TOP), box.right - sw, box.bottom - sw)
        outlined(c, r, pavement, h * 0.04f)
        // Wall and its chocolate roof band
        r.set(x(box, ShopRules.SHOP_LEFT), y(box, ShopRules.SHOP_TOP), x(box, ShopRules.SHOP_RIGHT), y(box, ShopRules.GROUND_TOP))
        outlined(c, r, wall, h * 0.03f)
        r.set(x(box, ShopRules.SHOP_LEFT), y(box, ShopRules.SHOP_TOP), x(box, ShopRules.SHOP_RIGHT), y(box, 0.30f))
        outlined(c, r, roofBand, h * 0.03f)
        // Shop window and door
        r.set(x(box, 0.36f), y(box, 0.60f), x(box, 0.52f), y(box, 0.82f))
        outlined(c, r, glass, h * 0.02f)
        r.set(x(box, 0.56f), y(box, 0.58f), x(box, 0.66f), y(box, ShopRules.GROUND_TOP))
        outlined(c, r, door, h * 0.02f)
        fill.color = signYellow
        c.drawCircle(x(box, 0.585f), y(box, 0.75f), h * 0.022f, fill)
    }

    private fun drawSign(c: Canvas, s: RectF) {
        r.set(s); r.inset(s.width() * 0.02f, s.height() * 0.08f)
        outlined(c, r, signYellow, s.height() * 0.25f)
        val label = "STEVEN'S"
        text.color = outline
        text.textSize = r.height() * 0.62f
        val fit = r.width() * 0.86f
        val wide = text.measureText(label)
        if (wide > fit) text.textSize *= fit / wide
        val fm = text.fontMetrics
        c.drawText(label, r.centerX(), r.centerY() - (fm.ascent + fm.descent) / 2f, text)
    }

    private fun drawAwning(c: Canvas, s: RectF) {
        val stripes = 7
        val sw = ink.strokeWidth
        val left = s.left + sw; val right = s.right - sw
        val top = s.top + sw; val hem = s.top + s.height() * 0.62f
        val bump = s.bottom - sw - hem
        val step = (right - left) / stripes
        for (i in 0 until stripes) {
            val l = left + i * step
            path.rewind()
            path.moveTo(l, top); path.lineTo(l + step, top); path.lineTo(l + step, hem)
            path.quadTo(l + step / 2f, hem + bump * 2f, l, hem); path.close()
            fill.color = if (i % 2 == 0) awningRed else Color.WHITE
            c.drawPath(path, fill)
        }
        path.rewind()
        path.moveTo(left, hem); path.lineTo(left, top); path.lineTo(right, top); path.lineTo(right, hem)
        for (i in stripes - 1 downTo 0) {
            val l = left + i * step
            path.quadTo(l + step / 2f, hem + bump * 2f, l, hem)
        }
        path.close()
        ink.color = outline; c.drawPath(path, ink)
    }

    private fun drawWindowDonuts(c: Canvas, s: RectF) {
        val rad = min(s.width() / 6.6f, s.height() * 0.36f)
        val cy = s.bottom - rad - ink.strokeWidth * 1.2f
        // A little tray under them
        r.set(s.left + ink.strokeWidth, cy + rad * 0.55f, s.right - ink.strokeWidth, s.bottom - ink.strokeWidth * 0.5f)
        fill.color = Color.WHITE; c.drawRoundRect(r, rad * 0.2f, rad * 0.2f, fill)
        for (i in 0 until 3) {
            val cx = s.left + s.width() * (i + 0.5f) / 3f
            donut(c, cx, cy, rad, glazes[i], sprinkles = i == 0)
        }
    }

    /** A small glazed donut: dough ring, glaze, hole, an outline round it all. */
    private fun donut(c: Canvas, cx: Float, cy: Float, rad: Float, glaze: Int, sprinkles: Boolean) {
        val sw = ink.strokeWidth * 0.8f
        fill.color = Color.rgb(240, 180, 105); c.drawCircle(cx, cy, rad - sw / 2f, fill)
        fill.color = glaze; c.drawCircle(cx, cy, rad * 0.80f, fill)
        if (sprinkles) {
            val colours = intArrayOf(Color.WHITE, Color.rgb(255, 230, 0), Color.rgb(0, 180, 255), Color.rgb(120, 220, 80))
            ink.color = Color.WHITE
            val old = ink.strokeWidth; ink.strokeWidth = rad * 0.09f
            for (k in 0 until 8) {
                val a = (k * PI / 4 + 0.3).toFloat(); val d = rad * 0.60f
                val px = cx + cos(a) * d; val py = cy + sin(a) * d
                ink.color = colours[k % colours.size]
                c.drawLine(px - cos(a + 1.2f) * rad * 0.08f, py - sin(a + 1.2f) * rad * 0.08f,
                    px + cos(a + 1.2f) * rad * 0.08f, py + sin(a + 1.2f) * rad * 0.08f, ink)
            }
            ink.strokeWidth = old
        }
        fill.color = glass; c.drawCircle(cx, cy, rad * 0.30f, fill)
        ink.color = outline
        val old = ink.strokeWidth; ink.strokeWidth = sw
        c.drawCircle(cx, cy, rad - sw / 2f, ink)
        c.drawCircle(cx, cy, rad * 0.30f, ink)
        ink.strokeWidth = old
    }

    private fun drawLamp(c: Canvas, s: RectF, glow: Float) {
        val cx = s.centerX(); val h = s.height(); val sw = ink.strokeWidth
        val headR = min(s.width() * 0.22f, h * 0.075f)
        val headY = s.top + s.width() * 0.48f
        // Warm glow
        val glowR = min(s.width() / 2f, headY - s.top) - 1f
        fill.color = Color.argb((110 * glow).toInt(), 255, 220, 80)
        c.drawCircle(cx, headY, glowR, fill)
        // Post and foot
        r.set(cx - sw * 1.1f, headY, cx + sw * 1.1f, s.bottom - sw * 2f)
        outlined(c, r, Color.rgb(70, 50, 110), sw)
        r.set(cx - headR * 1.3f, s.bottom - sw * 3.2f, cx + headR * 1.3f, s.bottom - sw * 0.6f)
        outlined(c, r, Color.rgb(70, 50, 110), sw)
        // Lamp head
        fill.color = Color.rgb(255, 235, 120); c.drawCircle(cx, headY, headR, fill)
        ink.color = outline; c.drawCircle(cx, headY, headR, ink)
        r.set(cx - headR * 1.2f, headY - headR * 1.55f, cx + headR * 1.2f, headY - headR * 0.75f)
        outlined(c, r, Color.rgb(70, 50, 110), headR * 0.3f)
    }

    private fun drawBall(c: Canvas, s: RectF) {
        val rad = min(s.width(), s.height()) / 2f - ink.strokeWidth
        val cx = s.centerX(); val cy = s.bottom - rad - ink.strokeWidth
        fill.color = Color.WHITE; c.drawCircle(cx, cy, rad, fill)
        c.save()
        path.rewind(); path.addCircle(cx, cy, rad, Path.Direction.CW); c.clipPath(path)
        fill.color = Color.rgb(22, 22, 22)
        pentagon(c, cx, cy, rad * 0.36f, -90f)
        for (i in 0 until 5) {
            val a = Math.toRadians(i * 72.0 - 90.0)
            pentagon(c, cx + (rad * 0.92f * cos(a)).toFloat(), cy + (rad * 0.92f * sin(a)).toFloat(), rad * 0.30f, i * 72f + 90f)
        }
        c.restore()
        ink.color = outline; c.drawCircle(cx, cy, rad, ink)
    }

    private fun pentagon(c: Canvas, cx: Float, cy: Float, rad: Float, startDeg: Float) {
        path.rewind()
        for (i in 0 until 5) {
            val a = Math.toRadians((startDeg + i * 72f).toDouble())
            val px = cx + (rad * cos(a)).toFloat(); val py = cy + (rad * sin(a)).toFloat()
            if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
        }
        path.close(); c.drawPath(path, fill)
    }

    /** The giant donut standing on the roof; [bob] (-1..1) lifts it a little. */
    private fun drawRoofDonut(c: Canvas, s: RectF, bob: Float) {
        val sw = ink.strokeWidth
        val rad = min(s.width() / 2f, s.height() * 0.44f) - sw
        val cx = s.centerX()
        val cy = s.bottom - rad - sw * 1.6f - (1f + bob) * s.height() * 0.025f
        // A stubby stand down to the roof
        r.set(cx - sw * 1.2f, cy + rad * 0.6f, cx + sw * 1.2f, s.bottom - sw / 2f)
        outlined(c, r, roofBand, sw * 0.6f)
        donut(c, cx, cy, rad, glazes[0], sprinkles = true)
    }

    /** A clay flower box under the window, four big flowers poking out of it. */
    private fun drawFlowers(c: Canvas, s: RectF) {
        val w = s.width(); val h = s.height(); val sw = ink.strokeWidth
        val pr = min(h * 0.22f, w * 0.08f)
        val headY = s.top + pr * 1.62f + sw * 0.5f   // the top petal's outline stays inside the spot
        for (i in 0 until 4) {
            val fx = s.left + w * (0.17f + i * 0.22f)
            // Brown discs first, the coloured ones a little smaller on top: one outline round the whole flower
            for (pass in 0..1) {
                fill.color = if (pass == 0) outline else petals[i]
                val pad = if (pass == 0) sw * 0.45f else -sw * 0.05f
                for (k in 0 until 5) {
                    val a = (k * 2 * PI / 5 - PI / 2).toFloat()
                    c.drawCircle(fx + cos(a) * pr, headY + sin(a) * pr, pr * 0.62f + pad, fill)
                }
            }
            fill.color = signYellow; c.drawCircle(fx, headY, pr * 0.5f, fill)
        }
        r.set(s.left + sw / 2f, s.top + h * 0.50f, s.right - sw / 2f, s.bottom - sw / 2f)
        outlined(c, r, potClay, h * 0.12f)
    }

    /** An orange basketball beside the soccer ball (Steven likes sports balls). */
    private fun drawBasketball(c: Canvas, s: RectF) {
        val rad = min(s.width(), s.height()) / 2f - ink.strokeWidth
        val cx = s.centerX(); val cy = s.bottom - rad - ink.strokeWidth
        fill.color = Color.rgb(255, 120, 20); c.drawCircle(cx, cy, rad, fill)
        c.save()
        path.rewind(); path.addCircle(cx, cy, rad, Path.Direction.CW); c.clipPath(path)
        val old = ink.strokeWidth; ink.strokeWidth = old * 0.55f; ink.color = outline
        c.drawLine(cx, cy - rad, cx, cy + rad, ink)
        c.drawLine(cx - rad, cy, cx + rad, cy, ink)
        r.set(cx - rad * 1.75f, cy - rad, cx - rad * 0.45f, cy + rad); c.drawOval(r, ink)
        r.set(cx + rad * 0.45f, cy - rad, cx + rad * 1.75f, cy + rad); c.drawOval(r, ink)
        ink.strokeWidth = old
        c.restore()
        ink.color = outline; c.drawCircle(cx, cy, rad, ink)
    }

    /** Three balloons on strings tied at the spot's foot, each bobbing on its own beat ([t] 0 holds them still). */
    private fun drawBalloons(c: Canvas, s: RectF, t: Float) {
        val w = s.width(); val h = s.height(); val sw = ink.strokeWidth
        val at = arrayOf(floatArrayOf(0.27f, 0.33f), floatArrayOf(0.50f, 0.24f), floatArrayOf(0.73f, 0.35f))
        val rx = w * 0.19f; val ry = min(h * 0.16f, rx * 1.25f)
        val knotX = s.centerX(); val knotY = s.bottom - sw
        for ((i, b) in at.withIndex()) {
            val bob = if (t == 0f) 0f else sin(t * 1.7f + i * 2.1f) * h * 0.02f
            val bx = s.left + w * b[0]; val by = s.top + h * b[1] + bob
            path.rewind(); path.moveTo(bx, by + ry + sw)
            path.quadTo(bx + w * 0.06f, (by + ry + knotY) / 2f, knotX, knotY)
            val old = ink.strokeWidth; ink.strokeWidth = sw * 0.45f; ink.color = outline
            c.drawPath(path, ink); ink.strokeWidth = old
            r.set(bx - rx, by - ry, bx + rx, by + ry)
            fill.color = balloonColours[i]; c.drawOval(r, fill)
            fill.color = Color.argb(150, 255, 255, 255)
            r.set(bx - rx * 0.6f, by - ry * 0.7f, bx - rx * 0.2f, by - ry * 0.15f); c.drawOval(r, fill)
            r.set(bx - rx, by - ry, bx + rx, by + ry)
            ink.color = outline; c.drawOval(r, ink)
            path.rewind(); path.moveTo(bx, by + ry); path.lineTo(bx - sw * 0.9f, by + ry + sw * 1.1f)
            path.lineTo(bx + sw * 0.9f, by + ry + sw * 1.1f); path.close()
            fill.color = balloonColours[i]; c.drawPath(path, fill)
            ink.strokeWidth = sw * 0.5f; c.drawPath(path, ink); ink.strokeWidth = sw
        }
    }

    /** A smiling sun whose rays turn slowly ([spin] in radians, 0 holds them still). */
    private fun drawSun(c: Canvas, s: RectF, spin: Float) {
        val sw = ink.strokeWidth
        val rad = min(s.width(), s.height()) * 0.27f
        val cx = s.centerX(); val cy = s.centerY()
        for (pass in 0..1) {
            ink.strokeWidth = if (pass == 0) sw * 1.6f else sw * 0.7f
            ink.color = if (pass == 0) outline else Color.rgb(255, 150, 0)
            for (k in 0 until 8) {
                val a = spin + (k * PI / 4).toFloat()
                c.drawLine(cx + cos(a) * rad * 1.25f, cy + sin(a) * rad * 1.25f,
                    cx + cos(a) * rad * 1.55f, cy + sin(a) * rad * 1.55f, ink)
            }
        }
        ink.strokeWidth = sw
        fill.color = Color.rgb(255, 205, 0); c.drawCircle(cx, cy, rad, fill)
        ink.color = outline; c.drawCircle(cx, cy, rad, ink)
        fill.color = outline
        c.drawCircle(cx - rad * 0.36f, cy - rad * 0.18f, rad * 0.13f, fill)
        c.drawCircle(cx + rad * 0.36f, cy - rad * 0.18f, rad * 0.13f, fill)
        r.set(cx - rad * 0.48f, cy - rad * 0.25f, cx + rad * 0.48f, cy + rad * 0.52f)
        ink.strokeWidth = sw * 0.6f; c.drawArc(r, 25f, 130f, false, ink); ink.strokeWidth = sw
    }
}
