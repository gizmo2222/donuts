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

// The stickers panel (with the donut box) and the settings panel.
// Part of GameView: these are extension functions on it, split out of GameView.kt.

// -----------------------------------------------------------------------
// Stickers panel
// -----------------------------------------------------------------------
/** Returns true if sticker i has been earned. */
internal fun GameView.isStickerEarned(i: Int): Boolean {
    val lifetime  = lifetimeDonuts()
    val bestScore = max(prefs.highScore6x6, prefs.highScore8x8)
    return when (i) {
        0  -> lifetime >= 10
        1  -> lifetime >= 50
        2  -> lifetime >= 500
        3  -> prefs.bestChainLength >= 4
        4  -> prefs.bestChainLength >= 6
        5  -> prefs.bestChainLength >= 8
        6  -> bestScore >= 20
        7  -> bestScore >= 60
        8  -> bestScore >= 200
        9  -> prefs.goldenPopped
        10 -> prefs.highScore6x6 > 0 && prefs.highScore8x8 > 0
        11 -> prefs.sessionCount >= 10
        else -> false
    }
}

internal fun GameView.drawStickersPanel(canvas: Canvas, now: Long) {
    val eased  = easeOutQuint(stickersAnim)
    val slideY = stickerPanelRect.height() * (1f - eased)

    dimPaint.alpha = (eased * 160).toInt()
    canvas.drawRect(0f, 0f, surfaceW.toFloat(), surfaceH.toFloat(), dimPaint)

    canvas.save()
    canvas.translate(0f, slideY)

    // ---- Panel shell ----
    val pr = 22f * u; val bd = 6f * u; val ring = 3f * u
    fillPaint.color = Color.argb(230, 28, 12, 0)
    canvas.drawRoundRect(
        RectF(stickerPanelRect.left - bd, stickerPanelRect.top - bd,
              stickerPanelRect.right + bd, stickerPanelRect.bottom + bd),
        pr + bd, pr + bd, fillPaint)
    strokePaint.color = Color.argb(200, 255, 255, 255)
    strokePaint.strokeWidth = ring; strokePaint.alpha = 255
    canvas.drawRoundRect(
        RectF(stickerPanelRect.left - ring, stickerPanelRect.top - ring,
              stickerPanelRect.right + ring, stickerPanelRect.bottom + ring),
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
    textPaint.textAlign = Paint.Align.CENTER
    textPaint.color = Color.argb(100, 0, 0, 0); textPaint.textSize = 28f * u
    canvas.drawText("My Stickers", titleX + 2f * u, titleY + 2.5f * u, textPaint)
    textPaint.color = theme.textPrimary
    canvas.drawText("My Stickers", titleX, titleY, textPaint)
    textOutlinePaint.color = Color.argb(80, 0, 0, 0); textOutlinePaint.strokeWidth = 3f * u
    textOutlinePaint.textSize = 28f * u; textOutlinePaint.textAlign = Paint.Align.CENTER
    textOutlinePaint.typeface = boldTypeface
    canvas.drawText("My Stickers", titleX, titleY, textOutlinePaint)

    val earnedCount = (0 until 12).count { isStickerEarned(it) }

    // ---- Sticker tiles ----
    val spinMs = 3200L   // shimmer ring rotation period
    for (i in 0 until 12) {
        val rect    = stickerRects[i]
        val earned  = isStickerEarned(i)
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
                RectF(rect.left - g, rect.top - g, rect.right + g, rect.bottom + g),
                r + g, r + g, strokePaint)
            // Gold border
            fillPaint.color = Color.argb(220, 28, 12, 0)
            canvas.drawRoundRect(RectF(rect.left-g, rect.top-g, rect.right+g, rect.bottom+g), r+g, r+g, fillPaint)
            val g2 = 2.5f * u
            strokePaint.color = Color.rgb(255, 215, 50); strokePaint.strokeWidth = 2.5f * u; strokePaint.alpha = 255
            canvas.drawRoundRect(RectF(rect.left-g2, rect.top-g2, rect.right+g2, rect.bottom+g2), r+g2, r+g2, strokePaint)
        } else {
            // Plain dark border
            fillPaint.color = Color.argb(140, 28, 12, 0)
            val g2 = 2.5f * u
            canvas.drawRoundRect(RectF(rect.left-g2, rect.top-g2, rect.right+g2, rect.bottom+g2), r+g2, r+g2, fillPaint)
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
                rect.height() * 0.150f, rect.width() - 10f * u, Color.WHITE, outlined = true)
            // Earned checkmark badge — top right
            val bx = rect.right - 1f; val by = rect.top + 1f; val br2 = 8f * u
            fillPaint.color = Color.argb(220, 28, 12, 0)
            canvas.drawCircle(bx, by, br2 + 1.5f * u, fillPaint)
            fillPaint.color = Color.rgb(80, 210, 80)
            canvas.drawCircle(bx, by, br2, fillPaint)
            textPaint.textSize = br2 * 1.4f; textPaint.color = Color.WHITE
            canvas.drawText("\u2713", bx, by + br2 * 0.42f, textPaint)
        } else {
            // Mystery "?" — warm and inviting, not a broken/disabled lock
            val qSz = rect.height() * 0.48f
            val qY  = cy + qSz * 0.36f
            textPaint.textSize  = qSz
            textPaint.textAlign = Paint.Align.CENTER
            textPaint.color     = Color.argb(45, 28, 12, 0)
            canvas.drawText("?", cx + 2f, qY + 2f, textPaint)
            textPaint.color     = Color.argb(140, 100, 60, 10)
            canvas.drawText("?", cx, qY, textPaint)
            // Condition hint — tells Steven how to earn it
            // Big and fully opaque: an early reader has to be able to sound it out
            drawLabel(canvas, STICKER_DESCS[i], cx, rect.bottom - rect.height() * 0.10f,
                rect.height() * 0.16f, rect.width() - 10f * u, theme.textPrimary, outlined = false)
        }
    }

    drawDonutBox(canvas)

    // ---- Stats line ----
    val statsY = donutBoxRect.bottom + 20f * u
    val lifetime = lifetimeDonuts()
    textPaint.textSize = 14f * u; textPaint.textAlign = Paint.Align.CENTER
    textPaint.color = theme.textSecondary
    canvas.drawText(
        "$lifetime donuts popped  \u00B7  $earnedCount of 12 stickers",
        stickerPanelRect.centerX(), statsY, textPaint)

    drawPrettyButton(canvas, stickersCloseRect, Color.rgb(60, 175, 80), "Done  \u2713", 18f * u, pressScaleFor(stickersCloseRect, now))

    canvas.restore()
}

/**
 * A bakery box with twelve slots that fill with donuts as Steven pops them, plus the count of
 * boxes already filled. Pictures, not words: he can see how close the next box is.
 */
internal fun GameView.drawDonutBox(canvas: Canvas) {
    val rect   = donutBoxRect
    val perBox = BOX_SLOTS * BOX_SLOT_DONUTS
    val life   = lifetimeDonuts()
    val boxes  = life / perBox
    val filled = (life % perBox) / BOX_SLOT_DONUTS

    // Box: two rows of six slots, sized to the row height
    val slot = rect.height() * 0.42f
    val gap  = slot * 0.14f
    val boxW = slot * 6f + gap * 7f
    val boxH = slot * 2f + gap * 3f
    val countSz = rect.height() * 0.62f
    textPaint.textSize = countSz
    val countStr = "×$boxes"
    val countW = textPaint.measureText(countStr)
    val groupW = boxW + 14f * u + countW
    val bx = rect.centerX() - groupW / 2f
    val by = rect.centerY() - boxH / 2f
    val bd = 3f * u

    fillPaint.color = Color.argb(230, 28, 12, 0)
    canvas.drawRoundRect(scratchRectF.apply { set(bx - bd, by - bd, bx + boxW + bd, by + boxH + bd) }, 12f * u, 12f * u, fillPaint)
    fillPaint.color = Color.rgb(255, 205, 220); fillPaint.alpha = 255     // strawberry-pink bakery box
    canvas.drawRoundRect(scratchRectF.apply { set(bx, by, bx + boxW, by + boxH) }, 10f * u, 10f * u, fillPaint)

    val types = DonutType.values()
    for (i in 0 until BOX_SLOTS) {
        val col = i % 6; val row = i / 6
        val cx = bx + gap + slot / 2f + col * (slot + gap)
        val cy = by + gap + slot / 2f + row * (slot + gap)
        if (i < filled) {
            drawPiece(canvas, cx, cy, slot * 0.46f, types[i % types.size], false)
        } else {
            // Empty paper cup waiting for a donut
            fillPaint.color = Color.argb(70, 120, 40, 60)
            canvas.drawCircle(cx, cy, slot * 0.40f, fillPaint)
        }
    }
    fillPaint.alpha = 255

    // How many boxes are already full
    drawOutlinedText(canvas, countStr, bx + boxW + 14f * u + countW / 2f, rect.centerY(), countSz, Color.rgb(255, 215, 50))
}

// Settings overlay — slides up from bottom
// -----------------------------------------------------------------------
internal fun GameView.drawSettings(canvas: Canvas, now: Long) {
    val eased  = easeOutQuint(settingsAnim)
    val slideY = panelRect.height() * (1f - eased)

    dimPaint.alpha = (eased * 160).toInt()
    canvas.drawRect(0f, 0f, surfaceW.toFloat(), surfaceH.toFloat(), dimPaint)

    canvas.save()
    canvas.translate(0f, slideY)

    val k  = u * settingsSc      // everything inside the panel scales with the fit factor
    val pr = 22f * k
    val bd = 6f * k; val ring = 3f * k

    // Panel: dark cartoon outline, white ring, cream fill
    fillPaint.color = Color.argb(230, 28, 12, 0)
    canvas.drawRoundRect(RectF(panelRect.left - bd, panelRect.top - bd, panelRect.right + bd, panelRect.bottom + bd), pr + bd, pr + bd, fillPaint)
    strokePaint.color = Color.argb(200, 255, 255, 255); strokePaint.strokeWidth = ring; strokePaint.alpha = 255
    canvas.drawRoundRect(RectF(panelRect.left - ring, panelRect.top - ring, panelRect.right + ring, panelRect.bottom + ring), pr + ring, pr + ring, strokePaint)
    fillPaint.color = theme.panelBg; fillPaint.alpha = 255
    canvas.drawRoundRect(panelRect, pr, pr, fillPaint)

    val pl = panelRect.left; val pw = panelRect.width(); val pt = panelRect.top
    val pad = 16f * k

    // Title
    val titleX = pl + pw / 2f; val titleY = pt + 48f * k; val titleSz = 30f * k
    textPaint.color = Color.argb(100, 0, 0, 0); textPaint.textSize = titleSz; textPaint.textAlign = Paint.Align.CENTER
    canvas.drawText("Settings", titleX + 2f * k, titleY + 2.5f * k, textPaint)
    textPaint.color = theme.textPrimary
    canvas.drawText("Settings", titleX, titleY, textPaint)
    textOutlinePaint.color = Color.argb(90, 0, 0, 0); textOutlinePaint.strokeWidth = 2.5f * k
    textOutlinePaint.textSize = titleSz; textOutlinePaint.textAlign = Paint.Align.CENTER; textOutlinePaint.typeface = boldTypeface
    canvas.drawText("Settings", titleX, titleY, textOutlinePaint)

    drawSectionLabel(canvas, "Sound", pl + pad, soundRects[0].top - 8f * k)
    drawSettingsBtn(canvas, now, soundRects[0], onOffLabels[0], prefs.soundEnabled)
    drawSettingsBtn(canvas, now, soundRects[1], onOffLabels[1], !prefs.soundEnabled)

    drawSectionLabel(canvas, "Hints", pl + pad, hintRects[0].top - 8f * k)
    val hintsOn = prefs.hintDelayMs != 0L
    drawSettingsBtn(canvas, now, hintRects[0], onOffLabels[0], hintsOn)
    drawSettingsBtn(canvas, now, hintRects[1], onOffLabels[1], !hintsOn)

    drawSectionLabel(canvas, "Donut size", pl + pad, gridRects[0].top - 8f * k)
    for (i in 0 until 2) drawSettingsBtn(canvas, now, gridRects[i], gridLabels[i], gridOptions[i] == prefs.gridSize)

    // New game: two-tap confirm so a stray tap cannot wipe the board
    val confirmActive = resetConfirmMs >= 0 && (now - resetConfirmMs) < RESET_CONFIRM_MS
    if (resetConfirmMs >= 0 && !confirmActive) resetConfirmMs = -1L
    val resetColor = if (confirmActive) Color.rgb(220, 130, 30) else Color.rgb(200, 70, 50)
    val resetLabel = if (confirmActive) "Tap again!" else "New game"
    drawPrettyButton(canvas, settingsResetRect, resetColor, resetLabel, 18f * k, pressScaleFor(settingsResetRect, now))
    if (confirmActive) {
        val progress = 1f - (now - resetConfirmMs).toFloat() / RESET_CONFIRM_MS
        val bx = settingsResetRect.left + 10f * k
        val bw = settingsResetRect.width() - 20f * k
        val by = settingsResetRect.bottom - 8f * k
        val bh = 3f * k
        fillPaint.color = Color.argb(60, 28, 12, 0)
        canvas.drawRoundRect(scratchRectF.apply { set(bx, by, bx + bw, by + bh) }, bh / 2, bh / 2, fillPaint)
        fillPaint.color = Color.argb(220, 255, 200, 60)
        canvas.drawRoundRect(scratchRectF.apply { set(bx, by, bx + bw * progress, by + bh) }, bh / 2, bh / 2, fillPaint)
        fillPaint.alpha = 255
    }

    drawPrettyButton(canvas, settingsCloseRect, Color.rgb(60, 175, 80), "Done  \u2713", 18f * k, pressScaleFor(settingsCloseRect, now))

    canvas.restore()
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
    val labelSz  = 15f * k
    val barW     = 5f * k
    val barPad   = 2f * k
    val textX    = x + barW + 8f * k

    textPaint.textSize  = labelSz
    textPaint.textAlign = Paint.Align.LEFT

    val barTop = baselineY - labelSz * 0.88f
    val barBot = baselineY + labelSz * 0.18f

    // Accent bar: dark border then theme color
    fillPaint.color = Color.argb(200, 28, 12, 0)
    canvas.drawRoundRect(RectF(x - barPad, barTop - barPad, x + barW + barPad, barBot + barPad),
        barW / 2f + barPad, barW / 2f + barPad, fillPaint)
    fillPaint.color = theme.accent; fillPaint.alpha = 255
    canvas.drawRoundRect(RectF(x, barTop, x + barW, barBot),
        barW / 2f, barW / 2f, fillPaint)
    fillPaint.alpha = 255

    val upper = text.uppercase()
    textPaint.letterSpacing = 0.10f
    textPaint.color = Color.argb(70, 0, 0, 0)
    canvas.drawText(upper, textX + 1f * k, baselineY + 1f * k, textPaint)
    textPaint.color = theme.textPrimary
    canvas.drawText(upper, textX, baselineY, textPaint)
    textPaint.letterSpacing = 0f
}

internal fun GameView.drawSettingsBtn(canvas: Canvas, now: Long, rect: RectF, label: String, selected: Boolean) {
    val k = u * settingsSc
    val pressScale = pressScaleFor(rect, now)
    canvas.save(); canvas.scale(pressScale, pressScale, rect.centerX(), rect.centerY())
    val borderPad = if (selected) 4f * k else 3f * k
    // Dark cartoon border
    fillPaint.color = Color.argb(210, 28, 12, 0)
    canvas.drawRoundRect(
        RectF(rect.left - borderPad, rect.top - borderPad, rect.right + borderPad, rect.bottom + borderPad),
        14f * k, 14f * k, fillPaint
    )
    // Gold ring on selected
    if (selected) {
        strokePaint.color = Color.rgb(255, 215, 50); strokePaint.strokeWidth = 2.5f * k; strokePaint.alpha = 255
        canvas.drawRoundRect(
            RectF(rect.left - borderPad + 1f * k, rect.top - borderPad + 1f * k,
                  rect.right + borderPad - 1f * k, rect.bottom + borderPad - 1f * k),
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
