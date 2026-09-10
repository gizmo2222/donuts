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

class GameView(context: Context, initialBoard: GameBoard, private val prefs: Prefs) :
    SurfaceView(context), SurfaceHolder.Callback {

    private var board = initialBoard

    private val theme = GameTheme

    // -----------------------------------------------------------------------
    // Layout
    // -----------------------------------------------------------------------
    private var surfaceW  = 0
    private var surfaceH  = 0
    private var cellSize  = 0f
    private var boardLeft = 0f
    private var boardTop  = 0f
    private var counterH  = 0f

    // Density-independent unit: pixels per design-dp, boosted a little on large screens
    // so chrome and text grow with the device instead of staying phone-sized.
    private val uiScale = UiScale(context)
    private val debuggable = (context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
    private var u = 1f

    // Window insets (status bar, gesture bar, display cutout). Layout stays inside them.
    private var insetL = 0; private var insetT = 0; private var insetR = 0; private var insetB = 0

    // HUD: two quiet buttons above the board, stickers (left) and settings (right)
    private var settingsBtnRect = RectF()

    // Settings panel layout
    private var panelRect         = RectF()
    private val soundRects        = Array(2) { RectF() }
    private val hintRects         = Array(2) { RectF() }
    private val gridRects         = Array(2) { RectF() }
    private var settingsResetRect = RectF()
    private var settingsCloseRect = RectF()

    private var settingsSc = 1f     // < 1 only when the full-size settings panel cannot fit the screen

    private val onOffLabels = arrayOf("On", "Off")
    private val hintOptions = longArrayOf(5_000L, 0L)
    private val gridOptions = intArrayOf(6, 8)
    private val gridLabels  = arrayOf("Big", "Small")      // 6x6 donuts are big, 8x8 are small

    // "Start over" two-tap confirm. It lives in Settings so a stray tap cannot wipe the board.
    private var resetConfirmMs   = -1L
    private val RESET_CONFIRM_MS = 1500L

    // -----------------------------------------------------------------------
    // Hint
    // -----------------------------------------------------------------------
    private var lastActionMs = SystemClock.elapsedRealtime()
    private var hintCells    = emptyList<Pair<Int, Int>>()
    private var hintPulseMs  = 0L

    // -----------------------------------------------------------------------
    // Touch / drag
    // -----------------------------------------------------------------------
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

    // 12 stickers — 4 rows × 3 cols
    private val STICKER_NAMES  = arrayOf(
        "Donut Taster",   "Donut Lover",   "Donut King",
        "Chain Starter",  "Chain Champ",   "Chain Hero",
        "Little Batch",   "Big Batch",     "Donut Party",
        "Gold Finder",    "Explorer",      "Super Fan"
    )
    // What to do to earn each one, in words an early reader can sound out
    private val STICKER_DESCS  = arrayOf(
        "Pop 10",         "Pop 50",        "Pop 500",
        "Connect 4",      "Connect 6",     "Connect 8",
        "20 in a game",   "60 in a game",  "200 in a game",
        "Pop a gold one", "Try both sizes","Play 10 times"
    )
    private val STICKER_COLORS = intArrayOf(
        Color.rgb(255, 140,  60), Color.rgb(255, 200,  30), Color.rgb(220,  80,  50),
        Color.rgb( 70, 170, 255), Color.rgb( 90,  90, 240), Color.rgb(160,  50, 220),
        Color.rgb( 50, 200, 160), Color.rgb( 60, 190,  70), Color.rgb( 30, 130,  80),
        Color.rgb(255,  90,  90), Color.rgb(240, 110, 200), Color.rgb(160,  80, 230)
    )

    // Button press scale feedback
    private var settingsPressMs = -1L       // time of last settings press
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
    private val TUTORIAL_LOOP_MS = 3000L
    private var tutorialCells   = emptyList<Pair<Int, Int>>()   // the three donuts the finger traces

    // Chain connection ping — scale-pop when each new cell joins
    private val chainPings = mutableMapOf<Pair<Int,Int>, Long>()  // cell -> time added
    private val PING_MS = 220L
    // Big center count pop
    private var centerPingMs    = -1L

    // Milestone celebration
    private val MILESTONES = intArrayOf(10, 25, 50, 100, 200, 500)
    private var lastMilestone   = 0
    private var celebrateMs     = -1L
    private val CELEBRATE_MS    = 2600L
    private var celebrateLabel  = ""
    private var physicsMs       = -1L      // last confetti physics step
    // Mutable class (not data class) so physics can update fields in-place each frame,
    // avoiding the 60-object copy + new-list allocation that a data-class copy() would incur.
    private class Particle(
        var x: Float, var y: Float,
        var vx: Float, var vy: Float,
        val color: Int, val radius: Float,
        val rotSpeed: Float, val ring: Boolean = false, var rot: Float = 0f
    )
    private val particles = mutableListOf<Particle>()

    // Landing squish: the whole column squashes for a beat when a drop lands
    private var landMs = -1L
    private val LAND_MS = 220L
    private val landedCols = mutableSetOf<Int>()

    // Sticker earned mid-game: it pops up on the board, then flies into the star button
    private var earnedMask     = -1          // bitmask of earned stickers; -1 until first frame
    private var stickerFlyMs   = -1L
    private var stickerFlyIdx  = -1
    private val STICKER_FLY_MS = 1200L
    private var starPulseMs    = -1L

    // Press feedback on Settings option buttons
    private var optPressRect: RectF? = null
    private var optPressMs = -1L

    // Honour the system animator scale: 0 means the user asked for no animation
    private val reducedMotion: Boolean = try {
        android.provider.Settings.Global.getFloat(context.contentResolver,
            android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    } catch (_: Exception) { false }

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
    // 24 points around a circle (15 degree steps) for drippy glaze and sprinkle placement
    private val c24 = FloatArray(24) { cos(Math.toRadians(it * 15.0)).toFloat() }
    private val s24 = FloatArray(24) { sin(Math.toRadians(it * 15.0)).toFloat() }
    // Glaze radius multiplier per point: > 1 makes a drip. Bottom of the ring is indices 3..9.
    private val dripMul = floatArrayOf(1f,1f,1f,1.05f,1.14f,1f,1.18f,1f,1.13f,1.04f,1f,1f,1f,1f,1f,1f,1f,1f,1f,1f,1f,1f,1f,1f)
    // Six rainbow sprinkles: angle index into c24/s24, distance from centre, rotation, colour
    private val sprinkleA   = intArrayOf(1, 5, 9, 13, 17, 21)
    private val sprinkleD   = floatArrayOf(0.58f, 0.50f, 0.60f, 0.52f, 0.57f, 0.49f)
    private val sprinkleRot = floatArrayOf(20f, 70f, -35f, 50f, -60f, 10f)
    private val SPRINKLE_COLORS = intArrayOf(
        Color.WHITE, Color.rgb(255, 230, 60), Color.rgb(80, 200, 255),
        Color.rgb(120, 230, 90), Color.rgb(200, 160, 255), Color.WHITE)
    // 48 points (7.5 degree steps) with an 8-lobe scallop multiplier for the flower ring
    private val c48 = FloatArray(48) { cos(Math.toRadians(it * 7.5)).toFloat() }
    private val s48 = FloatArray(48) { sin(Math.toRadians(it * 7.5)).toFloat() }
    private val scallopMul = FloatArray(48) { (0.88 + 0.12 * cos(Math.toRadians(it * 7.5 * 8.0))).toFloat() }
    // Sesame seeds on the matcha dip (x, y as fractions of r; rotation degrees)
    private val seedX   = floatArrayOf(-0.45f, -0.08f, 0.32f, 0.56f, -0.62f)
    private val seedY   = floatArrayOf(-0.55f, -0.68f, -0.58f, -0.32f, -0.22f)
    private val seedRot = floatArrayOf(20f, -30f, 10f, 40f, -15f)
    // Powdered sugar on the jelly bun (x, y, radius as fractions of r)
    private val sugarX = floatArrayOf(-0.55f, -0.30f, -0.05f, -0.42f, -0.18f, 0.15f, -0.62f)
    private val sugarY = floatArrayOf(-0.35f, -0.62f, -0.48f, -0.05f, -0.22f, -0.70f, 0.10f)
    private val sugarR = floatArrayOf(0.09f, 0.11f, 0.08f, 0.07f, 0.10f, 0.07f, 0.06f)
    // 5-point star (10 vertices) and 8-tooth gear (32 vertices) for the HUD icons
    private val starCos = FloatArray(10) { cos(Math.toRadians(-90.0 + it * 36.0)).toFloat() }
    private val starSin = FloatArray(10) { sin(Math.toRadians(-90.0 + it * 36.0)).toFloat() }
    private val gearCos = FloatArray(32) { cos(Math.toRadians(it * 11.25)).toFloat() }
    private val gearSin = FloatArray(32) { sin(Math.toRadians(it * 11.25)).toFloat() }
    // Sprinkles scattered behind everything: the bakery-counter feel, faint so the board stays the hero
    private val backdropN     = 40
    private val backdropX     = FloatArray(backdropN)
    private val backdropY     = FloatArray(backdropN)
    private val backdropRot   = FloatArray(backdropN)
    private val backdropColor = IntArray(backdropN)
    init {
        val rnd = java.util.Random(7L)
        val palette = DonutType.values()
        for (i in 0 until backdropN) {
            backdropX[i] = rnd.nextFloat(); backdropY[i] = rnd.nextFloat()
            backdropRot[i] = rnd.nextFloat() * 180f
            backdropColor[i] = palette[i % palette.size].glazeColor
        }
    }
    // 8 vertices of the ✦ spark drawn on golden cells (45° intervals, -90° start)
    private val sparkCos = FloatArray(8) { cos(Math.toRadians(it * 45.0 - 90.0)).toFloat() }
    private val sparkSin = FloatArray(8) { sin(Math.toRadians(it * 45.0 - 90.0)).toFloat() }

    // -----------------------------------------------------------------------
    // Lifecycle
    // -----------------------------------------------------------------------
    @Volatile private var renderThread: RenderThread? = null

    init {
        holder.addCallback(this); isFocusable = true
        prefs.sessionCount = prefs.sessionCount + 1   // one "play" per visit to the game screen
        ViewCompat.setOnApplyWindowInsetsListener(this) { _, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            if (bars.left != insetL || bars.top != insetT || bars.right != insetR || bars.bottom != insetB) {
                insetL = bars.left; insetT = bars.top; insetR = bars.right; insetB = bars.bottom
                synchronized(holder) { computeLayout() }
            }
            insets
        }
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        boardEntryMs = SystemClock.elapsedRealtime()
        renderThread = RenderThread(holder).also { it.start() }
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, w: Int, h: Int) {
        synchronized(holder) { surfaceW = w; surfaceH = h; computeLayout() }
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        // Bank this board's progress before the app goes to the background
        synchronized(holder) { saveSession() }
        // Stop the render loop when the surface goes away (e.g. the app is backgrounded)
        // so we don't keep a thread spinning — and draining battery — with nothing to
        // draw to. The thread is recreated in surfaceCreated when we return.
        val t = renderThread
        renderThread = null
        t?.running = false
        // Wait briefly for the frame in flight, but never pin the UI thread on the GPU: if the
        // hardware canvas is stuck in a swap, the thread ends by itself once the surface is
        // gone (lockFrame fails and the loop sees running == false).
        try { t?.join(600L) } catch (_: InterruptedException) { }
    }

    private fun computeLayout() {
        val w = surfaceW.toFloat(); val h = surfaceH.toFloat()
        if (w == 0f || h == 0f) return
        uiScale.update(surfaceW, surfaceH)
        u = uiScale.u

        // Safe area: never lay content under the status bar, gesture bar, or a cutout.
        val safeL = insetL.toFloat();    val safeT = insetT.toFloat()
        val safeW = w - insetL - insetR; val safeH = h - insetT - insetB
        val margin = 12f * u

        // Chrome in design-dp: one quiet button row above the board, one number below it
        val btnH      = 56f * u
        val btnGap    = 10f * u
        val btnBlockH = btnH + btnGap
        counterH      = 88f * u

        // Board: fills the safe width, but never taller than the space left after the
        // button row above and the counter below.
        val boardPx = min(safeW - margin * 2f, safeH - margin * 2f - btnBlockH - counterH)
        cellSize  = boardPx / board.cols
        boardLeft = safeL + (safeW - boardPx) / 2f

        // Centre the whole block (buttons + board + counter) inside the safe area
        val totalBlockH = btnBlockH + boardPx + counterH
        boardTop = safeT + (safeH - totalBlockH) / 2f + btnBlockH

        // HUD: stickers at the board's left edge, settings at its right edge
        val btnY = boardTop - btnH - btnGap
        stickersBtnRect = RectF(boardLeft, btnY, boardLeft + btnH, btnY + btnH)
        settingsBtnRect = RectF(boardLeft + boardPx - btnH, btnY, boardLeft + boardPx, btnY + btnH)

        layoutSettingsPanel(safeL, safeT, safeW, safeH)
        layoutStickersPanel(safeL, safeT, safeW, safeH)
        buildSprites()
    }

    /**
     * Settings panel: sized in design-dp, then uniformly shrunk ([settingsSc] < 1) when the
     * full-size panel would not fit the safe height, so small phones keep every control.
     */
    private fun layoutSettingsPanel(safeL: Float, safeT: Float, safeW: Float, safeH: Float) {
        val pw     = min(safeW - 24f * u, 480f * u)
        val availH = safeH - 24f * u
        settingsSc = 1f
        val ph = placeSettings(pw, safeL, safeT, safeW, safeH)
        if (ph > availH) {
            settingsSc = availH / ph
            placeSettings(pw, safeL, safeT, safeW, safeH)
        }
    }

    private fun placeSettings(pw: Float, safeL: Float, safeT: Float, safeW: Float, safeH: Float): Float {
        val k      = u * settingsSc
        val pad    = 16f * k
        val titleH = 84f * k
        val rowH   = 56f * k
        val secGap = 40f * k      // room for a section label above each row
        val closeH = 56f * k
        val ph = titleH + rowH + (secGap + rowH) * 3f + secGap + closeH + pad
        val pl = safeL + (safeW - pw) / 2f
        val pt = safeT + (safeH - ph) / 2f
        panelRect = RectF(pl, pt, pl + pw, pt + ph)

        fun row(rects: Array<RectF>, top: Float) {
            val n  = rects.size
            val bw = (pw - pad * (n + 1)) / n
            for (i in 0 until n) {
                val x = pl + pad + i * (bw + pad)
                rects[i].set(x, top, x + bw, top + rowH)
            }
        }
        val sTop = pt + titleH
        val hTop = sTop + rowH + secGap
        val gTop = hTop + rowH + secGap
        val rTop = gTop + rowH + secGap
        row(soundRects, sTop); row(hintRects, hTop); row(gridRects, gTop)
        settingsResetRect = RectF(pl + pad, rTop, pl + pw - pad, rTop + rowH)
        settingsCloseRect = RectF(pl + pad, pt + ph - closeH - pad, pl + pw - pad, pt + ph - pad)
        return ph
    }

    /** Sticker panel: 4 rows of 3 tiles sized by whichever of width or height is tighter. */
    private fun layoutStickersPanel(safeL: Float, safeT: Float, safeW: Float, safeH: Float) {
        val spW      = min(safeW - 24f * u, 560f * u)
        val spPad    = 10f * u
        val spTitleH = 56f * u
        val spStatsH = 28f * u
        val spCloseH = 56f * u
        val tileFromW = (spW - spPad * 4f) / 3f
        val tileFromH = (safeH - 24f * u - spTitleH - spStatsH - spCloseH - spPad * 7f) / 4f
        val side = min(tileFromW, tileFromH)
        val spH  = spTitleH + side * 4f + spPad * 3f + spStatsH + spCloseH + spPad * 3f
        val spL  = safeL + (safeW - spW) / 2f
        val spT  = safeT + (safeH - spH) / 2f
        stickerPanelRect = RectF(spL, spT, spL + spW, spT + spH)
        val gridW  = side * 3f + spPad * 2f
        val gridX0 = spL + (spW - gridW) / 2f
        val row1Y  = spT + spTitleH
        for (i in 0 until 12) {
            val col = i % 3; val row = i / 3
            val sx = gridX0 + col * (side + spPad)
            val sy = row1Y + row * (side + spPad)
            stickerRects[i].set(sx, sy, sx + side, sy + side)
        }
        val closeRowY = stickerPanelRect.bottom - spCloseH - spPad
        stickersCloseRect = RectF(spL + spPad, closeRowY, spL + spW - spPad, stickerPanelRect.bottom - spPad)
    }

    // Donuts of the current board already folded into lifetimeDonuts, so saveSession can run
    // any number of times (on background, on board-size change, on New game) without double counting.
    private var sessionSaved = 0

    private fun saveSession() {
        val sessionTotal = board.donutsCleared.values.sum()
        val delta = sessionTotal - sessionSaved
        if (delta > 0) { prefs.lifetimeDonuts = prefs.lifetimeDonuts + delta; sessionSaved = sessionTotal }
        val hs = if (board.cols == 6) prefs.highScore6x6 else prefs.highScore8x8
        if (sessionTotal > hs) {
            if (board.cols == 6) prefs.highScore6x6 = sessionTotal
            else prefs.highScore8x8 = sessionTotal
        }
    }

    // Donuts popped ever: what is banked plus what this board has added since the last save
    private fun lifetimeDonuts(): Int = prefs.lifetimeDonuts + (board.donutsCleared.values.sum() - sessionSaved)

    private fun rebuildBoard() {
        saveSession()
        sessionSaved = 0
        board = GameBoard(rows = prefs.gridSize, cols = prefs.gridSize)
        animPhase = AnimPhase.IDLE
        popCells.clear(); dropCells.clear()
        dragChain.clear()
        pendingResult  = null
        cascadeCount   = 0
        isCascade      = false
        hintCells = emptyList()
        tutorialCells = emptyList()
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
        canvas.drawColor(theme.bg)
        drawBackdrop(canvas)
        advanceAnimation(now)
        updateHint(now)
        advanceSettingsAnim(now)
        advanceCounter(now)
        drawHUD(canvas, now)
        drawBoardBackground(canvas)
        drawCells(canvas, now)
        drawChainLine(canvas)
        drawCenterPing(canvas, now)
        drawFloatLabels(canvas, now)
        drawCounter(canvas, now)
        drawNoMovesWarning(canvas, now)
        if (celebrateMs >= 0) drawCelebration(canvas, now)
        checkStickers(now)
        if (stickerFlyMs >= 0) drawStickerFly(canvas, now)
        if (settingsAnim > 0f) drawSettings(canvas, now)
        if (stickersAnim > 0f) drawStickersPanel(canvas, now)
        drawChainFlash(canvas, now)
        drawResetFlash(canvas, now)
        if (tutorialActive && settingsAnim == 0f && stickersAnim == 0f) drawTutorial(canvas, now)
    }

    // -----------------------------------------------------------------------
    // Hint
    // -----------------------------------------------------------------------
    private fun updateHint(now: Long) {
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
    private fun advanceAnimation(now: Long) {
        when (animPhase) {
            AnimPhase.POPPING -> if (now - animStartMs >= POP_MS) {
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
    private fun celebrate(now: Long, label: String) {
        celebrateMs    = now
        physicsMs      = now
        celebrateLabel = label
        spawnParticles()
        if (prefs.soundEnabled)  soundEngine.playMilestone()
        if (prefs.hapticEnabled) hapticEngine.milestone()
    }

    private fun spawnParticles() {
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
        drawIconButton(canvas, stickersBtnRect, theme.chrome, buttonPressScale(now, stickersPressMs) * starPulse(now), 0)
        drawIconButton(canvas, settingsBtnRect, theme.chrome, buttonPressScale(now, settingsPressMs), 1)
    }

    private fun drawBackdrop(canvas: Canvas) {
        val w = surfaceW.toFloat(); val h = surfaceH.toFloat()
        val sw = 14f * u; val sh = 5f * u
        for (i in 0 until backdropN) {
            val x = backdropX[i] * w; val y = backdropY[i] * h
            canvas.save(); canvas.rotate(backdropRot[i], x, y)
            fillPaint.color = backdropColor[i]; fillPaint.alpha = 60
            canvas.drawRoundRect(scratchRectF.apply { set(x - sw / 2f, y - sh / 2f, x + sw / 2f, y + sh / 2f) }, sh / 2f, sh / 2f, fillPaint)
            canvas.restore()
        }
        fillPaint.alpha = 255
    }

    // Chunky button with a hand-drawn icon instead of a system emoji: 0 = star (stickers), 1 = gear (settings)
    private fun drawIconButton(canvas: Canvas, rect: RectF, color: Int, scale: Float, icon: Int) {
        drawPrettyButton(canvas, rect, color, "", 1f, scale)
        val cx = rect.centerX(); val cy = rect.centerY(); val r = rect.height() * 0.30f
        canvas.save(); canvas.scale(scale, scale, cx, cy)
        if (icon == 0) drawStarIcon(canvas, cx, cy, r) else drawGearIcon(canvas, cx, cy, r)
        canvas.restore()
    }

    private fun drawStarIcon(canvas: Canvas, cx: Float, cy: Float, r: Float) {
        scratchPath.rewind()
        for (i in 0 until 10) {
            val rr = if (i % 2 == 0) r else r * 0.46f
            val x = cx + rr * starCos[i]; val y = cy + rr * starSin[i]
            if (i == 0) scratchPath.moveTo(x, y) else scratchPath.lineTo(x, y)
        }
        scratchPath.close()
        outlinePaint.color = Color.argb(255, 28, 12, 0); outlinePaint.strokeWidth = r * 0.30f
        canvas.drawPath(scratchPath, outlinePaint)
        fillPaint.color = Color.rgb(255, 215, 50); fillPaint.alpha = 255
        canvas.drawPath(scratchPath, fillPaint)
        fillPaint.color = Color.argb(150, 255, 255, 255)
        canvas.drawCircle(cx - r * 0.22f, cy - r * 0.30f, r * 0.16f, fillPaint)
        fillPaint.alpha = 255
    }

    private fun drawGearIcon(canvas: Canvas, cx: Float, cy: Float, r: Float) {
        scratchPath.rewind()
        for (i in 0 until 32) {
            val rr = if ((i / 2) % 2 == 0) r else r * 0.74f      // pairs of points give flat-topped teeth
            val x = cx + rr * gearCos[i]; val y = cy + rr * gearSin[i]
            if (i == 0) scratchPath.moveTo(x, y) else scratchPath.lineTo(x, y)
        }
        scratchPath.close()
        outlinePaint.color = Color.argb(255, 28, 12, 0); outlinePaint.strokeWidth = r * 0.24f
        canvas.drawPath(scratchPath, outlinePaint)
        fillPaint.color = Color.rgb(255, 250, 240); fillPaint.alpha = 255
        canvas.drawPath(scratchPath, fillPaint)
        canvas.drawCircle(cx, cy, r * 0.30f, outlinePaint)
        fillPaint.color = theme.chrome
        canvas.drawCircle(cx, cy, r * 0.30f, fillPaint)
    }

    /** Returns a scale factor that dips to 0.93 at tap then recovers to 1.0 over PRESS_MS. */
    private fun buttonPressScale(now: Long, pressMs: Long): Float {
        if (pressMs < 0) return 1f
        val t = ((now - pressMs).toFloat() / PRESS_MS).coerceIn(0f, 1f)
        // Dip then ease back: 0.86 at t=0, 1.0 at t=1 (ease-out) — chunkier press feel
        return 0.86f + 0.14f * easeOutQuint(t)
    }

    private fun drawPrettyButton(canvas: Canvas, rect: RectF, baseColor: Int, label: String, labelSize: Float, scale: Float = 1f) {
        val rx = 12f * u
        canvas.save()
        canvas.scale(scale, scale, rect.centerX(), rect.centerY())

        // Drop shadow
        shadowPaint.color = Color.argb(90, 0, 0, 0)
        canvas.drawRoundRect(
            scratchRectF.apply { set(rect.left + 2f * u, rect.top + 4f * u, rect.right + 2f * u, rect.bottom + 4f * u) },
            rx, rx, shadowPaint
        )
        // Cartoon border
        val bd = 2f * u
        fillPaint.color = Color.argb(200, 30, 15, 0)
        canvas.drawRoundRect(
            scratchRectF.apply { set(rect.left - bd, rect.top - bd, rect.right + bd, rect.bottom + bd) },
            rx + bd, rx + bd, fillPaint
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
        strokePaint.strokeWidth = 1.5f * u; strokePaint.alpha = 255
        canvas.drawRoundRect(rect, rx, rx, strokePaint)
        // Label: shrinks to fit the button width so long labels never overflow
        textPaint.textSize      = labelSize
        textPaint.textAlign     = Paint.Align.CENTER
        textPaint.letterSpacing = 0.06f
        val maxW = rect.width() - 8f * u
        while (textPaint.textSize > 8f * u && textPaint.measureText(label) > maxW) textPaint.textSize *= 0.92f
        val sz = textPaint.textSize
        textPaint.color = Color.argb(80, 0, 0, 0)
        canvas.drawText(label, rect.centerX() + 1.5f * u, rect.centerY() + sz * 0.36f + 1.5f * u, textPaint)
        textPaint.color = Color.WHITE
        canvas.drawText(label, rect.centerX(), rect.centerY() + sz * 0.36f, textPaint)
        textPaint.letterSpacing = 0f

        canvas.restore()
    }

    // -----------------------------------------------------------------------
    // Board & cells
    // -----------------------------------------------------------------------
    private fun drawBoardBackground(canvas: Canvas) {
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

    private fun drawChainLine(canvas: Canvas) {
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

    private fun drawCells(canvas: Canvas, now: Long) {
        val frame = 7f * u
        canvas.save()
        canvas.clipRect(boardLeft - frame, boardTop - frame,
                        boardLeft + board.cols * cellSize + frame, boardTop + board.rows * cellSize + frame)
        drawCellsInner(canvas, now)
        canvas.restore()
    }

    private fun drawCellsInner(canvas: Canvas, now: Long) {
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
                canvas.translate(0f, breatheOff)
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
                if (cell.isGolden) drawBall(canvas, cx, cy, cellSize * 0.43f, false, alpha)
                else drawPiece(canvas, cx, cy, cellSize * 0.43f, cell.type, false, alpha)
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
    private fun drawGoldenOverlay(canvas: Canvas, cx: Float, cy: Float, r: Float, now: Long) {
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
    // drawPiece dispatcher
    // -----------------------------------------------------------------------
    // -----------------------------------------------------------------------
    // Pieces: six donuts, six silhouettes. Colour is never the only difference.
    // -----------------------------------------------------------------------
    // -----------------------------------------------------------------------
    // Sprite cache: every piece is painted once per layout into a small bitmap and
    // blitted per frame. The vector painters below are far too heavy to run 64 times
    // a frame on a software canvas (they cost ~5 fps on the emulator).
    // -----------------------------------------------------------------------
    private var spriteR    = 0f                          // radius the sprites were painted at
    private var spriteHalf = 0                           // half side of each sprite, px
    private val pieceSprites = arrayOfNulls<Bitmap>(12)  // index = type.ordinal * 2 + selected
    private val ballSprites  = arrayOfNulls<Bitmap>(2)
    private val bitmapPaint  = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

    private fun buildSprites() {
        val r = cellSize * 0.43f
        if (r <= 0f) return
        spriteR    = r
        spriteHalf = ceil(r * 1.55f).toInt()              // outline, drips and the selection halo all fit
        val side   = spriteHalf * 2
        val c      = Canvas()
        val mid    = spriteHalf.toFloat()
        for (t in DonutType.values()) for (sel in 0..1) {
            pieceSprites[t.ordinal * 2 + sel]?.recycle()
            val bmp = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888)
            c.setBitmap(bmp)
            drawPieceVector(c, mid, mid, r, t, sel == 1, 255)
            pieceSprites[t.ordinal * 2 + sel] = bmp
        }
        for (sel in 0..1) {
            ballSprites[sel]?.recycle()
            val bmp = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888)
            c.setBitmap(bmp)
            drawBallVector(c, mid, mid, r, sel == 1, 255)
            ballSprites[sel] = bmp
        }
        c.setBitmap(null)
    }

    private fun blit(canvas: Canvas, bmp: Bitmap, cx: Float, cy: Float, radius: Float, alpha: Int) {
        val sc = radius / spriteR
        bitmapPaint.alpha = alpha
        if (abs(sc - 1f) < 0.002f) {
            // Fast path: unscaled blit
            canvas.drawBitmap(bmp, cx - spriteHalf, cy - spriteHalf, bitmapPaint)
            return
        }
        canvas.save()
        canvas.scale(sc, sc, cx, cy)
        canvas.drawBitmap(bmp, cx - spriteHalf, cy - spriteHalf, bitmapPaint)
        canvas.restore()
    }

    private fun drawPiece(
        canvas: Canvas, cx: Float, cy: Float,
        radius: Float, type: DonutType, selected: Boolean, alpha: Int = 255
    ) {
        val bmp = pieceSprites[type.ordinal * 2 + (if (selected) 1 else 0)]
        if (bmp == null || spriteR <= 0f) drawPieceVector(canvas, cx, cy, radius, type, selected, alpha)
        else blit(canvas, bmp, cx, cy, radius, alpha)
    }

    private fun drawBall(canvas: Canvas, cx: Float, cy: Float, r: Float, selected: Boolean, alpha: Int) {
        val bmp = ballSprites[if (selected) 1 else 0]
        if (bmp == null || spriteR <= 0f) drawBallVector(canvas, cx, cy, r, selected, alpha)
        else blit(canvas, bmp, cx, cy, r, alpha)
    }

    private fun drawPieceVector(
        canvas: Canvas, cx: Float, cy: Float,
        radius: Float, type: DonutType, selected: Boolean, alpha: Int
    ) {
        when (type) {
            DonutType.STRAWBERRY -> drawDrippyRing(canvas, cx, cy, radius, type, selected, alpha)
            DonutType.CHOCOLATE  -> drawStripedRing(canvas, cx, cy, radius, type, selected, alpha)
            DonutType.BLUEBERRY  -> drawJellyBun(canvas, cx, cy, radius, type, selected, alpha)
            DonutType.VANILLA    -> drawFlowerRing(canvas, cx, cy, radius, type, selected, alpha)
            DonutType.MATCHA     -> drawHalfDipRing(canvas, cx, cy, radius, type, selected, alpha)
            DonutType.CARAMEL    -> drawSquareDonut(canvas, cx, cy, radius, type, selected, alpha)
        }
    }

    // Shared round base: selection halo, dark outline, drop shadow, dough body.
    private fun ringBase(canvas: Canvas, cx: Float, cy: Float, r: Float, body: Int, selected: Boolean, alpha: Int) {
        val ow = r * 0.18f
        if (selected) {
            fillPaint.color = Color.argb(alpha, 255, 255, 255)
            canvas.drawCircle(cx, cy, r + ow + cellSize * 0.07f, fillPaint)
        }
        fillPaint.color = Color.argb(alpha, 28, 12, 0)
        canvas.drawCircle(cx, cy, r + ow, fillPaint)
        fillPaint.color = Color.argb(alpha / 3, 0, 0, 0)
        canvas.drawCircle(cx + r * 0.06f, cy + r * 0.12f, r, fillPaint)
        fillPaint.color = body; fillPaint.alpha = alpha
        canvas.drawCircle(cx, cy, r, fillPaint)
    }

    private fun drawHole(canvas: Canvas, cx: Float, cy: Float, hr: Float, alpha: Int) {
        fillPaint.color = theme.holeColor; fillPaint.alpha = alpha
        canvas.drawCircle(cx, cy, hr, fillPaint)
        outlinePaint.color = Color.argb(alpha / 2, 255, 255, 255); outlinePaint.strokeWidth = hr * 0.15f
        canvas.drawCircle(cx, cy, hr, outlinePaint)
        outlinePaint.color = Color.argb(alpha / 2, 28, 12, 0)
        canvas.drawCircle(cx, cy, hr * 1.06f, outlinePaint)
        fillPaint.alpha = 255
    }

    // Strawberry: pink ring, red glaze that drips at the bottom, rainbow sprinkles.
    private fun drawDrippyRing(canvas: Canvas, cx: Float, cy: Float, r: Float, type: DonutType, selected: Boolean, alpha: Int) {
        ringBase(canvas, cx, cy, r, type.bodyColor, selected, alpha)
        scratchPath2.rewind()
        for (i in 0 until 24) {
            val gr = r * 0.80f * dripMul[i]
            val x = cx + gr * c24[i]; val y = cy + gr * s24[i]
            if (i == 0) scratchPath2.moveTo(x, y) else scratchPath2.lineTo(x, y)
        }
        scratchPath2.close()
        fillPaint.color = type.glazeColor; fillPaint.alpha = alpha
        canvas.drawPath(scratchPath2, fillPaint)
        canvas.save(); canvas.clipPath(scratchPath2)
        addSheen(canvas, cx, cy, r * 0.82f, alpha)
        canvas.restore()
        val sw = r * 0.24f; val sh = r * 0.09f
        for (i in 0 until 6) {
            val sx = cx + r * sprinkleD[i] * c24[sprinkleA[i]]
            val sy = cy + r * sprinkleD[i] * s24[sprinkleA[i]]
            canvas.save(); canvas.rotate(sprinkleRot[i], sx, sy)
            fillPaint.color = SPRINKLE_COLORS[i]; fillPaint.alpha = alpha
            canvas.drawRoundRect(scratchRectF.apply { set(sx - sw / 2f, sy - sh / 2f, sx + sw / 2f, sy + sh / 2f) }, sh / 2f, sh / 2f, fillPaint)
            canvas.restore()
        }
        drawHole(canvas, cx, cy, r * 0.34f, alpha)
    }

    // Chocolate: dark glaze with three cream stripes.
    private fun drawStripedRing(canvas: Canvas, cx: Float, cy: Float, r: Float, type: DonutType, selected: Boolean, alpha: Int) {
        ringBase(canvas, cx, cy, r, type.bodyColor, selected, alpha)
        fillPaint.color = type.glazeColor; fillPaint.alpha = alpha
        canvas.drawCircle(cx, cy, r * 0.82f, fillPaint)
        scratchPath2.rewind(); scratchPath2.addCircle(cx, cy, r * 0.82f, Path.Direction.CW)
        canvas.save(); canvas.clipPath(scratchPath2)
        strokePaint.color = Color.argb(alpha, 255, 236, 200); strokePaint.strokeWidth = r * 0.13f
        for (i in -1..1) {
            val off = i * r * 0.40f
            canvas.drawLine(cx - r + off, cy - r * 0.35f - off, cx + r * 0.35f + off, cy + r - off, strokePaint)
        }
        addSheen(canvas, cx, cy, r * 0.82f, alpha, sheenAlpha = 0.22f)
        canvas.restore()
        strokePaint.alpha = 255
        drawHole(canvas, cx, cy, r * 0.34f, alpha)
    }

    // Blueberry: a filled bun with no hole, a purple jam spot and powdered sugar.
    private fun drawJellyBun(canvas: Canvas, cx: Float, cy: Float, r: Float, type: DonutType, selected: Boolean, alpha: Int) {
        ringBase(canvas, cx, cy, r, type.bodyColor, selected, alpha)
        scratchPath2.rewind(); scratchPath2.addCircle(cx, cy, r, Path.Direction.CW)
        canvas.save(); canvas.clipPath(scratchPath2)
        fillPaint.color = type.glazeColor; fillPaint.alpha = alpha
        canvas.drawCircle(cx + r * 0.62f, cy + r * 0.18f, r * 0.36f, fillPaint)
        fillPaint.color = Color.argb((alpha * 0.55f).toInt(), 255, 255, 255)
        canvas.drawCircle(cx + r * 0.54f, cy + r * 0.06f, r * 0.09f, fillPaint)
        addSheen(canvas, cx, cy, r, alpha, sheenAlpha = 0.30f)
        canvas.restore()
        fillPaint.color = Color.argb((alpha * 0.85f).toInt(), 255, 255, 255)
        for (i in 0 until 7) canvas.drawCircle(cx + r * sugarX[i], cy + r * sugarY[i], r * sugarR[i], fillPaint)
        fillPaint.alpha = 255
    }

    private fun buildScallop(path: Path, cx: Float, cy: Float, rr: Float) {
        path.rewind()
        for (i in 0 until 48) {
            val d = rr * scallopMul[i]
            val x = cx + d * c48[i]; val y = cy + d * s48[i]
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
    }

    // Vanilla: an eight-lobed flower ring with lemon glaze.
    private fun drawFlowerRing(canvas: Canvas, cx: Float, cy: Float, r: Float, type: DonutType, selected: Boolean, alpha: Int) {
        val ow = r * 0.16f
        if (selected) {
            fillPaint.color = Color.argb(alpha, 255, 255, 255)
            canvas.drawCircle(cx, cy, r * 1.32f, fillPaint)
        }
        buildScallop(scratchPath, cx, cy, r)
        outlinePaint.color = Color.argb(alpha, 28, 12, 0); outlinePaint.strokeWidth = ow * 2f
        canvas.drawPath(scratchPath, outlinePaint)
        canvas.save(); canvas.translate(r * 0.06f, r * 0.12f)
        fillPaint.color = Color.argb(alpha / 3, 0, 0, 0)
        canvas.drawPath(scratchPath, fillPaint)
        canvas.restore()
        fillPaint.color = type.bodyColor; fillPaint.alpha = alpha
        canvas.drawPath(scratchPath, fillPaint)
        buildScallop(scratchPath2, cx, cy, r * 0.80f)
        fillPaint.color = type.glazeColor; fillPaint.alpha = alpha
        canvas.drawPath(scratchPath2, fillPaint)
        canvas.save(); canvas.clipPath(scratchPath2)
        addSheen(canvas, cx, cy, r * 0.80f, alpha)
        canvas.restore()
        drawHole(canvas, cx, cy, r * 0.30f, alpha)
    }

    // Matcha: green ring dipped halfway in lime glaze, sesame seeds on top.
    private fun drawHalfDipRing(canvas: Canvas, cx: Float, cy: Float, r: Float, type: DonutType, selected: Boolean, alpha: Int) {
        ringBase(canvas, cx, cy, r, type.bodyColor, selected, alpha)
        scratchPath2.rewind(); scratchPath2.addCircle(cx, cy, r * 0.82f, Path.Direction.CW)
        canvas.save(); canvas.clipPath(scratchPath2)
        scratchPath.rewind()
        scratchPath.moveTo(cx - r, cy - r)
        scratchPath.lineTo(cx + r, cy - r)
        scratchPath.lineTo(cx + r, cy + r * 0.10f)
        val seg = r * 0.5f
        for (i in 0 until 4) {
            val x0 = cx + r - seg * i
            val ctrlY = cy + (if (i % 2 == 0) r * 0.42f else -r * 0.18f)
            scratchPath.quadTo(x0 - seg / 2f, ctrlY, x0 - seg, cy + r * 0.10f)
        }
        scratchPath.close()
        fillPaint.color = type.glazeColor; fillPaint.alpha = alpha
        canvas.drawPath(scratchPath, fillPaint)
        addSheen(canvas, cx, cy, r * 0.82f, alpha, sheenAlpha = 0.24f)
        canvas.restore()
        fillPaint.color = Color.argb(alpha, 60, 40, 20)
        for (i in 0 until 5) {
            val sx = cx + r * seedX[i]; val sy = cy + r * seedY[i]
            canvas.save(); canvas.rotate(seedRot[i], sx, sy)
            canvas.drawOval(scratchRectF.apply { set(sx - r * 0.075f, sy - r * 0.04f, sx + r * 0.075f, sy + r * 0.04f) }, fillPaint)
            canvas.restore()
        }
        drawHole(canvas, cx, cy, r * 0.34f, alpha)
    }

    // Caramel: a square donut with a round hole and dark caramel drizzle.
    private fun drawSquareDonut(canvas: Canvas, cx: Float, cy: Float, r: Float, type: DonutType, selected: Boolean, alpha: Int) {
        val ow = r * 0.18f; val side = r * 0.90f; val cr = r * 0.34f
        if (selected) {
            fillPaint.color = Color.argb(alpha, 255, 255, 255)
            canvas.drawCircle(cx, cy, r * 1.34f, fillPaint)
        }
        fillPaint.color = Color.argb(alpha, 28, 12, 0)
        canvas.drawRoundRect(scratchRectF.apply { set(cx - side - ow, cy - side - ow, cx + side + ow, cy + side + ow) }, cr + ow, cr + ow, fillPaint)
        fillPaint.color = Color.argb(alpha / 3, 0, 0, 0)
        canvas.drawRoundRect(scratchRectF.apply { set(cx - side + r * 0.06f, cy - side + r * 0.12f, cx + side + r * 0.06f, cy + side + r * 0.12f) }, cr, cr, fillPaint)
        fillPaint.color = type.bodyColor; fillPaint.alpha = alpha
        canvas.drawRoundRect(scratchRectF.apply { set(cx - side, cy - side, cx + side, cy + side) }, cr, cr, fillPaint)
        val g = side * 0.78f
        scratchRectF.set(cx - g, cy - g, cx + g, cy + g)
        fillPaint.color = type.glazeColor; fillPaint.alpha = alpha
        canvas.drawRoundRect(scratchRectF, cr * 0.8f, cr * 0.8f, fillPaint)
        scratchPath2.rewind(); scratchPath2.addRoundRect(scratchRectF, cr * 0.8f, cr * 0.8f, Path.Direction.CW)
        canvas.save(); canvas.clipPath(scratchPath2)
        strokePaint.color = Color.argb(alpha, 110, 55, 10); strokePaint.strokeWidth = r * 0.07f
        for (i in 0 until 3) {
            val yy = cy - g + g * (0.35f + 0.65f * i)
            canvas.drawLine(cx - g, yy - r * 0.14f, cx + g, yy + r * 0.14f, strokePaint)
        }
        addSheen(canvas, cx, cy, g, alpha, sheenAlpha = 0.22f)
        canvas.restore()
        strokePaint.alpha = 255
        drawHole(canvas, cx, cy, r * 0.30f, alpha)
    }

    // The wildcard is a sports ball: matches any donut, and echoes the title screen.
    private fun drawBallVector(canvas: Canvas, cx: Float, cy: Float, r: Float, selected: Boolean, alpha: Int) {
        val ow = r * 0.18f
        if (selected) {
            fillPaint.color = Color.argb(alpha, 255, 255, 255)
            canvas.drawCircle(cx, cy, r + ow + cellSize * 0.07f, fillPaint)
        }
        fillPaint.color = Color.argb(alpha, 28, 12, 0)
        canvas.drawCircle(cx, cy, r + ow, fillPaint)
        fillPaint.color = Color.argb(alpha / 3, 0, 0, 0)
        canvas.drawCircle(cx + r * 0.06f, cy + r * 0.12f, r, fillPaint)
        fillPaint.color = Color.argb(alpha, 255, 255, 255)
        canvas.drawCircle(cx, cy, r, fillPaint)
        scratchPath2.rewind(); scratchPath2.addCircle(cx, cy, r, Path.Direction.CW)
        canvas.save(); canvas.clipPath(scratchPath2)
        fillPaint.color = Color.argb(alpha, 22, 22, 22)
        drawPentagon(canvas, cx, cy, r * 0.34f, -90f)
        for (i in 0 until 5) {
            val a = i * 72f - 90f
            val rad = Math.toRadians(a.toDouble())
            drawPentagon(canvas, cx + (r * 0.80f * cos(rad)).toFloat(), cy + (r * 0.80f * sin(rad)).toFloat(), r * 0.28f, a + 180f)
        }
        canvas.restore()
        addSheen(canvas, cx, cy, r, alpha, sheenAlpha = 0.28f, specAlpha = 0.9f)
        fillPaint.alpha = 255
    }

    private fun drawPentagon(canvas: Canvas, cx: Float, cy: Float, r: Float, startDeg: Float) {
        scratchPath.rewind()
        for (i in 0 until 5) {
            val a = Math.toRadians((startDeg + i * 72f).toDouble())
            val x = cx + (r * cos(a)).toFloat(); val y = cy + (r * sin(a)).toFloat()
            if (i == 0) scratchPath.moveTo(x, y) else scratchPath.lineTo(x, y)
        }
        scratchPath.close()
        canvas.drawPath(scratchPath, fillPaint)
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
    // Counter — digit-flip scoreboard
    // -----------------------------------------------------------------------
    private fun drawCounter(canvas: Canvas, now: Long) {
        val stripY = boardTop + board.rows * cellSize
        val top    = stripY + 6f * u
        val bottom = stripY + counterH
        val midY   = (top + bottom) / 2f
        val ph     = bottom - top
        val cx     = boardLeft + board.cols * cellSize / 2f

        // Heartbeat: quick scale-pulse when the number increments
        val beat = if (counterPulseMs >= 0) {
            val t = ((now - counterPulseMs).toFloat() / COUNTER_PULSE_MS).coerceIn(0f, 1f)
            if (t >= 1f) { counterPulseMs = -1L; 1f }
            else 1f + sin(t * PI.toFloat()) * 0.085f
        } else 1f
        canvas.save()
        canvas.scale(beat, beat, cx, midY)

        // One big figure; a small donut beside it says what it counts without a word
        val digitSz = ph * 0.72f
        textPaint.textSize = digitSz
        val cellW   = textPaint.measureText("0") * 1.08f
        val numStr  = displayedCount.toString()
        val iconR   = ph * 0.22f
        val iconGap = ph * 0.18f
        val totalW  = iconR * 2.4f + iconGap + numStr.length * cellW
        val left    = cx - totalW / 2f
        val startX  = left + iconR * 2.4f + iconGap + cellW / 2f
        val baseY   = midY + digitSz * 0.36f

        drawPiece(canvas, left + iconR * 1.2f, midY, iconR, DonutType.STRAWBERRY, false, 255)

        textOutlinePaint.textSize    = digitSz
        textOutlinePaint.textAlign   = Paint.Align.CENTER
        textOutlinePaint.typeface    = boldTypeface
        textOutlinePaint.strokeWidth = 2.5f * u

        for ((idx, ch) in numStr.withIndex()) {
            val pos  = numStr.length - 1 - idx             // 0 = ones place
            val x    = startX + idx * cellW
            val flip = digitFlips[pos]
            val (displayCh, scaleY) = if (flip != null) {
                val t  = ((now - flip.startMs).toFloat() / DIGIT_FLIP_MS).coerceIn(0f, 1f)
                val sy = abs(1f - t * 2f)                  // 1 -> 0 at mid -> 1
                val dc = if (t < 0.5f) flip.from else flip.to
                if (t >= 1f) digitFlips.remove(pos)
                Pair(dc, sy)
            } else Pair(ch, 1f)
            // A brand-new leading digit starts from "0"; never show that zero (9 -> 18, not 09 -> 18)
            if (idx == 0 && numStr.length > 1 && displayCh == '0') continue

            canvas.save()
            canvas.scale(1f, scaleY, x, midY)
            textPaint.color     = Color.argb(70, 0, 0, 0)
            textPaint.textAlign = Paint.Align.CENTER
            canvas.drawText("$displayCh", x + 2f * u, baseY + 2f * u, textPaint)
            textPaint.color = theme.textPrimary
            canvas.drawText("$displayCh", x, baseY, textPaint)
            textOutlinePaint.color = Color.argb(40, 28, 12, 0)
            canvas.drawText("$displayCh", x, baseY, textOutlinePaint)
            canvas.restore()
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
    private fun drawCenterPing(canvas: Canvas, now: Long) {
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
    private fun drawCelebration(canvas: Canvas, now: Long) {
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

    private fun easeOutBack(t: Float): Float {
        val c1 = 1.70158f; val c3 = c1 + 1f; val x = t - 1f
        return 1f + c3 * x * x * x + c1 * x * x
    }

    // Every sticker earned mid-game gets its moment
    private fun checkStickers(now: Long) {
        var mask = 0
        for (i in 0 until 12) if (isStickerEarned(i)) mask = mask or (1 shl i)
        if (earnedMask < 0) { earnedMask = mask; return }
        if (stickerFlyMs >= 0) return
        val fresh = mask and earnedMask.inv()
        if (fresh == 0) return
        val idx = Integer.numberOfTrailingZeros(fresh)
        earnedMask = earnedMask or (1 shl idx)
        stickerFlyIdx = idx; stickerFlyMs = now
        if (prefs.soundEnabled)  soundEngine.playMilestone()
        if (prefs.hapticEnabled) hapticEngine.milestone()
    }

    private fun drawStickerFly(canvas: Canvas, now: Long) {
        val t = (now - stickerFlyMs).toFloat() / STICKER_FLY_MS
        if (t >= 1f) { stickerFlyMs = -1L; starPulseMs = now; return }
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

    private fun starPulse(now: Long): Float {
        if (starPulseMs < 0) return 1f
        val t = (now - starPulseMs).toFloat() / 450f
        if (t >= 1f) { starPulseMs = -1L; return 1f }
        return 1f + 0.35f * sin(t * PI.toFloat())
    }

    // -----------------------------------------------------------------------
    // First-run tutorial overlay
    // -----------------------------------------------------------------------
    private fun drawTutorial(canvas: Canvas, now: Long) {
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
        strokePaint.color = theme.hintRing; strokePaint.alpha = (150 + 105 * pulse).toInt()
        strokePaint.strokeWidth = cellSize * 0.10f
        for (i in 0 until 3) canvas.drawCircle(xs[i], ys[i], r * (1.12f + 0.06f * pulse), strokePaint)
        strokePaint.alpha = 255

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
    private fun chainOrder(cells: List<Pair<Int, Int>>): List<Pair<Int, Int>> {
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
    private fun drawFinger(canvas: Canvas, x: Float, y: Float, alpha: Int) {
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
    private fun drawNoMovesWarning(canvas: Canvas, now: Long) {
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
    // Stickers panel
    // -----------------------------------------------------------------------
    /** Returns true if sticker i has been earned. */
    private fun isStickerEarned(i: Int): Boolean {
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

    private fun drawStickersPanel(canvas: Canvas, now: Long) {
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
                val nameSz = rect.height() * 0.150f
                textPaint.textSize = nameSz
                textPaint.color    = Color.argb(90, 0, 0, 0)
                canvas.drawText(STICKER_NAMES[i], cx + 1f, rect.bottom - rect.height() * 0.09f + 1f, textPaint)
                textPaint.color = Color.WHITE
                canvas.drawText(STICKER_NAMES[i], cx, rect.bottom - rect.height() * 0.09f, textPaint)
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
                textPaint.textSize  = rect.height() * 0.115f
                textPaint.color     = Color.argb(130, 80, 50, 10)
                textPaint.textAlign = Paint.Align.CENTER
                canvas.drawText(STICKER_DESCS[i], cx, rect.bottom - rect.height() * 0.12f, textPaint)
            }
        }

        // ---- Stats line ----
        val statsY = stickerRects[11].bottom + 18f * u
        val lifetime = lifetimeDonuts()
        textPaint.textSize = 14f * u; textPaint.textAlign = Paint.Align.CENTER
        textPaint.color = theme.textSecondary
        canvas.drawText(
            "$lifetime donuts popped  \u00B7  $earnedCount of 12 stickers",
            stickerPanelRect.centerX(), statsY, textPaint)

        drawPrettyButton(canvas, stickersCloseRect, Color.rgb(60, 175, 80), "Done  \u2713", 18f * u)

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

        drawSectionLabel(canvas, "Donuts", pl + pad, gridRects[0].top - 8f * k)
        for (i in 0 until 2) drawSettingsBtn(canvas, now, gridRects[i], gridLabels[i], gridOptions[i] == prefs.gridSize)

        // New game: two-tap confirm so a stray tap cannot wipe the board
        val confirmActive = resetConfirmMs >= 0 && (now - resetConfirmMs) < RESET_CONFIRM_MS
        if (resetConfirmMs >= 0 && !confirmActive) resetConfirmMs = -1L
        val resetColor = if (confirmActive) Color.rgb(220, 130, 30) else Color.rgb(200, 70, 50)
        val resetLabel = if (confirmActive) "Tap again!" else "New game"
        drawPrettyButton(canvas, settingsResetRect, resetColor, resetLabel, 18f * k)
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

        drawPrettyButton(canvas, settingsCloseRect, Color.rgb(60, 175, 80), "Done  \u2713", 18f * k)

        canvas.restore()
    }

    /** Small green check badge used on selected settings buttons. */
    private fun drawCheckBadge(canvas: Canvas, bx: Float, by: Float, br: Float) {
        fillPaint.color = Color.argb(210, 28, 12, 0)
        canvas.drawCircle(bx, by, br + br * 0.2f, fillPaint)
        fillPaint.color = Color.rgb(80, 200, 80); fillPaint.alpha = 255
        canvas.drawCircle(bx, by, br, fillPaint)
        textPaint.textSize  = br * 1.3f; textPaint.textAlign = Paint.Align.CENTER
        textPaint.color     = Color.WHITE
        canvas.drawText("\u2713", bx, by + br * 0.42f, textPaint)
    }

    /** Draws a section label with a chunky left accent bar — bold and readable. */
    private fun drawSectionLabel(canvas: Canvas, text: String, x: Float, baselineY: Float) {
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

    private fun drawSettingsBtn(canvas: Canvas, now: Long, rect: RectF, label: String, selected: Boolean) {
        val k = u * settingsSc
        val pressScale = if (rect === optPressRect) buttonPressScale(now, optPressMs) else 1f
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
        // Label: shrinks to fit so long words never spill past the button
        textPaint.textSize      = 18f * k
        textPaint.textAlign     = Paint.Align.CENTER
        textPaint.letterSpacing = 0.04f
        val maxW = rect.width() - 8f * k
        while (textPaint.textSize > 8f * k && textPaint.measureText(label) > maxW) textPaint.textSize *= 0.92f
        val ty = rect.centerY() + textPaint.textSize * 0.36f
        textPaint.color = Color.argb(80, 0, 0, 0)
        canvas.drawText(label, rect.centerX() + 1f * k, ty + 1f * k, textPaint)
        textPaint.color = Color.WHITE
        canvas.drawText(label, rect.centerX(), ty, textPaint)
        textPaint.letterSpacing = 0f
        // Checkmark badge on selected
        if (selected) drawCheckBadge(canvas, rect.right - 1f, rect.top + 1f, 9f * k)
        canvas.restore()
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
                        stickersBtnRect.contains(event.x, event.y) -> { stickersPressMs = now; stickersOpen = true }
                        settingsBtnRect.contains(event.x, event.y) -> { settingsPressMs = now; settingsOpen = true }
                        else -> handleDown(event)
                    }
                }
                MotionEvent.ACTION_MOVE   -> handleMove(event)
                MotionEvent.ACTION_UP     -> handleUp()
                MotionEvent.ACTION_CANCEL -> { dragChain.clear() }
            }
        }
        return true
    }

    // System Back: close an open panel first; false means nothing was open
    fun onBackPressed(): Boolean {
        synchronized(holder) {
            if (settingsOpen) { settingsOpen = false; resetConfirmMs = -1L; return true }
            if (stickersOpen) { stickersOpen = false; return true }
        }
        return false
    }

    private fun handleSettingsTouch(x: Float, y: Float) {
        val now = SystemClock.elapsedRealtime()
        for (rs in arrayOf(soundRects, hintRects, gridRects)) for (rc in rs) if (rc.contains(x, y)) { optPressRect = rc; optPressMs = now }
        when {
            soundRects[0].contains(x, y) -> prefs.soundEnabled = true
            soundRects[1].contains(x, y) -> prefs.soundEnabled = false
            hintRects[0].contains(x, y)  -> prefs.hintDelayMs = hintOptions[0]
            hintRects[1].contains(x, y)  -> prefs.hintDelayMs = hintOptions[1]
            gridRects[0].contains(x, y) || gridRects[1].contains(x, y) -> {
                val size = if (gridRects[0].contains(x, y)) gridOptions[0] else gridOptions[1]
                if (prefs.gridSize != size) { prefs.gridSize = size; rebuildBoard() }
            }
            settingsResetRect.contains(x, y) -> {
                val confirmActive = resetConfirmMs >= 0 && (now - resetConfirmMs) < RESET_CONFIRM_MS
                if (confirmActive) { resetConfirmMs = -1L; startOver(); settingsOpen = false }
                else resetConfirmMs = now
            }
            settingsCloseRect.contains(x, y) || !panelRect.contains(x, y) -> {
                resetConfirmMs = -1L
                settingsOpen = false
            }
        }
    }

    private fun handleStickersTouch(x: Float, y: Float) {
        if (stickersCloseRect.contains(x, y) || !stickerPanelRect.contains(x, y)) stickersOpen = false
    }

    /** Fresh board and counter. Session stats are saved first so stickers keep counting. */
    private fun startOver() {
        val now = SystemClock.elapsedRealtime()
        saveSession()
        sessionSaved = 0
        board.reset()
        displayedCount   = 0
        prevDisplayCount = -1
        digitFlips.clear()
        lastMilestone    = 0
        celebrateMs      = -1L
        particles.clear()
        synchronized(floatLabels) { floatLabels.clear() }
        dragChain.clear(); chainPings.clear()
        pendingResult    = null
        cascadeCount     = 0
        isCascade        = false
        counterPulseMs   = -1L
        chainFlashMs     = -1L
        shuffleAnimMs    = -1L
        hintCells        = emptyList()
        tutorialCells    = emptyList()
        noMovesWarningMs = -1L
        resetFlashMs     = now
        lastActionMs     = now
        boardEntryMs     = now
    }

    private fun handleDown(event: MotionEvent) {
        lastActionMs = SystemClock.elapsedRealtime(); hintCells = emptyList()
        dragChain.clear(); chainPings.clear()
        val col = cellCol(event.x); val row = cellRow(event.y)
        if (inBounds(row, col)) {
            val now2 = SystemClock.elapsedRealtime()
            dragChain.add(Pair(row, col))
            chainPings[Pair(row, col)] = now2
            centerPingMs = now2
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
            centerPingMs = now2
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
        centerPingMs = now2
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
                // First chain ever made: the guided demo has done its job
                if (tutorialActive) { tutorialActive = false; prefs.tutorialSeen = true; celebrate(now, "YAY!") }

                val chainLen = result.chainCells.size
                if (chainLen > prefs.bestChainLength) prefs.bestChainLength = chainLen
                if (chainLen >= 6) {
                    chainFlashMs    = now
                    chainFlashColor = result.chainType.bodyColor
                }
                if (prefs.soundEnabled)  soundEngine.playPopClear()
                if (prefs.hapticEnabled) hapticEngine.pop()

                // Floating label for big chains
                // Chains of 5+ always fire a power-up, so those get the sound-word
                val label = when {
                    result.powerUp == PowerUp.COLOR_BURST -> "KABOOM!"
                    result.powerUp == PowerUp.ROW_BLAST   -> "WHOOSH!"
                    result.powerUp == PowerUp.BOMB        -> "BOOM!"
                    chainLen == 4 -> "NICE!"
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
        dragChain.clear(); chainPings.clear()
    }

    private fun cellCol(x: Float) = ((x - boardLeft) / cellSize).toInt()
    private fun cellRow(y: Float) = ((y - boardTop)  / cellSize).toInt()
    private fun inBounds(r: Int, c: Int) = r in 0 until board.rows && c in 0 until board.cols
    private fun adjacent8(r1: Int, c1: Int, r2: Int, c2: Int) =
        abs(r1 - r2) <= 1 && abs(c1 - c2) <= 1 && !(r1 == r2 && c1 == c2)

    // -----------------------------------------------------------------------
    // Sticker art: drawn with the pieces' own sprites and outline style, no system emoji.
    // -----------------------------------------------------------------------
    private fun drawStickerArt(canvas: Canvas, i: Int, cx: Float, cy: Float, size: Float) {
        val r = size * 0.30f
        when (i) {
            0  -> drawPiece(canvas, cx, cy, r * 1.15f, DonutType.STRAWBERRY, false)
            1  -> {
                drawPiece(canvas, cx - r * 0.72f, cy + r * 0.22f, r * 0.95f, DonutType.CHOCOLATE, false)
                drawPiece(canvas, cx + r * 0.72f, cy - r * 0.22f, r * 0.95f, DonutType.STRAWBERRY, false)
            }
            2  -> {
                drawPiece(canvas, cx, cy + r * 0.30f, r * 1.05f, DonutType.VANILLA, false)
                drawCrown(canvas, cx, cy - r * 1.05f, r * 1.0f)
            }
            3, 4, 5 -> drawChainArt(canvas, cx, cy, size, (4 + (i - 3) * 2).toString())
            6, 7, 8 -> drawBatchArt(canvas, cx, cy, size, intArrayOf(20, 60, 200)[i - 6].toString())
            9  -> {
                drawBall(canvas, cx, cy, r * 1.05f, false, 255)
                strokePaint.color = Color.argb(220, 255, 210, 30); strokePaint.strokeWidth = r * 0.16f
                scratchRectF.set(cx - r * 1.5f, cy - r * 1.5f, cx + r * 1.5f, cy + r * 1.5f)
                for (k in 0 until 4) canvas.drawArc(scratchRectF, k * 90f + 15f, 60f, false, strokePaint)
                strokePaint.alpha = 255
            }
            10 -> drawMiniBoard(canvas, cx, cy, size)
            else -> drawHeart(canvas, cx, cy + r * 0.1f, r * 1.25f)
        }
    }

    private fun drawOutlinedText(canvas: Canvas, text: String, x: Float, y: Float, sz: Float, fill: Int) {
        textPaint.textSize = sz; textPaint.textAlign = Paint.Align.CENTER
        textOutlinePaint.textSize = sz; textOutlinePaint.textAlign = Paint.Align.CENTER
        textOutlinePaint.typeface = boldTypeface; textOutlinePaint.strokeWidth = sz * 0.16f
        textOutlinePaint.color = Color.argb(255, 28, 12, 0)
        canvas.drawText(text, x, y + sz * 0.36f, textOutlinePaint)
        textPaint.color = fill
        canvas.drawText(text, x, y + sz * 0.36f, textPaint)
    }

    // A string with a donut at each end and the chain length in the middle
    private fun drawChainArt(canvas: Canvas, cx: Float, cy: Float, size: Float, label: String) {
        val half = size * 0.62f
        chainOutlinePaint.strokeWidth = size * 0.11f; chainOutlinePaint.color = Color.argb(230, 28, 12, 0)
        canvas.drawLine(cx - half, cy, cx + half, cy, chainOutlinePaint)
        chainLinePaint.strokeWidth = size * 0.05f; chainLinePaint.color = Color.WHITE
        canvas.drawLine(cx - half, cy, cx + half, cy, chainLinePaint)
        drawPiece(canvas, cx - half, cy, size * 0.20f, DonutType.MATCHA, false)
        drawPiece(canvas, cx + half, cy, size * 0.20f, DonutType.MATCHA, false)
        drawOutlinedText(canvas, label, cx, cy, size * 0.62f, Color.WHITE)
    }

    // A tray with a big number on it
    private fun drawBatchArt(canvas: Canvas, cx: Float, cy: Float, size: Float, label: String) {
        val w = size * 0.62f; val h = size * 0.42f; val bd = size * 0.045f
        fillPaint.color = Color.argb(255, 28, 12, 0)
        canvas.drawRoundRect(scratchRectF.apply { set(cx - w - bd, cy - h - bd, cx + w + bd, cy + h + bd) }, size * 0.16f, size * 0.16f, fillPaint)
        fillPaint.color = Color.rgb(245, 210, 165)
        canvas.drawRoundRect(scratchRectF.apply { set(cx - w, cy - h, cx + w, cy + h) }, size * 0.13f, size * 0.13f, fillPaint)
        drawPiece(canvas, cx - w + size * 0.05f, cy - h + size * 0.02f, size * 0.15f, DonutType.STRAWBERRY, false)
        drawOutlinedText(canvas, label, cx + size * 0.04f, cy + size * 0.02f, size * 0.50f, Color.WHITE)
    }

    private fun drawCrown(canvas: Canvas, cx: Float, cy: Float, r: Float) {
        val w = r * 0.95f; val h = r * 0.45f
        scratchPath.rewind()
        scratchPath.moveTo(cx - w, cy + h)
        scratchPath.lineTo(cx - w, cy - h * 0.5f)
        scratchPath.lineTo(cx - w * 0.5f, cy + h * 0.05f)
        scratchPath.lineTo(cx, cy - h)
        scratchPath.lineTo(cx + w * 0.5f, cy + h * 0.05f)
        scratchPath.lineTo(cx + w, cy - h * 0.5f)
        scratchPath.lineTo(cx + w, cy + h)
        scratchPath.close()
        outlinePaint.color = Color.argb(255, 28, 12, 0); outlinePaint.strokeWidth = r * 0.22f
        canvas.drawPath(scratchPath, outlinePaint)
        fillPaint.color = Color.rgb(255, 215, 50); fillPaint.alpha = 255
        canvas.drawPath(scratchPath, fillPaint)
        fillPaint.color = Color.rgb(235, 25, 80)
        canvas.drawCircle(cx, cy + h * 0.45f, r * 0.14f, fillPaint)
    }

    private fun drawMiniBoard(canvas: Canvas, cx: Float, cy: Float, size: Float) {
        val half = size * 0.50f; val bd = size * 0.05f
        fillPaint.color = Color.argb(255, 28, 12, 0)
        canvas.drawRoundRect(scratchRectF.apply { set(cx - half - bd, cy - half - bd, cx + half + bd, cy + half + bd) }, size * 0.16f, size * 0.16f, fillPaint)
        fillPaint.color = theme.boardBg
        canvas.drawRoundRect(scratchRectF.apply { set(cx - half, cy - half, cx + half, cy + half) }, size * 0.13f, size * 0.13f, fillPaint)
        val d = half * 0.5f; val pr = size * 0.19f
        drawPiece(canvas, cx - d, cy - d, pr, DonutType.STRAWBERRY, false)
        drawPiece(canvas, cx + d, cy - d, pr, DonutType.BLUEBERRY, false)
        drawPiece(canvas, cx - d, cy + d, pr, DonutType.VANILLA, false)
        drawPiece(canvas, cx + d, cy + d, pr, DonutType.MATCHA, false)
    }

    private fun drawHeart(canvas: Canvas, cx: Float, cy: Float, r: Float) {
        scratchPath.rewind()
        scratchPath.moveTo(cx, cy + r * 0.95f)
        scratchPath.cubicTo(cx - r * 1.6f, cy - r * 0.1f, cx - r * 0.9f, cy - r * 1.15f, cx, cy - r * 0.45f)
        scratchPath.cubicTo(cx + r * 0.9f, cy - r * 1.15f, cx + r * 1.6f, cy - r * 0.1f, cx, cy + r * 0.95f)
        scratchPath.close()
        outlinePaint.color = Color.argb(255, 28, 12, 0); outlinePaint.strokeWidth = r * 0.22f
        canvas.drawPath(scratchPath, outlinePaint)
        fillPaint.color = Color.rgb(235, 25, 80); fillPaint.alpha = 255
        canvas.drawPath(scratchPath, fillPaint)
        fillPaint.color = Color.argb(160, 255, 255, 255)
        canvas.drawCircle(cx - r * 0.45f, cy - r * 0.45f, r * 0.18f, fillPaint)
        fillPaint.alpha = 255
    }

    // -----------------------------------------------------------------------
    // Render thread
    // -----------------------------------------------------------------------
    inner class RenderThread(private val holder: SurfaceHolder) : Thread("GameRenderThread") {
        @Volatile var running = true
        private var frames = 0
        private var fpsMarkMs = 0L
        private var hwFailures = 0
        private fun lockFrame(): Canvas? {
            // The GPU canvas can fail transiently while the surface is being set up, so only
            // give up on it after a long run of consecutive failures.
            if (hwFailures < 120) {
                try {
                    val c = holder.lockHardwareCanvas()
                    if (c != null) { hwFailures = 0; return c }
                } catch (e: Exception) {
                    if (debuggable && hwFailures == 0) android.util.Log.d("Donuts", "hardware canvas unavailable: " + e)
                }
                hwFailures++
            }
            return try { holder.lockCanvas() } catch (_: Exception) { null }
        }
        private var lockNs = 0L; private var drawNs = 0L; private var postNs = 0L
        override fun run() {
            while (running) {
                val t0 = System.nanoTime()
                // GPU-backed canvas (API 26+); falls back to the software canvas if unavailable
                val canvas = lockFrame()
                if (canvas == null) { sleep(16L); continue }
                val t1 = System.nanoTime()
                try { synchronized(holder) { drawFrame(canvas) } }
                finally {
                    val t2 = System.nanoTime()
                    // The surface can vanish under us while we hold a canvas; that is not fatal
                    try { holder.unlockCanvasAndPost(canvas) } catch (_: Exception) { }
                    val t3 = System.nanoTime()
                    if (debuggable) {
                        lockNs += t1 - t0; drawNs += t2 - t1; postNs += t3 - t2
                        frames++
                        val t = SystemClock.elapsedRealtime()
                        if (t - fpsMarkMs >= 1000L) {
                            if (fpsMarkMs > 0L && frames > 0) android.util.Log.d("Donuts",
                                "fps=" + frames + " hw=" + (hwFailures == 0) + " lock=" + (lockNs / frames / 1_000_000) + "ms draw=" + (drawNs / frames / 1_000_000) + "ms post=" + (postNs / frames / 1_000_000) + "ms")
                            frames = 0; lockNs = 0; drawNs = 0; postNs = 0; fpsMarkMs = t
                        }
                    }
                }
                // Pace to ~60 fps: only sleep for whatever is left of the 16 ms budget
                val spent = (System.nanoTime() - t0) / 1_000_000L
                if (spent < 16L) sleep(16L - spent)
            }
        }
    }
}
