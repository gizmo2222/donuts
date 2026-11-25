package com.donuts.game

import android.graphics.*
import kotlin.math.*

// HUD chrome: backdrop, icon buttons, pretty buttons, the counter, text helpers.
// Part of GameView: these are extension functions on it, split out of GameView.kt.

// -----------------------------------------------------------------------
// HUD
// -----------------------------------------------------------------------
internal fun GameView.drawHUD(canvas: Canvas, now: Long) {
    drawIconButton(canvas, stickersBtnRect, theme.chrome, buttonPressScale(now, stickersPressMs) * starPulse(now), 0)
    drawIconButton(canvas, settingsBtnRect, theme.chrome, buttonPressScale(now, settingsPressMs), 1)
}

internal fun GameView.drawBackdrop(canvas: Canvas) {
    val w = surfaceW.toFloat(); val h = surfaceH.toFloat()
    val sw = 14f * u; val sh = 5f * u
    for (i in 0 until backdropN) {
        val x = backdropX[i] * w; val y = backdropY[i] * h
        canvas.save(); canvas.rotate(backdropRot[i], x, y)
        fillPaint.color = backdropColor[i]; fillPaint.alpha = 60
        canvas.drawRoundRect(scratchRectF.apply { set(x - sw / 2f, y - sh / 2f, x + sw / 2f, y + sh / 2f) }, sh / 2f, sh / 2f, fillPaint)
        canvas.restore()
    }
    fillPaint.alpha = 255
}

// Chunky button with a hand-drawn icon instead of a system emoji: 0 = star (stickers), 1 = gear (settings)
internal fun GameView.drawIconButton(canvas: Canvas, rect: RectF, color: Int, scale: Float, icon: Int) {
    drawPrettyButton(canvas, rect, color, "", 1f, scale)
    val cx = rect.centerX(); val cy = rect.centerY(); val r = rect.height() * 0.30f
    canvas.save(); canvas.scale(scale, scale, cx, cy)
    if (icon == 0) drawStarIcon(canvas, cx, cy, r) else drawGearIcon(canvas, cx, cy, r)
    canvas.restore()
}

internal fun GameView.drawStarIcon(canvas: Canvas, cx: Float, cy: Float, r: Float) {
    scratchPath.rewind()
    for (i in 0 until 10) {
        val rr = if (i % 2 == 0) r else r * 0.46f
        val x = cx + rr * starCos[i]; val y = cy + rr * starSin[i]
        if (i == 0) scratchPath.moveTo(x, y) else scratchPath.lineTo(x, y)
    }
    scratchPath.close()
    outlinePaint.color = Color.argb(255, 28, 12, 0); outlinePaint.strokeWidth = r * 0.30f
    canvas.drawPath(scratchPath, outlinePaint)
    fillPaint.color = Color.rgb(255, 215, 50); fillPaint.alpha = 255
    canvas.drawPath(scratchPath, fillPaint)
    fillPaint.color = Color.argb(150, 255, 255, 255)
    canvas.drawCircle(cx - r * 0.22f, cy - r * 0.30f, r * 0.16f, fillPaint)
    fillPaint.alpha = 255
}

internal fun GameView.drawGearIcon(canvas: Canvas, cx: Float, cy: Float, r: Float) {
    scratchPath.rewind()
    for (i in 0 until 32) {
        val rr = if ((i / 2) % 2 == 0) r else r * 0.74f      // pairs of points give flat-topped teeth
        val x = cx + rr * gearCos[i]; val y = cy + rr * gearSin[i]
        if (i == 0) scratchPath.moveTo(x, y) else scratchPath.lineTo(x, y)
    }
    scratchPath.close()
    outlinePaint.color = Color.argb(255, 28, 12, 0); outlinePaint.strokeWidth = r * 0.24f
    canvas.drawPath(scratchPath, outlinePaint)
    fillPaint.color = Color.rgb(255, 250, 240); fillPaint.alpha = 255
    canvas.drawPath(scratchPath, fillPaint)
    canvas.drawCircle(cx, cy, r * 0.30f, outlinePaint)
    fillPaint.color = theme.chrome
    canvas.drawCircle(cx, cy, r * 0.30f, fillPaint)
}

/** Press dip for whichever panel button was touched last; 1 for every other button. */
internal fun GameView.pressScaleFor(rect: RectF, now: Long): Float =
    if (rect === optPressRect) buttonPressScale(now, optPressMs) else 1f

/** Returns a scale factor that dips to 0.86 at tap then recovers to 1.0 over PRESS_MS. */
internal fun GameView.buttonPressScale(now: Long, pressMs: Long): Float {
    if (pressMs < 0) return 1f
    val t = ((now - pressMs).toFloat() / PRESS_MS).coerceIn(0f, 1f)
    // Dip then ease back: 0.86 at t=0, 1.0 at t=1 (ease-out) — chunkier press feel
    return 0.86f + 0.14f * easeOutQuint(t)
}

internal fun GameView.drawPrettyButton(canvas: Canvas, rect: RectF, baseColor: Int, label: String, labelSize: Float, scale: Float = 1f) {
    val rx = 12f * u
    canvas.save()
    canvas.scale(scale, scale, rect.centerX(), rect.centerY())

    // Drop shadow
    shadowPaint.color = Color.argb(90, 0, 0, 0)
    canvas.drawRoundRect(
        scratchRectF.apply { set(rect.left + 2f * u, rect.top + 4f * u, rect.right + 2f * u, rect.bottom + 4f * u) },
        rx, rx, shadowPaint
    )
    // Cartoon border
    val bd = 3f * u
    fillPaint.color = Color.rgb(28, 12, 0)
    canvas.drawRoundRect(
        scratchRectF.apply { set(rect.left - bd, rect.top - bd, rect.right + bd, rect.bottom + bd) },
        rx + bd, rx + bd, fillPaint
    )
    // Base fill
    fillPaint.color = baseColor; fillPaint.alpha = 255
    canvas.drawRoundRect(rect, rx, rx, fillPaint)
    // Top-half highlight
    canvas.save()
    canvas.clipRect(rect.left, rect.top, rect.right, rect.centerY())
    fillPaint.color = Color.argb(65, 255, 255, 255)
    canvas.drawRoundRect(rect, rx, rx, fillPaint)
    canvas.restore()
    // Inner border
    strokePaint.strokeWidth = 1.5f * u
    strokePaint.color = Color.argb(100, 255, 255, 255)
    canvas.drawRoundRect(rect, rx, rx, strokePaint)
    // Label: white with a dark cartoon outline, readable on any button colour
    if (label.isNotEmpty())
        drawLabel(canvas, label, rect.centerX(), rect.centerY() + labelSize * 0.36f, labelSize,
            rect.width() - 12f * u, Color.WHITE, outlined = true)

    canvas.restore()
}

// -----------------------------------------------------------------------
// Counter — digit-flip scoreboard
// -----------------------------------------------------------------------
internal fun GameView.drawCounter(canvas: Canvas, now: Long) {
    val stripY = boardTop + board.rows * cellSize
    val top    = stripY + 6f * u
    val bottom = stripY + counterH
    val midY   = (top + bottom) / 2f
    val ph     = bottom - top

    // One big figure; the donut pal beside it says what it counts without a word
    val digitSz = ph * 0.72f
    textPaint.textSize = digitSz
    val cellW   = textPaint.measureText("0") * 1.08f
    val numStr  = displayedCount.toString()
    val palR    = ph * 0.25f
    val palW    = palR * PAL_HALF_W * 2f
    val iconGap = ph * 0.18f
    val totalW  = palW + iconGap + numStr.length * cellW
    val cx      = layoutCounterRow(totalW, top, bottom)   // shares the row with the picture prompt

    // Heartbeat: quick scale-pulse when the number increments
    val beat = if (counterPulseMs >= 0) {
        val t = ((now - counterPulseMs).toFloat() / COUNTER_PULSE_MS).coerceIn(0f, 1f)
        if (t >= 1f) { counterPulseMs = -1L; 1f }
        else 1f + sin(t * PI.toFloat()) * 0.085f
    } else 1f
    canvas.save()
    canvas.scale(beat, beat, cx, midY)

    textPaint.textSize = digitSz
    val left    = cx - totalW / 2f
    val startX  = left + palW + iconGap + cellW / 2f
    val baseY   = midY + digitSz * 0.36f

    val footY = midY + ph * 0.30f
    palRect.set(left, footY - palR * PAL_TALL, left + palW, footY)
    counterDigits.set(left + palW + iconGap, midY - digitSz * 0.5f, left + totalW, midY + digitSz * 0.5f)
    drawPal(canvas, left + palW / 2f, footY, palR, PalMotion.at(now, reducedMotion, palReaction, palReactMs))

    textOutlinePaint.textSize    = digitSz
    textOutlinePaint.textAlign   = Paint.Align.CENTER
    textOutlinePaint.strokeWidth = 2.5f * u

    for ((idx, ch) in numStr.withIndex()) {
        val pos  = numStr.length - 1 - idx             // 0 = ones place
        val x    = startX + idx * cellW
        val flip = digitFlips[pos]
        val (displayCh, scaleY) = if (flip != null) {
            val t  = ((now - flip.startMs).toFloat() / DIGIT_FLIP_MS).coerceIn(0f, 1f)
            val sy = abs(1f - t * 2f)                  // 1 -> 0 at mid -> 1
            val dc = if (t < 0.5f) flip.from else flip.to
            if (t >= 1f) digitFlips.remove(pos)
            Pair(dc, sy)
        } else Pair(ch, 1f)
        // A brand-new leading digit starts from "0"; never show that zero (9 -> 18, not 09 -> 18)
        if (idx == 0 && numStr.length > 1 && displayCh == '0') continue

        canvas.save()
        canvas.scale(1f, scaleY, x, midY)
        textPaint.color     = Color.argb(70, 0, 0, 0)
        textPaint.textAlign = Paint.Align.CENTER
        canvas.drawText("$displayCh", x + 2f * u, baseY + 2f * u, textPaint)
        textPaint.color = theme.textPrimary
        canvas.drawText("$displayCh", x, baseY, textPaint)
        textOutlinePaint.color = Color.argb(40, 28, 12, 0)
        canvas.drawText("$displayCh", x, baseY, textOutlinePaint)
        canvas.restore()
    }

    canvas.restore()  // end heartbeat scale
}

/**
 * Centred label on a baseline, shrunk to fit [maxW]. Outlined labels get a dark cartoon stroke
 * so white text stays readable on any tile or button colour.
 */
internal fun GameView.drawLabel(canvas: Canvas, text: String, x: Float, baseline: Float, size: Float,
                      maxW: Float, fill: Int, outlined: Boolean) {
    textPaint.textAlign = Paint.Align.CENTER
    textPaint.textSize  = size
    while (textPaint.textSize > 8f * u && textPaint.measureText(text) > maxW) textPaint.textSize *= 0.92f
    val sz = textPaint.textSize
    if (outlined) {
        textOutlinePaint.textAlign = Paint.Align.CENTER
        textOutlinePaint.textSize = sz; textOutlinePaint.strokeWidth = max(2f * u, sz * 0.16f)
        textOutlinePaint.color = Color.argb(235, 28, 12, 0)
        canvas.drawText(text, x, baseline, textOutlinePaint)
    }
    textPaint.color = fill
    canvas.drawText(text, x, baseline, textPaint)
}

internal fun GameView.drawOutlinedText(canvas: Canvas, text: String, x: Float, y: Float, sz: Float, fill: Int) {
    textPaint.textSize = sz; textPaint.textAlign = Paint.Align.CENTER
    textOutlinePaint.textSize = sz; textOutlinePaint.textAlign = Paint.Align.CENTER
    textOutlinePaint.strokeWidth = sz * 0.16f
    textOutlinePaint.color = Color.argb(255, 28, 12, 0)
    canvas.drawText(text, x, y + sz * 0.36f, textOutlinePaint)
    textPaint.color = fill
    canvas.drawText(text, x, y + sz * 0.36f, textPaint)
}

// The donut box on the board screen: quiet, under the counter, smaller than it
internal fun GameView.drawBoxStrip(canvas: Canvas, now: Long) {
    if (boxStrip.shownLifetime < 0 || boxStrip.rect.isEmpty) return
    drawDonutBox(canvas, boxStrip.rect, now, boxStrip)
}

// The sprinkle party meter between the star and the gear: a chunky pill that fills with pink glaze and
// sprinkles as donuts pour in. During a party the glaze turns rainbow and drains one step per chain.
internal fun GameView.drawPartyMeter(canvas: Canvas, now: Long) {
    val r = partyMeterRect
    if (r.isEmpty || partyShownFill < 0f) return
    val h = r.height(); val rad = h / 2f
    val cx = r.centerX(); val cy = r.centerY()

    val bounce = if (partyPopMs >= 0 && !reducedMotion) {
        val t = ((now - partyPopMs).toFloat() / PARTY_POP_MS).coerceIn(0f, 1f)
        if (t >= 1f) { partyPopMs = -1L; 1f } else 1f + sin(t * PI.toFloat()) * (if (party.partyOn) 0.14f else 0.07f)
    } else { partyPopMs = -1L; 1f }
    canvas.save(); canvas.scale(bounce, bounce, cx, cy)

    // Thick cartoon outline round a cream track
    val ow = max(3f * u, h * 0.14f)
    fillPaint.color = Color.argb(235, 28, 12, 0)
    canvas.drawRoundRect(scratchRectF.apply { set(r.left - ow, r.top - ow, r.right + ow, r.bottom + ow) }, rad + ow, rad + ow, fillPaint)
    fillPaint.color = Color.rgb(255, 244, 222)
    canvas.drawRoundRect(r, rad, rad, fillPaint)

    // The glaze: pink while filling, a rolling rainbow during the party
    val fillW = r.width() * partyShownFill.coerceIn(0f, 1f)
    if (fillW > 1f) {
        val glaze = if (party.partyOn) {
            val colors = GameTheme.bannerColors
            val phase = if (reducedMotion) 0f else (now % 1200L) / 1200f * colors.size
            val i = phase.toInt() % colors.size
            blendColor(colors[i], colors[(i + 1) % colors.size], phase - phase.toInt())
        } else DonutType.STRAWBERRY.glazeColor
        fillPaint.color = glaze
        val fr = min(rad, fillW / 2f)
        canvas.drawRoundRect(scratchRectF.apply { set(r.left, r.top, r.left + fillW, r.bottom) }, fr, fr, fillPaint)

        // Sprinkles on the glaze, in a fixed scatter so they never jitter
        canvas.save(); canvas.clipRect(r.left, r.top, r.left + fillW, r.bottom)
        val sw = h * 0.30f; val sh = max(2f * u, h * 0.11f)
        val n = (r.width() / (h * 0.42f)).toInt()
        val sprinkleColors = intArrayOf(Color.WHITE, GameTheme.bannerColors[0], GameTheme.bannerColors[3], GameTheme.bannerColors[2])
        for (k in 0 until n) {
            val x = r.left + rad * 0.7f + k * (r.width() - rad * 1.4f) / max(1, n - 1)
            if (x > r.left + fillW - sw * 0.3f) break
            val y = cy + (if (k % 2 == 0) -0.17f else 0.17f) * h
            canvas.save(); canvas.rotate(((k * 53) % 120 - 60).toFloat(), x, y)
            fillPaint.color = sprinkleColors[k % sprinkleColors.size]
            canvas.drawRoundRect(scratchRectF.apply { set(x - sw / 2f, y - sh / 2f, x + sw / 2f, y + sh / 2f) }, sh / 2f, sh / 2f, fillPaint)
            canvas.restore()
        }
        canvas.restore()
    }

    // A soft sheen along the top makes it read as a glossy tube
    fillPaint.color = Color.argb(70, 255, 255, 255)
    canvas.drawRoundRect(scratchRectF.apply { set(r.left + rad * 0.6f, r.top + h * 0.12f, r.right - rad * 0.6f, r.top + h * 0.32f) }, h * 0.1f, h * 0.1f, fillPaint)
    canvas.restore()
}

/** [a] blended toward [b] by [t] (0..1), channel by channel. */
internal fun blendColor(a: Int, b: Int, t: Float): Int {
    fun ch(x: Int, y: Int) = (x + (y - x) * t).roundToInt().coerceIn(0, 255)
    return Color.rgb(ch(Color.red(a), Color.red(b)), ch(Color.green(a), Color.green(b)), ch(Color.blue(a), Color.blue(b)))
}
