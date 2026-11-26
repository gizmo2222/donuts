package com.donuts.game

import kotlin.random.Random

/**
 * The smarter hint (B30): which chain the idle hint rings, and when and how the slow finger trace
 * that follows it moves. Pure, no Android types, unit tested.
 *
 * The hint is a real drag path (each cell next to the one before, golden cells wild), not just three
 * donuts of a type: up to [LONGEST] cells, so a bomb chain is shown when the board has one. A chain of
 * the picture prompt's donut gets a [PROMPT_BONUS], which beats one cell more but not two: a 4 of the
 * prompt's donut wins over a 5 of another, a 3 of it loses to a 5. Ties are broken by [Random], so the
 * hint is not always the top-left group. The trace only draws; it never plays the move.
 */
object HintRules {
    /** The longest hint: five, a bomb. Longer chains exist but five is enough to show what "long" is. */
    const val LONGEST = 5
    /** Score bonus for a chain of the picture prompt's donut, in cells. */
    const val PROMPT_BONUS = 1.5

    /** How long the hint rings alone before the slow finger trace starts. */
    const val TRACE_AFTER_MS = 5_000L
    /** Fade in, then this long per step from one donut to the next. Slow on purpose. */
    const val FADE_MS = 400L
    const val STEP_MS = 900L
    /** Rest on the last donut, then fade out, then a pause before the next pass. */
    const val HOLD_MS = 700L
    const val PAUSE_MS = 1_600L

    /** One pass of the trace for a path of [cells] donuts. */
    fun loopMs(cells: Int): Long = FADE_MS + STEP_MS * (cells - 1).coerceAtLeast(0) + HOLD_MS + FADE_MS + PAUSE_MS

    /**
     * Where the finger is [sinceMs] after the trace started, on a path of [cells] donuts: [seg] runs
     * 0 to cells-1 along the path, [alpha] 0 to 1. Under [reducedMotion] the finger rests on the first
     * donut with the whole path drawn ([seg] is the end), fully shown, never moving.
     */
    fun traceAt(sinceMs: Long, cells: Int, reducedMotion: Boolean): TracePose {
        val last = (cells - 1).coerceAtLeast(0).toFloat()
        if (reducedMotion) return TracePose(last, 1f, still = true)
        val t = sinceMs.coerceAtLeast(0L) % loopMs(cells)
        val sweepEnd = FADE_MS + STEP_MS * last.toLong()
        return when {
            t < FADE_MS -> TracePose(0f, t / FADE_MS.toFloat())
            t < sweepEnd -> {
                val s = (t - FADE_MS).toFloat() / STEP_MS
                val i = s.toInt()
                TracePose(i + smooth(s - i), 1f)
            }
            t < sweepEnd + HOLD_MS -> TracePose(last, 1f)
            t < sweepEnd + HOLD_MS + FADE_MS -> TracePose(last, 1f - (t - sweepEnd - HOLD_MS) / FADE_MS.toFloat())
            else -> TracePose(last, 0f)
        }
    }

    /** Ease in and out within a step, so the finger settles on each donut. */
    private fun smooth(x: Float): Float = x * x * (3f - 2f * x)

    /**
     * The hint path for [board]: the best-scoring drag path of three to [LONGEST] cells, [prefer]
     * (the picture prompt's donut, or null) earning [PROMPT_BONUS]. Empty when no chain of three exists.
     */
    fun pick(board: GameBoard, prefer: DonutType?, random: Random): List<Pair<Int, Int>> {
        val grid = board.grid
        val best = mutableListOf<List<Pair<Int, Int>>>()
        var bestScore = 0.0
        val path = ArrayList<Pair<Int, Int>>(LONGEST)
        val seen = Array(board.rows) { BooleanArray(board.cols) }
        var found: List<Pair<Int, Int>> = emptyList()

        // Longest path from the current end, first found wins (one candidate per start cell)
        fun go(r: Int, c: Int, type: DonutType?) {
            if (path.size > found.size) found = path.toList()
            if (path.size >= LONGEST) return
            for (dr in -1..1) for (dc in -1..1) {
                val nr = r + dr; val nc = c + dc
                if ((dr == 0 && dc == 0) || nr !in 0 until board.rows || nc !in 0 until board.cols || seen[nr][nc]) continue
                val n = grid[nr][nc]
                if (!n.isGolden && type != null && n.type != type) continue
                seen[nr][nc] = true; path.add(nr to nc)
                go(nr, nc, if (n.isGolden) type else n.type)
                path.removeAt(path.size - 1); seen[nr][nc] = false
                if (found.size >= LONGEST) return
            }
        }

        for (r in 0 until board.rows) for (c in 0 until board.cols) {
            val cell = grid[r][c]
            found = emptyList()
            seen[r][c] = true; path.add(r to c)
            go(r, c, if (cell.isGolden) null else cell.type)
            path.clear(); seen[r][c] = false
            if (found.size < 3) continue
            val type = found.firstOrNull { (fr, fc) -> !grid[fr][fc].isGolden }?.let { (fr, fc) -> grid[fr][fc].type }
            val score = found.size + if (prefer != null && type == prefer) PROMPT_BONUS else 0.0
            if (score > bestScore) { bestScore = score; best.clear() }
            if (score == bestScore) best.add(found)
        }
        return if (best.isEmpty()) emptyList() else best[random.nextInt(best.size)]
    }
}

/** The trace's finger: [seg] cells along the path (0 = first donut), [alpha] 0-1, [still] under reduced motion. */
data class TracePose(val seg: Float, val alpha: Float, val still: Boolean = false)
