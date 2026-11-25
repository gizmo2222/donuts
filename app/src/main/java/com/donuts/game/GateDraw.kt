package com.donuts.game

import android.graphics.*
import kotlin.math.*

// The grown-ups corner of the settings panel: the locked Grown-ups button on Steven's page,
// the parent gate (a times-table question in words and a keypad), and the promise to parents.
// Part of GameView: these are extension functions on it.

/** How long a settings page takes to pop in after a turn. */
internal const val PAGE_POP_MS = 180L
/** How long the answer box shakes (or flashes red, under reduced motion) after a wrong answer. */
internal const val GATE_WRONG_MS = 420L

/** Steven's page: a wide button with a padlock that leads to the gate. */
internal fun GameView.drawGrownUpsButton(canvas: Canvas, now: Long) {
    val k = u * settingsSc
    val rect = settingsGrownRect
    val press = pressScaleFor(rect, now)
    drawPrettyButton(canvas, rect, Color.rgb(90, 120, 200), "", 18f * k, press)
    canvas.save(); canvas.scale(press, press, rect.centerX(), rect.centerY())
    val label = "Grown-ups"
    textPaint.textSize = 18f * k
    val tw = textPaint.measureText(label)
    val ir = 14f * k
    val total = ir * 2f + 10f * k + tw
    val left = rect.centerX() - total / 2f
    drawLockIcon(canvas, left + ir, rect.centerY(), ir)
    drawLabel(canvas, label, left + ir * 2f + 10f * k + tw / 2f, rect.centerY() + 18f * k * 0.36f,
        18f * k, rect.width() - ir * 2f - 30f * k, Color.WHITE, outlined = true)
    canvas.restore()
}

/** The gate: the question in words, the box the digits go in, and the keypad. */
internal fun GameView.drawGatePage(canvas: Canvas, now: Long) {
    val k = u * settingsSc
    val pl = panelRect.left; val pw = panelRect.width()
    val qTop = gateAnswerRect.top - 48f * k
    drawLabel(canvas, parentGate.question, pl + pw / 2f, qTop + 30f * k, 21f * k, pw - 32f * k, theme.textPrimary, outlined = false)

    // The answer box: two slots; a wrong answer flashes it pink and shakes it (no shake under reduced motion)
    val wrongT = if (gateWrongMs < 0) 1f else ((now - gateWrongMs) / GATE_WRONG_MS.toFloat()).coerceIn(0f, 1f)
    val shake = if (wrongT < 1f && !reducedMotion) sin(wrongT * PI.toFloat() * 6f) * (1f - wrongT) * 10f * k else 0f
    val a = gateAnswerRect
    canvas.save(); canvas.translate(shake, 0f)
    fillPaint.color = Color.argb(210, 28, 12, 0)
    canvas.drawRoundRect(scratchRectF.apply { set(a.left - 3f * k, a.top - 3f * k, a.right + 3f * k, a.bottom + 3f * k) }, 14f * k, 14f * k, fillPaint)
    fillPaint.color = if (wrongT < 1f) Color.rgb(255, 210, 200) else Color.WHITE
    canvas.drawRoundRect(a, 12f * k, 12f * k, fillPaint)
    val slotW = a.width() / 2f
    for (i in 0..1) {
        val cx = a.left + slotW * (i + 0.5f)
        val ch = parentGate.entry.getOrNull(i)
        if (ch != null) drawLabel(canvas, ch.toString(), cx, a.centerY() + 30f * k * 0.36f, 30f * k, slotW, theme.textPrimary, outlined = false)
        else {
            strokePaint.color = theme.textPrimary; strokePaint.alpha = 140; strokePaint.strokeWidth = 3f * k
            canvas.drawLine(cx - 14f * k, a.bottom - 14f * k, cx + 14f * k, a.bottom - 14f * k, strokePaint)
            strokePaint.alpha = 255
        }
    }
    canvas.restore()

    for (d in 0..9) drawSettingsBtn(canvas, now, gateKeyRects[d], d.toString(), selected = false, textSp = 28f)
}

/** The grown-ups page's promise: what this game never does. Small, calm, two lines. */
internal fun GameView.drawPromise(canvas: Canvas) {
    val k = u * settingsSc
    val cx = panelRect.centerX(); val w = panelRect.width() - 32f * k
    val y0 = settingsResetRect.bottom + 26f * k
    val col = Color.argb(200, 60, 30, 0)
    drawLabel(canvas, "No ads. No purchases. No timers.", cx, y0, 14f * k, w, col, outlined = false)
    drawLabel(canvas, "Nothing leaves this phone.", cx, y0 + 20f * k, 14f * k, w, col, outlined = false)
}
