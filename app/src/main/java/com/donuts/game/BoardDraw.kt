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

// Drawing the board: background, cells, chain string, hint ring, flashes.
// Part of GameView: these are extension functions on it, split out of GameView.kt.

// -----------------------------------------------------------------------
// Reset flash overlay
// -----------------------------------------------------------------------
internal fun GameView.drawResetFlash(canvas: Canvas, now: Long) {
    if (resetFlashMs < 0) return
    val t = ((now - resetFlashMs).toFloat() / FLASH_MS).coerceIn(0f, 1f)
    if (t >= 1f) { resetFlashMs = -1L; return }
    // Bright white burst fades out — ease-in so it hits fast then decays
    val alpha = ((1f - t) * (1f - t) * 220).toInt().coerceIn(0, 220)
    flashPaint.color = Color.argb(alpha, 255, 255, 255)
    canvas.drawRect(0f, 0f, surfaceW.toFloat(), surfaceH.toFloat(), flashPaint)
}

// -----------------------------------------------------------------------
// Big-chain color flash (chain ≥ 6)
// -----------------------------------------------------------------------
internal fun GameView.drawChainFlash(canvas: Canvas, now: Long) {
    if (chainFlashMs < 0) return
    val t = ((now - chainFlashMs).toFloat() / CHAIN_FLASH_MS).coerceIn(0f, 1f)
    if (t >= 1f) { chainFlashMs = -1L; return }
    val alpha = ((1f - t) * (1f - t) * 85).toInt().coerceIn(0, 85)
    flashPaint.color = (chainFlashColor and 0x00FFFFFF) or (alpha shl 24)
    canvas.drawRect(0f, 0f, surfaceW.toFloat(), surfaceH.toFloat(), flashPaint)
}

// -----------------------------------------------------------------------
// Board & cells
// -----------------------------------------------------------------------
internal fun GameView.drawBoardBackground(canvas: Canvas) {
    val boardR = boardLeft + board.cols * cellSize
    val boardB = boardTop  + board.rows * cellSize

    // Chunky drop shadow, offset for cartoon depth
    shadowPaint.color = Color.argb(80, 0, 0, 0)
    canvas.drawRoundRect(scratchRectF.apply { set(boardLeft, boardTop + 8f * u, boardR + 4f * u, boardB + 8f * u) }, 14f * u, 14f * u, shadowPaint)
    // Thick dark cartoon border
    val bd = 7f * u
    fillPaint.color = Color.argb(220, 28, 12, 0)
    canvas.drawRoundRect(scratchRectF.apply { set(boardLeft - bd, boardTop - bd, boardR + bd, boardB + bd) }, 16f * u, 16f * u, fillPaint)
    // Board fill
    val fi = 2f * u
    fillPaint.color = theme.boardBg; fillPaint.alpha = 255
    canvas.drawRoundRect(scratchRectF.apply { set(boardLeft - fi, boardTop - fi, boardR + fi, boardB + fi) }, 13f * u, 13f * u, fillPaint)

    // Polka-dot texture: subtle circles at cell intersections
    val dotR = cellSize * 0.06f
    fillPaint.color = Color.argb(28, 28, 12, 0)
    canvas.save()
    canvas.clipRect(boardLeft - fi, boardTop - fi, boardR + fi, boardB + fi)
    for (r in 0..board.rows) {
        for (c in 0..board.cols) {
            canvas.drawCircle(boardLeft + c * cellSize, boardTop + r * cellSize, dotR, fillPaint)
        }
    }
    canvas.restore()
    fillPaint.alpha = 255

}

internal fun GameView.drawChainLine(canvas: Canvas) {
    if (dragChain.size < 2) return
    scratchPath2.rewind()
    dragChain.forEachIndexed { i, (r, c) ->
        val cx = boardLeft + c * cellSize + cellSize / 2f
        val cy = boardTop  + r * cellSize + cellSize / 2f
        if (i == 0) scratchPath2.moveTo(cx, cy) else scratchPath2.lineTo(cx, cy)
    }
    val path = scratchPath2
    val chainColor = dragChainType?.glazeColor ?: Color.WHITE
    val cr = Color.red(chainColor); val cg = Color.green(chainColor); val cb = Color.blue(chainColor)
    // Grows with the chain: 0 at 1 cell, 1 at 8+
    val boost = ((dragChain.size - 1f) / 7f).coerceIn(0f, 1f)
    // A string threaded through the donuts, drawn over them: soft glow, dark rope, bright core
    chainOutlinePaint.strokeWidth = cellSize * (0.34f + boost * 0.16f)
    chainOutlinePaint.color = Color.argb((70 + boost * 90).toInt(), cr, cg, cb)
    canvas.drawPath(path, chainOutlinePaint)
    chainOutlinePaint.strokeWidth = cellSize * (0.20f + boost * 0.04f)
    chainOutlinePaint.color = Color.argb(230, 28, 12, 0)
    canvas.drawPath(path, chainOutlinePaint)
    chainLinePaint.strokeWidth = cellSize * (0.12f + boost * 0.03f)
    chainLinePaint.color = Color.argb(255, cr, cg, cb)
    canvas.drawPath(path, chainLinePaint)
    chainLinePaint.strokeWidth = cellSize * 0.045f
    chainLinePaint.color = Color.argb(200, 255, 255, 255)
    canvas.drawPath(path, chainLinePaint)
}

internal fun GameView.drawCells(canvas: Canvas, now: Long) {
    val frame = 7f * u
    canvas.save()
    canvas.clipRect(boardLeft - frame, boardTop - frame,
                    boardLeft + board.cols * cellSize + frame, boardTop + board.rows * cellSize + frame)
    drawCellsInner(canvas, now)
    canvas.restore()
}

/** The "try these" ring used by the idle hint and the first-run demo. */
internal fun GameView.drawHintRing(canvas: Canvas, cx: Float, cy: Float, radius: Float, alpha: Int) {
    hintRingPaint.color       = Color.argb(alpha * 220 / 255, 28, 12, 0)
    hintRingPaint.strokeWidth = cellSize * 0.17f
    canvas.drawCircle(cx, cy, radius, hintRingPaint)
    hintRingPaint.color       = Color.argb(alpha, 255, 255, 255)
    hintRingPaint.strokeWidth = cellSize * 0.10f
    canvas.drawCircle(cx, cy, radius, hintRingPaint)
}

/** Sideways wiggle for near-miss feedback: decaying sine, nothing under reduced motion. */
internal fun GameView.nearMissOffset(r: Int, c: Int, now: Long): Float {
    if (reducedMotion) return 0f
    var off = 0f
    if (wobbleMs >= 0) {
        val t = (now - wobbleMs).toFloat() / WOBBLE_MS
        if (t >= 1f) wobbleMs = -1L
        else if (Pair(r, c) in wobbleCells) off += sin(t * PI.toFloat() * 5f) * (1f - t) * cellSize * 0.08f
    }
    if (shakeMs >= 0) {
        val t = (now - shakeMs).toFloat() / SHAKE_MS
        if (t >= 1f) { shakeMs = -1L; shakeCell = null }
        else if (shakeCell?.first == r && shakeCell?.second == c) off += sin(t * PI.toFloat() * 4f) * (1f - t) * cellSize * 0.07f
    }
    return off
}

internal fun GameView.drawCellsInner(canvas: Canvas, now: Long) {
    val popSet  = popCells.map { it.row to it.col }.toSet()
    // During DROPPING, entire changed columns are hidden via dropColMask so
    // survivors don't flicker at their new positions before the animation ends.

    val hintAlpha = if (hintCells.isNotEmpty()) {
        val t = ((now - hintPulseMs) % 900L) / 900f
        val pulse = if (t < 0.5f) t * 2f else (1f - t) * 2f
        (190 + (65 * pulse)).toInt()      // stays bright; the pulse is mostly in the size
    } else 0

    for (r in 0 until board.rows) {
        for (c in 0 until board.cols) {
            if ((r to c) in popSet || c in dropColMask) continue
            var entryYOff = 0f
            var skipCell  = false
            if (boardEntryMs >= 0) {
                val elapsed = now - boardEntryMs - c * 55L
                if (elapsed < 0) {
                    skipCell = true
                } else {
                    val t     = (elapsed.toFloat() / BOARD_ENTRY_MS).coerceIn(0f, 1f)
                    val eased = 1f - (1f - t) * (1f - t) * (1f - t)
                    entryYOff = -cellSize * 3f * (1f - eased)
                }
            }
            if (skipCell) continue
            val cx      = boardLeft + c * cellSize + cellSize / 2f
            val cy      = boardTop  + r * cellSize + cellSize / 2f + entryYOff
            val inChain = Pair(r, c) in dragChain

            // Idle breathing: each cell breathes at a slightly different phase
            // Period 1400–2200ms, amplitude ±5%. Feels alive.
            // Suppressed during shuffle so the pop animation reads cleanly.
            // Idle breathing is a gentle bob (a translate, not a scale) so sprites blit unscaled
            val breatheOff = if (!reducedMotion && animPhase == AnimPhase.IDLE && !inChain && shuffleAnimMs < 0) {
                val phase  = (r * board.cols + c) * 0.61f   // golden-ratio-ish spread
                val period = 1400f + (r * board.cols + c) % 5 * 160f
                sin((now / period + phase) * 2f * PI.toFloat()) * cellSize * 0.025f
            } else 0f
            val breatheScale = 1f

            // Shuffle pop: each cell shrinks to 0 then bounces back up with a stagger
            val shuffleScale = if (shuffleAnimMs >= 0) {
                val cellDelay = ((r * 5 + c * 3 + r * c) % 16).toLong() * 19L
                val elapsed   = now - shuffleAnimMs - cellDelay
                when {
                    elapsed <= 0L              -> 1f
                    elapsed >= SHUFFLE_ANIM_MS -> 1f
                    else -> {
                        val t = elapsed.toFloat() / SHUFFLE_ANIM_MS
                        when {
                            t < 0.35f -> 1f - (t / 0.35f)                          // shrink to 0
                            t < 0.62f -> (t - 0.35f) / 0.27f * 1.38f              // pop up big
                            else      -> 1.38f - ((t - 0.62f) / 0.38f) * 0.38f    // settle to 1.0
                        }
                    }
                }
            } else 1f

            // Ping scale-pop when cell joins chain
            val pingMs = chainPings[Pair(r, c)]
            val pingScale = pingMs?.let { pm ->
                val t = ((now - pm).toFloat() / PING_MS).coerceIn(0f, 1f)
                if (t < 0.35f) 1f + (t / 0.35f) * 0.28f
                else 1.28f - ((t - 0.35f) / 0.65f) * 0.28f
            } ?: 1f

            val finalScale = (if (inChain) breatheScale * pingScale else breatheScale) * shuffleScale
            val pieceR = cellSize * 0.43f * finalScale

            var sqX = 1f; var sqY = 1f
            if (!reducedMotion && landMs >= 0 && c in landedCols) {
                val lt = (now - landMs).toFloat() / LAND_MS
                if (lt < 1f) { val sv = sin(lt * PI.toFloat()); sqX = 1f + 0.16f * sv; sqY = 1f - 0.20f * sv }
            }
            canvas.save()
            canvas.translate(nearMissOffset(r, c, now), breatheOff)
            if (sqX != 1f || sqY != 1f) canvas.scale(sqX, sqY, cx, cy + pieceR)
            if (board.grid[r][c].isGolden) drawBall(canvas, cx, cy, pieceR, inChain, 255)
            else drawPiece(canvas, cx, cy, pieceR, board.grid[r][c].type, inChain)
            canvas.restore()

            // Golden shimmer overlay — rotating gold ring + warm tint
            if (board.grid[r][c].isGolden) {
                drawGoldenOverlay(canvas, cx, cy, pieceR, now)
            }

            // Expanding ring on ping — colored to match the donut
            pingMs?.let { pm ->
                val t = ((now - pm).toFloat() / PING_MS).coerceIn(0f, 1f)
                val ringAlpha = ((1f - t) * (1f - t) * 220).toInt().coerceIn(0, 255)
                val pieceColor = board.grid[r][c].type.glazeColor
                strokePaint.color       = Color.argb(ringAlpha,
                    Color.red(pieceColor), Color.green(pieceColor), Color.blue(pieceColor))
                strokePaint.strokeWidth = cellSize * 0.07f
                canvas.drawCircle(cx, cy, cellSize * (0.43f + t * 0.40f), strokePaint)
                strokePaint.alpha = 255
            }

            if (hintCells.isNotEmpty() && Pair(r, c) in hintCells) {
                // Hint ring pulses in both alpha AND scale for a bouncier feel
                val hintT = ((now - hintPulseMs) % 900L) / 900f
                val hintPulse = if (hintT < 0.5f) hintT * 2f else (1f - hintT) * 2f
                val hintRingScale = 1f + hintPulse * 0.09f
                // White ring on a dark rope, like the drag halo: reads on every flavour,
                // including the yellow vanilla donuts and the tan board
                canvas.save()
                canvas.scale(hintRingScale, hintRingScale, cx, cy)
                drawHintRing(canvas, cx, cy, cellSize * 0.49f * breatheScale, hintAlpha)
                canvas.restore()
            }
        }
    }

    if (boardEntryMs >= 0 && now - boardEntryMs >= BOARD_ENTRY_MS + board.cols * 55L) {
        boardEntryMs = -1L
    }

    if (animPhase == AnimPhase.POPPING) drawPoppingCells(canvas, now)

    if (animPhase == AnimPhase.DROPPING) {
        val t     = ((now - animStartMs).toFloat() / DROP_MS).coerceIn(0f, 1f)
        val eased = 1f - (1f - t) * (1f - t) * (1f - t)
        for (cell in dropCells) {
            val cx     = boardLeft + cell.col     * cellSize + cellSize / 2f
            val startY = boardTop  + cell.fromRow * cellSize + cellSize / 2f
            val endY   = boardTop  + cell.row     * cellSize + cellSize / 2f
            val cy     = startY + (endY - startY) * eased
            if (cell.isGolden) { drawBall(canvas, cx, cy, cellSize * 0.43f, false, 255); drawGoldenOverlay(canvas, cx, cy, cellSize * 0.43f, now) }
            else drawPiece(canvas, cx, cy, cellSize * 0.43f, cell.type, false)
        }
    }
}

// -----------------------------------------------------------------------
// Golden shimmer overlay
// -----------------------------------------------------------------------
/**
 * Draws a rotating gold ring and a warm tint over any piece to mark it as golden.
 * Rendered AFTER the base piece so it sits on top without clipping complexity.
 */
internal fun GameView.drawGoldenOverlay(canvas: Canvas, cx: Float, cy: Float, r: Float, now: Long) {
    // Slow spin: one full rotation every 2400 ms
    val spinAngle = (now % 2400L) / 2400f * 360f
    strokePaint.color = Color.argb(200, 255, 210, 30)
    strokePaint.strokeWidth = r * 0.13f
    canvas.save()
    canvas.rotate(spinAngle, cx, cy)
    scratchRectF.set(cx - r * 1.14f, cy - r * 1.14f, cx + r * 1.14f, cy + r * 1.14f)
    for (i in 0 until 4) canvas.drawArc(scratchRectF, i * 90f, 60f, false, strokePaint)
    canvas.restore()
    strokePaint.alpha = 255
    // Small spark at top-left
    val sparkR  = r * 0.22f
    val sparkCx = cx - r * 0.62f
    val sparkCy = cy - r * 0.62f
    fillPaint.color = Color.argb(230, 255, 240, 80)
    scratchPath.rewind()
    for (i in 0 until 8) {
        val sr = if (i % 2 == 0) sparkR else sparkR * 0.38f
        val sx = sparkCx + sr * sparkCos[i]
        val sy = sparkCy + sr * sparkSin[i]
        if (i == 0) scratchPath.moveTo(sx, sy) else scratchPath.lineTo(sx, sy)
    }
    scratchPath.close()
    canvas.drawPath(scratchPath, fillPaint)
    fillPaint.alpha = 255
}

// -----------------------------------------------------------------------
// Popping cells. Each cell has its own clock (delayMs) so a power-up can pop in a ripple.
// Before its turn a cell is a normal donut, plus the power-up's build-up:
// BOOM swells the middle donut and shoves the neighbours, KABOOM wiggles the matching donuts.
// -----------------------------------------------------------------------
internal fun GameView.drawPoppingCells(canvas: Canvas, now: Long) {
    val fx       = powerFx
    val chainSet = pendingResult?.chainCells?.toSet() ?: emptySet()
    val lead     = if (fx != null) PopTiming.leadMs(fx.kind).toFloat() else 0f
    val sinceGo  = (now - animStartMs).toFloat()
    for (cell in popCells) {
        val cx0 = boardLeft + cell.col * cellSize + cellSize / 2f
        val cy0 = boardTop  + cell.row * cellSize + cellSize / 2f
        val t   = ((now - animStartMs - cell.delayMs).toFloat() / POP_MS).coerceIn(-1f, 1f)
        val isBonus = !isCascade && Pair(cell.row, cell.col) !in chainSet
        if (now - animStartMs < cell.delayMs) {
            // Waiting for its turn
            var cx = cx0; var cy = cy0
            var sx = 1f; var sy = 1f; var rot = 0f
            if (fx != null && !reducedMotion) {
                when (fx.kind) {
                    PowerUp.BOMB -> {
                        val p = (sinceGo / lead).coerceIn(0f, 1f)
                        if (cell.row == fx.originRow && cell.col == fx.originCol) {
                            // Squash-and-stretch swell: tall and thin, then fat
                            val s = 1f + 0.35f * p
                            sx = s * (1f - 0.12f * kotlin.math.sin(p * 9.4f)); sy = s * (1f + 0.12f * kotlin.math.sin(p * 9.4f))
                        } else if (isBonus) {
                            val dx = (cell.col - fx.originCol).toFloat(); val dy = (cell.row - fx.originRow).toFloat()
                            val len = kotlin.math.sqrt(dx * dx + dy * dy).coerceAtLeast(1f)
                            val shove = cellSize * 0.18f * p
                            cx += dx / len * shove; cy += dy / len * shove
                        }
                    }
                    PowerUp.COLOR_BURST -> if (isBonus) {
                        rot = 12f * kotlin.math.sin(sinceGo * 0.045f + cell.col * 1.7f + cell.row)
                    }
                    else -> {}
                }
            }
            canvas.save()
            canvas.rotate(rot, cx, cy)
            canvas.scale(sx, sy, cx, cy)
            if (cell.isGolden) drawBall(canvas, cx, cy, cellSize * 0.43f, false, 255)
            else drawPiece(canvas, cx, cy, cellSize * 0.43f, cell.type, false, 255)
            canvas.restore()
            continue
        }
        // Squash-and-stretch: grow, hold, squash wide, vanish
        val scaleX: Float; val scaleY: Float
        when {
            t < 0.25f -> { val p = t / 0.25f; scaleX = 1f + p * 0.25f; scaleY = 1f + p * 0.75f }
            t < 0.50f -> { scaleX = 1.25f; scaleY = 1.75f }
            t < 0.75f -> { val p = (t - 0.50f) / 0.25f; scaleX = 1.25f + p * 0.80f; scaleY = 1.75f - p * 1.30f }
            else      -> { val p = (t - 0.75f) / 0.25f; scaleX = 2.05f * (1f - p); scaleY = 0.45f * (1f - p) }
        }
        val alpha = ((1f - t * t * t) * 255).toInt().coerceIn(0, 255)
        canvas.save()
        canvas.scale(scaleX, scaleY, cx0, cy0)
        if (cell.isGolden) drawBall(canvas, cx0, cy0, cellSize * 0.43f, false, alpha)
        else drawPiece(canvas, cx0, cy0, cellSize * 0.43f, cell.type, false, alpha)
        canvas.restore()
        // Expanding burst ring: gold for bonus cells (power-up), white for chain cells
        val burstT     = (t / 0.6f).coerceIn(0f, 1f)
        val burstAlpha = ((1f - burstT) * 200).toInt().coerceIn(0, 255)
        strokePaint.strokeWidth = cellSize * 0.07f
        strokePaint.color = if (isBonus) Color.argb(burstAlpha, 255, 210, 0) else Color.argb(burstAlpha, 255, 255, 255)
        val ringScale = if (isBonus) 0.52f + burstT * 0.72f else 0.44f + burstT * 0.52f
        canvas.drawCircle(cx0, cy0, cellSize * ringScale, strokePaint)
    }
    strokePaint.alpha = 255
}
