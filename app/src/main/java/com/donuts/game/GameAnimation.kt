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

// Per-frame state updates: pops, drops, counter, hint, celebrations, stickers earned.
// Part of GameView: these are extension functions on it, split out of GameView.kt.

// -----------------------------------------------------------------------
// Hint
// -----------------------------------------------------------------------
internal fun GameView.updateHint(now: Long) {
    val delay = prefs.hintDelayMs
    if (tutorialActive || delay == 0L || animPhase != AnimPhase.IDLE || dragChain.isNotEmpty()) return
    if (now - lastActionMs >= delay) {
        if (hintCells.isEmpty()) { hintCells = board.findHint(); hintPulseMs = now }
    } else {
        hintCells = emptyList()
    }
}

// -----------------------------------------------------------------------
// Game animation advance
// -----------------------------------------------------------------------
/** Power-up pops: as each bonus cell's turn comes, one soft sound and one tick (not one per cell). */
internal fun GameView.advancePowerPops(now: Long) {
    val fx = powerFx ?: return
    var added = false
    for (cell in popCells) {
        val key = Pair(cell.row, cell.col)
        if (cell.delayMs > 0 && now - animStartMs >= cell.delayMs && fx.popped.add(key)) added = true
    }
    if (!added) return
    if (prefs.soundEnabled)  soundEngine.playConnectBlip(3 + fx.popped.size)
    if (prefs.hapticEnabled) hapticEngine.tick()
}

internal fun GameView.advanceAnimation(now: Long) {
    if (animPhase == AnimPhase.POPPING) advancePowerPops(now)
    when (animPhase) {
        AnimPhase.POPPING -> if (now - animStartMs >= (if (isCascade) POP_MS else powerFx?.totalMs ?: POP_MS)) {
            powerFx = null
            if (!isCascade && popCells.any { it.isGolden }) prefs.goldenPopped = true

            // Snapshot golden flags BEFORE mutating the board.
            val preGolden = Array(board.rows) { r -> Array(board.cols) { c -> board.grid[r][c].isGolden } }

            // Capture the exact set of cells being removed BEFORE any board mutation.
            // For cascades, popCells was populated from findMatches() at DROPPING end.
            // For player clears, pendingResult holds chainCells + bonusCells.
            // Using exact cleared positions (rather than type-matching survivors) is
            // necessary to correctly handle columns with multiple cells of the same type.
            val clearedSet: Set<Pair<Int, Int>> = if (isCascade) {
                popCells.map { Pair(it.row, it.col) }.toSet()
            } else {
                val res = pendingResult
                if (res != null) (res.chainCells + res.bonusCells).toSet() else emptySet()
            }

            if (isCascade) {
                // Auto-resolve one cascade pass (findMatches → clear → gravity).
                board.resolveOnce()
            } else {
                // Player-initiated clear — apply the pre-computed result.
                pendingResult?.let { board.clearChain(it) }
                pendingResult = null
            }

            // Build dropCells using exact cleared-position knowledge.
            //
            // Gravity rule: surviving cells (those NOT in clearedSet) are packed to
            // the BOTTOM of the column in their original top-to-bottom order.
            // New cells (spawned to fill the gap) fall in from ABOVE the board.
            //
            // So for a column with k cleared cells:
            //   - Rows [0 .. k-1]   → new cells  (fromRow = row - k, i.e. above the board)
            //   - Rows [k .. rows-1] → survivors  (fromRow = original preRow)
            //
            // dropColMask hides changed columns from the normal draw loop.
            dropCells.clear(); dropColMask.clear()
            for (c in 0 until board.cols) {
                val clearedRowsInCol = (0 until board.rows)
                    .filter { r -> Pair(r, c) in clearedSet }
                    .toSet()
                if (clearedRowsInCol.isEmpty()) continue
                dropColMask.add(c)
                val k = clearedRowsInCol.size  // number of new cells at top

                // New cells: each starts k rows above its destination so they all
                // travel the same distance and land simultaneously.
                for (row in 0 until k)
                    dropCells.add(AnimCell(row, c, board.grid[row][c].type,
                        fromRow = row - k, isGolden = board.grid[row][c].isGolden))

                // Survivors: we know exactly which preRow each one came from, so
                // the animation source is always correct — even with duplicate types.
                var survivorIdx = 0
                for (preR in 0 until board.rows) {
                    if (preR !in clearedRowsInCol) {
                        val postRow = k + survivorIdx
                        dropCells.add(AnimCell(postRow, c, board.grid[postRow][c].type,
                            fromRow = preR, isGolden = preGolden[preR][c]))
                        survivorIdx++
                    }
                }
            }
            popCells.clear(); animPhase = AnimPhase.DROPPING; animStartMs = now
        }
        AnimPhase.DROPPING -> if (now - animStartMs >= DROP_MS) {
            landedCols.clear(); landedCols.addAll(dropColMask); landMs = now
            dropCells.clear(); dropColMask.clear()
            if (prefs.soundEnabled) soundEngine.playDropLand()

            // Check for cascades: if the board now has auto-matches, animate them.
            val cascadeMatches = board.findMatches()
            if (cascadeMatches.isNotEmpty()) {
                cascadeCount++
                isCascade = true
                popCells.clear()
                popCells.addAll(cascadeMatches.map { (r, c) ->
                    AnimCell(r, c, board.grid[r][c].type, isGolden = board.grid[r][c].isGolden)
                })
                animPhase = AnimPhase.POPPING; animStartMs = now
                // Floating cascade-combo label
                val cx = cascadeMatches.map { (_, c) -> boardLeft + c * cellSize + cellSize / 2f }.average().toFloat()
                val cy = cascadeMatches.map { (r, _) -> boardTop  + r * cellSize + cellSize / 2f }.average().toFloat()
                val comboLabel = "\u00d7${cascadeCount + 1}"
                synchronized(floatLabels) {
                    floatLabels.add(FloatLabel(comboLabel, cx, cy, Color.rgb(255, 220, 40), now))
                }
                if (prefs.soundEnabled)  soundEngine.playPopClear()
                if (prefs.hapticEnabled) hapticEngine.pop()
            } else {
                cascadeCount   = 0
                isCascade      = false
                animPhase      = AnimPhase.IDLE
                if (!board.hasValidMoves()) noMovesWarningMs = now
                else startHeldDrag()
            }
        }
        AnimPhase.IDLE -> {
            if (landMs >= 0 && now - landMs > LAND_MS) { landMs = -1L; landedCols.clear() }
            // Clear expired shuffle animation
            if (shuffleAnimMs >= 0 && now - shuffleAnimMs > SHUFFLE_ANIM_MS + SHUFFLE_MAX_DELAY)
                shuffleAnimMs = -1L
            // Auto-shuffle after warning delay
            if (noMovesWarningMs >= 0 && now - noMovesWarningMs >= NO_MOVES_DELAY_MS) {
                board.shuffle()
                shuffleAnimMs    = now
                noMovesWarningMs = -1L
                lastActionMs = now
                hintCells = emptyList()
                if (prefs.soundEnabled)  soundEngine.playShuffle()
                if (prefs.hapticEnabled) hapticEngine.shuffle()
            }
        }
    }
}

// -----------------------------------------------------------------------
// Settings panel slide animation
// -----------------------------------------------------------------------
internal fun GameView.advanceSettingsAnim(now: Long) {
    // Step by real elapsed time (capped), so the slide takes the same time at any frame rate
    val dt = if (panelAnimMs < 0) 16f else (now - panelAnimMs).coerceIn(0L, 50L).toFloat()
    panelAnimMs = now
    val settingsTarget = if (settingsOpen) 1f else 0f
    val settingsMs = if (settingsTarget > settingsAnim) SETTINGS_OPEN_MS else SETTINGS_CLOSE_MS
    val settingsStep = dt / settingsMs
    settingsAnim = if (settingsTarget > settingsAnim)
        (settingsAnim + settingsStep).coerceAtMost(1f)
    else
        (settingsAnim - settingsStep).coerceAtLeast(0f)

    val stickersTarget = if (stickersOpen) 1f else 0f
    val stickersMs = if (stickersTarget > stickersAnim) SETTINGS_OPEN_MS else SETTINGS_CLOSE_MS
    val stickersStep = dt / stickersMs
    stickersAnim = if (stickersTarget > stickersAnim)
        (stickersAnim + stickersStep).coerceAtMost(1f)
    else
        (stickersAnim - stickersStep).coerceAtLeast(0f)
}

// ease-out-quint: f(t) = 1 - (1-t)^5
internal fun GameView.easeOutQuint(t: Float): Float {
    val x = 1f - t
    return 1f - x * x * x * x * x
}

// -----------------------------------------------------------------------
// Counter count-up
// -----------------------------------------------------------------------
internal fun GameView.advanceCounter(now: Long) {
    val target = board.donutsCleared.values.sum()
    if (displayedCount < target) {
        val step = max(1, (target - displayedCount) / 4)
        val oldCount = displayedCount
        displayedCount = (displayedCount + step).coerceAtMost(target)
        // Record digit flips for any digit that changed
        if (prevDisplayCount >= 0) {
            var pos = 0; var m = maxOf(displayedCount, oldCount, 1)
            while (m > 0) {
                val p   = generateSequence(1) { it * 10 }.drop(pos).first()
                val nd  = (displayedCount / p) % 10
                val od  = (oldCount       / p) % 10
                if (nd != od) digitFlips[pos] = DigitFlip('0' + od, '0' + nd, now)
                m /= 10; pos++
            }
        }
        prevDisplayCount = displayedCount
        counterPulseMs = now
        // Check milestones
        for (m in MILESTONES) {
            if (m > lastMilestone && displayedCount >= m) {
                lastMilestone = m
                celebrate(now, if (m >= 100) "\u2605 $m DONUTS! \u2605" else "$m DONUTS!")
            }
        }
    } else if (displayedCount > target) {
        displayedCount = 0; prevDisplayCount = -1
        lastMilestone  = 0; digitFlips.clear()
    }
    // Particle physics is driven by drawCelebration() each frame; nothing to do here.
}

// Full-screen party: a banner plus confetti cannons from both bottom corners of the board
internal fun GameView.celebrate(now: Long, label: String) {
    celebrateMs    = now
    physicsMs      = now
    celebrateLabel = label
    spawnParticles()
    if (prefs.soundEnabled)  soundEngine.playMilestone()
    if (prefs.hapticEnabled) hapticEngine.milestone()
}

internal fun GameView.spawnParticles() {
    particles.clear()
    val n  = if (reducedMotion) 24 else 140
    val bL = boardLeft + cellSize * 0.3f
    val bR = boardLeft + board.cols * cellSize - cellSize * 0.3f
    val bB = boardTop + board.rows * cellSize
    val types = DonutType.values()
    for (i in 0 until n) {
        val fromLeft = i % 2 == 0
        val angle = Math.toRadians((if (fromLeft) -68.0 else -112.0) + (Math.random() - 0.5) * 55.0)
        val speed = cellSize * (0.20 + Math.random() * 0.20)
        particles.add(Particle(
            x        = if (fromLeft) bL else bR,
            y        = bB,
            vx       = (cos(angle) * speed).toFloat(),
            vy       = (sin(angle) * speed).toFloat(),
            color    = if (i % 4 == 0) SPRINKLE_COLORS[i % 6] else types[i % 6].glazeColor,
            radius   = (Math.random() * cellSize * 0.07 + cellSize * 0.05).toFloat(),
            rotSpeed = (Math.random() * 10f - 5f).toFloat(),
            ring     = i % 3 == 0
        ))
    }
}

internal fun GameView.easeOutBack(t: Float): Float {
    val c1 = 1.70158f; val c3 = c1 + 1f; val x = t - 1f
    return 1f + c3 * x * x * x + c1 * x * x
}

// Every sticker earned mid-game gets its moment; so does every full donut box
internal fun GameView.checkStickers(now: Long) {
    val boxes = StickerRules.boxesFilled(lifetimeDonuts())
    if (boxesSeen < 0) boxesSeen = boxes
    else if (boxes > boxesSeen && celebrateMs < 0 && stickerFlyMs < 0) {
        boxesSeen = boxes
        celebrate(now, "BOX FULL!")
        starPulseMs = now
    }

    val mask = StickerRules.earnedMask(stickerStats())
    if (earnedMask < 0) { earnedMask = mask; return }
    if (stickerFlyMs >= 0) return
    val fresh = mask and earnedMask.inv()
    if (fresh == 0) return
    val idx = Integer.numberOfTrailingZeros(fresh)
    earnedMask = earnedMask or (1 shl idx)
    if (earnedMask == (1 shl 12) - 1) allStickersParty = true
    stickerFlyIdx = idx; stickerFlyMs = now
    if (prefs.soundEnabled)  soundEngine.playMilestone()
    if (prefs.hapticEnabled) hapticEngine.milestone()
}

internal fun GameView.starPulse(now: Long): Float {
    if (starPulseMs < 0) return 1f
    val t = (now - starPulseMs).toFloat() / 450f
    if (t >= 1f) { starPulseMs = -1L; return 1f }
    return 1f + 0.35f * sin(t * PI.toFloat())
}

// Lifetime donuts as the player sees them (banked + what the counter shows), and the pops when a slot or box fills
internal fun GameView.advanceBoxStrip(now: Long) {
    val seen = (prefs.lifetimeDonuts + displayedCount - sessionSaved).coerceAtLeast(0)
    val before = boxStrip.shownLifetime
    boxStrip.shownLifetime = seen
    if (before < 0 || seen <= before) return        // first frame, or a reset: no pop
    if (StickerRules.slotJustFilled(before, seen)) {
        boxStrip.slotPopMs = now
        if (prefs.hapticEnabled) hapticEngine.tick()
    }
    if (StickerRules.boxJustFilled(before, seen)) boxStrip.boxPopMs = now
}
