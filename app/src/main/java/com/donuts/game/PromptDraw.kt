package com.donuts.game

import android.graphics.Canvas
import android.graphics.Color
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

// The picture prompt: a chip beside the counter showing one donut and how many more of it to pop
// ("Pop 3 pink!" without a word). It counts every pop of that type, cascades too; there is no timer
// and nothing happens when it is ignored. Finishing one gets a star, a jingle and a "YAY!", and a new one follows.

private val PROMPT_TYPES = DonutType.values()
private const val SPARKLE_MS = 700L

/** Counts the pops toward the prompt, finishes it, and brings the next one PROMPT_NEXT_MS later. */
internal fun GameView.advancePrompt(now: Long) {
    var p = prompt ?: run {
        val saved = PicturePrompts.decode(prefs.picturePrompt, PROMPT_TYPES.size)
        promptsFinished = saved?.second ?: 0
        val first = saved?.first ?: PicturePrompts.next(promptRandom, -1, PROMPT_TYPES.size, promptsFinished)
        prompt = first
        if (saved == null) savePrompt(first)
        first
    }
    // The tutorial teaches the drag on its own; the prompt waits and does not count its chain
    if (tutorialActive) { promptSeen = -1; return }
    val seen = board.donutsCleared[PROMPT_TYPES[p.type]] ?: 0
    if (promptSeen < 0 || seen < promptSeen) {
        promptSeen = seen                     // the first look, or New game or a new grid zeroed the counts
    } else if (seen > promptSeen) {
        val next = PicturePrompts.after(p, seen - promptSeen)
        promptSeen = seen
        if (next != p) {
            p = next; prompt = next; promptPopMs = now
            if (next.complete) {
                promptsFinished++; promptDoneMs = now
                if (prefs.soundEnabled)  soundEngine.playPromptDone()
                if (prefs.hapticEnabled) hapticEngine.milestone()
                if (!promptRect.isEmpty) synchronized(floatLabels) {
                    floatLabels.add(FloatLabel("YAY!", promptRect.centerX(), promptRect.top, GameTheme.bannerColors[0], now))
                }
            }
            savePrompt(next)
        }
    }
    if (p.complete && promptDoneMs < 0) promptDoneMs = now   // restored already finished
    if (promptDoneMs >= 0 && now - promptDoneMs >= PROMPT_NEXT_MS) {
        val fresh = PicturePrompts.next(promptRandom, p.type, PROMPT_TYPES.size, promptsFinished)
        prompt = fresh
        promptSeen = board.donutsCleared[PROMPT_TYPES[fresh.type]] ?: 0
        promptDoneMs = -1L; promptPopMs = now
        savePrompt(fresh)
    }
}

internal fun GameView.savePrompt(p: Prompt) { prefs.picturePrompt = PicturePrompts.encode(p, promptsFinished) }

/**
 * Lays out the counter row: the prompt chip on the left, then the counter [counterW] wide, the pair centred
 * under the board and kept inside the safe area. Sets [promptRect] (empty when hidden or when it does not fit)
 * and returns the counter's centre x.
 */
internal fun GameView.layoutCounterRow(counterW: Float, top: Float, bottom: Float): Float {
    val cx = boardLeft + board.cols * cellSize / 2f
    promptRect.setEmpty()
    if (tutorialActive) return cx
    val h = min((bottom - top) * 0.80f, 84f * u)
    val chipW = h * 2.05f
    val gap = 12f * u
    val minL = insetL + 12f * u
    val maxR = surfaceW - insetR - 12f * u
    val total = chipW + gap + counterW
    if (total > maxR - minL) return cx
    val left = (cx - total / 2f).coerceIn(minL, maxR - total)
    val midY = (top + bottom) / 2f
    promptRect.set(left, midY - h / 2f, left + chipW, midY + h / 2f)
    return left + chipW + gap + counterW / 2f
}

/** The chip: a cream pill with the donut to pop and a big number, a star once it is done. */
internal fun GameView.drawPromptChip(canvas: Canvas, now: Long) {
    val p = prompt ?: return
    val r = promptRect
    if (r.isEmpty) return
    val h = r.height(); val rad = h / 2f
    val cx = r.centerX(); val cy = r.centerY()
    val type = PROMPT_TYPES[p.type]

    val bounce = if (promptPopMs >= 0 && !reducedMotion) {
        val t = ((now - promptPopMs).toFloat() / PROMPT_POP_MS).coerceIn(0f, 1f)
        if (t >= 1f) { promptPopMs = -1L; 1f } else 1f + sin(t * PI.toFloat()) * (if (p.complete) 0.22f else 0.12f)
    } else { promptPopMs = -1L; 1f }
    canvas.save(); canvas.scale(bounce, bounce, cx, cy)

    // Thick cartoon outline round a cream pill, gold once the prompt is done
    val ow = max(3f * u, h * 0.09f)
    fillPaint.color = Color.argb(235, 28, 12, 0)
    canvas.drawRoundRect(scratchRectF.apply { set(r.left - ow, r.top - ow, r.right + ow, r.bottom + ow) }, rad + ow, rad + ow, fillPaint)
    fillPaint.color = if (p.complete) GameTheme.bannerColors[0] else Color.rgb(255, 244, 222)
    canvas.drawRoundRect(r, rad, rad, fillPaint)
    fillPaint.color = Color.argb(70, 255, 255, 255)
    canvas.drawRoundRect(scratchRectF.apply { set(r.left + rad * 0.6f, r.top + h * 0.10f, r.right - rad * 0.6f, r.top + h * 0.28f) }, h * 0.09f, h * 0.09f, fillPaint)

    // The donut to pop, bobbing a little while it waits
    val bob = if (reducedMotion || p.complete) 0f else sin(now / 380.0).toFloat() * h * 0.035f
    drawPiece(canvas, r.left + h * 0.56f, cy + bob, h * 0.36f, type, false)

    // How many more, in the donut's own colour; a star when there are none left
    val nx = r.left + h * 1.46f
    if (p.complete) drawStarIcon(canvas, nx, cy, h * 0.34f)
    else drawOutlinedText(canvas, p.left.toString(), nx, cy, h * 0.72f, type.labelColor)
    canvas.restore()

    // A burst of gold sparkles when it is done
    if (p.complete && promptDoneMs >= 0 && !reducedMotion) {
        val t = ((now - promptDoneMs).toFloat() / SPARKLE_MS)
        if (t < 1f) {
            fillPaint.color = GameTheme.goldColors[0]; fillPaint.alpha = ((1f - t) * 255).toInt()
            val dist = h * (0.7f + t * 0.9f)
            for (k in 0 until 8) {
                val a = k * PI / 4
                canvas.drawCircle(cx + (cos(a) * dist * 1.3f).toFloat(), cy + (sin(a) * dist).toFloat(), h * 0.07f * (1f - t * 0.5f), fillPaint)
            }
            fillPaint.alpha = 255
        }
    }
}
