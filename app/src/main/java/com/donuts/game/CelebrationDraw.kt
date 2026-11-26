package com.donuts.game

import android.graphics.*
import kotlin.math.*

// The milestone celebration: confetti in its look's style, the banner in its colour, golden sparkles.
// Part of GameView: extension functions on it. The looks themselves are chosen in CelebrationMix.kt.

internal fun GameView.drawCelebration(canvas: Canvas, now: Long) {
    val elapsed = (now - celebrateMs).toFloat()
    if (elapsed > CELEBRATE_MS) { celebrateMs = -1L; particles.clear(); return }

    // Confetti physics, in place and time-based: correct at any frame rate, no allocations
    val k       = ((now - physicsMs).coerceIn(0L, 100L)) / 16.667f     // frames' worth of time since last step
    physicsMs   = now
    val gravity = cellSize * 0.005f                                       // per frame-equivalent
    val alpha   = ((1f - (elapsed / CELEBRATE_MS)) * 255).toInt().coerceIn(0, 255)
    val iter = particles.iterator()
    while (iter.hasNext()) {
        val p = iter.next()
        p.vy  += gravity * k
        if (p.fall > 0f) {
            // Rain flutters: falls no faster than its own speed and sways side to side as it spins
            if (p.vy > p.fall) p.vy = p.fall
            p.x += cos(Math.toRadians(p.rot.toDouble() * 0.6).toFloat()) * p.fall * 0.6f * k
        }
        p.x   += p.vx * k
        p.y   += p.vy * k
        p.rot += p.rotSpeed * k
        if (p.y >= surfaceH + cellSize) { iter.remove(); continue }
        canvas.save()
        canvas.translate(p.x, p.y)
        canvas.rotate(p.rot)
        if (p.ring) {
            strokePaint.color = (p.color and 0x00FFFFFF) or (alpha shl 24)
            strokePaint.strokeWidth = p.radius * 0.55f
            canvas.drawCircle(0f, 0f, p.radius * 0.8f, strokePaint)
        } else {
            fillPaint.color = (p.color and 0x00FFFFFF) or (alpha shl 24)
            scratchRectF.set(-p.radius, -p.radius * 0.45f, p.radius, p.radius * 0.45f)
            canvas.drawRoundRect(scratchRectF, p.radius * 0.4f, p.radius * 0.4f, fillPaint)
        }
        canvas.restore()
    }
    fillPaint.alpha = 255; strokePaint.alpha = 255

    // Banner: pops in with a little overshoot, holds, fades
    val SLIDE_MS = 320f; val FADE_MS = 400f
    val bannerAlpha = when {
        elapsed < SLIDE_MS               -> ((elapsed / SLIDE_MS) * 255).toInt()
        elapsed < CELEBRATE_MS - FADE_MS -> 255
        else -> (((CELEBRATE_MS - elapsed) / FADE_MS) * 255).toInt()
    }.coerceIn(0, 255)
    val t  = (elapsed / SLIDE_MS).coerceIn(0f, 1f)
    val sc = if (reducedMotion) 1f else 0.5f + 0.5f * easeOutBack(t)
    val bw = min(board.cols * cellSize * 0.92f, 340f * u)
    val cheer = celebrateCheer
    val bh = (if (cheer.isEmpty()) 72f else 108f) * u
    val bx = boardLeft + board.cols * cellSize / 2f - bw / 2f
    val by = boardTop + board.rows * cellSize * 0.5f - bh / 2f
    canvas.save()
    canvas.scale(sc, sc, bx + bw / 2f, by + bh / 2f)
    fillPaint.color = Color.argb((bannerAlpha * 0.35f).toInt(), 0, 0, 0)
    canvas.drawRoundRect(scratchRectF.apply { set(bx + 3f * u, by + 6f * u, bx + bw + 3f * u, by + bh + 6f * u) }, 18f * u, 18f * u, fillPaint)
    fillPaint.color = ink(bannerAlpha)
    canvas.drawRoundRect(scratchRectF.apply { set(bx - 4f * u, by - 4f * u, bx + bw + 4f * u, by + bh + 4f * u) }, 20f * u, 20f * u, fillPaint)
    val look = celebrateLook ?: CelebrationMix.CLASSIC
    fillPaint.color = (GameTheme.bannerColors[look.banner] and 0x00FFFFFF) or (bannerAlpha shl 24)
    canvas.drawRoundRect(scratchRectF.apply { set(bx, by, bx + bw, by + bh) }, 16f * u, 16f * u, fillPaint)
    canvas.save()
    canvas.clipRect(bx, by, bx + bw, by + bh * 0.45f)
    fillPaint.color = Color.argb((bannerAlpha * 0.35f).toInt(), 255, 255, 255)
    canvas.drawRoundRect(scratchRectF.apply { set(bx, by, bx + bw, by + bh) }, 16f * u, 16f * u, fillPaint)
    canvas.restore()
    textPaint.textAlign = Paint.Align.CENTER
    textPaint.textSize  = 28f * u
    textPaint.letterSpacing = 0.05f
    val maxW = bw - 24f * u
    fitText(celebrateLabel, maxW)
    // One line sits in the middle; with a cheer the main line rides higher and the cheer sits under it
    val ty = (if (cheer.isEmpty()) by + bh / 2f else by + bh * 0.38f) + textPaint.textSize * 0.36f
    drawBannerLine(canvas, celebrateLabel, bx + bw / 2f, ty, bannerAlpha)
    if (cheer.isNotEmpty()) {
        textPaint.textSize = 21f * u
        fitText(cheer, maxW)
        drawBannerLine(canvas, cheer, bx + bw / 2f, by + bh * 0.74f + textPaint.textSize * 0.36f, bannerAlpha)
    }
    textPaint.letterSpacing = 0f
    canvas.restore()
    if (look.golden) drawBannerSparkles(canvas, bx, by, bw, bh, elapsed, bannerAlpha)
}

// The golden shower's banner twinkles: four white sparks at its corners, each pulsing on its own beat
private fun GameView.drawBannerSparkles(canvas: Canvas, bx: Float, by: Float, bw: Float, bh: Float,
                                        elapsed: Float, alpha: Int) {
    fillPaint.color = Color.argb(alpha, 255, 255, 255)
    for (i in 0 until 4) {
        val pulse = if (reducedMotion) 1f else 0.6f + 0.4f * sin(elapsed / 140f + i * 1.7f)
        val r  = 13f * u * pulse
        val cx = if (i % 2 == 0) bx + 6f * u else bx + bw - 6f * u
        val cy = if (i < 2) by + 4f * u else by + bh - 4f * u
        scratchPath.reset()
        for (j in 0 until 8) {
            val rr = if (j % 2 == 0) r else r * 0.32f
            val px = cx + sparkCos[j] * rr; val py = cy + sparkSin[j] * rr
            if (j == 0) scratchPath.moveTo(px, py) else scratchPath.lineTo(px, py)
        }
        scratchPath.close()
        canvas.drawPath(scratchPath, fillPaint)
    }
    fillPaint.alpha = 255
}
