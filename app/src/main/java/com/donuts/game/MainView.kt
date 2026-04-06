package com.donuts.game

import android.content.Context
import android.graphics.*
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import kotlin.math.*

class MainView(context: Context, private val onPlay: () -> Unit) : View(context) {

    private var w = 0f
    private var h = 0f
    private var logoCX  = 0f
    private var logoCY  = 0f
    private var logoR   = 0f
    private var playRect = RectF()

    private var playPressMs = -1L
    private val PRESS_MS    = 140L
    private val startMs     = SystemClock.elapsedRealtime()

    // Colours
    private val bgTop       = Color.rgb(255, 248, 232)
    private val bgBot       = Color.rgb(255, 225, 185)
    private val brownDark   = Color.rgb( 60,  25,   0)
    private val warmPink    = Color.rgb(230,  40,  85)
    private val caramel     = Color.rgb(175,  85,   0)
    private val donutBody   = Color.rgb(255, 145, 170)
    private val donutGlaze  = Color.rgb(230,  40,  85)
    private val holeClr     = Color.rgb(255, 248, 232)

    // Paints
    private val fillP   = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val strokeP = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val textP   = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; typeface = Typeface.DEFAULT_BOLD }
    private val bgPaint = Paint()

    init { postInvalidateOnAnimation() }

    override fun onSizeChanged(W: Int, H: Int, oW: Int, oH: Int) {
        w = W.toFloat(); h = H.toFloat()
        logoR   = min(w, h) * 0.20f
        logoCX  = w / 2f
        // Logo sits at 34% — gives the title+button area room to breathe below
        logoCY  = h * 0.34f
        // Title clears logo bottom: logoCY + logoR*1.82 ensures text-top clears logo circle
        val line1Y = logoCY + logoR * 1.82f
        val line2Y = line1Y + logoR * 0.56f
        // Button anchored below subtitle — not hardcoded to screen height
        val btnW = min(w * 0.62f, 320f)
        val btnH = 90f
        val btnY = (line2Y + logoR * 0.72f).coerceAtMost(h * 0.76f)
        playRect = RectF(w / 2f - btnW / 2f, btnY, w / 2f + btnW / 2f, btnY + btnH)

        // Gradient background
        bgPaint.shader = LinearGradient(0f, 0f, 0f, h, bgTop, bgBot, Shader.TileMode.CLAMP)
    }

    override fun onDraw(canvas: Canvas) {
        val now     = SystemClock.elapsedRealtime()
        val elapsed = (now - startMs) / 1000f

        canvas.drawRect(0f, 0f, w, h, bgPaint)
        drawDotGrid(canvas)

        // Bigger, livelier bounce
        val bounce = sin(elapsed * 2.2f) * logoR * 0.055f
        drawLogo(canvas, logoCX, logoCY + bounce)

        drawTitle(canvas, elapsed)
        drawPlayButton(canvas, buttonPressScale(now))
        drawSubtitle(canvas)

        postInvalidateOnAnimation()
    }

    // -----------------------------------------------------------------------
    // Background dot grid
    // -----------------------------------------------------------------------
    private fun drawDotGrid(canvas: Canvas) {
        fillP.color = Color.argb(22, 160, 80, 10)
        val sp = 38f
        var y = sp
        while (y < h) {
            var x = sp
            while (x < w) { canvas.drawCircle(x, y, 2.5f, fillP); x += sp }
            y += sp
        }
    }

    // -----------------------------------------------------------------------
    // Logo: donut left, soccer ball right, slightly overlapping
    // -----------------------------------------------------------------------
    private fun drawLogo(canvas: Canvas, cx: Float, cy: Float) {
        val r      = logoR
        val gap    = r * 0.18f          // overlap between donut and ball
        val donutCX = cx - r * 0.55f
        val ballCX  = cx + r * 0.55f

        // Unified drop shadow beneath both
        fillP.color = Color.argb(40, 0, 0, 0)
        canvas.drawOval(RectF(donutCX - r * 1.1f, cy + r * 0.88f,
                              ballCX  + r * 1.1f, cy + r * 1.10f), fillP)

        // Draw donut behind ball (ball is on the right, slightly overlapping)
        drawDonut(canvas, donutCX, cy, r)
        drawSoccerBall(canvas, ballCX, cy, r * 0.82f)
    }

    // -----------------------------------------------------------------------
    // Donut
    // -----------------------------------------------------------------------
    private fun drawDonut(canvas: Canvas, cx: Float, cy: Float, r: Float) {
        val ow = r * 0.13f

        // Dark outer outline
        fillP.color = Color.argb(220, 28, 12, 0)
        canvas.drawCircle(cx, cy, r + ow, fillP)
        // Drop shadow
        fillP.color = Color.argb(35, 0, 0, 0)
        canvas.drawCircle(cx + r * 0.05f, cy + r * 0.10f, r, fillP)
        // Body
        fillP.color = donutBody; fillP.alpha = 255
        canvas.drawCircle(cx, cy, r, fillP)
        // Glaze
        fillP.color = donutGlaze
        canvas.drawCircle(cx, cy, r * 0.82f, fillP)
        // 3D sheen on glaze
        addSheen(canvas, cx, cy, r * 0.82f, 255)
        // Sprinkles
        fillP.color = Color.WHITE
        val glazeR = r * 0.82f
        val dotR   = glazeR * 0.09f
        val dist   = glazeR * 0.50f
        for (i in 0 until 5) {
            val a = Math.toRadians(i * 72.0 + 15.0)
            canvas.drawCircle(cx + dist * cos(a).toFloat(), cy + dist * sin(a).toFloat(), dotR, fillP)
        }
        // Hole
        fillP.color = holeClr
        canvas.drawCircle(cx, cy, r * 0.36f, fillP)
        // Hole rim light
        strokeP.color = Color.argb(70, 255, 200, 150); strokeP.strokeWidth = r * 0.04f
        canvas.drawCircle(cx, cy, r * 0.36f, strokeP)
        // Hole outline
        strokeP.color = Color.argb(60, 28, 12, 0); strokeP.strokeWidth = r * 0.04f
        canvas.drawCircle(cx, cy, r * 0.38f, strokeP)
    }

    // -----------------------------------------------------------------------
    // Soccer ball
    // -----------------------------------------------------------------------
    private fun drawSoccerBall(canvas: Canvas, cx: Float, cy: Float, r: Float) {
        val ow = r * 0.13f

        // Dark outer outline
        fillP.color = Color.argb(220, 28, 12, 0)
        canvas.drawCircle(cx, cy, r + ow, fillP)
        // Drop shadow
        fillP.color = Color.argb(35, 0, 0, 0)
        canvas.drawCircle(cx + r * 0.05f, cy + r * 0.10f, r, fillP)
        // White base
        fillP.color = Color.WHITE; fillP.alpha = 255
        canvas.drawCircle(cx, cy, r, fillP)

        // --- Classic soccer ball patches ---
        // Clip everything to the ball circle
        val ballClip = Path().apply { addCircle(cx, cy, r, Path.Direction.CW) }
        canvas.save()
        canvas.clipPath(ballClip)

        fillP.color = Color.argb(255, 22, 22, 22)

        // Central pentagon (pointing up) — 5 vertices
        canvas.drawPath(regularPolygon(cx, cy, r * 0.34f, 5, -90f), fillP)

        // 5 pentagons around the equator, connected to edges of central one
        // Each is offset 72° apart, shifted outward
        for (i in 0 until 5) {
            val angleDeg = i * 72f - 90f
            val rad      = Math.toRadians(angleDeg.toDouble())
            val px       = cx + (r * 0.62f * cos(rad)).toFloat()
            val py       = cy + (r * 0.62f * sin(rad)).toFloat()
            // Rotate each pentagon so a flat edge faces the centre
            canvas.drawPath(regularPolygon(px, py, r * 0.28f, 5, angleDeg + 180f), fillP)
        }

        // 5 more partial pentagons near the bottom pole
        for (i in 0 until 5) {
            val angleDeg = i * 72f - 54f
            val rad      = Math.toRadians(angleDeg.toDouble())
            val px       = cx + (r * 0.90f * cos(rad)).toFloat()
            val py       = cy + (r * 0.90f * sin(rad)).toFloat()
            canvas.drawPath(regularPolygon(px, py, r * 0.28f, 5, angleDeg + 180f), fillP)
        }

        canvas.restore()

        // Thin seam lines (dark stroke, clipped to ball)
        canvas.save()
        canvas.clipPath(ballClip)
        strokeP.color = Color.argb(80, 22, 22, 22); strokeP.strokeWidth = r * 0.025f
        // 5 seam lines from centre pentagon to equator pentagons
        for (i in 0 until 5) {
            val a1 = Math.toRadians((i * 72f - 90f).toDouble())
            val a2 = Math.toRadians((i * 72f - 90f + 36f).toDouble())
            canvas.drawLine(
                cx + (r * 0.34f * cos(a1)).toFloat(), cy + (r * 0.34f * sin(a1)).toFloat(),
                cx + (r * 0.62f * cos(a2)).toFloat(), cy + (r * 0.62f * sin(a2)).toFloat(),
                strokeP
            )
        }
        canvas.restore()

        // 3D sheen
        addSheen(canvas, cx, cy, r, 255, sheenAlpha = 0.30f, specAlpha = 0.90f)
    }

    /** Builds a regular n-gon path centred at (cx,cy) with given outer radius and start angle. */
    private fun regularPolygon(cx: Float, cy: Float, r: Float, sides: Int, startDeg: Float): Path {
        val path = Path()
        for (i in 0 until sides) {
            val a = Math.toRadians((startDeg + i * (360f / sides)).toDouble())
            val x = cx + (r * cos(a)).toFloat()
            val y = cy + (r * sin(a)).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        return path
    }

    // -----------------------------------------------------------------------
    // 3D sheen (shared with pieces)
    // -----------------------------------------------------------------------
    private fun addSheen(canvas: Canvas, cx: Float, cy: Float, r: Float, alpha: Int,
                         sheenAlpha: Float = 0.32f, specAlpha: Float = 0.80f) {
        val hlPath = Path()
        hlPath.addOval(RectF(cx - r * 0.88f, cy - r * 1.05f, cx + r * 0.52f, cy + r * 0.05f), Path.Direction.CW)
        canvas.save()
        canvas.clipPath(hlPath)
        fillP.color = Color.argb((alpha * sheenAlpha).toInt(), 255, 255, 255)
        canvas.drawRect(cx - r * 2f, cy - r * 2f, cx + r * 2f, cy + r * 2f, fillP)
        canvas.restore()
        fillP.color = Color.argb((alpha * specAlpha).toInt(), 255, 255, 255)
        canvas.drawCircle(cx - r * 0.30f, cy - r * 0.44f, r * 0.16f, fillP)
        fillP.alpha = 255
    }

    // -----------------------------------------------------------------------
    // Title
    // -----------------------------------------------------------------------
    private fun drawTitle(canvas: Canvas, elapsed: Float) {
        val line1Y = logoCY + logoR * 1.82f
        val line2Y = line1Y + logoR * 0.60f

        // "Donuts" — app name, each letter slightly wiggles up/down
        val sz1 = logoR * 0.70f
        textP.textSize = sz1; textP.textAlign = Paint.Align.LEFT
        val word1 = "Donuts"
        // Measure full width to center
        var totalW = 0f
        word1.forEach { ch -> totalW += textP.measureText(ch.toString()) }
        var charX = w / 2f - totalW / 2f
        for ((i, ch) in word1.withIndex()) {
            val waveY = sin(elapsed * 3.0f + i * 0.7f) * logoR * 0.07f
            textP.color = Color.argb(90, 0, 0, 0)
            canvas.drawText(ch.toString(), charX + 2f, line1Y + waveY + 2f, textP)
            textP.color = brownDark
            canvas.drawText(ch.toString(), charX, line1Y + waveY, textP)
            charX += textP.measureText(ch.toString())
        }

        // "for Steven" — subtitle with gentle wave
        val sz2 = logoR * 0.46f
        textP.textSize = sz2; textP.textAlign = Paint.Align.LEFT
        val word2 = "for Steven"
        var totalW2 = 0f
        word2.forEach { ch -> totalW2 += textP.measureText(ch.toString()) }
        var charX2 = w / 2f - totalW2 / 2f
        for ((i, ch) in word2.withIndex()) {
            val waveY = sin(elapsed * 2.4f + i * 0.55f + 1.2f) * logoR * 0.05f
            textP.color = Color.argb(85, 0, 0, 0)
            canvas.drawText(ch.toString(), charX2 + 2f, line2Y + waveY + 2f, textP)
            textP.color = caramel
            canvas.drawText(ch.toString(), charX2, line2Y + waveY, textP)
            charX2 += textP.measureText(ch.toString())
        }
    }

    // -----------------------------------------------------------------------
    // Play button
    // -----------------------------------------------------------------------
    private fun buttonPressScale(now: Long): Float {
        if (playPressMs < 0) return 1f
        val t = ((now - playPressMs).toFloat() / PRESS_MS).coerceIn(0f, 1f)
        val x = 1f - t
        return 0.86f + 0.14f * (1f - x * x * x * x * x)
    }

    private fun drawPlayButton(canvas: Canvas, scale: Float) {
        val rx = 24f
        canvas.save()
        canvas.scale(scale, scale, playRect.centerX(), playRect.centerY())

        fillP.color = Color.argb(80, 0, 0, 0)
        canvas.drawRoundRect(RectF(playRect.left + 4f, playRect.top + 7f, playRect.right + 4f, playRect.bottom + 7f), rx, rx, fillP)
        fillP.color = Color.argb(210, 28, 12, 0)
        canvas.drawRoundRect(RectF(playRect.left - 4f, playRect.top - 4f, playRect.right + 4f, playRect.bottom + 4f), rx + 4f, rx + 4f, fillP)
        fillP.color = warmPink; fillP.alpha = 255
        canvas.drawRoundRect(playRect, rx, rx, fillP)
        canvas.save()
        canvas.clipRect(playRect.left, playRect.top, playRect.right, playRect.centerY())
        fillP.color = Color.argb(65, 255, 255, 255)
        canvas.drawRoundRect(playRect, rx, rx, fillP)
        canvas.restore()
        strokeP.style = Paint.Style.STROKE
        strokeP.color = Color.argb(90, 255, 255, 255); strokeP.strokeWidth = 2f
        canvas.drawRoundRect(playRect, rx, rx, strokeP)

        textP.textSize = 34f; textP.textAlign = Paint.Align.CENTER
        textP.color = Color.argb(80, 0, 0, 0)
        canvas.drawText("PLAY", playRect.centerX() + 2f, playRect.centerY() + 13f + 2f, textP)
        textP.color = Color.WHITE
        canvas.drawText("PLAY", playRect.centerX(), playRect.centerY() + 13f, textP)

        canvas.restore()
    }

    // -----------------------------------------------------------------------
    // Subtitle
    // -----------------------------------------------------------------------
    private fun drawSubtitle(canvas: Canvas) {
        textP.textSize = 17f; textP.textAlign = Paint.Align.CENTER
        textP.color = Color.argb(100, 120, 60, 10)
        // Centered in the space below the play button
        val subtitleY = playRect.bottom + (h - playRect.bottom) * 0.52f
        canvas.drawText("✦  Connect matching pieces  ·  Infinite play  ✦", w / 2f, subtitleY, textP)
    }

    // -----------------------------------------------------------------------
    // Touch
    // -----------------------------------------------------------------------
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                if (playRect.contains(event.x, event.y)) {
                    playPressMs = SystemClock.elapsedRealtime(); invalidate()
                }
            }
            MotionEvent.ACTION_UP -> {
                if (playRect.contains(event.x, event.y) && playPressMs >= 0) onPlay()
                playPressMs = -1L
            }
            MotionEvent.ACTION_CANCEL -> playPressMs = -1L
        }
        return true
    }
}
