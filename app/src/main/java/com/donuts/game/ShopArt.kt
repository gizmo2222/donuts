package com.donuts.game

import android.graphics.*
import kotlin.math.PI
import kotlin.math.cos
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
    private val glazes = intArrayOf(Color.rgb(255, 105, 160), Color.rgb(125, 65, 30), Color.rgb(255, 200, 40))

    /** Draws the shop with [earned] pieces into [box]. [t] is seconds for the motion; [still] holds every piece still. */
    fun draw(c: Canvas, box: RectF, earned: Int, t: Float, still: Boolean) {
        if (box.width() <= 0f || box.height() <= 0f) return
        val h = box.height()
        ink.strokeWidth = h * 0.025f
        drawBase(c, box)
        for (piece in ShopRules.shown(earned)) {
            val s = spot(box, piece)
            when (piece) {
                ShopPiece.SIGN -> drawSign(c, s)
                ShopPiece.AWNING -> drawAwning(c, s)
                ShopPiece.WINDOW_DONUTS -> drawWindowDonuts(c, s)
                ShopPiece.LAMP -> drawLamp(c, s, if (still) 1f else 0.75f + 0.25f * sin(t * 2.4f))
                ShopPiece.BALL -> drawBall(c, s)
                ShopPiece.ROOF_DONUT -> drawRoofDonut(c, s, if (still) 0f else sin(t * 2f))
            }
        }
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
}
