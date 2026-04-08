package com.donuts.game

import android.content.Context
import android.graphics.*
import android.os.SystemClock
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.core.content.res.ResourcesCompat
import kotlin.math.*

class GameView(context: Context, initialBoard: GameBoard, private val prefs: Prefs) :
    SurfaceView(context), SurfaceHolder.Callback {

    private var board = initialBoard

    private val theme get() = GameTheme.all[prefs.themeIndex]

    // -----------------------------------------------------------------------
    // Layout
    // -----------------------------------------------------------------------
    private var surfaceW  = 0
    private var surfaceH  = 0
    private var cellSize  = 0f
    private var boardLeft = 0f
    private var boardTop  = 0f
    private val hudHeight = 220f
    private val counterH  = 210f

    private var resetBtnRect    = RectF()
    private var settingsBtnRect = RectF()
    private var soundBtnRect    = RectF()
    private var hapticBtnRect   = RectF()

    // Settings panel layout
    private var panelRect         = RectF()
    private val themeRects        = Array(4) { RectF() }
    private val hintRects         = Array(4) { RectF() }
    private val gridRects         = Array(2) { RectF() }
    private var settingsCloseRect = RectF()

    private val settingsSc = 1.5f   // draw-time scale for all settings text/icons

    private val hintOptions = longArrayOf(3_000L, 5_000L, 10_000L, 0L)
    private val hintLabels  = arrayOf("3s", "5s", "10s", "Off")
    private val gridOptions = intArrayOf(6, 8)
    private val gridLabels  = arrayOf("6×6", "8×8")

    private val packRects    = Array(4) { RectF() }
    private val hapticRects  = Array(3) { RectF() }
    private val packLabels   = arrayOf("Bubbly", "Space", "Wild", "Mech")
    private val packOptions  = intArrayOf(0, 1, 2, 3)
    private val hapticLabels = arrayOf("Gentle", "Normal", "Strong")
    private val hapticOptions = intArrayOf(0, 1, 2)

    // -----------------------------------------------------------------------
    // Hint
    // -----------------------------------------------------------------------
    private var lastActionMs = SystemClock.elapsedRealtime()
    private var hintCells    = emptyList<Pair<Int, Int>>()
    private var hintPulseMs  = 0L

    // -----------------------------------------------------------------------
    // Touch / drag
    // -----------------------------------------------------------------------
    private var selRow = -1
    private var selCol = -1
    private val dragChain = mutableListOf<Pair<Int, Int>>()

    // Returns the type of the chain: first non-golden cell's type, or first cell's type
    // if every cell in the chain is golden (all-golden chain is valid).
    private val dragChainType: DonutType?
        get() = dragChain.firstOrNull { (r, c) -> !board.grid[r][c].isGolden }
            ?.let { (r, c) -> board.grid[r][c].type }
            ?: dragChain.firstOrNull()?.let { (r, c) -> board.grid[r][c].type }

    // -----------------------------------------------------------------------
    // Game animation
    // -----------------------------------------------------------------------
    private enum class AnimPhase { IDLE, POPPING, DROPPING }
    @Volatile private var animPhase = AnimPhase.IDLE
    private var animStartMs = 0L
    private val POP_MS  = 280L
    private val DROP_MS = 380L

    // fromRow = starting row for animation (may be negative = above the board)
    // row     = destination row
    private data class AnimCell(
        val row: Int, val col: Int, val type: DonutType,
        val fromRow: Int = row, val isGolden: Boolean = false
    )
    private val popCells    = mutableListOf<AnimCell>()
    private val dropCells   = mutableListOf<AnimCell>()  // all moving cells during DROPPING
    private val dropColMask = mutableSetOf<Int>()        // columns hidden from the normal draw loop

    // Pre-computed chain clear result (set in handleUp, consumed in advanceAnimation).
    private var pendingResult: ChainResult? = null

    // Cascade counter — how many auto-resolve passes have fired after the player's clear.
    private var cascadeCount    = 0
    private var isCascade       = false
    private var cascadeLabelMs  = -1L
    private val CASCADE_LABEL_MS = 1000L

    // -----------------------------------------------------------------------
    // UI Animations
    // -----------------------------------------------------------------------

    // Settings panel slide-up
    private var settingsOpen    = false     // logical open/close intent
    private var settingsAnim    = 0f        // 0 = fully closed, 1 = fully open
    private val SETTINGS_OPEN_MS  = 200f
    private val SETTINGS_CLOSE_MS = 85f

    // Stickers panel
    private var stickersOpen      = false
    private var stickersAnim      = 0f
    private var stickersBtnRect   = RectF()
    private var stickersPressMs        = -1L
    private var stickerResetConfirmMs  = -1L
    private val STICKER_RESET_CONFIRM_MS = 1500L
    private var stickerPanelRect  = RectF()
    private val stickerRects      = Array(12) { RectF() }
    private var stickersCloseRect = RectF()
    private var stickersResetRect = RectF()

    // 12 stickers — 4 rows × 3 cols
    private val STICKER_SYMS   = arrayOf(
        "\uD83C\uDF69", "\u2B50",        "\uD83D\uDC51",  // 🍩 ⭐ 👑  row 1: Donut Collector
        "\uD83D\uDD17", "\u26A1",        "\uD83C\uDF1F",  // 🔗 ⚡ 🌟  row 2: Chain Builder
        "\uD83C\uDFAF", "\uD83C\uDFC6",  "\uD83D\uDC8E",  // 🎯 🏆 💎  row 3: High Scorer
        "\uD83D\uDD00", "\uD83D\uDD2D",  "\u2764"         // 🔀 🔭 ❤   row 4: Special
    )
    private val STICKER_NAMES  = arrayOf(
        "Donut Taster",   "Donut Lover",   "Donut King",
        "Chain Starter",  "Chain Pro",     "Chain Legend",
        "On The Board",   "High Scorer",   "Donut Master",
        "Survivor",       "Explorer",      "True Fan"
    )
    private val STICKER_DESCS  = arrayOf(
        "10 donuts",   "50 donuts",   "500 donuts",
        "chain of 4",  "chain of 6",  "chain of 8",
        "score 20",    "score 60",    "score 200",
        "5 shuffles",  "both grids",  "10 sessions"
    )
    private val STICKER_COLORS = intArrayOf(
        Color.rgb(255, 140,  60), Color.rgb(255, 200,  30), Color.rgb(220,  80,  50),
        Color.rgb( 70, 170, 255), Color.rgb( 90,  90, 240), Color.rgb(160,  50, 220),
        Color.rgb( 50, 200, 160), Color.rgb( 60, 190,  70), Color.rgb( 30, 130,  80),
        Color.rgb(255,  90,  90), Color.rgb(240, 110, 200), Color.rgb(160,  80, 230)
    )

    // Button press scale feedback
    private var resetPressMs    = -1L       // time of last reset press
    private var settingsPressMs = -1L       // time of last settings press
    private var soundPressMs    = -1L
    private var hapticPressMs   = -1L
    private val PRESS_MS        = 150L      // duration of press shrink

    // Counter count-up
    private var displayedCount  = 0         // animates toward actual total

    // Digit flip — each digit flips independently when its value changes
    private data class DigitFlip(val from: Char, val to: Char, val startMs: Long)
    private val digitFlips       = HashMap<Int, DigitFlip>()  // right-to-left digit position (0=ones)
    private var prevDisplayCount = -1
    private val DIGIT_FLIP_MS    = 200L

    // Counter heartbeat — panel scale-pulse when score increments
    private var counterPulseMs   = -1L
    private val COUNTER_PULSE_MS = 240L

    // Big-chain color flash overlay (chain ≥ 6)
    private var chainFlashMs     = -1L
    private var chainFlashColor  = 0
    private val CHAIN_FLASH_MS   = 200L

    // Shuffle pop animation
    private var shuffleAnimMs    = -1L
    private val SHUFFLE_ANIM_MS  = 360L
    private val SHUFFLE_MAX_DELAY = 300L   // max stagger across all cells

    // Reset flash overlay
    private var resetFlashMs    = -1L       // time of last reset
    private val FLASH_MS        = 350L

    // No-moves warning + auto-shuffle
    private var noMovesWarningMs = -1L
    private val NO_MOVES_DELAY_MS = 2400L

    // First-run tutorial
    private var tutorialActive  = !prefs.tutorialSeen
    private var tutorialStartMs = -1L
    private val TUTORIAL_LOOP_MS = 2800L

    // Chain connection ping — scale-pop when each new cell joins
    private val chainPings = mutableMapOf<Pair<Int,Int>, Long>()  // cell -> time added
    private val PING_MS = 220L
    // Big center count pop
    private var centerPingMs    = -1L
    private var centerPingCount = 0

    // Milestone celebration
    private val MILESTONES = intArrayOf(10, 25, 50, 100, 200, 500)
    private var lastMilestone   = 0
    private var celebrateMs     = -1L
    private val CELEBRATE_MS    = 2200L
    private var celebrateCount  = 0
    // Mutable class (not data class) so physics can update fields in-place each frame,
    // avoiding the 60-object copy + new-list allocation that a data-class copy() would incur.
    private class Particle(
        var x: Float, var y: Float,
        var vx: Float, var vy: Float,
        val color: Int, val radius: Float,
        val rotSpeed: Float, var rot: Float = 0f
    )
    private val particles = mutableListOf<Particle>()

    private data class FloatLabel(
        val text: String, val cx: Float, val cy: Float,
        val color: Int, val startMs: Long
    )
    private val floatLabels = mutableListOf<FloatLabel>()
    private val FLOAT_MS    = 1400L

    // Board entry drop-in animation
    private var boardEntryMs  = -1L
    private val BOARD_ENTRY_MS = 640L

    // Sound and haptic engines
    private val soundEngine  = SoundEngine()
    private val hapticEngine = HapticEngine(context)

    // -----------------------------------------------------------------------
    // Typeface — Fredoka One; falls back to system bold if unavailable
    // -----------------------------------------------------------------------
    private val boldTypeface: Typeface =
        try { ResourcesCompat.getFont(context, R.font.fredoka_one) ?: Typeface.DEFAULT_BOLD }
        catch (e: Exception) { Typeface.DEFAULT_BOLD }

    // -----------------------------------------------------------------------
    // Paints (allocated once)
    // -----------------------------------------------------------------------
    private val fillPaint         = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val strokePaint       = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val outlinePaint      = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
    private val textPaint         = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; typeface = boldTypeface }
    private val textOutlinePaint  = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeJoin = Paint.Join.ROUND; typeface = boldTypeface }
    private val chainOutlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
    private val chainLinePaint    = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
    private val hintRingPaint     = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val dimPaint          = Paint().apply { color = Color.argb(160, 0, 0, 0) }
    private val shadowPaint       = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val flashPaint        = Paint().apply { style = Paint.Style.FILL }

    // -----------------------------------------------------------------------
    // Scratch objects — allocated once, reused every frame via rewind()/set().
    // NEVER allocate Path/RectF inside the render loop; use these instead.
    // -----------------------------------------------------------------------
    // scratchPath  : transient paths (chain line, sheen clip, dino tail/spines,
    //                golden spark, anything used-then-discarded within one draw call)
    // scratchPath2 : clip paths that must survive while addSheen() runs inside them
    //                (donut glaze clip, star body clip, dino body clip)
    // scratchRectF : any transient RectF used for drawOval/drawRoundRect arguments
    private val scratchPath  = Path()
    private val scratchPath2 = Path()
    private val scratchRectF = RectF()

    // -----------------------------------------------------------------------
    // Precomputed trigonometry — computed once at class init, never recalculated.
    // -----------------------------------------------------------------------
    // 10 vertices of a 5-point star at angles -90°, -54°, -18°, …
    private val starCos = FloatArray(10) { cos(Math.toRadians(-90.0 + it * 36.0)).toFloat() }
    private val starSin = FloatArray(10) { sin(Math.toRadians(-90.0 + it * 36.0)).toFloat() }
    // 5 sprinkle dots at 72° intervals starting at 18°
    private val sprinkleCos = FloatArray(5) { cos(Math.toRadians(it * 72.0 + 18.0)).toFloat() }
    private val sprinkleSin = FloatArray(5) { sin(Math.toRadians(it * 72.0 + 18.0)).toFloat() }
    // 8 vertices of the ✦ spark drawn on golden cells (45° intervals, -90° start)
    private val sparkCos = FloatArray(8) { cos(Math.toRadians(it * 45.0 - 90.0)).toFloat() }
    private val sparkSin = FloatArray(8) { sin(Math.toRadians(it * 45.0 - 90.0)).toFloat() }

    // -----------------------------------------------------------------------
    // Lifecycle
    // -----------------------------------------------------------------------
    init { holder.addCallback(this); isFocusable = true }

    override fun surfaceCreated(holder: SurfaceHolder) {
        boardEntryMs = SystemClock.elapsedRealtime()
        RenderThread(holder).start()
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, w: Int, h: Int) {
        surfaceW = w; surfaceH = h; computeLayout()
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {}

    private fun computeLayout() {
        val w = surfaceW.toFloat(); val h = surfaceH.toFloat()
        if (w == 0f || h == 0f) return

        // Board size: constrained by width and by height (leaving room for counter)
        val boardPx = min(w, h - counterH) * 0.94f
        cellSize    = boardPx / board.cols
        boardLeft   = (w - boardPx) / 2f

        // Center the board+counter block on screen; never start above hudHeight minimum
        boardTop    = ((h - boardPx - counterH) / 2f).coerceAtLeast(hudHeight)

        // Buttons sit just above the board with a comfortable gap
        val btnH = 100f
        val btnY = boardTop - btnH - 26f
        val sbEnd = boardLeft + board.cols * cellSize
        // Left: RESET alone
        resetBtnRect    = RectF(boardLeft, btnY, boardLeft + 230f, btnY + btnH)
        // Centre: stickers 🏅 — anchored to screen midpoint
        stickersBtnRect = RectF(w / 2f - 65f, btnY, w / 2f + 65f, btnY + btnH)
        // Right cluster: ♪ · ≋ · ⚙ — 10px gaps
        settingsBtnRect = RectF(sbEnd - 100f, btnY, sbEnd,        btnY + btnH)
        hapticBtnRect   = RectF(sbEnd - 210f, btnY, sbEnd - 110f, btnY + btnH)
        soundBtnRect    = RectF(sbEnd - 320f, btnY, sbEnd - 220f, btnY + btnH)

        // Settings panel — width fits screen with margin; height computed from content
        val sc     = 1.5f
        val pad    = 24f * sc
        val thBtnH = 90f * sc
        val hBtnH  = 80f * sc
        val gBtnH  = 80f * sc
        val closeH = 80f * sc

        val packBtnH   = 80f * sc
        val hapticBtnH = 80f * sc
        // Stack content to find required panel height:
        // title (156*sc) + themeRow1 + gap + themeRow2 + gap + hintRow + gap + gridRow + gap + packRow + gap + hapticRow + gap + close + bottomPad
        val requiredPh = 156f*sc +
            thBtnH + 10f*sc + thBtnH +   // two theme rows
            62f*sc + hBtnH +              // hint section
            62f*sc + gBtnH +              // grid section
            62f*sc + packBtnH +           // sound pack section
            62f*sc + hapticBtnH +         // haptic theme section
            62f*sc + closeH + pad         // close + bottom padding
        val pw = min(w - 32f, 660f)
        val ph = requiredPh.coerceAtMost(h - 32f)
        val pl = (w - pw) / 2f
        val pt = (h - ph) / 2f
        panelRect = RectF(pl, pt, pl + pw, pt + ph)

        val thBtnW = (pw - pad * 3) / 2f
        val thRow1 = pt + 156f * sc
        val thRow2 = thRow1 + thBtnH + 10f * sc
        themeRects[0] = RectF(pl + pad,              thRow1, pl + pad + thBtnW,    thRow1 + thBtnH)
        themeRects[1] = RectF(pl + pad * 2 + thBtnW, thRow1, pl + pw - pad,        thRow1 + thBtnH)
        themeRects[2] = RectF(pl + pad,              thRow2, pl + pad + thBtnW,    thRow2 + thBtnH)
        themeRects[3] = RectF(pl + pad * 2 + thBtnW, thRow2, pl + pw - pad,        thRow2 + thBtnH)

        val hBtnW = (pw - pad * 5) / 4f
        val hTop  = thRow2 + thBtnH + 62f * sc
        for (i in 0 until 4) {
            val x = pl + pad + i * (hBtnW + pad)
            hintRects[i] = RectF(x, hTop, x + hBtnW, hTop + hBtnH)
        }

        val gBtnW = (pw - pad * 3) / 2f
        val gTop  = hTop + hBtnH + 62f * sc
        for (i in 0 until 2) {
            val x = pl + pad + i * (gBtnW + pad)
            gridRects[i] = RectF(x, gTop, x + gBtnW, gTop + gBtnH)
        }

        // Sound Pack buttons (4 in a row)
        val pBtnW = (pw - pad * 5) / 4f
        val pTop  = gTop + gBtnH + 62f * sc
        for (i in 0 until 4) {
            val x = pl + pad + i * (pBtnW + pad)
            packRects[i] = RectF(x, pTop, x + pBtnW, pTop + packBtnH)
        }

        // Haptic Theme buttons (3 in a row)
        val hThBtnW = (pw - pad * 4) / 3f
        val hThTop  = pTop + packBtnH + 62f * sc
        for (i in 0 until 3) {
            val x = pl + pad + i * (hThBtnW + pad)
            hapticRects[i] = RectF(x, hThTop, x + hThBtnW, hThTop + hapticBtnH)
        }

        settingsCloseRect = RectF(pl + pad, pt + ph - closeH - pad, pl + pw - pad, pt + ph - pad)

        // Sticker panel layout — 12 tiles, 4 rows × 3 cols
        // Panel fills width minus 40px margin (20 each side) and height minus 80px (40 each side)
        val spW   = w - 40f
        val spPad = 18f
        val spTitleH    = 116f
        val spStatsH    = 52f
        val spCloseH    = 88f
        // Tile size fills remaining height or width, whichever is tighter
        val tileFromW   = (spW - spPad * 4f) / 3f
        val tileFromH   = (h - 80f - spTitleH - spStatsH - spCloseH - spPad * 7f) / 4f
        val stickerBtnSide = min(tileFromW, tileFromH).coerceAtLeast(80f)
        val spContentH  = spTitleH + 4f * stickerBtnSide + 3f * spPad + spStatsH + spCloseH + spPad * 3f
        val spH = spContentH.coerceAtMost(h - 80f)
        val spL = (w - spW) / 2f
        val spT = (h - spH) / 2f
        stickerPanelRect = RectF(spL, spT, spL + spW, spT + spH)
        val stRow1Y = spT + spTitleH
        for (i in 0 until 12) {
            val col = i % 3; val row = i / 3
            val sx = spL + spPad + col * (stickerBtnSide + spPad)
            val sy = stRow1Y + row * (stickerBtnSide + spPad)
            stickerRects[i] = RectF(sx, sy, sx + stickerBtnSide, sy + stickerBtnSide)
        }
        // Bottom bar: "Done ✓" (wide, green) left · "Reset" (narrow, red) right
        val spResetW  = 130f
        val spDoneW   = spW - spPad * 3f - spResetW
        val closeRowY = stickerPanelRect.bottom - spCloseH - spPad
        stickersCloseRect = RectF(spL + spPad,                  closeRowY, spL + spPad + spDoneW, stickerPanelRect.bottom - spPad)
        stickersResetRect = RectF(spL + spW - spPad - spResetW, closeRowY, spL + spW - spPad,     stickerPanelRect.bottom - spPad)
    }

    private fun saveSession() {
        val sessionTotal = board.donutsCleared.values.sum()
        if (sessionTotal == 0) return
        prefs.lifetimeDonuts = prefs.lifetimeDonuts + sessionTotal
        val hs = if (board.cols == 6) prefs.highScore6x6 else prefs.highScore8x8
        if (sessionTotal > hs) {
            if (board.cols == 6) prefs.highScore6x6 = sessionTotal
            else prefs.highScore8x8 = sessionTotal
        }
        prefs.sessionCount = prefs.sessionCount + 1
    }

    private fun rebuildBoard() {
        board = GameBoard(rows = prefs.gridSize, cols = prefs.gridSize, sandbox = true)
        animPhase = AnimPhase.IDLE
        popCells.clear(); dropCells.clear()
        dragChain.clear(); selRow = -1; selCol = -1
        pendingResult  = null
        cascadeCount   = 0
        isCascade      = false
        cascadeLabelMs = -1L
        hintCells = emptyList()
        displayedCount = 0
        lastActionMs = SystemClock.elapsedRealtime()
        computeLayout()
        boardEntryMs = SystemClock.elapsedRealtime()
    }

    // -----------------------------------------------------------------------
    // Frame
    // -----------------------------------------------------------------------
    fun drawFrame(canvas: Canvas) {
        if (cellSize == 0f) return
        val now = SystemClock.elapsedRealtime()
        soundEngine.packIndex  = prefs.soundPackIndex
        hapticEngine.theme     = prefs.hapticTheme
        canvas.drawColor(theme.bg)
        advanceAnimation(now)
        updateHint(now)
        advanceSettingsAnim(now)
        advanceCounter(now)
        drawHUD(canvas, now)
        drawBoardBackground(canvas)
        drawChainLine(canvas)
        drawCells(canvas, now)
        drawCenterPing(canvas, now)
        drawFloatLabels(canvas, now)
        drawCounter(canvas, now)
        drawNoMovesWarning(canvas, now)
        if (celebrateMs >= 0) drawCelebration(canvas, now)
        if (settingsAnim > 0f) drawSettings(canvas, now)
        if (stickersAnim > 0f) drawStickersPanel(canvas, now)
        drawChainFlash(canvas, now)
        drawResetFlash(canvas, now)
        if (tutorialActive) drawTutorial(canvas, now)
    }

    // -----------------------------------------------------------------------
    // Hint
    // -----------------------------------------------------------------------
    private fun updateHint(now: Long) {
        val delay = prefs.hintDelayMs
        if (delay == 0L || animPhase != AnimPhase.IDLE || dragChain.isNotEmpty()) return
        if (now - lastActionMs >= delay) {
            if (hintCells.isEmpty()) { hintCells = board.findHint(); hintPulseMs = now }
        } else {
            hintCells = emptyList()
        }
    }

    // -----------------------------------------------------------------------
    // Game animation advance
    // -----------------------------------------------------------------------
    private fun advanceAnimation(now: Long) {
        when (animPhase) {
            AnimPhase.POPPING -> if (now - animStartMs >= POP_MS) {
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
                    val comboLabel = "COMBO ×${cascadeCount + 1}"
                    synchronized(floatLabels) {
                        floatLabels.add(FloatLabel(comboLabel, cx, cy, Color.rgb(255, 220, 40), now))
                    }
                    cascadeLabelMs = now
                    if (prefs.soundEnabled)  soundEngine.playPopClear()
                    if (prefs.hapticEnabled) hapticEngine.pop()
                } else {
                    cascadeCount   = 0
                    isCascade      = false
                    animPhase      = AnimPhase.IDLE
                    if (!board.hasValidMoves()) noMovesWarningMs = now
                }
            }
            AnimPhase.IDLE -> {
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
                    prefs.shufflesSurvived = prefs.shufflesSurvived + 1
                    if (prefs.soundEnabled)  soundEngine.playShuffle()
                    if (prefs.hapticEnabled) hapticEngine.shuffle()
                }
            }
        }
    }

    // -----------------------------------------------------------------------
    // Settings panel slide animation
    // -----------------------------------------------------------------------
    private fun advanceSettingsAnim(now: Long) {
        val settingsTarget = if (settingsOpen) 1f else 0f
        val settingsMs = if (settingsTarget > settingsAnim) SETTINGS_OPEN_MS else SETTINGS_CLOSE_MS
        val settingsStep = (1000f / 60f) / settingsMs
        settingsAnim = if (settingsTarget > settingsAnim)
            (settingsAnim + settingsStep).coerceAtMost(1f)
        else
            (settingsAnim - settingsStep).coerceAtLeast(0f)

        val stickersTarget = if (stickersOpen) 1f else 0f
        val stickersMs = if (stickersTarget > stickersAnim) SETTINGS_OPEN_MS else SETTINGS_CLOSE_MS
        val stickersStep = (1000f / 60f) / stickersMs
        stickersAnim = if (stickersTarget > stickersAnim)
            (stickersAnim + stickersStep).coerceAtMost(1f)
        else
            (stickersAnim - stickersStep).coerceAtLeast(0f)
    }

    // ease-out-quint: f(t) = 1 - (1-t)^5
    private fun easeOutQuint(t: Float): Float {
        val x = 1f - t
        return 1f - x * x * x * x * x
    }


    // -----------------------------------------------------------------------
    // Counter count-up
    // -----------------------------------------------------------------------
    private fun advanceCounter(now: Long) {
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
                    lastMilestone  = m
                    celebrateMs    = now
                    celebrateCount = m
                    spawnParticles()
                    if (prefs.soundEnabled)  soundEngine.playMilestone()
                    if (prefs.hapticEnabled) hapticEngine.milestone()
                }
            }
        } else if (displayedCount > target) {
            displayedCount = 0; prevDisplayCount = -1
            lastMilestone  = 0; digitFlips.clear()
        }
        // Particle physics is driven by drawCelebration() each frame; nothing to do here.
    }

    private fun spawnParticles() {
        particles.clear()
        val cx = boardLeft + board.cols * cellSize / 2f
        val cy = boardTop  + board.rows * cellSize / 2f
        val colors = intArrayOf(
            Color.rgb(255, 80, 120), Color.rgb(255, 200, 40), Color.rgb(80, 200, 255),
            Color.rgb(160, 255, 80), Color.rgb(200, 100, 255), Color.rgb(255, 140, 40)
        )
        repeat(60) {
            val angle  = Math.random() * Math.PI * 2
            val speed  = (Math.random() * cellSize * 0.18 + cellSize * 0.06).toFloat()
            particles.add(Particle(
                x        = cx + (Math.random() * cellSize - cellSize/2).toFloat(),
                y        = cy + (Math.random() * cellSize - cellSize/2).toFloat(),
                vx       = (cos(angle) * speed).toFloat(),
                vy       = (sin(angle) * speed - cellSize * 0.12f).toFloat(),
                color    = colors[(Math.random() * colors.size).toInt()],
                radius   = (Math.random() * cellSize * 0.08 + cellSize * 0.04).toFloat(),
                rotSpeed = (Math.random() * 8f - 4f).toFloat()
            ))
        }
    }

    // -----------------------------------------------------------------------
    // Reset flash overlay
    // -----------------------------------------------------------------------
    private fun drawResetFlash(canvas: Canvas, now: Long) {
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
    private fun drawChainFlash(canvas: Canvas, now: Long) {
        if (chainFlashMs < 0) return
        val t = ((now - chainFlashMs).toFloat() / CHAIN_FLASH_MS).coerceIn(0f, 1f)
        if (t >= 1f) { chainFlashMs = -1L; return }
        val alpha = ((1f - t) * (1f - t) * 85).toInt().coerceIn(0, 85)
        flashPaint.color = (chainFlashColor and 0x00FFFFFF) or (alpha shl 24)
        canvas.drawRect(0f, 0f, surfaceW.toFloat(), surfaceH.toFloat(), flashPaint)
    }

    // -----------------------------------------------------------------------
    // HUD
    // -----------------------------------------------------------------------
    private fun drawHUD(canvas: Canvas, now: Long) {
        val titleX = surfaceW / 2f

        // "for Steven" baseline sits just above the buttons; "Donuts" above that
        val sz2    = 26f
        val line2Y = resetBtnRect.top - 14f
        val sz1    = 36f
        val line1Y = line2Y - sz2 - 10f

        textPaint.textAlign = Paint.Align.CENTER

        // "Donuts"
        textPaint.color    = Color.argb(90, 0, 0, 0)
        textPaint.textSize = sz1
        canvas.drawText("Donuts", titleX + 2f, line1Y + 2f, textPaint)
        textPaint.color = theme.textPrimary
        canvas.drawText("Donuts", titleX, line1Y, textPaint)

        // "for Steven"
        textPaint.color    = Color.argb(90, 0, 0, 0)
        textPaint.textSize = sz2
        canvas.drawText("for Steven", titleX + 2f, line2Y + 2f, textPaint)
        textPaint.color = theme.textSecondary
        canvas.drawText("for Steven", titleX, line2Y, textPaint)

        // Button press scale: 0.93x for PRESS_MS then spring back to 1.0
        val resetScale    = buttonPressScale(now, resetPressMs)
        val settingsScale = buttonPressScale(now, settingsPressMs)
        val soundScale    = buttonPressScale(now, soundPressMs)
        val hapticScale   = buttonPressScale(now, hapticPressMs)
        val stickerScale  = buttonPressScale(now, stickersPressMs)

        val soundColor  = if (prefs.soundEnabled)  theme.btnSelected else theme.btnUnselected
        val hapticColor = if (prefs.hapticEnabled) theme.btnSelected else theme.btnUnselected

        val soundLabel  = if (prefs.soundEnabled)  "\uD83D\uDD0A" else "\uD83D\uDD07"  // 🔊 / 🔇
        val hapticLabel = if (prefs.hapticEnabled) "\uD83D\uDCF3" else "\uD83D\uDCF1"  // 📳 / 📱
        drawPrettyButton(canvas, resetBtnRect,    theme.resetBtn,    "RESET",      36f, resetScale)
        drawPrettyButton(canvas, stickersBtnRect, theme.settingsBtn, "\uD83C\uDFC5", 36f, stickerScale)  // 🏅
        drawPrettyButton(canvas, soundBtnRect,    soundColor,        soundLabel,   38f, soundScale)
        drawPrettyButton(canvas, hapticBtnRect,   hapticColor,       hapticLabel,  38f, hapticScale)
        drawPrettyButton(canvas, settingsBtnRect, theme.settingsBtn, "\u2699",     38f, settingsScale)
    }

    /** Returns a scale factor that dips to 0.93 at tap then recovers to 1.0 over PRESS_MS. */
    private fun buttonPressScale(now: Long, pressMs: Long): Float {
        if (pressMs < 0) return 1f
        val t = ((now - pressMs).toFloat() / PRESS_MS).coerceIn(0f, 1f)
        // Dip then ease back: 0.86 at t=0, 1.0 at t=1 (ease-out) — chunkier press feel
        return 0.86f + 0.14f * easeOutQuint(t)
    }

    private fun drawPrettyButton(canvas: Canvas, rect: RectF, baseColor: Int, label: String, labelSize: Float, scale: Float = 1f) {
        val rx = 20f
        canvas.save()
        canvas.scale(scale, scale, rect.centerX(), rect.centerY())

        // Drop shadow
        shadowPaint.color = Color.argb(90, 0, 0, 0)
        canvas.drawRoundRect(
            RectF(rect.left + 4f, rect.top + 7f, rect.right + 4f, rect.bottom + 7f),
            rx, rx, shadowPaint
        )
        // Cartoon border
        fillPaint.color = Color.argb(200, 30, 15, 0)
        canvas.drawRoundRect(
            RectF(rect.left - 3f, rect.top - 3f, rect.right + 3f, rect.bottom + 3f),
            rx + 3f, rx + 3f, fillPaint
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
        strokePaint.color       = Color.argb(100, 255, 255, 255)
        strokePaint.strokeWidth = 2f; strokePaint.alpha = 255
        canvas.drawRoundRect(rect, rx, rx, strokePaint)
        // Label shadow
        textPaint.color         = Color.argb(80, 0, 0, 0)
        textPaint.textSize      = labelSize
        textPaint.textAlign     = Paint.Align.CENTER
        textPaint.letterSpacing = 0.06f
        canvas.drawText(label, rect.centerX() + 2f, rect.centerY() + labelSize * 0.36f + 2f, textPaint)
        // Label
        textPaint.color = Color.WHITE
        canvas.drawText(label, rect.centerX(), rect.centerY() + labelSize * 0.36f, textPaint)
        textPaint.letterSpacing = 0f

        canvas.restore()
    }

    // -----------------------------------------------------------------------
    // Board & cells
    // -----------------------------------------------------------------------
    private fun drawBoardBackground(canvas: Canvas) {
        val boardR = boardLeft + board.cols * cellSize
        val boardB = boardTop  + board.rows * cellSize
        val boardW = boardR - boardLeft
        val boardH = boardB - boardTop

        // Chunky drop shadow — offset further for cartoon depth
        shadowPaint.color = Color.argb(80, 0, 0, 0)
        canvas.drawRoundRect(RectF(boardLeft, boardTop + 14f, boardR + 6f, boardB + 14f), 24f, 24f, shadowPaint)
        // Thick dark cartoon border
        fillPaint.color = Color.argb(220, 28, 12, 0)
        canvas.drawRoundRect(RectF(boardLeft - 12f, boardTop - 12f, boardR + 12f, boardB + 12f), 28f, 28f, fillPaint)
        // Board fill
        fillPaint.color = theme.boardBg; fillPaint.alpha = 255
        canvas.drawRoundRect(RectF(boardLeft - 4f, boardTop - 4f, boardR + 4f, boardB + 4f), 22f, 22f, fillPaint)

        // Polka-dot texture — subtle circles at cell intersections
        val dotR = cellSize * 0.06f
        fillPaint.color = Color.argb(28, 28, 12, 0)
        canvas.save()
        canvas.clipRect(boardLeft - 4f, boardTop - 4f, boardR + 4f, boardB + 4f)
        for (r in 0..board.rows) {
            for (c in 0..board.cols) {
                val dx = boardLeft + c * cellSize
                val dy = boardTop  + r * cellSize
                canvas.drawCircle(dx, dy, dotR, fillPaint)
            }
        }
        canvas.restore()
        fillPaint.alpha = 255

        // Cell grid — soft rounded dots instead of hard lines
        strokePaint.color       = Color.argb(22, 28, 12, 0)
        strokePaint.strokeWidth = 1f
        for (r in 1 until board.rows) {
            val y = boardTop + r * cellSize
            canvas.drawLine(boardLeft, y, boardR, y, strokePaint)
        }
        for (c in 1 until board.cols) {
            val x = boardLeft + c * cellSize
            canvas.drawLine(x, boardTop, x, boardB, strokePaint)
        }
    }

    private fun drawChainLine(canvas: Canvas) {
        if (dragChain.size < 2) return
        // Reuse scratchPath2 — drawChainLine runs before drawCells, so there is no conflict.
        scratchPath2.rewind()
        dragChain.forEachIndexed { i, (r, c) ->
            val cx = boardLeft + c * cellSize + cellSize / 2f
            val cy = boardTop  + r * cellSize + cellSize / 2f
            if (i == 0) scratchPath2.moveTo(cx, cy) else scratchPath2.lineTo(cx, cy)
        }
        val path = scratchPath2
        // Color-match chain to the piece type
        val chainColor = dragChainType?.glazeColor ?: Color.WHITE
        val cr = Color.red(chainColor); val cg = Color.green(chainColor); val cb = Color.blue(chainColor)

        // Scale glow intensity with chain length: 0 at 1 cell, 1 at 8+ cells
        val boost = ((dragChain.size - 1f) / 7f).coerceIn(0f, 1f)

        // Outer super-glow — only visible on big chains (5+)
        if (boost > 0.5f) {
            chainOutlinePaint.strokeWidth = cellSize * 1.4f
            chainOutlinePaint.color = Color.argb(((boost - 0.5f) * 80).toInt(), cr, cg, cb)
            canvas.drawPath(path, chainOutlinePaint)
        }
        // Wide color glow — grows with chain length
        chainOutlinePaint.strokeWidth = cellSize * (0.72f + boost * 0.28f)
        chainOutlinePaint.color = Color.argb((55 + (boost * 85).toInt()), cr, cg, cb)
        canvas.drawPath(path, chainOutlinePaint)
        // Mid color layer
        chainOutlinePaint.strokeWidth = cellSize * (0.50f + boost * 0.10f)
        chainOutlinePaint.color = Color.argb((120 + (boost * 60).toInt()), cr, cg, cb)
        canvas.drawPath(path, chainOutlinePaint)
        // Dark cartoon border
        chainOutlinePaint.strokeWidth = cellSize * (0.38f + boost * 0.06f)
        chainOutlinePaint.color = Color.argb(200, 28, 12, 0)
        canvas.drawPath(path, chainOutlinePaint)
        // Colored core — widens with chain
        chainLinePaint.strokeWidth = cellSize * (0.24f + boost * 0.06f)
        chainLinePaint.color = Color.argb(255, cr, cg, cb)
        canvas.drawPath(path, chainLinePaint)
        // White highlight thread
        chainLinePaint.strokeWidth = cellSize * 0.09f
        chainLinePaint.color = Color.argb(210, 255, 255, 255)
        canvas.drawPath(path, chainLinePaint)
    }

    private fun drawCells(canvas: Canvas, now: Long) {
        val popSet  = popCells.map { it.row to it.col }.toSet()
        // During DROPPING, entire changed columns are hidden via dropColMask so
        // survivors don't flicker at their new positions before the animation ends.

        val hintAlpha = if (hintCells.isNotEmpty()) {
            val t = ((now - hintPulseMs) % 900L) / 900f
            val pulse = if (t < 0.5f) t * 2f else (1f - t) * 2f
            (100 + (155 * pulse)).toInt()
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
                val breatheScale = if (animPhase == AnimPhase.IDLE && !inChain && shuffleAnimMs < 0) {
                    val phase  = (r * board.cols + c) * 0.61f   // golden-ratio-ish spread
                    val period = 1400f + (r * board.cols + c) % 5 * 160f
                    val t      = ((now / period + phase) * 2f * PI.toFloat())
                    1f + sin(t) * 0.05f
                } else 1f

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

                drawPiece(canvas, cx, cy, pieceR, board.grid[r][c].type, inChain)

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
                    val hintRingScale = 1f + hintPulse * 0.06f
                    hintRingPaint.color       = theme.hintRing
                    hintRingPaint.alpha       = hintAlpha
                    hintRingPaint.strokeWidth = cellSize * 0.12f
                    canvas.save()
                    canvas.scale(hintRingScale, hintRingScale, cx, cy)
                    canvas.drawCircle(cx, cy, cellSize * 0.47f * breatheScale, hintRingPaint)
                    canvas.restore()
                }
            }
        }

        if (boardEntryMs >= 0 && now - boardEntryMs >= BOARD_ENTRY_MS + board.cols * 55L) {
            boardEntryMs = -1L
        }

        if (animPhase == AnimPhase.POPPING) {
            val t     = ((now - animStartMs).toFloat() / POP_MS).coerceIn(0f, 1f)
            // Squash-and-stretch: grow → hold → squash wide → vanish
            val scaleX: Float; val scaleY: Float
            when {
                t < 0.25f -> { // punch up: grow tall
                    val p = t / 0.25f
                    scaleX = 1f + p * 0.25f
                    scaleY = 1f + p * 0.75f
                }
                t < 0.50f -> { // peak hold
                    scaleX = 1.25f; scaleY = 1.75f
                }
                t < 0.75f -> { // squash wide
                    val p = (t - 0.50f) / 0.25f
                    scaleX = 1.25f + p * 0.80f
                    scaleY = 1.75f - p * 1.30f
                }
                else -> { // shrink to nothing
                    val p = (t - 0.75f) / 0.25f
                    scaleX = 2.05f * (1f - p)
                    scaleY = 0.45f * (1f - p)
                }
            }
            val alpha = ((1f - t * t * t) * 255).toInt().coerceIn(0, 255)
            for (cell in popCells) {
                val cx = boardLeft + cell.col * cellSize + cellSize / 2f
                val cy = boardTop  + cell.row * cellSize + cellSize / 2f
                canvas.save()
                canvas.scale(scaleX, scaleY, cx, cy)
                drawPiece(canvas, cx, cy, cellSize * 0.43f, cell.type, false, alpha)
                canvas.restore()
            }
            // Expanding burst ring — gold for bonus cells (power-up), white for chain cells
            val burstT     = (t / 0.6f).coerceIn(0f, 1f)
            val burstAlpha = ((1f - burstT) * 200).toInt().coerceIn(0, 255)
            val chainSet   = pendingResult?.chainCells?.toSet() ?: emptySet()
            strokePaint.strokeWidth = cellSize * 0.07f
            strokePaint.alpha       = burstAlpha
            for (cell in popCells) {
                val cx = boardLeft + cell.col * cellSize + cellSize / 2f
                val cy = boardTop  + cell.row * cellSize + cellSize / 2f
                val isBonus = !isCascade && Pair(cell.row, cell.col) !in chainSet
                strokePaint.color = if (isBonus)
                    Color.argb(burstAlpha, 255, 210, 0)   // gold ring for power-up bonus
                else
                    Color.argb(burstAlpha, 255, 255, 255) // white ring for normal
                val ringScale = if (isBonus) 0.52f + burstT * 0.72f else 0.44f + burstT * 0.52f
                canvas.drawCircle(cx, cy, cellSize * ringScale, strokePaint)
            }
            strokePaint.alpha = 255
        }

        if (animPhase == AnimPhase.DROPPING) {
            val t     = ((now - animStartMs).toFloat() / DROP_MS).coerceIn(0f, 1f)
            val eased = 1f - (1f - t) * (1f - t) * (1f - t)
            for (cell in dropCells) {
                val cx     = boardLeft + cell.col     * cellSize + cellSize / 2f
                val startY = boardTop  + cell.fromRow * cellSize + cellSize / 2f
                val endY   = boardTop  + cell.row     * cellSize + cellSize / 2f
                val cy     = startY + (endY - startY) * eased
                drawPiece(canvas, cx, cy, cellSize * 0.43f, cell.type, false)
                if (cell.isGolden) drawGoldenOverlay(canvas, cx, cy, cellSize * 0.43f, now)
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
    private fun drawGoldenOverlay(canvas: Canvas, cx: Float, cy: Float, r: Float, now: Long) {
        // Slow spin: one full rotation every 2400 ms
        val spinAngle = (now % 2400L) / 2400f * 360f

        // Outer dashed-looking gold ring (drawn as a stroked arc sweep — full circle)
        strokePaint.color       = Color.argb(200, 255, 210, 30)
        strokePaint.strokeWidth = r * 0.13f
        strokePaint.alpha       = 200
        canvas.save()
        canvas.rotate(spinAngle, cx, cy)
        // Draw 4 arcs spaced 90° apart to fake a dashed ring
        for (i in 0 until 4) {
            canvas.drawArc(
                RectF(cx - r * 1.10f, cy - r * 1.10f, cx + r * 1.10f, cy + r * 1.10f),
                i * 90f, 60f, false, strokePaint
            )
        }
        canvas.restore()
        strokePaint.alpha = 255

        // Inner warm gold tint — very subtle fill overlay
        fillPaint.color = Color.argb(55, 255, 200, 0)
        canvas.drawCircle(cx, cy, r * 0.90f, fillPaint)

        // Small ✦ star spark at top-left — reuse scratchPath + precomputed sparkCos/sparkSin.
        val sparkR  = r * 0.22f
        val sparkCx = cx - r * 0.52f
        val sparkCy = cy - r * 0.52f
        fillPaint.color = Color.argb(220, 255, 240, 80)
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
    // drawPiece dispatcher
    // -----------------------------------------------------------------------
    private fun drawPiece(
        canvas: Canvas, cx: Float, cy: Float,
        radius: Float, type: DonutType, selected: Boolean, alpha: Int = 255
    ) {
        when (theme.iconType) {
            IconType.DONUT -> drawDonutShape(canvas, cx, cy, radius, type, selected, alpha)
            IconType.STAR  -> drawStarShape (canvas, cx, cy, radius, type, selected, alpha)
            IconType.DINO  -> drawDinoShape (canvas, cx, cy, radius, type, selected, alpha)
            IconType.TRUCK -> drawTruckShape(canvas, cx, cy, radius, type, selected, alpha)
        }
    }

    // -----------------------------------------------------------------------
    // 3D sheen helper — upper-left crescent highlight + specular dot
    // -----------------------------------------------------------------------
    /**
     * Adds a 3D-style highlight to any piece. Clips to an upper-left oval,
     * paints a semi-transparent white sheen, then drops a bright specular dot.
     * Call AFTER drawing the main body, BEFORE any hole/overlay elements.
     */
    private fun addSheen(canvas: Canvas, cx: Float, cy: Float, r: Float, alpha: Int,
                         sheenAlpha: Float = 0.32f, specAlpha: Float = 0.75f) {
        // Soft top-left crescent via clipPath — reuse scratchPath (callers use scratchPath2
        // for their own outer clip, so there is no aliasing conflict here).
        scratchPath.rewind()
        scratchPath.addOval(
            scratchRectF.apply { set(cx - r * 0.88f, cy - r * 1.05f, cx + r * 0.52f, cy + r * 0.05f) },
            Path.Direction.CW
        )
        canvas.save()
        canvas.clipPath(scratchPath)
        fillPaint.color = Color.argb((alpha * sheenAlpha).toInt(), 255, 255, 255)
        canvas.drawRect(cx - r * 2f, cy - r * 2f, cx + r * 2f, cy + r * 2f, fillPaint)
        canvas.restore()
        // Bright specular dot upper-left
        fillPaint.color = Color.argb((alpha * specAlpha).toInt(), 255, 255, 255)
        canvas.drawCircle(cx - r * 0.30f, cy - r * 0.44f, r * 0.16f, fillPaint)
        fillPaint.alpha = 255
    }

    // -----------------------------------------------------------------------
    // Shape: Donut
    // -----------------------------------------------------------------------
    private fun drawDonutShape(
        canvas: Canvas, cx: Float, cy: Float,
        radius: Float, type: DonutType, selected: Boolean, alpha: Int
    ) {
        val ow = radius * 0.18f
        if (selected) {
            fillPaint.color = Color.argb(alpha, 255, 255, 255)
            canvas.drawCircle(cx, cy, radius + ow + cellSize * 0.07f, fillPaint)
        }
        // Dark outline circle
        fillPaint.color = Color.argb(alpha, 28, 12, 0)
        canvas.drawCircle(cx, cy, radius + ow, fillPaint)
        // Drop shadow
        fillPaint.color = Color.argb(alpha / 3, 0, 0, 0)
        canvas.drawCircle(cx + radius * 0.06f, cy + radius * 0.12f, radius, fillPaint)
        // Body
        fillPaint.color = type.bodyColor; fillPaint.alpha = alpha
        canvas.drawCircle(cx, cy, radius, fillPaint)
        // Glaze
        fillPaint.color = type.glazeColor; fillPaint.alpha = alpha
        canvas.drawCircle(cx, cy, radius * 0.82f, fillPaint)
        // 3D sheen clipped to glaze circle — reuse scratchPath2 (addSheen uses scratchPath).
        scratchPath2.rewind()
        scratchPath2.addCircle(cx, cy, radius * 0.82f, Path.Direction.CW)
        canvas.save()
        canvas.clipPath(scratchPath2)
        addSheen(canvas, cx, cy, radius * 0.82f, alpha)
        canvas.restore()
        // Sprinkles
        drawSprinkles(canvas, cx, cy, radius * 0.82f, type, alpha)
        // Hole
        fillPaint.color = theme.holeColor; fillPaint.alpha = alpha
        canvas.drawCircle(cx, cy, radius * 0.34f, fillPaint)
        // Hole inner highlight (rim light)
        outlinePaint.color       = Color.argb(alpha / 2, 255, 255, 255)
        outlinePaint.strokeWidth = radius * 0.05f
        canvas.drawCircle(cx, cy, radius * 0.34f, outlinePaint)
        // Hole dark outline
        outlinePaint.color       = Color.argb(alpha / 2, 28, 12, 0)
        outlinePaint.strokeWidth = radius * 0.05f
        canvas.drawCircle(cx, cy, radius * 0.36f, outlinePaint)
        fillPaint.alpha = 255
    }

    private fun drawSprinkles(canvas: Canvas, cx: Float, cy: Float, glazeR: Float, type: DonutType, alpha: Int) {
        fillPaint.color = when (type) {
            DonutType.STRAWBERRY -> Color.WHITE
            DonutType.VANILLA    -> Color.rgb(255, 100, 160)
            DonutType.CHOCOLATE  -> Color.rgb(235, 195, 130)
            DonutType.BLUEBERRY  -> Color.WHITE
            DonutType.MATCHA     -> Color.rgb(255, 235, 80)
            DonutType.CARAMEL    -> Color.WHITE
        }
        fillPaint.alpha = alpha
        val dotR = glazeR * 0.10f; val dist = glazeR * 0.48f
        for (i in 0 until 5) {
            canvas.drawCircle(
                cx + dist * sprinkleCos[i],
                cy + dist * sprinkleSin[i],
                dotR, fillPaint
            )
        }
        fillPaint.alpha = 255
    }

    // -----------------------------------------------------------------------
    // Shape: Star
    // -----------------------------------------------------------------------
    private fun drawStarShape(
        canvas: Canvas, cx: Float, cy: Float,
        radius: Float, type: DonutType, selected: Boolean, alpha: Int
    ) {
        val ow = radius * 0.18f
        if (selected) {
            fillPaint.color = Color.argb(alpha, 255, 255, 255)
            canvas.drawCircle(cx, cy, radius + ow + radius * 0.08f, fillPaint)
        }
        // Dark outline — scratchPath is transient here (drawn immediately, then reused below).
        outlinePaint.color       = Color.argb(alpha, 28, 12, 0)
        outlinePaint.strokeWidth = ow * 2f
        scratchPath.rewind()
        buildStarPathInto(scratchPath, cx, cy, radius + ow * 0.5f, (radius + ow * 0.5f) * 0.42f)
        canvas.drawPath(scratchPath, outlinePaint)
        // Drop shadow
        fillPaint.color = Color.argb(alpha / 3, 0, 0, 0)
        scratchPath.rewind()
        buildStarPathInto(scratchPath, cx + radius * 0.05f, cy + radius * 0.10f, radius, radius * 0.42f)
        canvas.drawPath(scratchPath, fillPaint)
        // Body star — must survive for both fill AND clipPath, so use scratchPath2.
        scratchPath2.rewind()
        buildStarPathInto(scratchPath2, cx, cy, radius, radius * 0.42f)
        fillPaint.color = type.bodyColor; fillPaint.alpha = alpha
        canvas.drawPath(scratchPath2, fillPaint)
        // 3D sheen clipped to star (addSheen uses scratchPath — no aliasing conflict).
        canvas.save()
        canvas.clipPath(scratchPath2)
        addSheen(canvas, cx, cy, radius, alpha, sheenAlpha = 0.28f)
        canvas.restore()
        // Inner accent star — scratchPath is free again after the sheen block.
        scratchPath.rewind()
        buildStarPathInto(scratchPath, cx, cy, radius * 0.58f, radius * 0.24f)
        fillPaint.color = type.glazeColor; fillPaint.alpha = alpha
        canvas.drawPath(scratchPath, fillPaint)
        // Inner star rim light
        outlinePaint.color       = Color.argb((alpha * 0.4f).toInt(), 255, 255, 255)
        outlinePaint.strokeWidth = radius * 0.04f
        canvas.drawPath(scratchPath, outlinePaint)   // reuse same accent path
        fillPaint.alpha = 255
    }

    /**
     * Fills [path] (which must already be rewound) with a 5-point star centred at (cx, cy).
     * Uses precomputed [starCos]/[starSin] arrays — no trigonometry at call time.
     */
    private fun buildStarPathInto(path: Path, cx: Float, cy: Float, outerR: Float, innerR: Float) {
        for (i in 0 until 10) {
            val r = if (i % 2 == 0) outerR else innerR
            val x = cx + r * starCos[i]
            val y = cy + r * starSin[i]
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
    }

    // -----------------------------------------------------------------------
    // Shape: Dino  (no backing circle — each body part gets its own outline)
    // -----------------------------------------------------------------------
    private fun drawDinoShape(
        canvas: Canvas, cx: Float, cy: Float,
        radius: Float, type: DonutType, selected: Boolean, alpha: Int
    ) {
        val r = radius
        val ow = r * 0.16f
        if (selected) {
            fillPaint.color = Color.argb(alpha, 255, 255, 255)
            canvas.drawCircle(cx, cy, r * 1.32f, fillPaint)
        }

        // ---- Helper lambdas for outline+fill ----
        // Each body part: stroke (dark outline), then fill (body color)
        outlinePaint.color       = Color.argb(alpha, 28, 12, 0)
        outlinePaint.strokeWidth = ow * 2f

        // Drop shadow pass — reuse scratchRectF.
        fillPaint.color = Color.argb(alpha / 4, 0, 0, 0)
        scratchRectF.set(cx - r*0.74f + r*0.06f, cy - r*0.28f + r*0.12f,
                         cx + r*0.58f + r*0.06f, cy + r*0.60f + r*0.12f)
        canvas.drawOval(scratchRectF, fillPaint)

        // ----- Tail — scratchPath, drawn immediately -----
        scratchPath.rewind()
        scratchPath.moveTo(cx - r*0.66f, cy - r*0.04f)
        scratchPath.lineTo(cx - r*0.66f, cy + r*0.34f)
        scratchPath.lineTo(cx - r*1.06f, cy - r*0.12f)
        scratchPath.close()
        canvas.drawPath(scratchPath, outlinePaint)
        fillPaint.color = type.bodyColor; fillPaint.alpha = alpha
        canvas.drawPath(scratchPath, fillPaint)

        // ----- Body -----
        scratchRectF.set(cx - r*0.74f, cy - r*0.28f, cx + r*0.58f, cy + r*0.60f)
        canvas.drawOval(scratchRectF, outlinePaint)
        fillPaint.color = type.bodyColor; fillPaint.alpha = alpha
        canvas.drawOval(scratchRectF, fillPaint)

        // ----- Head -----
        scratchRectF.set(cx + r*0.22f, cy - r*0.76f, cx + r*0.92f, cy - r*0.08f)
        canvas.drawOval(scratchRectF, outlinePaint)
        fillPaint.color = type.bodyColor; fillPaint.alpha = alpha
        canvas.drawOval(scratchRectF, fillPaint)

        // ----- Snout -----
        scratchRectF.set(cx + r*0.54f, cy - r*0.44f, cx + r*1.05f, cy - r*0.06f)
        canvas.drawOval(scratchRectF, outlinePaint)
        fillPaint.color = type.bodyColor; fillPaint.alpha = alpha
        canvas.drawOval(scratchRectF, fillPaint)

        // ----- Spines (glazeColor) — reuse scratchPath each iteration -----
        outlinePaint.color = Color.argb(alpha, 28, 12, 0)
        fillPaint.color = type.glazeColor; fillPaint.alpha = alpha
        for (i in 0 until 3) {
            val sx = cx - r*0.34f + i * r*0.28f
            scratchPath.rewind()
            scratchPath.moveTo(sx - r*0.09f, cy - r*0.28f)
            scratchPath.lineTo(sx,           cy - r*0.64f)
            scratchPath.lineTo(sx + r*0.09f, cy - r*0.28f)
            scratchPath.close()
            canvas.drawPath(scratchPath, outlinePaint)
            canvas.drawPath(scratchPath, fillPaint)
        }

        // ----- Arm (glazeColor) -----
        scratchRectF.set(cx + r*0.16f, cy - r*0.02f, cx + r*0.52f, cy + r*0.20f)
        canvas.drawOval(scratchRectF, outlinePaint)
        fillPaint.color = type.glazeColor; fillPaint.alpha = alpha
        canvas.drawOval(scratchRectF, fillPaint)

        // ----- Legs -----
        val legRx = r * 0.10f
        outlinePaint.color = Color.argb(alpha, 28, 12, 0)
        scratchRectF.set(cx - r*0.52f, cy + r*0.52f, cx - r*0.16f, cy + r*0.90f)
        canvas.drawRoundRect(scratchRectF, legRx, legRx, outlinePaint)
        canvas.drawRoundRect(scratchRectF, legRx, legRx, fillPaint.also { it.color = type.bodyColor; it.alpha = alpha })
        scratchRectF.set(cx + r*0.02f, cy + r*0.52f, cx + r*0.38f, cy + r*0.90f)
        canvas.drawRoundRect(scratchRectF, legRx, legRx, outlinePaint)
        canvas.drawRoundRect(scratchRectF, legRx, legRx, fillPaint)

        // 3D sheen clipped to body oval — scratchPath2 holds the clip; addSheen uses scratchPath.
        scratchPath2.rewind()
        scratchPath2.addOval(
            scratchRectF.apply { set(cx - r*0.74f, cy - r*0.28f, cx + r*0.58f, cy + r*0.60f) },
            Path.Direction.CW
        )
        canvas.save()
        canvas.clipPath(scratchPath2)
        addSheen(canvas, cx - r*0.08f, cy + r*0.16f, r * 0.72f, alpha, sheenAlpha = 0.22f, specAlpha = 0.55f)
        canvas.restore()

        // ----- Eye -----
        fillPaint.color = Color.WHITE; fillPaint.alpha = alpha
        canvas.drawCircle(cx + r*0.60f, cy - r*0.48f, r*0.14f, fillPaint)
        // Eye glint
        fillPaint.color = Color.argb((alpha * 0.9f).toInt(), 255, 255, 255)
        canvas.drawCircle(cx + r*0.55f, cy - r*0.53f, r*0.05f, fillPaint)
        // Pupil
        fillPaint.color = Color.argb(alpha, 18, 18, 18)
        canvas.drawCircle(cx + r*0.63f, cy - r*0.44f, r*0.07f, fillPaint)

        // ----- Nostril -----
        fillPaint.color = Color.argb((alpha * 0.5f).toInt(), 28, 12, 0)
        canvas.drawCircle(cx + r*0.90f, cy - r*0.22f, r*0.04f, fillPaint)

        fillPaint.alpha = 255
    }

    // -----------------------------------------------------------------------
    // Shape: Truck  (no backing circle — each component gets its own outline)
    // -----------------------------------------------------------------------
    private fun drawTruckShape(
        canvas: Canvas, cx: Float, cy: Float,
        radius: Float, type: DonutType, selected: Boolean, alpha: Int
    ) {
        val r = radius
        val ow = r * 0.16f
        if (selected) {
            fillPaint.color = Color.argb(alpha, 255, 255, 255)
            canvas.drawCircle(cx, cy, r * 1.32f, fillPaint)
        }

        outlinePaint.color       = Color.argb(alpha, 28, 12, 0)
        outlinePaint.strokeWidth = ow * 2f

        val groundY  = cy + r * 0.52f
        val truckTop = cy - r * 0.68f
        val cargoT   = truckTop + r * 0.30f
        val cargoL   = cx - r * 0.96f
        val cargoR   = cx + r * 0.14f
        val cabL     = cargoR - r * 0.05f
        val cabR     = cx + r * 0.90f
        val wheelR   = r * 0.22f
        val wheelCY  = groundY + wheelR * 0.58f

        // Drop shadow — reuse scratchRectF.
        fillPaint.color = Color.argb(alpha / 4, 0, 0, 0)
        scratchRectF.set(cargoL + r*0.06f, cargoT + r*0.10f, cabR + r*0.06f, groundY + r*0.10f)
        canvas.drawRoundRect(scratchRectF, r*0.08f, r*0.08f, fillPaint)

        // ----- Cargo box -----
        scratchRectF.set(cargoL, cargoT, cargoR, groundY)
        canvas.drawRoundRect(scratchRectF, r*0.08f, r*0.08f, outlinePaint)
        fillPaint.color = type.bodyColor; fillPaint.alpha = alpha
        canvas.drawRoundRect(scratchRectF, r*0.08f, r*0.08f, fillPaint)
        // Cargo top-panel highlight (lighter top third)
        canvas.save()
        canvas.clipRect(cargoL, cargoT, cargoR, cargoT + (groundY - cargoT) * 0.38f)
        fillPaint.color = Color.argb((alpha * 0.28f).toInt(), 255, 255, 255)
        canvas.drawRoundRect(scratchRectF, r*0.08f, r*0.08f, fillPaint)
        canvas.restore()
        // Cargo stripe (glazeColor panel)
        fillPaint.color = type.glazeColor; fillPaint.alpha = alpha
        canvas.drawRect(cargoL + r*0.10f, cargoT + r*0.08f, cargoR - r*0.10f, cargoT + r*0.26f, fillPaint)
        // Cargo door line
        outlinePaint.strokeWidth = ow * 0.6f
        canvas.drawLine(cargoL + (cargoR - cargoL)/2f, cargoT + r*0.30f,
                        cargoL + (cargoR - cargoL)/2f, groundY - r*0.06f, outlinePaint)
        outlinePaint.strokeWidth = ow * 2f

        // ----- Cab -----
        // Save wind coords before we overwrite scratchRectF for cab.
        val windL = cabL + r*0.08f; val windT = truckTop + r*0.10f
        val windR = cabR - r*0.10f; val windB = cy   - r*0.08f
        scratchRectF.set(cabL, truckTop, cabR, groundY)
        canvas.drawRoundRect(scratchRectF, r*0.12f, r*0.12f, outlinePaint)
        fillPaint.color = type.glazeColor; fillPaint.alpha = alpha
        canvas.drawRoundRect(scratchRectF, r*0.12f, r*0.12f, fillPaint)
        // Cab top highlight
        canvas.save()
        canvas.clipRect(cabL, truckTop, cabR, truckTop + (groundY - truckTop) * 0.38f)
        fillPaint.color = Color.argb((alpha * 0.32f).toInt(), 255, 255, 255)
        canvas.drawRoundRect(scratchRectF, r*0.12f, r*0.12f, fillPaint)
        canvas.restore()

        // ----- Windshield -----
        scratchRectF.set(windL, windT, windR, windB)
        canvas.drawRoundRect(scratchRectF, r*0.07f, r*0.07f, outlinePaint)
        fillPaint.color = Color.argb((alpha * 0.90f).toInt(), 130, 210, 255)
        canvas.drawRoundRect(scratchRectF, r*0.07f, r*0.07f, fillPaint)
        // Windshield reflection — reuse scratchRectF (wind values saved as locals above).
        fillPaint.color = Color.argb((alpha * 0.50f).toInt(), 255, 255, 255)
        scratchRectF.set(windL + r*0.05f, windT + r*0.05f,
                         (windL + windR) / 2f - r*0.04f, windB - r*0.08f)
        canvas.drawRoundRect(scratchRectF, r*0.04f, r*0.04f, fillPaint)

        // ----- Wheels -----
        // Tyre outline+fill
        outlinePaint.strokeWidth = ow * 2f
        canvas.drawCircle(cx - r*0.50f, wheelCY, wheelR, outlinePaint)
        canvas.drawCircle(cx + r*0.55f, wheelCY, wheelR, outlinePaint)
        fillPaint.color = Color.argb(alpha, 30, 30, 30)
        canvas.drawCircle(cx - r*0.50f, wheelCY, wheelR, fillPaint)
        canvas.drawCircle(cx + r*0.55f, wheelCY, wheelR, fillPaint)
        // Rim (glazeColor)
        fillPaint.color = type.glazeColor; fillPaint.alpha = alpha
        canvas.drawCircle(cx - r*0.50f, wheelCY, wheelR * 0.50f, fillPaint)
        canvas.drawCircle(cx + r*0.55f, wheelCY, wheelR * 0.50f, fillPaint)
        // Rim highlight
        fillPaint.color = Color.argb((alpha * 0.40f).toInt(), 255, 255, 255)
        canvas.drawCircle(cx - r*0.54f, wheelCY - wheelR*0.22f, wheelR * 0.20f, fillPaint)
        canvas.drawCircle(cx + r*0.51f, wheelCY - wheelR*0.22f, wheelR * 0.20f, fillPaint)
        // Hub dot
        fillPaint.color = Color.argb(alpha, 28, 12, 0)
        canvas.drawCircle(cx - r*0.50f, wheelCY, wheelR * 0.14f, fillPaint)
        canvas.drawCircle(cx + r*0.55f, wheelCY, wheelR * 0.14f, fillPaint)

        // 3D sheen over cab
        addSheen(canvas, cx + r*0.42f, cy - r*0.18f, r * 0.52f, alpha, sheenAlpha = 0.25f, specAlpha = 0.60f)

        fillPaint.alpha = 255
    }

    // -----------------------------------------------------------------------
    // Counter — digit-flip scoreboard
    // -----------------------------------------------------------------------
    private fun drawCounter(canvas: Canvas, now: Long) {
        val stripY = boardTop + board.rows * cellSize
        val midY   = stripY + counterH / 2f
        val cw     = board.cols * cellSize * 0.88f
        val pl     = surfaceW / 2f - cw / 2f
        val pr     = surfaceW / 2f + cw / 2f

        // Heartbeat — quick scale-pulse when score increments
        val beat = if (counterPulseMs >= 0) {
            val t = ((now - counterPulseMs).toFloat() / COUNTER_PULSE_MS).coerceIn(0f, 1f)
            if (t >= 1f) { counterPulseMs = -1L; 1f }
            else 1f + sin(t * PI.toFloat()) * 0.045f
        } else 1f
        canvas.save()
        canvas.scale(beat, beat, surfaceW / 2f, midY)

        // Panel shell
        shadowPaint.color = Color.argb(50, 0, 0, 0)
        canvas.drawRoundRect(RectF(pl + 2f, stripY + 12f, pr + 2f, stripY + counterH - 2f), 18f, 18f, shadowPaint)
        fillPaint.color = Color.argb(180, 28, 12, 0)
        canvas.drawRoundRect(RectF(pl - 4f, stripY + 4f, pr + 4f, stripY + counterH), 20f, 20f, fillPaint)
        fillPaint.color = theme.panelBg; fillPaint.alpha = 255
        canvas.drawRoundRect(RectF(pl, stripY + 8f, pr, stripY + counterH - 4f), 18f, 18f, fillPaint)

        // "CLEARED" — small header label at top of panel (caption before the value)
        textPaint.textSize      = 24f
        textPaint.textAlign     = Paint.Align.CENTER
        textPaint.letterSpacing = 0.12f
        textPaint.color         = Color.argb(80, 0, 0, 0)
        canvas.drawText("CLEARED  \u2746", surfaceW / 2f + 1f, stripY + 36f + 1f, textPaint)
        textPaint.color         = theme.textSecondary
        canvas.drawText("CLEARED  \u2746", surfaceW / 2f, stripY + 36f, textPaint)
        textPaint.letterSpacing = 0f

        // --- Digit-flip number ---
        val digitSz = 96f
        textPaint.textSize = digitSz
        val cellW  = textPaint.measureText("0") * 1.18f   // fixed per-digit width
        val numStr = displayedCount.toString()
        val totalW = numStr.length * cellW
        val startX = surfaceW / 2f - totalW / 2f + cellW / 2f
        val baseY  = stripY + 150f                         // shifted down to leave room for header label
        val pivotY = baseY - digitSz * 0.36f               // visual centre of digit

        textOutlinePaint.textSize    = digitSz
        textOutlinePaint.textAlign   = Paint.Align.CENTER
        textOutlinePaint.typeface    = boldTypeface
        textOutlinePaint.strokeWidth = 4f

        for ((idx, ch) in numStr.withIndex()) {
            val pos  = numStr.length - 1 - idx             // 0 = ones place
            val x    = startX + idx * cellW
            val flip = digitFlips[pos]

            // Dark cell background — makes it look like a scoreboard slot
            fillPaint.color = Color.argb(55, 28, 12, 0)
            canvas.drawRoundRect(RectF(x - cellW * 0.46f, pivotY - digitSz * 0.54f,
                                       x + cellW * 0.46f, pivotY + digitSz * 0.54f), 8f, 8f, fillPaint)

            val (displayCh, scaleY) = if (flip != null) {
                val t  = ((now - flip.startMs).toFloat() / DIGIT_FLIP_MS).coerceIn(0f, 1f)
                val sy = abs(1f - t * 2f)                  // 1 → 0 at mid → 1
                val dc = if (t < 0.5f) flip.from else flip.to
                if (t >= 1f) digitFlips.remove(pos)
                Pair(dc, sy)
            } else {
                Pair(ch, 1f)
            }

            canvas.save()
            canvas.scale(1f, scaleY, x, pivotY)
            // Shadow
            textPaint.color     = Color.argb(80, 0, 0, 0)
            textPaint.textAlign = Paint.Align.CENTER
            canvas.drawText("$displayCh", x + 4f, baseY + 4f, textPaint)
            // Fill
            textPaint.color = theme.textPrimary
            canvas.drawText("$displayCh", x, baseY, textPaint)
            // Outline
            textOutlinePaint.color = Color.argb(40, 28, 12, 0)
            canvas.drawText("$displayCh", x, baseY, textOutlinePaint)
            canvas.restore()
        }

        // Subtle separator between digits and best-score footer
        strokePaint.color       = Color.argb(30, 28, 12, 0)
        strokePaint.strokeWidth = 1.5f
        canvas.drawLine(pl + 28f, stripY + 174f, pr - 28f, stripY + 174f, strokePaint)

        // "best" — personal record anchored to bottom of panel
        val bestScore = if (board.cols == 6) prefs.highScore6x6 else prefs.highScore8x8
        if (bestScore > 0) {
            textPaint.textSize      = 26f
            textPaint.textAlign     = Paint.Align.CENTER
            textPaint.letterSpacing = 0.06f
            textPaint.color         = Color.argb(80, 0, 0, 0)
            canvas.drawText("best  \u00B7  $bestScore", surfaceW / 2f + 1f, stripY + 196f + 1f, textPaint)
            textPaint.color         = theme.textSecondary
            canvas.drawText("best  \u00B7  $bestScore", surfaceW / 2f, stripY + 196f, textPaint)
            textPaint.letterSpacing = 0f
        }

        canvas.restore()  // end heartbeat scale
    }

    // -----------------------------------------------------------------------
    // Floating labels (NICE!, AMAZING!, etc.)
    // -----------------------------------------------------------------------
    private fun drawFloatLabels(canvas: Canvas, now: Long) {
        synchronized(floatLabels) {
            val iter = floatLabels.iterator()
            while (iter.hasNext()) {
                val fl = iter.next()
                val t = ((now - fl.startMs).toFloat() / FLOAT_MS).coerceIn(0f, 1f)
                if (t >= 1f) { iter.remove(); continue }
                val alpha = if (t > 0.6f) ((1f - (t - 0.6f) / 0.4f) * 255).toInt().coerceIn(0, 255) else 255
                val rise  = cellSize * 1.8f * t
                val scale = if (t < 0.12f) (t / 0.12f) * 1.3f else 1.3f - (t - 0.12f) * 0.3f
                val sz    = 52f * scale
                canvas.save()
                canvas.translate(fl.cx, fl.cy - rise)
                // Shadow
                textPaint.color     = Color.argb((alpha * 0.4f).toInt(), 0, 0, 0)
                textPaint.textSize  = sz
                textPaint.textAlign = Paint.Align.CENTER
                canvas.drawText(fl.text, 3f, 3f + sz * 0.36f, textPaint)
                // Outline
                textOutlinePaint.color         = Color.argb((alpha * 0.8f).toInt(), 28, 12, 0)
                textOutlinePaint.textSize      = sz
                textOutlinePaint.textAlign     = Paint.Align.CENTER
                textOutlinePaint.strokeWidth   = 7f
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
    private fun drawCenterPing(canvas: Canvas, now: Long) {
        if (centerPingMs < 0 || dragChain.isEmpty()) return
        val t = ((now - centerPingMs).toFloat() / PING_MS).coerceIn(0f, 1f)
        if (t >= 1f) return

        // Position: just above the last cell in the chain
        val (lr, lc) = dragChain.last()
        val baseCx = boardLeft + lc * cellSize + cellSize / 2f
        val baseCy = boardTop  + lr * cellSize + cellSize / 2f

        // Scale: punch in big, ease back down
        val scale = if (t < 0.25f) 1f + (t / 0.25f) * 0.9f
                    else 1.9f - ((t - 0.25f) / 0.75f) * 0.9f
        val alpha = ((1f - t * t) * 255).toInt().coerceIn(0, 255)
        val floatY = baseCy - cellSize * 0.7f - t * cellSize * 0.4f

        val sz = cellSize * 0.82f * scale
        textPaint.textSize  = sz
        textPaint.textAlign = Paint.Align.CENTER

        // Dark outline
        textOutlinePaint.textSize    = sz
        textOutlinePaint.textAlign   = Paint.Align.CENTER
        textOutlinePaint.typeface    = boldTypeface
        textOutlinePaint.strokeWidth = sz * 0.12f
        textOutlinePaint.color       = Color.argb(alpha, 28, 12, 0)
        canvas.drawText("$centerPingCount", baseCx, floatY, textOutlinePaint)

        // White fill
        textPaint.color = Color.argb(alpha, 255, 255, 255)
        canvas.drawText("$centerPingCount", baseCx, floatY, textPaint)
    }

    // -----------------------------------------------------------------------
    // Milestone celebration
    // -----------------------------------------------------------------------
    private fun drawCelebration(canvas: Canvas, now: Long) {
        val elapsed = (now - celebrateMs).toFloat()
        if (elapsed > CELEBRATE_MS) { celebrateMs = -1L; particles.clear(); return }

        val t = elapsed / CELEBRATE_MS

        // Draw + update particles in-place — no new allocations per frame.
        val dt      = 1f / 60f
        val gravity = cellSize * 0.28f
        val alpha   = ((1f - (elapsed / CELEBRATE_MS)) * 255).toInt().coerceIn(0, 255)
        val iter = particles.iterator()
        while (iter.hasNext()) {
            val p = iter.next()
            // Physics: apply gravity to vy, then move.
            p.vy  += gravity * dt
            p.x   += p.vx * dt * 60f
            p.y   += p.vy * dt * 60f
            p.rot += p.rotSpeed
            if (p.y >= surfaceH + cellSize) { iter.remove(); continue }
            // Draw as a small rounded square rotated — reuse scratchRectF.
            canvas.save()
            canvas.translate(p.x, p.y)
            canvas.rotate(p.rot)
            fillPaint.color = (p.color and 0x00FFFFFF) or (alpha shl 24)
            scratchRectF.set(-p.radius, -p.radius * 0.6f, p.radius, p.radius * 0.6f)
            canvas.drawRoundRect(scratchRectF, p.radius * 0.3f, p.radius * 0.3f, fillPaint)
            canvas.restore()
        }
        fillPaint.alpha = 255

        // Banner — slides down from top, holds, then fades
        val SLIDE_MS = 300f; val HOLD_MS = 1200f; val FADE_MS = 400f
        val bannerAlpha = when {
            elapsed < SLIDE_MS              -> ((elapsed / SLIDE_MS) * 255).toInt()
            elapsed < SLIDE_MS + HOLD_MS    -> 255
            else -> (((CELEBRATE_MS - elapsed) / FADE_MS) * 255).toInt()
        }.coerceIn(0, 255)
        val slideT = easeOutQuint((elapsed / SLIDE_MS).coerceIn(0f, 1f))
        val bw = min(board.cols * cellSize * 0.88f, 420f)
        val bh = 100f
        val bx = surfaceW / 2f - bw / 2f
        val startY = boardTop - bh - 20f
        val endY   = boardTop + 18f
        val by = startY + (endY - startY) * slideT

        // Shadow
        fillPaint.color = Color.argb((bannerAlpha * 0.35f).toInt(), 0, 0, 0)
        canvas.drawRoundRect(RectF(bx + 5f, by + 8f, bx + bw + 5f, by + bh + 8f), 24f, 24f, fillPaint)
        // Dark border
        fillPaint.color = Color.argb(bannerAlpha, 28, 12, 0)
        canvas.drawRoundRect(RectF(bx - 6f, by - 6f, bx + bw + 6f, by + bh + 6f), 28f, 28f, fillPaint)
        // Fill — warm gold
        fillPaint.color = Color.argb(bannerAlpha, 255, 210, 50)
        canvas.drawRoundRect(RectF(bx, by, bx + bw, by + bh), 22f, 22f, fillPaint)
        // Top sheen
        canvas.save()
        canvas.clipRect(bx, by, bx + bw, by + bh * 0.45f)
        fillPaint.color = Color.argb((bannerAlpha * 0.35f).toInt(), 255, 255, 255)
        canvas.drawRoundRect(RectF(bx, by, bx + bw, by + bh), 22f, 22f, fillPaint)
        canvas.restore()

        // Text — milestone number large, label small
        val label = when {
            celebrateCount >= 500 -> "\u2605 $celebrateCount CLEARED! \u2605"
            celebrateCount >= 100 -> "$celebrateCount CLEARED! \u2605"
            else                  -> "$celebrateCount CLEARED!"
        }
        textPaint.textAlign     = Paint.Align.CENTER
        textPaint.textSize      = 36f
        textPaint.letterSpacing = 0.06f
        textPaint.color         = Color.argb((bannerAlpha * 0.5f).toInt(), 0, 0, 0)
        canvas.drawText(label, surfaceW / 2f + 2f, by + bh * 0.62f + 2f, textPaint)
        textPaint.color = Color.argb(bannerAlpha, 90, 40, 0)
        canvas.drawText(label, surfaceW / 2f, by + bh * 0.62f, textPaint)
        textPaint.letterSpacing = 0f
    }

    // -----------------------------------------------------------------------
    // First-run tutorial overlay
    // -----------------------------------------------------------------------
    private fun drawTutorial(canvas: Canvas, now: Long) {
        if (tutorialStartMs < 0L) tutorialStartMs = now
        // Auto-dismiss after 10 seconds
        if (now - tutorialStartMs > 10_000L) {
            tutorialActive = false; prefs.tutorialSeen = true; return
        }

        val loopT = ((now - tutorialStartMs) % TUTORIAL_LOOP_MS).toFloat()

        // Full-screen dim
        fillPaint.color = Color.argb(200, 0, 0, 0)
        canvas.drawRect(0f, 0f, surfaceW.toFloat(), surfaceH.toFloat(), fillPaint)

        val boardCX  = boardLeft + board.cols * cellSize / 2f
        val boardCY  = boardTop  + board.rows * cellSize / 2f
        val r        = cellSize * 0.43f
        val spacing  = cellSize * 1.05f
        val demoType = DonutType.values()[0]
        val x0 = boardCX - spacing; val x1 = boardCX; val x2 = boardCX + spacing
        val xs = floatArrayOf(x0, x1, x2)
        val dy = boardCY

        // Soft glow behind pieces
        fillPaint.color = Color.argb(50, 255, 255, 255)
        for (x in xs) canvas.drawCircle(x, dy, r * 1.5f, fillPaint)

        // Animation phases
        val FADE_MS  = 200f
        val SWEEP_MS = 850f
        val HOLD_MS  = 250f
        val BURST_MS = 300f
        val chainEnd = FADE_MS + SWEEP_MS + HOLD_MS

        // Chain line (sweep phase + hold phase)
        if (loopT >= FADE_MS && loopT < chainEnd) {
            val sweepFrac = ((loopT - FADE_MS) / SWEEP_MS).coerceIn(0f, 1f)
            val lineEndX  = x0 + (x2 - x0) * sweepFrac
            chainOutlinePaint.strokeWidth = cellSize * 0.38f
            chainLinePaint.strokeWidth    = cellSize * 0.22f
            chainOutlinePaint.color = Color.argb(180, 30, 15, 0)
            chainLinePaint.color    = Color.argb(245, 255, 255, 255)
            canvas.drawLine(x0, dy, lineEndX, dy, chainOutlinePaint)
            canvas.drawLine(x0, dy, lineEndX, dy, chainLinePaint)
        }

        // Pieces on top of chain line
        for (x in xs) drawPiece(canvas, x, dy, r, demoType, false)

        // Burst rings
        if (loopT >= chainEnd && loopT < chainEnd + BURST_MS) {
            val bt = (loopT - chainEnd) / BURST_MS
            val ba = ((1f - bt) * 200).toInt().coerceIn(0, 255)
            strokePaint.strokeWidth = cellSize * 0.06f
            for (x in xs) {
                strokePaint.color = Color.argb(ba, 255, 255, 255)
                canvas.drawCircle(x, dy, r * (1f + bt * 0.9f), strokePaint)
            }
            strokePaint.alpha = 255
        }

        // Finger cursor
        val cursorAlpha: Int
        val cx: Float
        when {
            loopT < FADE_MS -> {
                cursorAlpha = ((loopT / FADE_MS) * 220).toInt(); cx = x0
            }
            loopT < FADE_MS + SWEEP_MS -> {
                val t = (loopT - FADE_MS) / SWEEP_MS
                cursorAlpha = 220; cx = x0 + (x2 - x0) * t
            }
            loopT < chainEnd -> {
                cursorAlpha = 220; cx = x2
            }
            loopT < chainEnd + BURST_MS -> {
                val t = (loopT - chainEnd) / BURST_MS
                cursorAlpha = ((1f - t) * 220).toInt(); cx = x2
            }
            else -> { cursorAlpha = 0; cx = x0 }
        }
        if (cursorAlpha > 0) {
            fillPaint.color = Color.argb((cursorAlpha * 0.30f).toInt(), 255, 255, 255)
            canvas.drawCircle(cx, dy + r * 0.7f, r * 0.60f, fillPaint)
            fillPaint.color = Color.argb(cursorAlpha, 255, 255, 255)
            canvas.drawCircle(cx, dy + r * 0.7f, r * 0.18f, fillPaint)
            fillPaint.alpha = 255
        }

        // Instruction text
        textPaint.textSize  = 36f
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.color = Color.argb(80, 0, 0, 0)
        canvas.drawText("Draw through 3 matching pieces!", surfaceW / 2f + 2f, dy - r * 2.6f + 2f, textPaint)
        textPaint.color = Color.WHITE
        canvas.drawText("Draw through 3 matching pieces!", surfaceW / 2f, dy - r * 2.6f, textPaint)

        // Tap to play
        textPaint.textSize = 28f
        textPaint.color    = Color.argb(200, 255, 255, 255)
        canvas.drawText("Tap anywhere to play!", surfaceW / 2f, dy + r * 2.8f, textPaint)
    }

    // -----------------------------------------------------------------------
    // No-moves warning banner
    // -----------------------------------------------------------------------
    private fun drawNoMovesWarning(canvas: Canvas, now: Long) {
        if (noMovesWarningMs < 0) return
        val elapsed = (now - noMovesWarningMs).toFloat()
        // Fade in over 300ms
        val alpha = ((elapsed / 300f).coerceIn(0f, 1f) * 220).toInt()
        // Progress bar filling left→right over NO_MOVES_DELAY_MS
        val progress = (elapsed / NO_MOVES_DELAY_MS).coerceIn(0f, 1f)

        val boardCX = boardLeft + board.cols * cellSize / 2f
        val boardCY = boardTop  + board.rows * cellSize / 2f
        val bw = min(board.cols * cellSize * 0.82f, 380f)
        val bh = 108f
        val bx = boardCX - bw / 2f
        val by = boardCY - bh / 2f

        // Dark cartoon border
        fillPaint.color = Color.argb(alpha, 28, 12, 0)
        canvas.drawRoundRect(RectF(bx - 6f, by - 6f, bx + bw + 6f, by + bh + 6f), 26f, 26f, fillPaint)
        // Panel fill
        fillPaint.color = Color.argb(alpha, 255, 230, 80)
        canvas.drawRoundRect(RectF(bx, by, bx + bw, by + bh), 20f, 20f, fillPaint)
        // Top highlight
        canvas.save()
        canvas.clipRect(bx, by, bx + bw, by + bh * 0.45f)
        fillPaint.color = Color.argb((alpha * 0.30f).toInt(), 255, 255, 255)
        canvas.drawRoundRect(RectF(bx, by, bx + bw, by + bh), 20f, 20f, fillPaint)
        canvas.restore()
        // Progress bar (shows countdown to shuffle)
        val pbH = 12f; val pbPad = 18f
        val pbY = by + bh - pbH - pbPad
        fillPaint.color = Color.argb((alpha * 0.25f).toInt(), 28, 12, 0)
        canvas.drawRoundRect(RectF(bx + pbPad, pbY, bx + bw - pbPad, pbY + pbH), pbH/2, pbH/2, fillPaint)
        fillPaint.color = Color.argb(alpha, 200, 120, 0)
        canvas.drawRoundRect(RectF(bx + pbPad, pbY, bx + pbPad + (bw - pbPad*2) * progress, pbY + pbH), pbH/2, pbH/2, fillPaint)

        // Text — shadow then fill
        val lineY = by + bh * 0.46f
        textPaint.textSize  = 34f
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.color = Color.argb(alpha / 2, 0, 0, 0)
        canvas.drawText("No matches — shuffling!", boardCX + 2f, lineY + 2f, textPaint)
        textPaint.color = Color.argb(alpha, 100, 48, 0)
        canvas.drawText("No matches — shuffling!", boardCX, lineY, textPaint)
    }

    // -----------------------------------------------------------------------
    // Stickers panel
    // -----------------------------------------------------------------------
    /** Returns true if sticker i has been earned. */
    private fun isStickerEarned(i: Int): Boolean {
        val lifetime  = prefs.lifetimeDonuts + board.donutsCleared.values.sum()
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
            9  -> prefs.shufflesSurvived >= 5
            10 -> prefs.highScore6x6 > 0 && prefs.highScore8x8 > 0
            11 -> prefs.sessionCount >= 10
            else -> false
        }
    }

    private fun drawStickersPanel(canvas: Canvas, now: Long) {
        val eased  = easeOutQuint(stickersAnim)
        val slideY = stickerPanelRect.height() * (1f - eased)

        dimPaint.alpha = (eased * 160).toInt()
        canvas.drawRect(0f, 0f, surfaceW.toFloat(), surfaceH.toFloat(), dimPaint)

        canvas.save()
        canvas.translate(0f, slideY)

        // ---- Panel shell ----
        val pr = 36f
        fillPaint.color = Color.argb(230, 28, 12, 0)
        canvas.drawRoundRect(
            RectF(stickerPanelRect.left - 10f, stickerPanelRect.top - 10f,
                  stickerPanelRect.right + 10f, stickerPanelRect.bottom + 10f),
            pr + 10f, pr + 10f, fillPaint)
        strokePaint.color = Color.argb(200, 255, 255, 255)
        strokePaint.strokeWidth = 5f; strokePaint.alpha = 255
        canvas.drawRoundRect(
            RectF(stickerPanelRect.left - 5f, stickerPanelRect.top - 5f,
                  stickerPanelRect.right + 5f, stickerPanelRect.bottom + 5f),
            pr + 5f, pr + 5f, strokePaint)
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
        val titleY = stickerPanelRect.top + 78f
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.color = Color.argb(100, 0, 0, 0); textPaint.textSize = 58f
        canvas.drawText("My Stickers", titleX + 3f, titleY + 4f, textPaint)
        textPaint.color = theme.textPrimary
        canvas.drawText("My Stickers", titleX, titleY, textPaint)
        textOutlinePaint.color = Color.argb(80, 0, 0, 0); textOutlinePaint.strokeWidth = 6f
        textOutlinePaint.textSize = 58f; textOutlinePaint.textAlign = Paint.Align.CENTER
        textOutlinePaint.typeface = boldTypeface
        canvas.drawText("My Stickers", titleX, titleY, textOutlinePaint)

        val earnedCount = (0 until 12).count { isStickerEarned(it) }

        // ---- Sticker tiles ----
        val spinMs = 3200L   // shimmer ring rotation period
        for (i in 0 until 12) {
            val rect    = stickerRects[i]
            val earned  = isStickerEarned(i)
            val color   = STICKER_COLORS[i]
            val r       = 16f
            val cx      = rect.centerX()
            val cy      = rect.centerY()

            if (earned) {
                // Outer glow ring — pulsing
                val pulseT  = ((now % 1600L).toFloat() / 1600f)
                val pulse   = if (pulseT < 0.5f) pulseT * 2f else (1f - pulseT) * 2f
                val glowA   = (80 + (pulse * 100).toInt()).coerceIn(0, 255)
                val glowR   = Color.red(color); val glowG = Color.green(color); val glowB = Color.blue(color)
                strokePaint.color       = Color.argb(glowA, glowR, glowG, glowB)
                strokePaint.strokeWidth = 8f; strokePaint.alpha = glowA
                canvas.drawRoundRect(
                    RectF(rect.left - 6f, rect.top - 6f, rect.right + 6f, rect.bottom + 6f),
                    r + 6f, r + 6f, strokePaint)
                // Gold border
                fillPaint.color = Color.argb(220, 28, 12, 0)
                canvas.drawRoundRect(RectF(rect.left-6f, rect.top-6f, rect.right+6f, rect.bottom+6f), r+6f, r+6f, fillPaint)
                strokePaint.color = Color.rgb(255, 215, 50); strokePaint.strokeWidth = 4f; strokePaint.alpha = 255
                canvas.drawRoundRect(RectF(rect.left-4f, rect.top-4f, rect.right+4f, rect.bottom+4f), r+4f, r+4f, strokePaint)
            } else {
                // Plain dark border
                fillPaint.color = Color.argb(140, 28, 12, 0)
                canvas.drawRoundRect(RectF(rect.left-4f, rect.top-4f, rect.right+4f, rect.bottom+4f), r+4f, r+4f, fillPaint)
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
                // Symbol — large, visually centered in the tile
                val symSz      = rect.height() * 0.50f
                val symBaseline = cy + symSz * 0.36f
                textPaint.textSize  = symSz
                textPaint.textAlign = Paint.Align.CENTER
                textPaint.color     = Color.argb(70, 0, 0, 0)
                canvas.drawText(STICKER_SYMS[i], cx + 2f, symBaseline + 2f, textPaint)
                textPaint.color = Color.WHITE
                canvas.drawText(STICKER_SYMS[i], cx, symBaseline, textPaint)
                // Name — bottom
                val nameSz = rect.height() * 0.150f
                textPaint.textSize = nameSz
                textPaint.color    = Color.argb(90, 0, 0, 0)
                canvas.drawText(STICKER_NAMES[i], cx + 1f, rect.bottom - rect.height() * 0.09f + 1f, textPaint)
                textPaint.color = Color.WHITE
                canvas.drawText(STICKER_NAMES[i], cx, rect.bottom - rect.height() * 0.09f, textPaint)
                // Earned checkmark badge — top right
                val bx = rect.right - 1f; val by = rect.top + 1f; val br2 = 11f
                fillPaint.color = Color.argb(220, 28, 12, 0)
                canvas.drawCircle(bx, by, br2 + 2f, fillPaint)
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
                textPaint.textSize  = rect.height() * 0.115f
                textPaint.color     = Color.argb(130, 80, 50, 10)
                textPaint.textAlign = Paint.Align.CENTER
                canvas.drawText(STICKER_DESCS[i], cx, rect.bottom - rect.height() * 0.12f, textPaint)
            }
        }

        // ---- Stats line ----
        val statsY = stickerRects[11].bottom + 28f
        val lifetime = prefs.lifetimeDonuts + board.donutsCleared.values.sum()
        textPaint.textSize = 28f; textPaint.textAlign = Paint.Align.CENTER
        textPaint.color = theme.textSecondary
        canvas.drawText(
            "$lifetime donuts lifetime  \u00B7  $earnedCount of 12 stickers",
            stickerPanelRect.centerX(), statsY, textPaint)

        drawPrettyButton(canvas, stickersCloseRect, Color.rgb(60, 175, 80), "Done  \u2713", 32f)

        // Reset button — two-tap confirm: first tap → amber "Sure?"; second tap → executes
        val confirmActive = stickerResetConfirmMs >= 0 &&
                            (now - stickerResetConfirmMs) < STICKER_RESET_CONFIRM_MS
        if (stickerResetConfirmMs >= 0 && !confirmActive) stickerResetConfirmMs = -1L
        val resetBtnColor = if (confirmActive) Color.rgb(220, 130, 30) else Color.rgb(200, 70, 50)
        val resetBtnLabel = if (confirmActive) "Sure?" else "Reset"
        drawPrettyButton(canvas, stickersResetRect, resetBtnColor, resetBtnLabel, 26f)
        // Countdown bar drains left→right while confirm is pending
        if (confirmActive) {
            val progress = 1f - (now - stickerResetConfirmMs).toFloat() / STICKER_RESET_CONFIRM_MS
            val bx = stickersResetRect.left  + 10f
            val bw = stickersResetRect.width() - 20f
            val by = stickersResetRect.bottom - 12f
            val bh = 5f
            fillPaint.color = Color.argb(60, 28, 12, 0)
            canvas.drawRoundRect(RectF(bx, by, bx + bw, by + bh), bh / 2, bh / 2, fillPaint)
            fillPaint.color = Color.argb(220, 255, 200, 60)
            canvas.drawRoundRect(RectF(bx, by, bx + bw * progress, by + bh), bh / 2, bh / 2, fillPaint)
            fillPaint.alpha = 255
        }

        canvas.restore()
    }

    // Settings overlay — slides up from bottom
    // -----------------------------------------------------------------------
    private fun drawSettings(canvas: Canvas, now: Long) {
        val eased  = easeOutQuint(settingsAnim)
        val slideY = panelRect.height() * (1f - eased)

        dimPaint.alpha = (eased * 160).toInt()
        canvas.drawRect(0f, 0f, surfaceW.toFloat(), surfaceH.toFloat(), dimPaint)

        canvas.save()
        canvas.translate(0f, slideY)

        val pr = 36f   // panel corner radius

        // Thick dark cartoon outline (10px on each side)
        fillPaint.color = Color.argb(230, 28, 12, 0)
        canvas.drawRoundRect(
            RectF(panelRect.left - 10f, panelRect.top - 10f, panelRect.right + 10f, panelRect.bottom + 10f),
            pr + 10f, pr + 10f, fillPaint
        )
        // Bright accent ring inside the dark border
        strokePaint.color       = Color.argb(200, 255, 255, 255)
        strokePaint.strokeWidth = 5f; strokePaint.alpha = 255
        canvas.drawRoundRect(
            RectF(panelRect.left - 5f, panelRect.top - 5f, panelRect.right + 5f, panelRect.bottom + 5f),
            pr + 5f, pr + 5f, strokePaint
        )
        // Panel fill
        fillPaint.color = theme.panelBg; fillPaint.alpha = 255
        canvas.drawRoundRect(panelRect, pr, pr, fillPaint)
        // Top-half sheen
        canvas.save()
        canvas.clipRect(panelRect.left, panelRect.top, panelRect.right, panelRect.centerY())
        fillPaint.color = Color.argb(25, 255, 255, 255)
        canvas.drawRoundRect(panelRect, pr, pr, fillPaint)
        canvas.restore()

        val pl = panelRect.left; val pw = panelRect.width(); val pt = panelRect.top
        val sc = settingsSc

        // ---- Title ----
        val titleX = pl + pw / 2f; val titleY = pt + 76f * sc; val titleSz = 54f * sc
        textPaint.color = Color.argb(100, 0, 0, 0)
        textPaint.textSize = titleSz; textPaint.textAlign = Paint.Align.CENTER
        canvas.drawText("Settings", titleX + 4f, titleY + 5f, textPaint)
        textPaint.color = theme.textPrimary
        canvas.drawText("Settings", titleX, titleY, textPaint)
        textOutlinePaint.color = Color.argb(90, 0, 0, 0)
        textOutlinePaint.strokeWidth = 5f * sc
        textOutlinePaint.textSize    = titleSz
        textOutlinePaint.textAlign   = Paint.Align.CENTER
        textOutlinePaint.typeface    = boldTypeface
        canvas.drawText("Settings", titleX, titleY, textOutlinePaint)

        // ---- Theme section ----
        drawSectionLabel(canvas, "Theme", pl + 24f * sc, themeRects[0].top - 10f * sc)

        val swatchTypes = DonutType.values()
        for (i in 0 until 4) {
            val t    = GameTheme.all[i]
            val rect = themeRects[i]
            val sel  = i == prefs.themeIndex

            // Dark cartoon border — thicker on selected
            val borderPad = if (sel) 9f else 7f
            fillPaint.color = Color.argb(220, 28, 12, 0)
            canvas.drawRoundRect(
                RectF(rect.left - borderPad, rect.top - borderPad, rect.right + borderPad, rect.bottom + borderPad),
                26f, 26f, fillPaint
            )
            // Selected: gold outer ring
            if (sel) {
                strokePaint.color = Color.rgb(255, 215, 50); strokePaint.strokeWidth = 5f; strokePaint.alpha = 255
                canvas.drawRoundRect(
                    RectF(rect.left - borderPad + 2f, rect.top - borderPad + 2f,
                          rect.right + borderPad - 2f, rect.bottom + borderPad - 2f),
                    24f, 24f, strokePaint
                )
            }
            // Button fill — use theme bg
            fillPaint.color = t.bg; fillPaint.alpha = 255
            canvas.drawRoundRect(rect, 20f, 20f, fillPaint)
            // Top highlight
            canvas.save()
            canvas.clipRect(rect.left, rect.top, rect.right, rect.centerY())
            fillPaint.color = Color.argb(55, 255, 255, 255)
            canvas.drawRoundRect(rect, 20f, 20f, fillPaint)
            canvas.restore()

            // Mini piece swatches — 3 small colored circles using the theme's piece palette
            val swatchR   = rect.height() * 0.16f
            val swatchY   = rect.top + rect.height() * 0.38f
            val swatchGap = swatchR * 2.6f
            val swatchStartX = rect.centerX() - swatchGap
            for (s in 0 until 3) {
                val sc = swatchTypes[s]
                val sx = swatchStartX + s * swatchGap
                // Outline
                fillPaint.color = Color.argb(200, 28, 12, 0)
                canvas.drawCircle(sx, swatchY, swatchR + swatchR * 0.25f, fillPaint)
                // Body
                fillPaint.color = sc.bodyColor; fillPaint.alpha = 255
                canvas.drawCircle(sx, swatchY, swatchR, fillPaint)
                // Glaze dot
                fillPaint.color = sc.glazeColor
                canvas.drawCircle(sx, swatchY, swatchR * 0.62f, fillPaint)
                // Sheen
                fillPaint.color = Color.argb(80, 255, 255, 255)
                canvas.drawCircle(sx - swatchR * 0.22f, swatchY - swatchR * 0.28f, swatchR * 0.28f, fillPaint)
            }
            fillPaint.alpha = 255

            // Theme name below swatches
            textPaint.color    = Color.argb(80, 0, 0, 0)
            textPaint.textSize = 26f * sc; textPaint.textAlign = Paint.Align.CENTER
            val nameY = rect.bottom - rect.height() * 0.12f
            canvas.drawText(t.name, rect.centerX() + 2f, nameY + 2f, textPaint)
            textPaint.color = t.textPrimary
            canvas.drawText(t.name, rect.centerX(), nameY, textPaint)

            // Selected checkmark badge — top-right corner
            if (sel) {
                val badgeX = rect.right - 2f; val badgeY = rect.top + 2f; val badgeR = 16f * sc
                fillPaint.color = Color.argb(220, 28, 12, 0)
                canvas.drawCircle(badgeX, badgeY, badgeR + 3f, fillPaint)
                fillPaint.color = Color.rgb(80, 200, 80)
                canvas.drawCircle(badgeX, badgeY, badgeR, fillPaint)
                textPaint.textSize  = badgeR * 1.3f; textPaint.textAlign = Paint.Align.CENTER
                textPaint.color     = Color.WHITE
                canvas.drawText("✓", badgeX, badgeY + badgeR * 0.42f, textPaint)
            }
        }

        // ---- Hint Delay section ----
        drawSectionLabel(canvas, "Hint Delay", pl + 24f * sc, hintRects[0].top - 10f * sc)
        val currentHint = prefs.hintDelayMs
        for (i in 0 until 4) {
            drawSettingsBtn(canvas, hintRects[i], hintLabels[i], hintOptions[i] == currentHint)
        }

        // ---- Grid Size section ----
        drawSectionLabel(canvas, "Grid Size", pl + 24f * sc, gridRects[0].top - 10f * sc)
        for (i in 0 until 2) {
            drawSettingsBtn(canvas, gridRects[i], gridLabels[i], gridOptions[i] == prefs.gridSize)
        }

        // ---- Sound Pack section ----
        drawSectionLabel(canvas, "Sound Pack", pl + 24f * sc, packRects[0].top - 10f * sc)
        for (i in 0 until 4) {
            drawSettingsBtn(canvas, packRects[i], packLabels[i], packOptions[i] == prefs.soundPackIndex)
        }

        // ---- Haptic Style section ----
        drawSectionLabel(canvas, "Haptic Style", pl + 24f * sc, hapticRects[0].top - 10f * sc)
        for (i in 0 until 3) {
            drawSettingsBtn(canvas, hapticRects[i], hapticLabels[i], hapticOptions[i] == prefs.hapticTheme)
        }

        // Close — celebratory green "Done ✓"
        drawPrettyButton(canvas, settingsCloseRect, Color.rgb(60, 175, 80), "Done  \u2713", 32f * sc)

        canvas.restore()
    }

    /** Draws a section label with a chunky left accent bar — bold and readable. */
    private fun drawSectionLabel(canvas: Canvas, text: String, x: Float, baselineY: Float) {
        val sc       = settingsSc
        val labelSz  = 28f * sc
        val barW     = 8f * sc
        val barPad   = 4f * sc
        val textX    = x + barW + 12f

        textPaint.textSize  = labelSz
        textPaint.textAlign = Paint.Align.LEFT

        val barTop = baselineY - labelSz * 0.88f
        val barBot = baselineY + labelSz * 0.18f

        // Accent bar — dark border then theme color
        fillPaint.color = Color.argb(200, 28, 12, 0)
        canvas.drawRoundRect(RectF(x - barPad, barTop - barPad, x + barW + barPad, barBot + barPad),
            barW / 2f + barPad, barW / 2f + barPad, fillPaint)
        fillPaint.color = theme.btnSelected; fillPaint.alpha = 255
        canvas.drawRoundRect(RectF(x, barTop, x + barW, barBot),
            barW / 2f, barW / 2f, fillPaint)
        fillPaint.alpha = 255

        val upper = text.uppercase()
        textPaint.letterSpacing = 0.10f
        textPaint.color = Color.argb(70, 0, 0, 0)
        canvas.drawText(upper, textX + 2f, baselineY + 2f, textPaint)
        textPaint.color = theme.textPrimary
        canvas.drawText(upper, textX, baselineY, textPaint)
        textPaint.letterSpacing = 0f
    }

    private fun drawSettingsBtn(canvas: Canvas, rect: RectF, label: String, selected: Boolean) {
        val borderPad = if (selected) 8f else 6f
        // Dark cartoon border
        fillPaint.color = Color.argb(210, 28, 12, 0)
        canvas.drawRoundRect(
            RectF(rect.left - borderPad, rect.top - borderPad, rect.right + borderPad, rect.bottom + borderPad),
            26f, 26f, fillPaint
        )
        // Gold ring on selected
        if (selected) {
            strokePaint.color = Color.rgb(255, 215, 50); strokePaint.strokeWidth = 4f; strokePaint.alpha = 255
            canvas.drawRoundRect(
                RectF(rect.left - borderPad + 2f, rect.top - borderPad + 2f,
                      rect.right + borderPad - 2f, rect.bottom + borderPad - 2f),
                24f, 24f, strokePaint
            )
        }
        // Fill
        fillPaint.color = if (selected) theme.btnSelected else theme.btnUnselected; fillPaint.alpha = 255
        canvas.drawRoundRect(rect, 22f, 22f, fillPaint)
        // Top highlight
        canvas.save()
        canvas.clipRect(rect.left, rect.top, rect.right, rect.centerY())
        fillPaint.color = Color.argb(if (selected) 70 else 35, 255, 255, 255)
        canvas.drawRoundRect(rect, 22f, 22f, fillPaint)
        canvas.restore()
        val sc = settingsSc
        textPaint.color         = Color.argb(80, 0, 0, 0)
        textPaint.textSize      = 30f * sc
        textPaint.textAlign     = Paint.Align.CENTER
        textPaint.letterSpacing = 0.04f
        canvas.drawText(label, rect.centerX() + 2f, rect.centerY() + 10f * sc + 2f, textPaint)
        textPaint.color = Color.WHITE
        canvas.drawText(label, rect.centerX(), rect.centerY() + 10f * sc, textPaint)
        textPaint.letterSpacing = 0f
        // Checkmark badge on selected
        if (selected) {
            val bx = rect.right - 1f; val by = rect.top + 1f; val br = 13f * sc
            fillPaint.color = Color.argb(210, 28, 12, 0)
            canvas.drawCircle(bx, by, br + 3f, fillPaint)
            fillPaint.color = Color.rgb(80, 200, 80); fillPaint.alpha = 255
            canvas.drawCircle(bx, by, br, fillPaint)
            textPaint.textSize  = br * 1.3f; textPaint.textAlign = Paint.Align.CENTER
            textPaint.color     = Color.WHITE
            canvas.drawText("✓", bx, by + br * 0.42f, textPaint)
        }
    }

    // -----------------------------------------------------------------------
    // Touch
    // -----------------------------------------------------------------------
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action != MotionEvent.ACTION_DOWN &&
            event.action != MotionEvent.ACTION_MOVE &&
            event.action != MotionEvent.ACTION_UP &&
            event.action != MotionEvent.ACTION_CANCEL) return true

        synchronized(holder) {
            // Tutorial tap-to-dismiss
            if (tutorialActive) {
                if (event.action == MotionEvent.ACTION_DOWN) {
                    tutorialActive = false
                    prefs.tutorialSeen = true
                }
                return true
            }

            // When settings or stickers is open or animating, consume touch
            if (settingsOpen || settingsAnim > 0f || stickersOpen || stickersAnim > 0f) {
                if (event.action == MotionEvent.ACTION_DOWN) {
                    if (settingsOpen || settingsAnim > 0f) {
                        val eased  = easeOutQuint(settingsAnim)
                        val slideY = panelRect.height() * (1f - eased)
                        handleSettingsTouch(event.x, event.y + slideY)
                    } else {
                        val eased  = easeOutQuint(stickersAnim)
                        val slideY = stickerPanelRect.height() * (1f - eased)
                        handleStickersTouch(event.x, event.y + slideY)
                    }
                }
                return true
            }

            if (animPhase != AnimPhase.IDLE) return true

            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    val now = SystemClock.elapsedRealtime()
                    when {
                        resetBtnRect.contains(event.x, event.y) -> {
                            resetPressMs     = now
                            saveSession()
                            board.reset()
                            displayedCount   = 0
                            prevDisplayCount = -1
                            digitFlips.clear()
                            lastMilestone    = 0
                            celebrateMs      = -1L
                            particles.clear()
                            synchronized(floatLabels) { floatLabels.clear() }
                            dragChain.clear(); chainPings.clear()
                            selRow = -1; selCol = -1
                            pendingResult    = null
                            cascadeCount     = 0
                            isCascade        = false
                            cascadeLabelMs   = -1L
                            counterPulseMs   = -1L
                            chainFlashMs     = -1L
                            shuffleAnimMs    = -1L
                            hintCells        = emptyList()
                            noMovesWarningMs = -1L
                            resetFlashMs     = now
                            lastActionMs     = now
                            boardEntryMs     = now
                        }
                        stickersBtnRect.contains(event.x, event.y) -> {
                            stickersPressMs = now
                            stickersOpen    = true
                        }
                        soundBtnRect.contains(event.x, event.y) -> {
                            soundPressMs       = now
                            prefs.soundEnabled = !prefs.soundEnabled
                        }
                        hapticBtnRect.contains(event.x, event.y) -> {
                            hapticPressMs       = now
                            prefs.hapticEnabled = !prefs.hapticEnabled
                            if (prefs.hapticEnabled) hapticEngine.tick()
                        }
                        settingsBtnRect.contains(event.x, event.y) -> {
                            settingsPressMs = now
                            settingsOpen    = true
                        }
                        else -> handleDown(event)
                    }
                }
                MotionEvent.ACTION_MOVE   -> handleMove(event)
                MotionEvent.ACTION_UP     -> handleUp()
                MotionEvent.ACTION_CANCEL -> { dragChain.clear(); selRow = -1; selCol = -1 }
            }
        }
        return true
    }

    private fun handleSettingsTouch(x: Float, y: Float) {
        for (i in 0 until 4) {
            if (themeRects[i].contains(x, y)) { prefs.themeIndex = i; return }
        }
        for (i in 0 until 4) {
            if (hintRects[i].contains(x, y)) { prefs.hintDelayMs = hintOptions[i]; return }
        }
        for (i in 0 until 2) {
            if (gridRects[i].contains(x, y)) {
                if (prefs.gridSize != gridOptions[i]) {
                    prefs.gridSize = gridOptions[i]
                    rebuildBoard()
                }
                settingsOpen = false
                return
            }
        }
        for (i in 0 until 4) {
            if (packRects[i].contains(x, y)) { prefs.soundPackIndex = packOptions[i]; return }
        }
        for (i in 0 until 3) {
            if (hapticRects[i].contains(x, y)) { prefs.hapticTheme = hapticOptions[i]; return }
        }
        if (settingsCloseRect.contains(x, y) || !panelRect.contains(x, y)) {
            settingsOpen = false
        }
    }

    private fun handleStickersTouch(x: Float, y: Float) {
        val now = SystemClock.elapsedRealtime()
        when {
            stickersResetRect.contains(x, y) -> {
                val confirmActive = stickerResetConfirmMs >= 0 &&
                                    (now - stickerResetConfirmMs) < STICKER_RESET_CONFIRM_MS
                if (confirmActive) {
                    resetStickerProgress()
                    stickerResetConfirmMs = -1L
                } else {
                    stickerResetConfirmMs = now   // arm the confirm
                }
            }
            stickersCloseRect.contains(x, y) || !stickerPanelRect.contains(x, y) -> {
                stickerResetConfirmMs = -1L
                stickersOpen = false
            }
        }
    }

    private fun resetStickerProgress() {
        prefs.lifetimeDonuts   = 0
        prefs.highScore6x6     = 0
        prefs.highScore8x8     = 0
        prefs.bestChainLength  = 0
        prefs.shufflesSurvived = 0
        prefs.sessionCount     = 0
        board.donutsCleared.keys.forEach { board.donutsCleared[it] = 0 }
        displayedCount   = 0
        prevDisplayCount = -1
        lastMilestone    = 0
        digitFlips.clear()
    }

    private fun handleDown(event: MotionEvent) {
        lastActionMs = SystemClock.elapsedRealtime(); hintCells = emptyList()
        dragChain.clear(); chainPings.clear()
        val col = cellCol(event.x); val row = cellRow(event.y)
        if (inBounds(row, col)) {
            val now2 = SystemClock.elapsedRealtime()
            selRow = row; selCol = col
            dragChain.add(Pair(row, col))
            chainPings[Pair(row, col)] = now2
            centerPingMs = now2; centerPingCount = 1
        }
    }

    private fun handleMove(event: MotionEvent) {
        val col = cellCol(event.x); val row = cellRow(event.y)
        if (!inBounds(row, col)) return
        val cell = Pair(row, col)
        if (dragChain.size >= 2 && dragChain[dragChain.size - 2] == cell) {
            chainPings.remove(dragChain.last())
            dragChain.removeAt(dragChain.size - 1)
            val now2 = SystemClock.elapsedRealtime()
            centerPingMs = now2; centerPingCount = dragChain.size
            return
        }
        if (cell in dragChain) return
        val last = dragChain.lastOrNull() ?: return
        if (!adjacent8(last.first, last.second, row, col)) return
        // Golden cells are wild — they join any chain regardless of type.
        // Non-golden cells must match the chain type.
        val chainType = dragChainType ?: return
        val cellIsGolden = board.grid[row][col].isGolden
        if (!cellIsGolden && board.grid[row][col].type != chainType) return
        val now2 = SystemClock.elapsedRealtime()
        dragChain.add(cell)
        chainPings[cell] = now2
        centerPingMs = now2; centerPingCount = dragChain.size
        if (prefs.soundEnabled)  soundEngine.playConnectBlip(dragChain.size)
        if (prefs.hapticEnabled) hapticEngine.tick()
    }

    private fun handleUp() {
        val chain = dragChain.toList()
        if (chain.size >= 3) {
            val result = board.peekChainClear(chain)
            if (result != null) {
                pendingResult = result
                isCascade     = false
                cascadeCount  = 0

                // Include both the player's chain AND any power-up bonus cells in the
                // pop animation so everything lights up at once before the clear fires.
                popCells.clear()
                val allPop = result.chainCells + result.bonusCells
                popCells.addAll(allPop.map { (r, c) ->
                    AnimCell(r, c, board.grid[r][c].type, isGolden = board.grid[r][c].isGolden)
                })

                val now = SystemClock.elapsedRealtime()
                animStartMs = now; animPhase = AnimPhase.POPPING
                lastActionMs = now; hintCells = emptyList()

                val chainLen = result.chainCells.size
                if (chainLen > prefs.bestChainLength) prefs.bestChainLength = chainLen
                if (chainLen >= 6) {
                    chainFlashMs    = now
                    chainFlashColor = result.chainType.bodyColor
                }
                if (prefs.soundEnabled)  soundEngine.playPopClear()
                if (prefs.hapticEnabled) hapticEngine.pop()

                // Floating label for big chains
                val label = when {
                    result.powerUp == PowerUp.COLOR_BURST -> "COLOR BURST!"
                    result.powerUp == PowerUp.ROW_BLAST   -> "ROW BLAST!"
                    result.powerUp == PowerUp.BOMB        -> "BOOM!"
                    chainLen == 4 -> "NICE!"
                    chainLen == 5 -> "GREAT!"
                    chainLen == 6 -> "AMAZING!"
                    chainLen == 7 -> "WOW!"
                    chainLen >= 8 -> "INCREDIBLE!"
                    else          -> null
                }
                label?.let {
                    val cx = allPop.map { (_, c) -> boardLeft + c * cellSize + cellSize / 2f }.average().toFloat()
                    val cy = allPop.map { (r, _) -> boardTop  + r * cellSize + cellSize / 2f }.average().toFloat()
                    val colors = intArrayOf(
                        Color.rgb(255, 80, 60), Color.rgb(255, 180, 0),
                        Color.rgb(80, 210, 80), Color.rgb(60, 160, 255), Color.rgb(200, 80, 255)
                    )
                    val col = colors[(chainLen - 4).coerceIn(0, colors.size - 1)]
                    synchronized(floatLabels) { floatLabels.add(FloatLabel(it, cx, cy, col, now)) }
                }
            }
        }
        dragChain.clear(); chainPings.clear(); selRow = -1; selCol = -1
    }

    private fun cellCol(x: Float) = ((x - boardLeft) / cellSize).toInt()
    private fun cellRow(y: Float) = ((y - boardTop)  / cellSize).toInt()
    private fun inBounds(r: Int, c: Int) = r in 0 until board.rows && c in 0 until board.cols
    private fun adjacent8(r1: Int, c1: Int, r2: Int, c2: Int) =
        abs(r1 - r2) <= 1 && abs(c1 - c2) <= 1 && !(r1 == r2 && c1 == c2)

    // -----------------------------------------------------------------------
    // Render thread
    // -----------------------------------------------------------------------
    inner class RenderThread(private val holder: SurfaceHolder) : Thread("GameRenderThread") {
        @Volatile var running = true
        override fun run() {
            while (running) {
                val canvas = holder.lockCanvas() ?: continue
                try { synchronized(holder) { drawFrame(canvas) } }
                finally { holder.unlockCanvasAndPost(canvas) }
                sleep(16L)
            }
        }
    }
}
