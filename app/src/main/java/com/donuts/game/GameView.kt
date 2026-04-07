package com.donuts.game

import android.content.Context
import android.graphics.*
import android.os.SystemClock
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
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
    private val counterH  = 130f

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
    private val dragChainType: DonutType?
        get() = dragChain.firstOrNull()?.let { (r, c) -> board.grid[r][c].type }

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
    private data class AnimCell(val row: Int, val col: Int, val type: DonutType, val fromRow: Int = row)
    private val popCells    = mutableListOf<AnimCell>()
    private val dropCells   = mutableListOf<AnimCell>()  // all moving cells during DROPPING
    private val dropColMask = mutableSetOf<Int>()        // columns hidden from the normal draw loop
    private var pendingChain = emptyList<Pair<Int, Int>>()

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
    private var stickersPressMs   = -1L
    private var stickerPanelRect  = RectF()
    private val stickerRects      = Array(12) { RectF() }
    private var stickersCloseRect = RectF()

    // 12 stickers — 4 rows × 3 cols. Symbols use reliable Unicode/ASCII.
    private val STICKER_SYMS   = arrayOf(
        "\u2726", "\u2605", "\u2665",  // row 1: Donut Collector (✦ ★ ♥)
        "4",      "6",      "8",       // row 2: Chain Master
        "20",     "60",     "120",     // row 3: High Scorer
        "\u21BA", "\u2295", "\u221E"   // row 4: Special (↺ ⊕ ∞)
    )
    private val STICKER_NAMES  = arrayOf(
        "First Taste!",  "Donut Fan",    "Donut Legend",
        "Linked Up",     "Chain Pro",    "Chain God",
        "Scorer",        "Star Player",  "Top Score",
        "Survivor",      "Explorer",     "Dedicated"
    )
    private val STICKER_DESCS  = arrayOf(
        "10 donuts",  "50 donuts",  "200 donuts",
        "chain of 4", "chain of 6", "chain of 8",
        "score 20",   "score 60",   "score 120",
        "1 shuffle",  "both grids", "5 sessions"
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

    // Squish on land — per-cell damped bounce when a dropping piece arrives
    private val squishCells = HashMap<Pair<Int,Int>, Long>()  // (row,col) → squish start ms
    private val SQUISH_MS   = 380L

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
    private data class Particle(
        val x: Float, val y: Float,
        val vx: Float, val vy: Float,
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
    // Paints (allocated once)
    // -----------------------------------------------------------------------
    private val fillPaint         = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val strokePaint       = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val outlinePaint      = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
    private val textPaint         = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; typeface = Typeface.DEFAULT_BOLD }
    private val textOutlinePaint  = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeJoin = Paint.Join.ROUND }
    private val chainOutlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
    private val chainLinePaint    = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
    private val hintRingPaint     = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val dimPaint          = Paint().apply { color = Color.argb(160, 0, 0, 0) }
    private val shadowPaint       = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val flashPaint        = Paint().apply { style = Paint.Style.FILL }

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
        resetBtnRect    = RectF(boardLeft, btnY, boardLeft + 230f, btnY + btnH)
        val sbEnd = boardLeft + board.cols * cellSize
        // Settings ⚙ (100px) + haptic ≋ (100px) + sound ♪ (100px) — 10px gaps
        settingsBtnRect = RectF(sbEnd - 100f, btnY, sbEnd,          btnY + btnH)
        hapticBtnRect   = RectF(sbEnd - 210f, btnY, sbEnd - 110f,   btnY + btnH)
        soundBtnRect    = RectF(sbEnd - 320f, btnY, sbEnd - 220f,   btnY + btnH)
        // Stickers ★ — centred on the full screen width (matches title alignment)
        stickersBtnRect = RectF(w / 2f - 60f, btnY, w / 2f + 60f, btnY + btnH)

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
        stickersCloseRect = RectF(spL + spPad, stickerPanelRect.bottom - spCloseH - spPad, spL + spW - spPad, stickerPanelRect.bottom - spPad)
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
                // Snapshot full board BEFORE mutating.
                val preTypes = Array(board.rows) { r ->
                    Array(board.cols) { c -> board.grid[r][c].type }
                }
                board.clearChain(pendingChain)
                board.resolveAll()
                // Build one dropCells list where every cell has:
                //   row     = destination (post-gravity)
                //   fromRow = where it starts the animation
                // New pieces (fromRow < 0): fall in from above the board.
                // Survivors (fromRow >= 0): slide down from their old row to their new row.
                // dropColMask hides changed columns from the normal draw loop.
                dropCells.clear(); dropColMask.clear()
                for (c in 0 until board.cols) {
                    // Match survivors bottom-up between pre- and post-states.
                    var postR = board.rows - 1
                    val survivorMap = mutableListOf<Pair<Int,Int>>() // (preRow, postRow)
                    for (preR in board.rows - 1 downTo 0) {
                        if (postR >= 0 && board.grid[postR][c].type == preTypes[preR][c]) {
                            survivorMap.add(0, Pair(preR, postR))
                            postR--
                        }
                    }
                    val k = postR + 1   // number of new pieces at top
                    if (k == 0) continue
                    dropColMask.add(c)
                    // New pieces: start k rows above their destination so they
                    // all travel the same distance and land simultaneously.
                    for (row in 0 until k)
                        dropCells.add(AnimCell(row, c, board.grid[row][c].type, fromRow = row - k))
                    // Survivors: slide from old row down to new row.
                    for ((preRow, postRow) in survivorMap)
                        dropCells.add(AnimCell(postRow, c, board.grid[postRow][c].type, fromRow = preRow))
                }
                popCells.clear(); animPhase = AnimPhase.DROPPING; animStartMs = now
            }
            AnimPhase.DROPPING -> if (now - animStartMs >= DROP_MS) {
                for (cell in dropCells) squishCells[cell.row to cell.col] = now
                dropCells.clear(); dropColMask.clear()
                animPhase = AnimPhase.IDLE
                if (prefs.soundEnabled) soundEngine.playDropLand()
                if (!board.hasValidMoves()) noMovesWarningMs = now
            }
            AnimPhase.IDLE -> {
                // Auto-shuffle after warning delay
                if (noMovesWarningMs >= 0 && now - noMovesWarningMs >= NO_MOVES_DELAY_MS) {
                    board.shuffle()
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
        // Advance particles
        if (particles.isNotEmpty()) {
            val dt = 1f / 60f
            val iter = particles.iterator()
            while (iter.hasNext()) {
                val p = iter.next()
                // gravity + update — we don't mutate Particle fields (val), so rebuild into new list below
            }
        }
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
    // HUD
    // -----------------------------------------------------------------------
    private fun drawHUD(canvas: Canvas, now: Long) {
        val titleX = surfaceW / 2f

        // "for Steven" baseline sits just above the buttons; "Donuts" above that
        val sz2    = 22f
        val line2Y = resetBtnRect.top - 14f
        val sz1    = 36f
        val line1Y = line2Y - sz2 - 8f

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

        drawPrettyButton(canvas, resetBtnRect,    theme.resetBtn,    "RESET",     28f, resetScale)
        drawPrettyButton(canvas, stickersBtnRect, theme.settingsBtn, "\u2605",    30f, stickerScale)
        drawPrettyButton(canvas, soundBtnRect,    soundColor,        "\u266A",    34f, soundScale)
        drawPrettyButton(canvas, hapticBtnRect,   hapticColor,       "\u2248",    34f, hapticScale)
        drawPrettyButton(canvas, settingsBtnRect, theme.settingsBtn, "\u2699",    38f, settingsScale)
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
        textPaint.color     = Color.argb(80, 0, 0, 0)
        textPaint.textSize  = labelSize
        textPaint.textAlign = Paint.Align.CENTER
        canvas.drawText(label, rect.centerX() + 2f, rect.centerY() + labelSize * 0.36f + 2f, textPaint)
        // Label
        textPaint.color = Color.WHITE
        canvas.drawText(label, rect.centerX(), rect.centerY() + labelSize * 0.36f, textPaint)

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
        val path = Path()
        dragChain.forEachIndexed { i, (r, c) ->
            val cx = boardLeft + c * cellSize + cellSize / 2f
            val cy = boardTop  + r * cellSize + cellSize / 2f
            if (i == 0) path.moveTo(cx, cy) else path.lineTo(cx, cy)
        }
        // Color-match chain to the piece type
        val chainColor = dragChainType?.glazeColor ?: Color.WHITE
        val cr = Color.red(chainColor); val cg = Color.green(chainColor); val cb = Color.blue(chainColor)

        // Wide color glow
        chainOutlinePaint.strokeWidth = cellSize * 0.72f
        chainOutlinePaint.color = Color.argb(55, cr, cg, cb)
        canvas.drawPath(path, chainOutlinePaint)
        // Mid color layer
        chainOutlinePaint.strokeWidth = cellSize * 0.50f
        chainOutlinePaint.color = Color.argb(120, cr, cg, cb)
        canvas.drawPath(path, chainOutlinePaint)
        // Dark cartoon border
        chainOutlinePaint.strokeWidth = cellSize * 0.38f
        chainOutlinePaint.color = Color.argb(200, 28, 12, 0)
        canvas.drawPath(path, chainOutlinePaint)
        // Colored core
        chainLinePaint.strokeWidth = cellSize * 0.24f
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
                val breatheScale = if (animPhase == AnimPhase.IDLE && !inChain) {
                    val phase  = (r * board.cols + c) * 0.61f   // golden-ratio-ish spread
                    val period = 1400f + (r * board.cols + c) % 5 * 160f
                    val t      = ((now / period + phase) * 2f * PI.toFloat())
                    1f + sin(t) * 0.05f
                } else 1f

                // Ping scale-pop when cell joins chain
                val pingMs = chainPings[Pair(r, c)]
                val pingScale = pingMs?.let { pm ->
                    val t = ((now - pm).toFloat() / PING_MS).coerceIn(0f, 1f)
                    if (t < 0.35f) 1f + (t / 0.35f) * 0.28f
                    else 1.28f - ((t - 0.35f) / 0.65f) * 0.28f
                } ?: 1f

                val finalScale = if (inChain) breatheScale * pingScale else breatheScale

                // Squish on land — damped cosine oscillation
                val squishMs = squishCells[r to c]
                var sqX = 1f; var sqY = 1f
                if (squishMs != null) {
                    val t = ((now - squishMs).toFloat() / SQUISH_MS).coerceIn(0f, 1f)
                    if (t >= 1f) {
                        squishCells.remove(r to c)
                    } else {
                        val factor = (1f - t) * (1f - t) * cos(t * PI.toFloat() * 3.5f) * 0.32f
                        sqX = 1f + factor; sqY = 1f - factor
                    }
                }
                if (sqX != 1f) {
                    canvas.save()
                    canvas.scale(sqX, sqY, cx, cy)
                    drawPiece(canvas, cx, cy, cellSize * 0.43f * finalScale, board.grid[r][c].type, inChain)
                    canvas.restore()
                } else {
                    drawPiece(canvas, cx, cy, cellSize * 0.43f * finalScale, board.grid[r][c].type, inChain)
                }

                // Expanding ring on ping
                pingMs?.let { pm ->
                    val t = ((now - pm).toFloat() / PING_MS).coerceIn(0f, 1f)
                    val ringAlpha = ((1f - t) * (1f - t) * 200).toInt().coerceIn(0, 255)
                    strokePaint.color       = Color.argb(ringAlpha, 255, 255, 255)
                    strokePaint.strokeWidth = cellSize * 0.06f
                    canvas.drawCircle(cx, cy, cellSize * (0.43f + t * 0.38f), strokePaint)
                    strokePaint.alpha = 255
                }

                if (hintCells.isNotEmpty() && Pair(r, c) in hintCells) {
                    hintRingPaint.color       = theme.hintRing
                    hintRingPaint.alpha       = hintAlpha
                    hintRingPaint.strokeWidth = cellSize * 0.12f
                    canvas.drawCircle(cx, cy, cellSize * 0.47f * breatheScale, hintRingPaint)
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
            // Expanding burst ring
            val burstT     = (t / 0.6f).coerceIn(0f, 1f)
            val burstAlpha = ((1f - burstT) * 200).toInt().coerceIn(0, 255)
            strokePaint.strokeWidth = cellSize * 0.07f
            strokePaint.alpha       = burstAlpha
            for (cell in popCells) {
                val cx = boardLeft + cell.col * cellSize + cellSize / 2f
                val cy = boardTop  + cell.row * cellSize + cellSize / 2f
                strokePaint.color = Color.argb(burstAlpha, 255, 255, 255)
                canvas.drawCircle(cx, cy, cellSize * (0.44f + burstT * 0.52f), strokePaint)
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
                // New pieces (fromRow < 0) fade in; survivors are always fully opaque
                val alpha  = if (cell.fromRow < 0) (eased * 255).toInt().coerceIn(0, 255) else 255
                drawPiece(canvas, cx, cy, cellSize * 0.43f, cell.type, false, alpha)
            }
        }
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
        // Soft top-left crescent via clipPath
        val hlPath = Path()
        hlPath.addOval(
            RectF(cx - r * 0.88f, cy - r * 1.05f, cx + r * 0.52f, cy + r * 0.05f),
            Path.Direction.CW
        )
        canvas.save()
        canvas.clipPath(hlPath)
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
        // 3D sheen clipped to glaze circle so it doesn't bleed into the body ring
        val glazePath = Path().apply { addCircle(cx, cy, radius * 0.82f, Path.Direction.CW) }
        canvas.save()
        canvas.clipPath(glazePath)
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
            val angle = Math.toRadians(i * 72.0 + 18.0)
            canvas.drawCircle(
                cx + dist * cos(angle).toFloat(),
                cy + dist * sin(angle).toFloat(),
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
        // Dark outline (stroked around outer star path)
        outlinePaint.color       = Color.argb(alpha, 28, 12, 0)
        outlinePaint.strokeWidth = ow * 2f
        canvas.drawPath(buildStarPath(cx, cy, radius + ow * 0.5f, (radius + ow * 0.5f) * 0.42f), outlinePaint)
        // Drop shadow
        fillPaint.color = Color.argb(alpha / 3, 0, 0, 0)
        canvas.drawPath(buildStarPath(cx + radius * 0.05f, cy + radius * 0.10f, radius, radius * 0.42f), fillPaint)
        // Body star — build path once, reuse for both fill and sheen clip
        val bodyPath = buildStarPath(cx, cy, radius, radius * 0.42f)
        fillPaint.color = type.bodyColor; fillPaint.alpha = alpha
        canvas.drawPath(bodyPath, fillPaint)
        // 3D sheen clipped to star shape so it doesn't bleed into concave areas
        canvas.save()
        canvas.clipPath(bodyPath)
        addSheen(canvas, cx, cy, radius, alpha, sheenAlpha = 0.28f)
        canvas.restore()
        // Inner accent star
        fillPaint.color = type.glazeColor; fillPaint.alpha = alpha
        canvas.drawPath(buildStarPath(cx, cy, radius * 0.58f, radius * 0.24f), fillPaint)
        // Inner star rim light (brighter edge)
        outlinePaint.color       = Color.argb((alpha * 0.4f).toInt(), 255, 255, 255)
        outlinePaint.strokeWidth = radius * 0.04f
        canvas.drawPath(buildStarPath(cx, cy, radius * 0.58f, radius * 0.24f), outlinePaint)
        fillPaint.alpha = 255
    }

    private fun buildStarPath(cx: Float, cy: Float, outerR: Float, innerR: Float): Path {
        val path = Path()
        for (i in 0 until 10) {
            val angle = Math.toRadians(-90.0 + i * 36.0)
            val r = if (i % 2 == 0) outerR else innerR
            val x = cx + (r * cos(angle)).toFloat()
            val y = cy + (r * sin(angle)).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        return path
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

        // Drop shadow pass (single large offset shape)
        fillPaint.color = Color.argb(alpha / 4, 0, 0, 0)
        canvas.drawOval(RectF(cx - r*0.74f + r*0.06f, cy - r*0.28f + r*0.12f,
                              cx + r*0.58f + r*0.06f, cy + r*0.60f + r*0.12f), fillPaint)

        // ----- Tail -----
        val tail = Path().apply {
            moveTo(cx - r*0.66f, cy - r*0.04f)
            lineTo(cx - r*0.66f, cy + r*0.34f)
            lineTo(cx - r*1.06f, cy - r*0.12f)
            close()
        }
        canvas.drawPath(tail, outlinePaint)
        fillPaint.color = type.bodyColor; fillPaint.alpha = alpha
        canvas.drawPath(tail, fillPaint)

        // ----- Body -----
        val bodyRect = RectF(cx - r*0.74f, cy - r*0.28f, cx + r*0.58f, cy + r*0.60f)
        canvas.drawOval(bodyRect, outlinePaint)
        fillPaint.color = type.bodyColor; fillPaint.alpha = alpha
        canvas.drawOval(bodyRect, fillPaint)

        // ----- Head -----
        val headRect = RectF(cx + r*0.22f, cy - r*0.76f, cx + r*0.92f, cy - r*0.08f)
        canvas.drawOval(headRect, outlinePaint)
        fillPaint.color = type.bodyColor; fillPaint.alpha = alpha
        canvas.drawOval(headRect, fillPaint)

        // ----- Snout -----
        val snoutRect = RectF(cx + r*0.54f, cy - r*0.44f, cx + r*1.05f, cy - r*0.06f)
        canvas.drawOval(snoutRect, outlinePaint)
        fillPaint.color = type.bodyColor; fillPaint.alpha = alpha
        canvas.drawOval(snoutRect, fillPaint)

        // ----- Spines (glazeColor) -----
        outlinePaint.color = Color.argb(alpha, 28, 12, 0)
        fillPaint.color = type.glazeColor; fillPaint.alpha = alpha
        for (i in 0 until 3) {
            val sx = cx - r*0.34f + i * r*0.28f
            val spine = Path().apply {
                moveTo(sx - r*0.09f, cy - r*0.28f)
                lineTo(sx,           cy - r*0.64f)
                lineTo(sx + r*0.09f, cy - r*0.28f)
                close()
            }
            canvas.drawPath(spine, outlinePaint)
            canvas.drawPath(spine, fillPaint)
        }

        // ----- Arm (glazeColor) -----
        val armRect = RectF(cx + r*0.16f, cy - r*0.02f, cx + r*0.52f, cy + r*0.20f)
        canvas.drawOval(armRect, outlinePaint)
        fillPaint.color = type.glazeColor; fillPaint.alpha = alpha
        canvas.drawOval(armRect, fillPaint)

        // ----- Legs -----
        val legRx = r * 0.10f
        val leg1 = RectF(cx - r*0.52f, cy + r*0.52f, cx - r*0.16f, cy + r*0.90f)
        val leg2 = RectF(cx + r*0.02f, cy + r*0.52f, cx + r*0.38f, cy + r*0.90f)
        outlinePaint.color = Color.argb(alpha, 28, 12, 0)
        canvas.drawRoundRect(leg1, legRx, legRx, outlinePaint)
        canvas.drawRoundRect(leg2, legRx, legRx, outlinePaint)
        fillPaint.color = type.bodyColor; fillPaint.alpha = alpha
        canvas.drawRoundRect(leg1, legRx, legRx, fillPaint)
        canvas.drawRoundRect(leg2, legRx, legRx, fillPaint)

        // 3D sheen clipped to body oval so it doesn't bleed above it
        val bodyOvalPath = Path().apply {
            addOval(RectF(cx - r*0.74f, cy - r*0.28f, cx + r*0.58f, cy + r*0.60f), Path.Direction.CW)
        }
        canvas.save()
        canvas.clipPath(bodyOvalPath)
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

        // Drop shadow
        fillPaint.color = Color.argb(alpha / 4, 0, 0, 0)
        canvas.drawRoundRect(RectF(cargoL + r*0.06f, cargoT + r*0.10f, cabR + r*0.06f, groundY + r*0.10f),
                             r*0.08f, r*0.08f, fillPaint)

        // ----- Cargo box -----
        val cargoRect = RectF(cargoL, cargoT, cargoR, groundY)
        canvas.drawRoundRect(cargoRect, r*0.08f, r*0.08f, outlinePaint)
        fillPaint.color = type.bodyColor; fillPaint.alpha = alpha
        canvas.drawRoundRect(cargoRect, r*0.08f, r*0.08f, fillPaint)
        // Cargo top-panel highlight (lighter top third)
        canvas.save()
        canvas.clipRect(cargoL, cargoT, cargoR, cargoT + (groundY - cargoT) * 0.38f)
        fillPaint.color = Color.argb((alpha * 0.28f).toInt(), 255, 255, 255)
        canvas.drawRoundRect(cargoRect, r*0.08f, r*0.08f, fillPaint)
        canvas.restore()
        // Cargo stripe (glazeColor panel)
        fillPaint.color = type.glazeColor; fillPaint.alpha = alpha
        canvas.drawRect(cargoL + r*0.10f, cargoT + r*0.08f, cargoR - r*0.10f, cargoT + r*0.26f, fillPaint)
        // Cargo door line
        outlinePaint.strokeWidth = ow * 0.6f
        canvas.drawLine(cargoL + (cargoR - cargoL)/2f, cargoT + r*0.30f,
                        cargoL + (cargoR - cargoL)/2f, groundY - r*0.06f, outlinePaint)
        outlinePaint.strokeWidth = ow * 2f

        // ----- Cab (glazeColor so cab and cargo are visually distinct at small sizes) -----
        val cabRect = RectF(cabL, truckTop, cabR, groundY)
        canvas.drawRoundRect(cabRect, r*0.12f, r*0.12f, outlinePaint)
        fillPaint.color = type.glazeColor; fillPaint.alpha = alpha
        canvas.drawRoundRect(cabRect, r*0.12f, r*0.12f, fillPaint)
        // Cab top highlight
        canvas.save()
        canvas.clipRect(cabL, truckTop, cabR, truckTop + (groundY - truckTop) * 0.38f)
        fillPaint.color = Color.argb((alpha * 0.32f).toInt(), 255, 255, 255)
        canvas.drawRoundRect(cabRect, r*0.12f, r*0.12f, fillPaint)
        canvas.restore()

        // ----- Windshield -----
        val windRect = RectF(cabL + r*0.08f, truckTop + r*0.10f, cabR - r*0.10f, cy - r*0.08f)
        canvas.drawRoundRect(windRect, r*0.07f, r*0.07f, outlinePaint)
        fillPaint.color = Color.argb((alpha * 0.90f).toInt(), 130, 210, 255)
        canvas.drawRoundRect(windRect, r*0.07f, r*0.07f, fillPaint)
        // Windshield reflection
        fillPaint.color = Color.argb((alpha * 0.50f).toInt(), 255, 255, 255)
        canvas.drawRoundRect(
            RectF(windRect.left + r*0.05f, windRect.top + r*0.05f,
                  windRect.centerX() - r*0.04f, windRect.bottom - r*0.08f),
            r*0.04f, r*0.04f, fillPaint
        )

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
        val cw     = board.cols * cellSize * 0.72f
        val pl     = surfaceW / 2f - cw / 2f
        val pr     = surfaceW / 2f + cw / 2f

        // Panel shell
        shadowPaint.color = Color.argb(50, 0, 0, 0)
        canvas.drawRoundRect(RectF(pl + 2f, stripY + 12f, pr + 2f, stripY + counterH - 2f), 18f, 18f, shadowPaint)
        fillPaint.color = Color.argb(180, 28, 12, 0)
        canvas.drawRoundRect(RectF(pl - 4f, stripY + 4f, pr + 4f, stripY + counterH), 20f, 20f, fillPaint)
        fillPaint.color = theme.panelBg; fillPaint.alpha = 255
        canvas.drawRoundRect(RectF(pl, stripY + 8f, pr, stripY + counterH - 4f), 18f, 18f, fillPaint)

        // --- Digit-flip number ---
        val digitSz = 96f
        textPaint.textSize = digitSz
        val cellW  = textPaint.measureText("0") * 1.18f   // fixed per-digit width
        val numStr = displayedCount.toString()
        val totalW = numStr.length * cellW
        val startX = surfaceW / 2f - totalW / 2f + cellW / 2f
        val baseY  = midY + 26f
        val pivotY = baseY - digitSz * 0.36f               // visual centre of digit

        textOutlinePaint.textSize    = digitSz
        textOutlinePaint.textAlign   = Paint.Align.CENTER
        textOutlinePaint.typeface    = Typeface.DEFAULT_BOLD
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

        textPaint.textSize  = 21f
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.color     = theme.textSecondary
        canvas.drawText("cleared  \u2746", surfaceW / 2f, midY + 52f, textPaint)
        val bestScore = if (board.cols == 6) prefs.highScore6x6 else prefs.highScore8x8
        if (bestScore > 0) {
            textPaint.textSize = 18f
            textPaint.color    = theme.textSecondary
            canvas.drawText("best: $bestScore", surfaceW / 2f, midY + 72f, textPaint)
        }
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
                textOutlinePaint.color       = Color.argb((alpha * 0.8f).toInt(), 28, 12, 0)
                textOutlinePaint.textSize    = sz
                textOutlinePaint.textAlign   = Paint.Align.CENTER
                textOutlinePaint.strokeWidth = 7f
                textOutlinePaint.typeface    = Typeface.DEFAULT_BOLD
                canvas.drawText(fl.text, 0f, sz * 0.36f, textOutlinePaint)
                // Fill
                textPaint.color = Color.argb(alpha, Color.red(fl.color), Color.green(fl.color), Color.blue(fl.color))
                canvas.drawText(fl.text, 0f, sz * 0.36f, textPaint)
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
        textOutlinePaint.typeface    = Typeface.DEFAULT_BOLD
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

        // Draw + update particles
        val dt = 1f / 60f
        val gravity = cellSize * 0.28f
        val updatedParticles = mutableListOf<Particle>()
        for (p in particles) {
            val newVy = p.vy + gravity * dt
            val newX  = p.x  + p.vx * dt * 60f
            val newY  = p.y  + newVy * dt * 60f
            val newRot = p.rot + p.rotSpeed
            val alpha = ((1f - (elapsed / CELEBRATE_MS)) * 255).toInt().coerceIn(0, 255)
            if (newY < surfaceH + cellSize) {
                updatedParticles.add(p.copy(x = newX, y = newY, vy = newVy, rot = newRot))
                // Draw as a small rounded square rotated
                canvas.save()
                canvas.translate(newX, newY)
                canvas.rotate(newRot)
                fillPaint.color = (p.color and 0x00FFFFFF) or (alpha shl 24)
                canvas.drawRoundRect(
                    RectF(-p.radius, -p.radius * 0.6f, p.radius, p.radius * 0.6f),
                    p.radius * 0.3f, p.radius * 0.3f, fillPaint
                )
                canvas.restore()
            }
        }
        particles.clear(); particles.addAll(updatedParticles)
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
            celebrateCount >= 500 -> "⭐ $celebrateCount CLEARED! ⭐"
            celebrateCount >= 100 -> "🎉 $celebrateCount CLEARED!"
            else                  -> "$celebrateCount cleared!"
        }
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.textSize  = 36f
        textPaint.color     = Color.argb((bannerAlpha * 0.5f).toInt(), 0, 0, 0)
        canvas.drawText(label, surfaceW / 2f + 2f, by + bh * 0.62f + 2f, textPaint)
        textPaint.color = Color.argb(bannerAlpha, 90, 40, 0)
        canvas.drawText(label, surfaceW / 2f, by + bh * 0.62f, textPaint)
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
        textPaint.textSize  = 26f
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.color = Color.argb(80, 0, 0, 0)
        canvas.drawText("Draw through 3 matching pieces!", surfaceW / 2f + 2f, dy - r * 2.6f + 2f, textPaint)
        textPaint.color = Color.WHITE
        canvas.drawText("Draw through 3 matching pieces!", surfaceW / 2f, dy - r * 2.6f, textPaint)

        // Tap to play
        textPaint.textSize = 20f
        textPaint.color    = Color.argb(180, 255, 255, 255)
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
        textPaint.textSize  = 30f
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
            2  -> lifetime >= 200
            3  -> prefs.bestChainLength >= 4
            4  -> prefs.bestChainLength >= 6
            5  -> prefs.bestChainLength >= 8
            6  -> bestScore >= 20
            7  -> bestScore >= 60
            8  -> bestScore >= 120
            9  -> prefs.shufflesSurvived >= 1
            10 -> prefs.highScore6x6 > 0 && prefs.highScore8x8 > 0
            11 -> prefs.sessionCount >= 5
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
        textOutlinePaint.typeface = Typeface.DEFAULT_BOLD
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

            // Tile body
            fillPaint.color = if (earned) color else Color.rgb(155, 148, 140)
            fillPaint.alpha = if (earned) 255 else 160
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
                // Large symbol — top half
                val symSz = rect.height() * 0.36f
                val symY  = cy - rect.height() * 0.04f
                textPaint.color = Color.argb(70, 0, 0, 0)
                textPaint.textSize = symSz; textPaint.textAlign = Paint.Align.CENTER
                canvas.drawText(STICKER_SYMS[i], cx + 2f, symY + 2f, textPaint)
                textPaint.color = Color.WHITE
                canvas.drawText(STICKER_SYMS[i], cx, symY, textPaint)
                // Sticker name — bottom
                val nameSz = rect.height() * 0.145f
                textPaint.textSize = nameSz
                textPaint.color    = Color.argb(90, 0, 0, 0)
                canvas.drawText(STICKER_NAMES[i], cx + 1f, rect.bottom - rect.height() * 0.12f + 1f, textPaint)
                textPaint.color = Color.WHITE
                canvas.drawText(STICKER_NAMES[i], cx, rect.bottom - rect.height() * 0.12f, textPaint)
                // Small condition text
                textPaint.textSize = rect.height() * 0.105f
                textPaint.color    = Color.argb(180, 255, 255, 255)
                canvas.drawText(STICKER_DESCS[i], cx, rect.bottom - rect.height() * 0.30f, textPaint)
                // Earned checkmark badge — top right
                val bx = rect.right - 1f; val by = rect.top + 1f; val br2 = 11f
                fillPaint.color = Color.argb(220, 28, 12, 0)
                canvas.drawCircle(bx, by, br2 + 2f, fillPaint)
                fillPaint.color = Color.rgb(80, 210, 80)
                canvas.drawCircle(bx, by, br2, fillPaint)
                textPaint.textSize = br2 * 1.4f; textPaint.color = Color.WHITE
                canvas.drawText("\u2713", bx, by + br2 * 0.42f, textPaint)
            } else {
                // Lock icon — simple padlock shape
                val lockR  = rect.height() * 0.13f
                val lockCX = cx; val lockCY = cy - lockR * 0.2f
                // Shackle (arc)
                strokePaint.color       = Color.argb(160, 255, 255, 255)
                strokePaint.strokeWidth = lockR * 0.5f; strokePaint.alpha = 160
                canvas.drawArc(RectF(lockCX - lockR, lockCY - lockR * 1.3f,
                                     lockCX + lockR, lockCY + lockR * 0.3f),
                               180f, 180f, false, strokePaint)
                // Body
                fillPaint.color = Color.argb(160, 255, 255, 255)
                canvas.drawRoundRect(RectF(lockCX - lockR * 1.1f, lockCY - lockR * 0.1f,
                                          lockCX + lockR * 1.1f, lockCY + lockR * 1.5f),
                                     lockR * 0.35f, lockR * 0.35f, fillPaint)
                // Condition hint at bottom
                textPaint.textSize  = rect.height() * 0.115f
                textPaint.color     = Color.argb(160, 255, 255, 255)
                textPaint.textAlign = Paint.Align.CENTER
                canvas.drawText(STICKER_DESCS[i], cx, rect.bottom - rect.height() * 0.12f, textPaint)
                strokePaint.alpha = 255
            }
        }

        // ---- Stats line ----
        val statsY = stickerRects[11].bottom + 28f
        val lifetime = prefs.lifetimeDonuts + board.donutsCleared.values.sum()
        textPaint.textSize = 22f; textPaint.textAlign = Paint.Align.CENTER
        textPaint.color = theme.textSecondary
        canvas.drawText(
            "$lifetime donuts lifetime  \u00B7  $earnedCount of 12 stickers",
            stickerPanelRect.centerX(), statsY, textPaint)

        drawPrettyButton(canvas, stickersCloseRect, Color.rgb(60, 175, 80), "Done  \u2713", 36f)

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
        textOutlinePaint.typeface    = Typeface.DEFAULT_BOLD
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
        textPaint.color = Color.argb(70, 0, 0, 0)
        canvas.drawText(upper, textX + 2f, baselineY + 2f, textPaint)
        textPaint.color = theme.textPrimary
        canvas.drawText(upper, textX, baselineY, textPaint)
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
        textPaint.color     = Color.argb(80, 0, 0, 0)
        textPaint.textSize  = 30f * sc
        textPaint.textAlign = Paint.Align.CENTER
        canvas.drawText(label, rect.centerX() + 2f, rect.centerY() + 10f * sc + 2f, textPaint)
        textPaint.color = Color.WHITE
        canvas.drawText(label, rect.centerX(), rect.centerY() + 10f * sc, textPaint)
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
                            hintCells        = emptyList()
                            noMovesWarningMs = -1L
                            resetFlashMs     = now
                            lastActionMs     = now
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
        if (stickersCloseRect.contains(x, y) || !stickerPanelRect.contains(x, y)) {
            stickersOpen = false
        }
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
        val chainType = dragChainType ?: return
        if (board.grid[row][col].type != chainType) return
        val now2 = SystemClock.elapsedRealtime()
        dragChain.add(cell)
        chainPings[cell] = now2
        centerPingMs = now2; centerPingCount = dragChain.size
        if (prefs.soundEnabled)  soundEngine.playConnectBlip(dragChain.size)
        if (prefs.hapticEnabled) hapticEngine.tick()
    }

    private fun handleUp() {
        if (dragChain.size >= 3) {
            pendingChain = dragChain.toList()
            popCells.clear()
            popCells.addAll(pendingChain.map { (r, c) -> AnimCell(r, c, board.grid[r][c].type) })
            val now = SystemClock.elapsedRealtime()
            animStartMs = now; animPhase = AnimPhase.POPPING
            lastActionMs = now; hintCells = emptyList()
            if (pendingChain.size > prefs.bestChainLength) prefs.bestChainLength = pendingChain.size
            if (prefs.soundEnabled)  soundEngine.playPopClear()
            if (prefs.hapticEnabled) hapticEngine.pop()
            val label = when (pendingChain.size) {
                4    -> "NICE!"
                5    -> "GREAT!"
                6    -> "AMAZING!"
                7    -> "WOW!"
                in 8..Int.MAX_VALUE -> "INCREDIBLE!"
                else -> null
            }
            label?.let {
                val cx = pendingChain.map { (r, c) -> boardLeft + c * cellSize + cellSize/2f }.average().toFloat()
                val cy = pendingChain.map { (r, c) -> boardTop  + r * cellSize + cellSize/2f }.average().toFloat()
                val colors = intArrayOf(
                    Color.rgb(255, 80, 60), Color.rgb(255, 180, 0),
                    Color.rgb(80, 210, 80), Color.rgb(60, 160, 255), Color.rgb(200, 80, 255)
                )
                val col = colors[(pendingChain.size - 4).coerceIn(0, colors.size - 1)]
                synchronized(floatLabels) { floatLabels.add(FloatLabel(it, cx, cy, col, now)) }
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
