package com.donuts.game

import android.graphics.*
import kotlin.math.*

// The stickers panel (with the donut box) and the settings panel.
// Part of GameView: these are extension functions on it, split out of GameView.kt.

// -----------------------------------------------------------------------
// Stickers panel
// -----------------------------------------------------------------------
/** What the sticker rules need, read from prefs and the current board. */
internal fun GameView.stickerStats() = StickerStats(
    lifetime     = lifetimeDonuts(),
    highScore6x6 = if (board.cols == 6) maxOf(prefs.highScore6x6, board.totalCleared) else prefs.highScore6x6,
    highScore8x8 = if (board.cols != 6) maxOf(prefs.highScore8x8, board.totalCleared) else prefs.highScore8x8,
    bestChain    = prefs.bestChainLength,
    goldenPopped = prefs.goldenPopped,
    sessions     = prefs.sessionCount
)

internal fun GameView.drawStickersPanel(canvas: Canvas, now: Long) {
    val eased  = easeOutQuint(stickersAnim)
    val slideY = stickersSlideY()

    dimPaint.alpha = (eased * 160).toInt()
    canvas.drawRect(0f, 0f, surfaceW.toFloat(), surfaceH.toFloat(), dimPaint)

    canvas.save()
    canvas.translate(0f, slideY)

    // ---- Panel shell ----
    val pr = 22f * u; val bd = 6f * u; val ring = 3f * u
    fillPaint.color = Color.argb(230, 28, 12, 0)
    canvas.drawRoundRect(
        scratchRectF.apply { set(stickerPanelRect.left - bd, stickerPanelRect.top - bd,
              stickerPanelRect.right + bd, stickerPanelRect.bottom + bd) },
        pr + bd, pr + bd, fillPaint)
    strokePaint.color = Color.WHITE
    strokePaint.strokeWidth = ring
    canvas.drawRoundRect(
        scratchRectF.apply { set(stickerPanelRect.left - ring, stickerPanelRect.top - ring,
              stickerPanelRect.right + ring, stickerPanelRect.bottom + ring) },
        pr + ring, pr + ring, strokePaint)
    fillPaint.color = theme.panelBg; fillPaint.alpha = 255
    canvas.drawRoundRect(stickerPanelRect, pr, pr, fillPaint)
    canvas.save()
    canvas.clipRect(stickerPanelRect.left, stickerPanelRect.top,
                    stickerPanelRect.right, stickerPanelRect.top + stickerPanelRect.height() * 0.35f)
    fillPaint.color = Color.argb(28, 255, 255, 255)
    canvas.drawRoundRect(stickerPanelRect, pr, pr, fillPaint)
    canvas.restore()

    // ---- Title ----
    val titleX = stickerPanelRect.centerX()
    val titleY = stickerPanelRect.top + 36f * u
    drawOutlinedText(canvas, "My Stickers", titleX, titleY - 28f * u * 0.36f, 28f * u, Color.WHITE)

    val earnedMask  = StickerRules.earnedMask(stickerStats())
    val earnedCount = Integer.bitCount(earnedMask)

    // ---- Sticker tiles ----
    val spinMs = 3200L   // shimmer ring rotation period
    for (i in 0 until 12) {
        val rect    = stickerRects[i]
        val earned  = earnedMask and (1 shl i) != 0
        val color   = STICKER_COLORS[i]
        val r       = 10f * u
        val cx      = rect.centerX()
        val cy      = rect.centerY()

        if (earned) {
            // Outer glow ring — pulsing
            val pulseT  = ((now % 1600L).toFloat() / 1600f)
            val pulse   = if (pulseT < 0.5f) pulseT * 2f else (1f - pulseT) * 2f
            val glowA   = (80 + (pulse * 100).toInt()).coerceIn(0, 255)
            val glowR   = Color.red(color); val glowG = Color.green(color); val glowB = Color.blue(color)
            strokePaint.color       = Color.argb(glowA, glowR, glowG, glowB)
            strokePaint.strokeWidth = 4f * u; strokePaint.alpha = glowA
            val g = 4f * u
            canvas.drawRoundRect(
                scratchRectF.apply { set(rect.left - g, rect.top - g, rect.right + g, rect.bottom + g) },
                r + g, r + g, strokePaint)
            // Gold border
            fillPaint.color = Color.argb(220, 28, 12, 0)
            canvas.drawRoundRect(scratchRectF.apply { set(rect.left-g, rect.top-g, rect.right+g, rect.bottom+g) }, r+g, r+g, fillPaint)
            val g2 = 2.5f * u
            strokePaint.color = Color.rgb(255, 215, 50); strokePaint.strokeWidth = 2.5f * u; strokePaint.alpha = 255
            canvas.drawRoundRect(scratchRectF.apply { set(rect.left-g2, rect.top-g2, rect.right+g2, rect.bottom+g2) }, r+g2, r+g2, strokePaint)
        } else {
            // Plain dark border
            fillPaint.color = Color.argb(140, 28, 12, 0)
            val g2 = 2.5f * u
            canvas.drawRoundRect(scratchRectF.apply { set(rect.left-g2, rect.top-g2, rect.right+g2, rect.bottom+g2) }, r+g2, r+g2, fillPaint)
        }

        // Tile body — warm parchment for unearned (not cold grey) so it reads as collectible, not broken
        fillPaint.color = if (earned) color else Color.rgb(210, 190, 162)
        fillPaint.alpha = if (earned) 255 else 220
        canvas.drawRoundRect(rect, r, r, fillPaint)

        // Top-half highlight
        canvas.save()
        canvas.clipRect(rect.left, rect.top, rect.right, cy)
        fillPaint.color = Color.argb(if (earned) 70 else 40, 255, 255, 255)
        canvas.drawRoundRect(rect, r, r, fillPaint)
        canvas.restore()

        if (earned) {
            // Rotating shimmer streak across the tile
            val angle = ((now % spinMs).toFloat() / spinMs) * 360f
            canvas.save()
            canvas.clipRect(rect)
            canvas.rotate(angle, cx, cy)
            fillPaint.color = Color.argb(55, 255, 255, 255)
            canvas.drawRect(cx - rect.width() * 0.08f, cy - rect.height() * 0.7f,
                            cx + rect.width() * 0.08f, cy + rect.height() * 0.7f, fillPaint)
            canvas.restore()
        }

        fillPaint.alpha = 255; strokePaint.alpha = 255

        if (earned) {
            // Art in the same outline style as the pieces
            drawStickerArt(canvas, i, cx, cy - rect.height() * 0.07f, rect.height() * 0.58f)
            // Name — bottom
            // White with a dark cartoon outline, so it reads on the pale tiles (yellow, mint) too
            drawLabel(canvas, STICKER_NAMES[i], cx, rect.bottom - rect.height() * 0.09f,
                rect.height() * 0.150f, rect.width() - 16f * u, Color.WHITE, outlined = true)
            // Earned checkmark badge — inside the tile's top right corner
            val br2 = min(8f * u, rect.width() * 0.11f); val inset = 3f * u + 1.5f * u
            val bx = rect.right - br2 - inset; val by = rect.top + br2 + inset
            fillPaint.color = Color.argb(220, 28, 12, 0)
            canvas.drawCircle(bx, by, br2 + 1.5f * u, fillPaint)
            fillPaint.color = Color.rgb(80, 210, 80)
            canvas.drawCircle(bx, by, br2, fillPaint)
            textPaint.textSize = br2 * 1.4f; textPaint.color = Color.WHITE
            canvas.drawText("\u2713", bx, by + br2 * 0.42f, textPaint)
        } else {
            // Condition hint — tells Steven how to earn it
            // Big and fully opaque: an early reader has to be able to sound it out
            val lines = drawTileCaption(canvas, STICKER_DESCS[i], cx, rect.bottom - rect.height() * 0.10f,
                rect.height() * 0.16f, rect.width() - 16f * u, theme.textPrimary)
            // Mystery "?" — warm and inviting, not a broken/disabled lock; smaller and higher over a two-line hint
            val qSz = rect.height() * (if (lines > 1) 0.36f else 0.48f)
            val qY  = if (lines > 1) rect.top + rect.height() * 0.50f else cy + qSz * 0.36f
            textPaint.textSize  = qSz
            textPaint.textAlign = Paint.Align.CENTER
            textPaint.color     = Color.argb(45, 28, 12, 0)
            canvas.drawText("?", cx + 2f, qY + 2f, textPaint)
            textPaint.color     = Color.argb(140, 100, 60, 10)
            canvas.drawText("?", cx, qY, textPaint)
        }
    }

    drawDonutBox(canvas, donutBoxRect, now)

    // ---- Stats line ----
    val statsY = donutBoxRect.bottom + 20f * u
    val lifetime = lifetimeDonuts()
    drawLabel(canvas, "$lifetime donuts popped  ·  $earnedCount of 12 stickers",
        stickerPanelRect.centerX(), statsY, 18f * u, stickerPanelRect.width() - 20f * u, theme.textPrimary, outlined = false)

    drawPrettyButton(canvas, stickersCloseRect, Color.rgb(60, 175, 80), "Done  \u2713", 18f * u, pressScaleFor(stickersCloseRect, now))

    canvas.restore()
}

/**
 * A locked sticker's hint, kept inside its tile: one line when it fits at 85% of [size] or more,
 * otherwise two lines split at the space nearest the middle, so an early reader keeps big letters.
 */
internal fun GameView.drawTileCaption(canvas: Canvas, text: String, cx: Float, baseline: Float,
                                      size: Float, maxW: Float, fill: Int): Int {
    textPaint.textSize = size * 0.85f
    val split = text.indices.filter { text[it] == ' ' }.minByOrNull { abs(it - text.length / 2) }
    if (split == null || textPaint.measureText(text) <= maxW) {
        drawLabel(canvas, text, cx, baseline, size, maxW, fill, outlined = false)
        return 1
    }
    val a = text.substring(0, split); val b = text.substring(split + 1)
    while (textPaint.textSize > 8f * u && max(textPaint.measureText(a), textPaint.measureText(b)) > maxW)
        textPaint.textSize *= 0.92f
    val sz = textPaint.textSize
    drawLabel(canvas, a, cx, baseline - sz * 1.05f, sz, maxW, fill, outlined = false)
    drawLabel(canvas, b, cx, baseline, sz, maxW, fill, outlined = false)
    return 2
}

/**
 * A bakery box with twelve slots that fill with donuts as Steven pops them, plus a small stack of
 * closed boxes for the ones already filled. Pictures, not words. Shared by the stickers panel and
 * the board strip; only the strip passes [strip], which supplies the live lifetime and the pop timers.
 */
internal fun GameView.drawDonutBox(canvas: Canvas, rect: RectF, now: Long, strip: BoxStrip? = null) {
    val life   = if (strip != null) strip.shownLifetime.coerceAtLeast(0) else lifetimeDonuts()
    val boxes  = StickerRules.boxesFilled(life)
    val filled = StickerRules.slotsFilled(life)

    // Bounce when a box fills: squash and stretch about the centre, damped
    val boxT = if (strip != null && strip.boxPopMs >= 0) ((now - strip.boxPopMs) / 520f) else 1f
    if (boxT >= 1f && strip != null) strip.boxPopMs = -1L
    val wob = if (boxT < 1f) sin(boxT * PI.toFloat() * 3f) * (1f - boxT) * 0.28f else 0f
    canvas.save()
    canvas.scale(1f + wob, 1f - wob, rect.centerX(), rect.bottom)

    // Stack of closed boxes on the right, only once one is full (up to three drawn)
    val stackN  = min(boxes, 3)
    val iconS   = rect.height() * 0.78f
    val off     = iconS * 0.14f
    val iconW   = if (stackN > 0) iconS + off * (stackN - 1) else 0f
    val iconGap = if (stackN > 0) 12f * u else 0f

    // Box: two rows of six slots, sized to the row height (and shrunk if the width is tight)
    val slotMax = (rect.width() - iconW - iconGap) / (6f + 0.14f * 7f)
    val slot = min(rect.height() * 0.42f, slotMax)
    val gap  = slot * 0.14f
    val boxW = slot * 6f + gap * 7f
    val boxH = slot * 2f + gap * 3f
    val bx = rect.centerX() - (boxW + iconGap + iconW) / 2f
    val by = rect.centerY() - boxH / 2f
    val bd = 3f * u

    fillPaint.color = Color.argb(230, 28, 12, 0)
    canvas.drawRoundRect(scratchRectF.apply { set(bx - bd, by - bd, bx + boxW + bd, by + boxH + bd) }, 12f * u, 12f * u, fillPaint)
    fillPaint.color = Color.rgb(255, 205, 220); fillPaint.alpha = 255     // strawberry-pink bakery box
    canvas.drawRoundRect(scratchRectF.apply { set(bx, by, bx + boxW, by + boxH) }, 10f * u, 10f * u, fillPaint)

    val slotT = if (strip != null && strip.slotPopMs >= 0) ((now - strip.slotPopMs) / 350f) else 1f
    if (slotT >= 1f && strip != null) strip.slotPopMs = -1L
    val types = DonutType.values()
    for (i in 0 until StickerRules.BOX_SLOTS) {
        val col = i % 6; val row = i / 6
        val cx = bx + gap + slot / 2f + col * (slot + gap)
        val cy = by + gap + slot / 2f + row * (slot + gap)
        if (i < filled) {
            // The newest donut pops in: 0 -> 1.25 -> 1
            val k = if (i == filled - 1 && slotT < 1f) popScale(slotT) else 1f
            if (k > 0.01f) drawPiece(canvas, cx, cy, slot * 0.46f * k, types[i % types.size], false)
        } else {
            // Empty paper cup waiting for a donut
            fillPaint.color = Color.argb(70, 120, 40, 60)
            canvas.drawCircle(cx, cy, slot * 0.40f, fillPaint)
        }
    }
    fillPaint.alpha = 255

    // Closed boxes, back to front; the front one carries the number
    if (stackN > 0) {
        val ix = bx + boxW + iconGap
        val pop = if (boxT < 1f) 1f + 0.3f * (1f - boxT) else 1f
        for (n in 0 until stackN) {
            val back = stackN - 1 - n
            val x0 = ix + off * (stackN - 1 - back)
            val y0 = rect.centerY() - iconS / 2f - off * back * 0.6f
            drawClosedBox(canvas, x0, y0, iconS, pop, if (back == 0) boxes else 0)
        }
    }
    canvas.restore()
}

/** Overshoot pop: 0 at t=0, 1.25 around t=0.6, settling at 1. */
internal fun GameView.popScale(t: Float): Float =
    if (t < 0.6f) 1.25f * easeOutBack(t / 0.6f).coerceAtMost(1.1f) / 1.1f
    else 1.25f - 0.25f * ((t - 0.6f) / 0.4f)

/** A closed pink bakery box with a darker lid line and a yellow ribbon; [number] > 0 is printed on it. */
internal fun GameView.drawClosedBox(canvas: Canvas, x: Float, y: Float, s: Float, pop: Float, number: Int) {
    canvas.save()
    canvas.scale(pop, pop, x + s / 2f, y + s)
    val r = 8f * u
    fillPaint.color = Color.argb(240, 28, 12, 0)
    canvas.drawRoundRect(scratchRectF.apply { set(x - 3f * u, y - 3f * u, x + s + 3f * u, y + s + 3f * u) }, r + 3f * u, r + 3f * u, fillPaint)
    fillPaint.color = Color.rgb(255, 205, 220)
    canvas.drawRoundRect(scratchRectF.apply { set(x, y, x + s, y + s) }, r, r, fillPaint)
    fillPaint.color = Color.rgb(230, 120, 160)                       // darker lid band
    canvas.drawRoundRect(scratchRectF.apply { set(x, y, x + s, y + s * 0.30f) }, r, r, fillPaint)
    fillPaint.color = Color.rgb(255, 215, 50)                        // ribbon
    canvas.drawRect(x + s * 0.42f, y, x + s * 0.58f, y + s, fillPaint)
    strokePaint.color = Color.rgb(28, 12, 0); strokePaint.strokeWidth = 2.5f * u
    canvas.drawLine(x, y + s * 0.30f, x + s, y + s * 0.30f, strokePaint)
    if (number > 0) drawOutlinedText(canvas, "$number", x + s / 2f, y + s * 0.62f, s * 0.58f, Color.WHITE)
    canvas.restore()
}

// Settings overlay — slides up from bottom
// -----------------------------------------------------------------------
internal fun GameView.drawSettings(canvas: Canvas, now: Long) {
    val eased  = easeOutQuint(settingsAnim)
    val slideY = settingsSlideY()

    dimPaint.alpha = (eased * 160).toInt()
    canvas.drawRect(0f, 0f, surfaceW.toFloat(), surfaceH.toFloat(), dimPaint)

    canvas.save()
    canvas.translate(0f, slideY)

    val k  = u * settingsSc      // everything inside the panel scales with the fit factor
    val pr = 22f * k
    val bd = 6f * k; val ring = 3f * k

    // Panel: dark cartoon outline, white ring, cream fill
    fillPaint.color = Color.argb(230, 28, 12, 0)
    canvas.drawRoundRect(scratchRectF.apply { set(panelRect.left - bd, panelRect.top - bd, panelRect.right + bd, panelRect.bottom + bd) }, pr + bd, pr + bd, fillPaint)
    strokePaint.color = Color.WHITE; strokePaint.strokeWidth = ring
    canvas.drawRoundRect(scratchRectF.apply { set(panelRect.left - ring, panelRect.top - ring, panelRect.right + ring, panelRect.bottom + ring) }, pr + ring, pr + ring, strokePaint)
    fillPaint.color = theme.panelBg; fillPaint.alpha = 255
    canvas.drawRoundRect(panelRect, pr, pr, fillPaint)

    val pl = panelRect.left; val pw = panelRect.width(); val pt = panelRect.top
    val pad = 16f * k

    // Title
    val titleX = pl + pw / 2f; val titleY = pt + 48f * k; val titleSz = 30f * k
    drawOutlinedText(canvas, "Settings", titleX, titleY - titleSz * 0.36f, titleSz, Color.WHITE)

    drawSectionLabel(canvas, "Sound", pl + pad, soundRects[0].top - 8f * k)
    drawSettingsBtn(canvas, now, soundRects[0], onOffLabels[0], prefs.soundEnabled)
    drawSettingsBtn(canvas, now, soundRects[1], onOffLabels[1], !prefs.soundEnabled)

    drawSectionLabel(canvas, "Buzz", pl + pad, buzzRects[0].top - 8f * k)
    drawSettingsBtn(canvas, now, buzzRects[0], onOffLabels[0], prefs.hapticEnabled)
    drawSettingsBtn(canvas, now, buzzRects[1], onOffLabels[1], !prefs.hapticEnabled)

    drawSectionLabel(canvas, "Hints", pl + pad, hintRects[0].top - 8f * k)
    val hintsOn = prefs.hintDelayMs != 0L
    drawSettingsBtn(canvas, now, hintRects[0], onOffLabels[0], hintsOn)
    drawSettingsBtn(canvas, now, hintRects[1], onOffLabels[1], !hintsOn)

    drawSectionLabel(canvas, "Donut size", pl + pad, gridRects[0].top - 8f * k)
    for (i in gridOptions.indices) drawSettingsBtn(canvas, now, gridRects[i], gridLabels[i], gridOptions[i] == prefs.gridSize)

    // Two-tap confirm buttons so a stray tap cannot wipe the board or the stickers
    drawConfirmButton(canvas, now, settingsResetRect, newGameConfirm, "New game", "New game?", Color.rgb(200, 70, 50))
    drawConfirmButton(canvas, now, settingsStickersRect, stickersConfirm, "Reset stickers", "Clear all?", Color.rgb(150, 70, 170))

    drawPrettyButton(canvas, settingsCloseRect, Color.rgb(60, 175, 80), "Done  \u2713", 18f * k, pressScaleFor(settingsCloseRect, now))

    // Version: small and muted, not a button
    if (appVersion.isNotEmpty()) {
        textPaint.textSize = 13f * k; textPaint.textAlign = Paint.Align.CENTER
        textPaint.color = Color.argb(110, 60, 30, 0)
        canvas.drawText("v$appVersion", panelRect.centerX(), panelRect.bottom - 14f * k, textPaint)
    }

    canvas.restore()
}

/** A pretty button that needs two taps within the window, with a draining bar while armed. */
internal fun GameView.drawConfirmButton(canvas: Canvas, now: Long, rect: RectF, confirm: TwoTapConfirm, label: String, armedLabel: String, color: Int) {
    val k = u * settingsSc
    val armed = confirm.isArmed(now)
    drawPrettyButton(canvas, rect, if (armed) Color.rgb(220, 130, 30) else color,
        if (armed) armedLabel else label, 16f * k, pressScaleFor(rect, now))
    if (armed) {
        val progress = confirm.progress(now)
        val bx = rect.left + 10f * k
        val bw = rect.width() - 20f * k
        val by = rect.bottom - 8f * k
        val bh = 3f * k
        fillPaint.color = Color.argb(60, 28, 12, 0)
        canvas.drawRoundRect(scratchRectF.apply { set(bx, by, bx + bw, by + bh) }, bh / 2, bh / 2, fillPaint)
        fillPaint.color = Color.argb(220, 255, 200, 60)
        canvas.drawRoundRect(scratchRectF.apply { set(bx, by, bx + bw * progress, by + bh) }, bh / 2, bh / 2, fillPaint)
        fillPaint.alpha = 255
    }
}

/** Small green check badge used on selected settings buttons. */
internal fun GameView.drawCheckBadge(canvas: Canvas, bx: Float, by: Float, br: Float) {
    fillPaint.color = Color.argb(210, 28, 12, 0)
    canvas.drawCircle(bx, by, br + br * 0.2f, fillPaint)
    fillPaint.color = Color.rgb(80, 200, 80); fillPaint.alpha = 255
    canvas.drawCircle(bx, by, br, fillPaint)
    textPaint.textSize  = br * 1.3f; textPaint.textAlign = Paint.Align.CENTER
    textPaint.color     = Color.WHITE
    canvas.drawText("\u2713", bx, by + br * 0.42f, textPaint)
}

/** Draws a section label with a chunky left accent bar — bold and readable. */
internal fun GameView.drawSectionLabel(canvas: Canvas, text: String, x: Float, baselineY: Float) {
    val k        = u * settingsSc
    val labelSz  = 17f * k
    val barW     = 5f * k
    val barPad   = 2f * k
    val textX    = x + barW + 8f * k

    textPaint.textSize  = labelSz
    textPaint.textAlign = Paint.Align.LEFT

    val barTop = baselineY - labelSz * 0.88f
    val barBot = baselineY + labelSz * 0.18f

    // Accent bar: dark border then theme color
    fillPaint.color = Color.argb(200, 28, 12, 0)
    canvas.drawRoundRect(scratchRectF.apply { set(x - barPad, barTop - barPad, x + barW + barPad, barBot + barPad) },
        barW / 2f + barPad, barW / 2f + barPad, fillPaint)
    fillPaint.color = theme.accent; fillPaint.alpha = 255
    canvas.drawRoundRect(scratchRectF.apply { set(x, barTop, x + barW, barBot) },
        barW / 2f, barW / 2f, fillPaint)
    fillPaint.alpha = 255

    textPaint.color = Color.argb(70, 0, 0, 0)
    canvas.drawText(text, textX + 1f * k, baselineY + 1f * k, textPaint)
    textPaint.color = theme.textPrimary
    canvas.drawText(text, textX, baselineY, textPaint)
}

internal fun GameView.drawSettingsBtn(canvas: Canvas, now: Long, rect: RectF, label: String, selected: Boolean) {
    val k = u * settingsSc
    val pressScale = pressScaleFor(rect, now)
    canvas.save(); canvas.scale(pressScale, pressScale, rect.centerX(), rect.centerY())
    val borderPad = if (selected) 4f * k else 3f * k
    // Drop shadow, same as the pretty buttons
    shadowPaint.color = Color.argb(90, 0, 0, 0)
    canvas.drawRoundRect(
        scratchRectF.apply { set(rect.left + 2f * k, rect.top + 4f * k, rect.right + 2f * k, rect.bottom + 4f * k) },
        14f * k, 14f * k, shadowPaint
    )
    // Dark cartoon border
    fillPaint.color = Color.argb(210, 28, 12, 0)
    canvas.drawRoundRect(
        scratchRectF.apply { set(rect.left - borderPad, rect.top - borderPad, rect.right + borderPad, rect.bottom + borderPad) },
        14f * k, 14f * k, fillPaint
    )
    // Gold ring on selected
    if (selected) {
        strokePaint.color = Color.rgb(255, 215, 50); strokePaint.strokeWidth = 2.5f * k; strokePaint.alpha = 255
        canvas.drawRoundRect(
            scratchRectF.apply { set(rect.left - borderPad + 1f * k, rect.top - borderPad + 1f * k,
                  rect.right + borderPad - 1f * k, rect.bottom + borderPad - 1f * k) },
            13f * k, 13f * k, strokePaint
        )
    }
    // Fill
    fillPaint.color = if (selected) theme.accent else theme.accentMuted; fillPaint.alpha = 255
    canvas.drawRoundRect(rect, 12f * k, 12f * k, fillPaint)
    // Top highlight
    canvas.save()
    canvas.clipRect(rect.left, rect.top, rect.right, rect.centerY())
    fillPaint.color = Color.argb(if (selected) 70 else 35, 255, 255, 255)
    canvas.drawRoundRect(rect, 12f * k, 12f * k, fillPaint)
    canvas.restore()
    // Label: outlined white on the chosen option, dark brown on the tan ones (white on tan
    // was too faint to read). Shrinks to fit so long words never spill past the button.
    drawLabel(canvas, label, rect.centerX(), rect.centerY() + 18f * k * 0.36f, 18f * k,
        rect.width() - 12f * k, if (selected) Color.WHITE else theme.textPrimary, outlined = selected)
    // Checkmark badge on selected
    if (selected) drawCheckBadge(canvas, rect.right - 1f, rect.top + 1f, 9f * k)
    canvas.restore()
}
