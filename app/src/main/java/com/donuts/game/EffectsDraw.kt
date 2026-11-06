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

// Overlays: float labels, pings, confetti and banners, sticker fly, tutorial, no-moves.
// Part of GameView: these are extension functions on it, split out of GameView.kt.

// -----------------------------------------------------------------------
// Floating labels (NICE!, AMAZING!, etc.)
// -----------------------------------------------------------------------
internal fun GameView.drawFloatLabels(canvas: Canvas, now: Long) {
    synchronized(floatLabels) {
        val iter = floatLabels.iterator()
        while (iter.hasNext()) {
            val fl = iter.next()
            val t = ((now - fl.startMs).toFloat() / FLOAT_MS).coerceIn(0f, 1f)
            if (t >= 1f) { iter.remove(); continue }
            val alpha = if (t > 0.6f) ((1f - (t - 0.6f) / 0.4f) * 255).toInt().coerceIn(0, 255) else 255
            val rise  = cellSize * 1.8f * t
            val scale = if (t < 0.12f) (t / 0.12f) * 1.3f else 1.3f - (t - 0.12f) * 0.3f
            val sz    = 28f * u * scale
            canvas.save()
            canvas.translate(fl.cx, fl.cy - rise)
            // Shadow
            textPaint.color     = Color.argb((alpha * 0.4f).toInt(), 0, 0, 0)
            textPaint.textSize  = sz
            textPaint.textAlign = Paint.Align.CENTER
            canvas.drawText(fl.text, 2f * u, 2f * u + sz * 0.36f, textPaint)
            // Outline
            textOutlinePaint.color         = Color.argb((alpha * 0.8f).toInt(), 28, 12, 0)
            textOutlinePaint.textSize      = sz
            textOutlinePaint.textAlign     = Paint.Align.CENTER
            textOutlinePaint.strokeWidth   = 4f * u
            textOutlinePaint.typeface      = boldTypeface
            textOutlinePaint.letterSpacing = 0.08f
            canvas.drawText(fl.text, 0f, sz * 0.36f, textOutlinePaint)
            // Fill
            textPaint.color         = Color.argb(alpha, Color.red(fl.color), Color.green(fl.color), Color.blue(fl.color))
            textPaint.letterSpacing = 0.08f
            canvas.drawText(fl.text, 0f, sz * 0.36f, textPaint)
            textPaint.letterSpacing         = 0f
            textOutlinePaint.letterSpacing  = 0f
            canvas.restore()
        }
    }
}

// -----------------------------------------------------------------------
// Center chain-count pop
// -----------------------------------------------------------------------
internal fun GameView.drawCenterPing(canvas: Canvas, now: Long) {
    if (dragChain.isEmpty()) return
    val t = if (centerPingMs < 0) 1f else ((now - centerPingMs).toFloat() / PING_MS).coerceIn(0f, 1f)
    val (lr, lc) = dragChain.last()
    val baseCx = boardLeft + lc * cellSize + cellSize / 2f
    val baseCy = boardTop  + lr * cellSize + cellSize / 2f
    val pop = if (reducedMotion) 1f else if (t < 0.3f) 1f + (t / 0.3f) * 0.6f else 1.6f - ((t - 0.3f) / 0.7f) * 0.6f
    val big = dragChain.size >= 5                       // a power-up is coming
    val sz  = cellSize * (if (big) 0.95f else 0.70f) * pop
    val cx  = baseCx.coerceIn(boardLeft + sz * 0.6f, boardLeft + board.cols * cellSize - sz * 0.6f)
    val above = baseCy - cellSize * 0.85f
    val cy  = if (above - sz * 0.5f < boardTop - cellSize * 0.3f) baseCy + cellSize * 0.85f + sz * 0.3f else above
    textPaint.textSize = sz; textPaint.textAlign = Paint.Align.CENTER
    textOutlinePaint.textSize = sz; textOutlinePaint.textAlign = Paint.Align.CENTER
    textOutlinePaint.typeface = boldTypeface; textOutlinePaint.strokeWidth = sz * 0.14f
    textOutlinePaint.color = Color.argb(255, 28, 12, 0)
    canvas.drawText("${dragChain.size}", cx, cy + sz * 0.36f, textOutlinePaint)
    textPaint.color = if (big) Color.rgb(255, 215, 50) else Color.WHITE
    canvas.drawText("${dragChain.size}", cx, cy + sz * 0.36f, textPaint)
}

// -----------------------------------------------------------------------
// Milestone celebration
// -----------------------------------------------------------------------
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
    val bh = 72f * u
    val bx = boardLeft + board.cols * cellSize / 2f - bw / 2f
    val by = boardTop + board.rows * cellSize * 0.5f - bh / 2f
    canvas.save()
    canvas.scale(sc, sc, bx + bw / 2f, by + bh / 2f)
    fillPaint.color = Color.argb((bannerAlpha * 0.35f).toInt(), 0, 0, 0)
    canvas.drawRoundRect(scratchRectF.apply { set(bx + 3f * u, by + 6f * u, bx + bw + 3f * u, by + bh + 6f * u) }, 18f * u, 18f * u, fillPaint)
    fillPaint.color = Color.argb(bannerAlpha, 28, 12, 0)
    canvas.drawRoundRect(scratchRectF.apply { set(bx - 4f * u, by - 4f * u, bx + bw + 4f * u, by + bh + 4f * u) }, 20f * u, 20f * u, fillPaint)
    fillPaint.color = Color.argb(bannerAlpha, 255, 210, 50)
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
    while (textPaint.textSize > 10f * u && textPaint.measureText(celebrateLabel) > maxW) textPaint.textSize *= 0.92f
    val ty = by + bh / 2f + textPaint.textSize * 0.36f
    textPaint.color = Color.argb((bannerAlpha * 0.5f).toInt(), 0, 0, 0)
    canvas.drawText(celebrateLabel, bx + bw / 2f + 2f * u, ty + 2f * u, textPaint)
    textPaint.color = Color.argb(bannerAlpha, 90, 40, 0)
    canvas.drawText(celebrateLabel, bx + bw / 2f, ty, textPaint)
    textPaint.letterSpacing = 0f
    canvas.restore()
}

internal fun GameView.drawStickerFly(canvas: Canvas, now: Long) {
    val t = (now - stickerFlyMs).toFloat() / STICKER_FLY_MS
    if (t >= 1f) {
        stickerFlyMs = -1L; starPulseMs = now
        // The last sticker has landed: the whole collection is done, and that is a party
        if (allStickersParty) { allStickersParty = false; celebrate(now, "★ ALL 12 STICKERS! ★") }
        return
    }
    val i  = stickerFlyIdx
    val bx = boardLeft + board.cols * cellSize / 2f
    val by = boardTop + board.rows * cellSize / 2f
    val sx = stickersBtnRect.centerX(); val sy = stickersBtnRect.centerY()
    val big = cellSize * 1.3f
    val cx: Float; val cy: Float; val r: Float
    if (t < 0.45f) {
        val p = t / 0.45f
        cx = bx; cy = by; r = big * (if (reducedMotion) 1f else easeOutBack(p))
        strokePaint.color = Color.argb(((1f - p) * 200).toInt(), 255, 215, 50); strokePaint.strokeWidth = 5f * u
        canvas.drawCircle(bx, by, big * (0.6f + p * 1.2f), strokePaint)
        strokePaint.alpha = 255
    } else {
        val p = easeOutQuint((t - 0.45f) / 0.55f)
        cx = bx + (sx - bx) * p; cy = by + (sy - by) * p
        r = big * (1f - 0.7f * p)
    }
    if (r <= 1f) return
    fillPaint.color = Color.argb(255, 28, 12, 0)
    canvas.drawRoundRect(scratchRectF.apply { set(cx - r - 3f * u, cy - r - 3f * u, cx + r + 3f * u, cy + r + 3f * u) }, r * 0.28f, r * 0.28f, fillPaint)
    fillPaint.color = STICKER_COLORS[i]; fillPaint.alpha = 255
    canvas.drawRoundRect(scratchRectF.apply { set(cx - r, cy - r, cx + r, cy + r) }, r * 0.25f, r * 0.25f, fillPaint)
    drawStickerArt(canvas, i, cx, cy, r * 1.5f)
    if (t < 0.45f) {
        val sz = 22f * u
        textPaint.textSize = sz
        textOutlinePaint.textSize = sz; textOutlinePaint.textAlign = Paint.Align.CENTER
        textOutlinePaint.typeface = boldTypeface; textOutlinePaint.strokeWidth = 4f * u
        textOutlinePaint.color = Color.argb(255, 28, 12, 0)
        canvas.drawText("NEW!", cx, cy - r - 14f * u, textOutlinePaint)
        textPaint.color = Color.rgb(255, 215, 50)
        canvas.drawText("NEW!", cx, cy - r - 14f * u, textPaint)
    }
    fillPaint.alpha = 255
}

// -----------------------------------------------------------------------
// First-run tutorial overlay
// -----------------------------------------------------------------------
internal fun GameView.drawTutorial(canvas: Canvas, now: Long) {
    // Hands off while the board is busy or the child is already dragging
    if (animPhase != AnimPhase.IDLE || dragChain.isNotEmpty() || boardEntryMs >= 0) { tutorialStartMs = -1L; return }
    if (tutorialCells.isEmpty()) tutorialCells = chainOrder(board.findHint())
    if (tutorialCells.size < 3) { tutorialActive = false; prefs.tutorialSeen = true; return }
    if (tutorialStartMs < 0L) tutorialStartMs = now
    val loopT = ((now - tutorialStartMs) % TUTORIAL_LOOP_MS).toFloat()

    val r  = cellSize * 0.43f
    val xs = FloatArray(3) { boardLeft + tutorialCells[it].second * cellSize + cellSize / 2f }
    val ys = FloatArray(3) { boardTop  + tutorialCells[it].first  * cellSize + cellSize / 2f }

    // Dim everything on the board except the three donuts to connect
    scratchPath.rewind()
    for (i in 0 until 3) scratchPath.addCircle(xs[i], ys[i], r * 1.25f, Path.Direction.CW)
    canvas.save()
    canvas.clipOutPath(scratchPath)
    fillPaint.color = Color.argb(140, 40, 20, 0)
    canvas.drawRect(boardLeft - 8f * u, boardTop - 8f * u,
                    boardLeft + board.cols * cellSize + 8f * u, boardTop + board.rows * cellSize + 8f * u, fillPaint)
    canvas.restore()

    // Pulsing gold ring on each of the three
    val pulse = 0.5f + 0.5f * sin(now / 220f)
    for (i in 0 until 3) drawHintRing(canvas, xs[i], ys[i], r * (1.12f + 0.06f * pulse), (150 + 105 * pulse).toInt())

    // Timeline: fade in, sweep 0->1, sweep 1->2, hold with a burst, fade out
    val FADE = 250f; val SWEEP = 650f; val HOLD = 500f
    val t1 = FADE; val t2 = t1 + SWEEP; val t3 = t2 + SWEEP; val t4 = t3 + HOLD
    val alpha: Float; val seg: Float          // seg = how far along the chain, 0..2
    when {
        loopT < t1 -> { alpha = loopT / FADE; seg = 0f }
        loopT < t2 -> { alpha = 1f; seg = easeOutQuint((loopT - t1) / SWEEP) }
        loopT < t3 -> { alpha = 1f; seg = 1f + easeOutQuint((loopT - t2) / SWEEP) }
        loopT < t4 -> { alpha = 1f; seg = 2f }
        else       -> { alpha = (1f - (loopT - t4) / (TUTORIAL_LOOP_MS - t4)).coerceIn(0f, 1f); seg = 2f }
    }
    val i0 = seg.toInt().coerceAtMost(1); val frac = (seg - i0).coerceIn(0f, 1f)
    val fx = xs[i0] + (xs[i0 + 1] - xs[i0]) * frac
    val fy = ys[i0] + (ys[i0 + 1] - ys[i0]) * frac

    // Trail drawn on top of the pieces so the path is unmistakable
    if (seg > 0f) {
        scratchPath.rewind()
        scratchPath.moveTo(xs[0], ys[0])
        if (seg >= 1f) scratchPath.lineTo(xs[1], ys[1])
        scratchPath.lineTo(fx, fy)
        val a = (alpha * 255).toInt()
        chainOutlinePaint.strokeWidth = cellSize * 0.30f; chainOutlinePaint.color = Color.argb((a * 0.85f).toInt(), 28, 12, 0)
        canvas.drawPath(scratchPath, chainOutlinePaint)
        chainLinePaint.strokeWidth = cellSize * 0.16f; chainLinePaint.color = Color.argb(a, 255, 255, 255)
        canvas.drawPath(scratchPath, chainLinePaint)
    }

    // Burst rings when the demo chain completes
    if (loopT >= t3 && loopT < t4) {
        val bt = (loopT - t3) / HOLD
        strokePaint.strokeWidth = cellSize * 0.06f
        strokePaint.color = Color.argb(((1f - bt) * 220).toInt(), 255, 255, 255)
        for (i in 0 until 3) canvas.drawCircle(xs[i], ys[i], r * (1f + bt * 0.9f), strokePaint)
        strokePaint.alpha = 255
    }

    drawFinger(canvas, fx, fy, (alpha * 255).toInt())
}

// Orders three same-type cells so each is adjacent to the next, i.e. a path a finger can drag.
internal fun GameView.chainOrder(cells: List<Pair<Int, Int>>): List<Pair<Int, Int>> {
    if (cells.size < 3) return emptyList()
    val (a, b, c) = cells
    fun adj(p: Pair<Int, Int>, q: Pair<Int, Int>) = adjacent8(p.first, p.second, q.first, q.second)
    return when {
        adj(a, b) && adj(b, c) -> listOf(a, b, c)
        adj(b, a) && adj(a, c) -> listOf(b, a, c)
        adj(a, c) && adj(c, b) -> listOf(a, c, b)
        else -> emptyList()
    }
}

// A chunky cartoon fingertip: skin-toned pad with a dark outline, finger trailing down-right.
internal fun GameView.drawFinger(canvas: Canvas, x: Float, y: Float, alpha: Int) {
    if (alpha <= 0) return
    val tipR = cellSize * 0.17f
    canvas.save()
    canvas.rotate(-25f, x, y)
    outlinePaint.color = Color.argb(alpha, 28, 12, 0); outlinePaint.strokeWidth = tipR * 0.35f
    scratchRectF.set(x - tipR * 0.85f, y, x + tipR * 0.85f, y + tipR * 3.2f)
    canvas.drawRoundRect(scratchRectF, tipR * 0.8f, tipR * 0.8f, outlinePaint)
    fillPaint.color = Color.argb(alpha, 255, 224, 190)
    canvas.drawRoundRect(scratchRectF, tipR * 0.8f, tipR * 0.8f, fillPaint)
    canvas.drawCircle(x, y, tipR, outlinePaint)
    canvas.drawCircle(x, y, tipR, fillPaint)
    fillPaint.color = Color.argb((alpha * 0.6f).toInt(), 255, 255, 255)
    canvas.drawCircle(x - tipR * 0.3f, y - tipR * 0.3f, tipR * 0.35f, fillPaint)
    canvas.restore()
    fillPaint.alpha = 255
}

// -----------------------------------------------------------------------
// No-moves warning banner
// -----------------------------------------------------------------------
internal fun GameView.drawNoMovesWarning(canvas: Canvas, now: Long) {
    if (noMovesWarningMs < 0) return
    val elapsed = (now - noMovesWarningMs).toFloat()
    // Fade in over 300ms
    val alpha = ((elapsed / 300f).coerceIn(0f, 1f) * 220).toInt()
    // Progress bar filling left to right over NO_MOVES_DELAY_MS
    val progress = (elapsed / NO_MOVES_DELAY_MS).coerceIn(0f, 1f)

    val boardCX = boardLeft + board.cols * cellSize / 2f
    val boardCY = boardTop  + board.rows * cellSize / 2f
    val bw = min(board.cols * cellSize * 0.82f, 280f * u)
    val bh = 64f * u
    val bx = boardCX - bw / 2f
    val by = boardCY - bh / 2f
    val bd = 4f * u

    // Dark cartoon border
    fillPaint.color = Color.argb(alpha, 28, 12, 0)
    canvas.drawRoundRect(scratchRectF.apply { set(bx - bd, by - bd, bx + bw + bd, by + bh + bd) }, 16f * u, 16f * u, fillPaint)
    // Panel fill
    fillPaint.color = Color.argb(alpha, 255, 230, 80)
    canvas.drawRoundRect(scratchRectF.apply { set(bx, by, bx + bw, by + bh) }, 12f * u, 12f * u, fillPaint)
    // Top highlight
    canvas.save()
    canvas.clipRect(bx, by, bx + bw, by + bh * 0.45f)
    fillPaint.color = Color.argb((alpha * 0.30f).toInt(), 255, 255, 255)
    canvas.drawRoundRect(scratchRectF.apply { set(bx, by, bx + bw, by + bh) }, 12f * u, 12f * u, fillPaint)
    canvas.restore()
    // Progress bar (countdown to shuffle)
    val pbH = 6f * u; val pbPad = 10f * u
    val pbY = by + bh - pbH - pbPad
    fillPaint.color = Color.argb((alpha * 0.25f).toInt(), 28, 12, 0)
    canvas.drawRoundRect(scratchRectF.apply { set(bx + pbPad, pbY, bx + bw - pbPad, pbY + pbH) }, pbH / 2, pbH / 2, fillPaint)
    fillPaint.color = Color.argb(alpha, 200, 120, 0)
    canvas.drawRoundRect(scratchRectF.apply { set(bx + pbPad, pbY, bx + pbPad + (bw - pbPad * 2) * progress, pbY + pbH) }, pbH / 2, pbH / 2, fillPaint)

    // Text: shadow then fill; shrinks to fit the banner width
    val lineY = by + bh * 0.46f
    val msg   = "Mixing it up!"
    textPaint.textSize  = 18f * u
    textPaint.textAlign = Paint.Align.CENTER
    while (textPaint.textSize > 8f * u && textPaint.measureText(msg) > bw - 16f * u) textPaint.textSize *= 0.92f
    textPaint.color = Color.argb(alpha / 2, 0, 0, 0)
    canvas.drawText(msg, boardCX + 1.5f * u, lineY + 1.5f * u, textPaint)
    textPaint.color = Color.argb(alpha, 100, 48, 0)
    canvas.drawText(msg, boardCX, lineY, textPaint)
}

// -----------------------------------------------------------------------
// Power-up shows: BOOM shockwave, WHOOSH streaks, KABOOM ripple. Thick, cartoony, saturated.
// Everything travels one cell per PopTiming.STEP_MS so it arrives as each donut pops.
// -----------------------------------------------------------------------
internal fun GameView.drawPowerUpFx(canvas: Canvas, now: Long) {
    val fx = powerFx ?: return
    if (animPhase != AnimPhase.POPPING) return
    val cx = boardLeft + fx.originCol * cellSize + cellSize / 2f
    val cy = boardTop  + fx.originRow * cellSize + cellSize / 2f
    val lead    = PopTiming.leadMs(fx.kind)
    val since   = (now - animStartMs - lead).toFloat()
    if (since < 0f) return
    val cells   = since / PopTiming.STEP_MS            // how many cells the wave has travelled
    val reach   = maxOf(board.rows, board.cols).toFloat() + 1f
    val fade    = (1f - cells / (reach + 1f)).coerceIn(0f, 1f)
    val light   = Color.argb(255,
        (Color.red(fx.color) + 255) / 2, (Color.green(fx.color) + 255) / 2, (Color.blue(fx.color) + 255) / 2)
    fun ring(radius: Float, width: Float, alpha: Float, color: Int) {
        if (radius <= 0f || alpha <= 0f) return
        val a = (alpha * 255).toInt().coerceIn(0, 255)
        strokePaint.strokeWidth = width + cellSize * 0.10f
        strokePaint.color = Color.argb(a, 40, 20, 30)           // dark outline
        canvas.drawCircle(cx, cy, radius, strokePaint)
        strokePaint.strokeWidth = width
        strokePaint.color = Color.argb(a, Color.red(color), Color.green(color), Color.blue(color))
        canvas.drawCircle(cx, cy, radius, strokePaint)
    }
    when (fx.kind) {
        PowerUp.BOMB -> ring(cells * cellSize, cellSize * 0.22f, fade + 0.15f, fx.color)
        PowerUp.COLOR_BURST -> for (i in 0 until 3) {
            val r = (cells - i * 1.2f) * cellSize
            ring(r, cellSize * (0.16f - i * 0.03f), fade, if (i == 1) light else fx.color)
        }
        PowerUp.ROW_BLAST -> {
            val head = cells * cellSize + cellSize * 0.5f
            val tail = cellSize * 1.8f
            val h    = cellSize * 0.30f
            for (dir in intArrayOf(-1, 1)) {
                val x1 = cx + dir * head
                val x0 = cx + dir * maxOf(0f, head - tail)
                val left = minOf(x0, x1); val right = maxOf(x0, x1)
                val a = (fade * 255).toInt().coerceIn(0, 255)
                // fading tail, then a bright rounded head with a dark outline
                fillPaint.color = Color.argb((a * 0.45f).toInt(), Color.red(fx.color), Color.green(fx.color), Color.blue(fx.color))
                canvas.drawRoundRect(left, cy - h * 0.8f, right, cy + h * 0.8f, h, h, fillPaint)
                val hl = if (dir > 0) x1 - cellSize * 0.9f else x1
                val hr = if (dir > 0) x1 else x1 + cellSize * 0.9f
                strokePaint.strokeWidth = cellSize * 0.08f
                strokePaint.color = Color.argb(a, 40, 20, 30)
                canvas.drawRoundRect(hl, cy - h, hr, cy + h, h, h, strokePaint)
                fillPaint.color = Color.argb(a, Color.red(light), Color.green(light), Color.blue(light))
                canvas.drawRoundRect(hl, cy - h, hr, cy + h, h, h, fillPaint)
            }
        }
        else -> {}
    }
}
