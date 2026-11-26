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

class GameView(context: Context, initialBoard: GameBoard, internal val prefs: Prefs) :
    SurfaceView(context), SurfaceHolder.Callback {

    internal var board = initialBoard

    internal val theme = GameTheme

    // -----------------------------------------------------------------------
    // Layout
    // -----------------------------------------------------------------------
    internal var surfaceW  = 0
    internal var surfaceH  = 0
    internal var cellSize  = 0f
    internal var boardLeft = 0f
    internal var boardTop  = 0f
    internal var counterH  = 0f

    // Density-independent unit: pixels per design-dp, boosted a little on large screens
    // so chrome and text grow with the device instead of staying phone-sized.
    internal val uiScale = UiScale(context)
    internal val debuggable = (context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
    internal var u = 1f

    // Window insets (status bar, gesture bar, display cutout). Layout stays inside them.
    internal var insetL = 0; internal var insetT = 0; internal var insetR = 0; internal var insetB = 0

    // HUD: two quiet buttons above the board, stickers (left) and settings (right)
    internal var settingsBtnRect = RectF()

    // Settings panel layout
    internal var panelRect         = RectF()
    internal val soundRects        = Array(2) { RectF() }
    internal val hintRects         = Array(2) { RectF() }
    internal val buzzRects         = Array(2) { RectF() }
    internal var settingsStickersRect = RectF()
    internal val gridRects         = Array(2) { RectF() }
    internal val motionRects       = Array(2) { RectF() }   // grown-ups page: Bouncy, Calm
    internal val settingsIconRects = Array(5) { RectF() }   // picture with its word: sound, buzz, hints, size, motion
    internal var settingsResetRect = RectF()
    internal var settingsCloseRect = RectF()

    internal var settingsSc = 1f     // < 1 only when the full-size settings panel cannot fit the screen

    // The settings panel has three pages: Steven's (sound, buzz, hints), the grown-ups gate, and
    // the grown-ups corner (size, New game, Reset stickers). panelRect, settingsSc and
    // settingsCloseRect always hold the shown page's values; showSettingsPage copies them in.
    internal var settingsPage      = SETTINGS_KID
    internal val settingsPageRects = Array(3) { RectF() }
    internal val settingsPageSc    = FloatArray(3) { 1f }
    internal val settingsPageClose = Array(3) { RectF() }
    internal var settingsPageMs    = -1L                  // when the page last changed, for its pop
    internal var settingsGrownRect = RectF()              // Steven's page: the locked Grown-ups button
    internal val gateKeyRects      = Array(10) { RectF() } // the gate keypad, by digit
    internal var gateAnswerRect    = RectF()
    internal var gateWrongMs       = -1L                  // a wrong answer shakes the answer box
    internal var parentGate        = ParentGate()
    internal var settingsTallyRect = RectF()              // Grown-ups page: the play tally lines
    // How Steven plays (PlayTally), kept in Prefs; tallyLines is what the Grown-ups page draws
    internal val playTally         = PlayTally.decode(prefs.playTally)
    @Volatile internal var tallyLines: List<String> = playTally.lines()

    internal val onOffLabels = arrayOf("On", "Off")
    internal val hintOptions = longArrayOf(5_000L, 0L)
    internal val gridOptions = Prefs.GRID_SIZES
    // 6x6 donuts are big, 8x8 are small; the grid size tells a parent that "Small" means more of them
    internal val gridLabels  = arrayOf("Big  6×6", "Small  8×8")
    internal val motionLabels = arrayOf("Bouncy", "Calm")

    // "Start over" two-tap confirm. It lives in Settings so a stray tap cannot wipe the board.
    internal val newGameConfirm   = TwoTapConfirm(1500L)
    internal val stickersConfirm  = TwoTapConfirm(1500L)
    internal val appVersion: String = try {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: ""
    } catch (_: Exception) { "" }

    // -----------------------------------------------------------------------
    // Hint
    // -----------------------------------------------------------------------
    internal var lastActionMs = SystemClock.elapsedRealtime()
    internal var hintCells    = emptyList<Pair<Int, Int>>()
    internal var hintPulseMs  = 0L
    internal var hintRandom: kotlin.random.Random = kotlin.random.Random.Default   // ties between equal hints; tests seed it

    // -----------------------------------------------------------------------
    // Touch / drag
    // -----------------------------------------------------------------------
    internal val dragChain = mutableListOf<Pair<Int, Int>>()
    /** The drag chain has closed into a ring on its first cell (see [LoopRules]); it clears as RING. */
    internal var chainLooped = false
    internal val chainPointer = ChainPointer()
    /** Drag direction for [HitZone] (unit vector in cell units, zero before the finger moves) and where it was last set. */
    internal var aimU = 0f
    internal var aimV = 0f
    internal var aimAnchorU = 0f
    internal var aimAnchorV = 0f

    // -----------------------------------------------------------------------
    // Game animation
    // -----------------------------------------------------------------------
    @Volatile internal var animPhase = AnimPhase.IDLE
    internal var animStartMs = 0L
    internal val POP_MS  = 280L
    internal val DROP_MS = 380L

    internal var powerFx: PowerUpFx? = null              // set only for a power-up clear
    internal val popCells    = mutableListOf<AnimCell>()
    internal val dropCells   = mutableListOf<AnimCell>()  // all moving cells during DROPPING
    internal val dropColMask = mutableSetOf<Int>()        // columns hidden from the normal draw loop

    // Pre-computed chain clear result (set in handleUp, consumed in advanceAnimation).
    internal var pendingResult: ChainResult? = null

    // Cascade counter — how many auto-resolve passes have fired after the player's clear.
    internal var cascadeCount    = 0
    internal var isCascade       = false

    // -----------------------------------------------------------------------
    // UI Animations
    // -----------------------------------------------------------------------

    // Settings panel slide-up
    internal var settingsOpen    = false     // logical open/close intent
    internal var settingsAnim    = 0f        // 0 = fully closed, 1 = fully open
    internal val SETTINGS_OPEN_MS  = 200f
    internal val SETTINGS_CLOSE_MS = 85f
    internal var panelAnimMs       = -1L      // time of the last panel animation step

    // Stickers panel
    internal var stickersOpen      = false
    internal var stickersAnim      = 0f
    internal var stickersBtnRect   = RectF()
    internal var stickersPressMs   = -1L
    internal var stickerPanelRect  = RectF()
    internal val stickerRects      = Array(12) { RectF() }
    internal var stickersCloseRect = RectF()
    // Stickers panel pages: 0 stickers, 1 friends (tabs show once a friend is met). Not saved.
    internal var stickersPage      = 0
    internal var stickersTabRect   = RectF()
    internal var friendsTabRect    = RectF()
    internal var friendTapIdx      = -1      // the friend doing its happy hop on the friends page
    internal var friendTapMs       = -1L
    internal var toppingsTabRect   = RectF()
    internal var toppingTapIdx     = -1      // the picker tile doing its hop
    internal var toppingTapMs      = -1L
    // The topping on every donut, from the saved choice (PLAIN if unknown or not earned)
    internal var topping: Topping  = ToppingRules.chosen(prefs.topping, prefs.toppingsEarned)
    // Seasonal looks (SeasonRules): the date's season (checked once a minute), its run id, and the look worn
    // instead of the topping (null: the topping shows). [today] is swapped in tests.
    internal var today: () -> java.time.LocalDate = { java.time.LocalDate.now() }
    internal var season: Season?   = null
    internal var seasonId          = ""
    internal var seasonCheckMs     = -1L
    internal var seasonWorn: Season? = null

    // Donut box: one slot fills every StickerRules.BOX_SLOT_DONUTS donuts popped, ever; a full box
    // is banked and a fresh one starts. It never runs out, so there is always something filling up.
    internal val donutBoxRect     = RectF()
    internal val boxStrip         = BoxStrip()   // the same box on the board screen, under the counter
    internal var boxesSeen        = -1        // full boxes at the last check; -1 until first frame
    // "Show someone!" card: pending until the sticker fly and any panel are done, then up until a tap
    internal var showSomeonePending = SHOW_NONE   // SHOW_NONE, SHOW_BOX, or a sticker index
    internal var showSomeoneKind    = SHOW_NONE
    internal var showSomeoneMs      = -1L
    internal var allStickersParty = false     // "all 12" banner waits until the sticker fly lands

    // 12 stickers — 4 rows × 3 cols
    internal val STICKER_NAMES  = arrayOf(
        "Donut Taster",   "Donut Lover",   "Donut King",
        "Chain Starter",  "Chain Champ",   "Chain Hero",
        "Little Batch",   "Big Batch",     "Donut Party",
        "Ball Finder",    "Explorer",      "Super Fan"
    )
    internal val STICKER_COLORS = intArrayOf(
        Color.rgb(255, 140,  60), Color.rgb(255, 200,  30), Color.rgb(220,  80,  50),
        Color.rgb( 70, 170, 255), Color.rgb( 90,  90, 240), Color.rgb(160,  50, 220),
        Color.rgb( 50, 200, 160), Color.rgb( 60, 190,  70), Color.rgb( 30, 130,  80),
        Color.rgb(255,  90,  90), Color.rgb(240, 110, 200), Color.rgb(160,  80, 230)
    )

    // Button press scale feedback
    internal var settingsPressMs = -1L       // time of last settings press
    internal val PRESS_MS        = 150L      // duration of press shrink

    // Counter count-up
    internal var displayedCount  = 0         // animates toward actual total

    // Digit flip — each digit flips independently when its value changes
    internal val digitFlips       = HashMap<Int, DigitFlip>()  // right-to-left digit position (0=ones)
    internal var prevDisplayCount = -1
    internal val DIGIT_FLIP_MS    = 200L

    // Counter heartbeat — panel scale-pulse when score increments
    internal var counterPulseMs   = -1L
    internal val COUNTER_PULSE_MS = 240L

    // The donut pal beside the counter (PalArt.kt); both rects laid out each frame by drawCounter
    internal val palRect       = RectF()    // the pal standing still, arms and feet included
    internal val counterDigits = RectF()    // the counter's figures, right of the pal
    internal var palReaction   = PalReaction.NONE   // what the pal is cheering about (PalMotion)
    internal var palReactMs    = -1L                // when that reaction started

    // Big-chain color flash overlay (chain ≥ 6)
    internal var chainFlashMs     = -1L
    internal var chainFlashColor  = 0
    internal val CHAIN_FLASH_MS   = 200L

    // Shuffle pop animation
    internal var shuffleAnimMs    = -1L
    internal val SHUFFLE_ANIM_MS  = 360L
    internal val SHUFFLE_MAX_DELAY = 300L   // max stagger across all cells

    // When the last full-screen flash (reset or big chain) started; FlashGate keeps them a third of a second apart
    internal var lastFullFlashMs = -1L

    // Reset flash overlay
    internal var resetFlashMs    = -1L       // time of last reset
    internal val FLASH_MS        = 350L

    // No-moves warning + auto-shuffle
    internal var noMovesWarningMs = -1L
    internal val NO_MOVES_DELAY_MS = 2400L

    // First-run tutorial
    internal var tutorialActive  = !prefs.tutorialSeen
    internal var tutorialStartMs = -1L
    internal val TUTORIAL_LOOP_MS = 3000L
    internal var tutorialCells   = emptyList<Pair<Int, Int>>()   // the three donuts the finger traces

    // Chain connection ping — scale-pop when each new cell joins
    internal val chainPings = mutableMapOf<Pair<Int,Int>, Long>()  // cell -> time added
    internal val PING_MS = 220L
    // Big center count pop
    internal var centerPingMs    = -1L

    // Near-miss feedback: a chain let go too short wobbles; a wrong donut shakes its head
    internal var wobbleCells = emptyList<Pair<Int, Int>>()
    internal var wobbleMs    = -1L
    internal val WOBBLE_MS   = 420L
    internal var shakeCell: Pair<Int, Int>? = null
    internal var shakeMs     = -1L
    internal val SHAKE_MS    = 260L
    internal var rejectedCell: Pair<Int, Int>? = null   // the wrong donut under the finger right now

    // A finger that touched the board while pieces were still moving
    internal var fingerHeld = false
    internal var heldX = 0f
    internal var heldY = 0f

    // A chain whose finger lifted and may still come back (see LiftGrace): when it lifted (-1 none) and how long it waits
    internal var liftMs = -1L
    internal var liftGraceMs = 0L

    // Milestone celebration
    internal val MILESTONES = intArrayOf(10, 25, 50, 100, 200, 500)
    internal var lastMilestone   = 0
    internal var celebrateMs     = -1L
    internal val CELEBRATE_MS    = 2600L
    internal var celebrateLabel  = ""
    internal var celebrateCheer  = ""       // second banner line ("Great job, Steven!"), or empty
    internal val bannerQueue     = BannerQueue(BANNER_FIRST_PART_MS)
    internal var praiseCount     = 0        // milestones and box fills so far; every second one cheers
    internal var physicsMs       = -1L      // last confetti physics step
    // Mutable class (not data class) so physics can update fields in-place each frame,
    // avoiding the 60-object copy + new-list allocation that a data-class copy() would incur.
    internal val particles = mutableListOf<Particle>()
    internal var celebrateLook: CelebrationLook? = null   // this (or the last) celebration's look, see CelebrationMix
    // Every random choice in the effects (confetti, looks, the tap giggle); tests and the gallery seed it
    internal var fxRandom: kotlin.random.Random = kotlin.random.Random.Default
    internal var wobbleGiggle = false       // the current tap wobble is the rare giggle: bigger and longer

    // Sprinkle party meter (PartyMeter): in the button row between the star and the gear; kept in Prefs
    internal var party          = PartyMeter.restore(prefs.partyMeter, prefs.partyLeft)
    internal val partyMeterRect = RectF()
    internal var partyShownFill = -1f       // the fill as drawn, easing toward PartyMeter.fill; -1 until the first frame
    internal var partyPopMs     = -1L       // the meter's bounce when donuts pour in or a party starts
    internal val PARTY_POP_MS   = 320L

    // Quiet drought assist (DroughtAssist): Steven's chains in a row since a long chain was last possible; not saved
    internal var droughtDry     = 0

    // Picture prompt (PicturePrompts): a soft goal, a donut and a number beside the counter; kept in Prefs
    internal var promptRandom: kotlin.random.Random = kotlin.random.Random.Default   // tests and the gallery seed it
    internal var prompt: Prompt? = null     // null until the first frame restores or picks one
    internal var promptsFinished = 0
    internal var promptSeen   = -1          // the board's cleared count of the prompt's type when last looked; -1 = look again
    internal var promptDoneMs = -1L         // when the current prompt was finished; the next one follows PROMPT_NEXT_MS later
    internal var promptPopMs  = -1L         // the chip's bounce when it counts down or a new prompt arrives
    internal val promptRect   = RectF()     // the chip, laid out each frame by layoutCounterRow; empty = hidden
    internal val PROMPT_NEXT_MS = 1800L
    internal val PROMPT_POP_MS  = 320L

    // Landing squish: the whole column squashes for a beat when a drop lands
    internal var landMs = -1L
    internal val LAND_MS = 220L
    internal val landedCols = mutableSetOf<Int>()

    // Sticker earned mid-game: it pops up on the board, then flies into the star button
    internal var earnedMask     = -1          // bitmask of earned stickers; -1 until first frame
    internal var stickerFlyMs   = -1L
    internal var stickerFlyIdx  = -1
    internal val STICKER_FLY_MS = 1200L
    internal var starPulseMs    = -1L

    // A donut friend met on a box slot fill: it pops up on the board, then flies into the star button
    internal var friendFlyMs    = -1L
    internal var friendFlyIdx   = -1
    internal val FRIEND_FLY_MS  = 1600L

    // A topping earned at a lifetime threshold: a big strawberry in it pops up on the board, then flies into the star
    internal var toppingFlyMs   = -1L
    internal var toppingFlyTop  = Topping.PLAIN
    internal var toppingFlySeason: Season? = null   // set when the fly is a season's reveal
    internal val TOPPING_FLY_MS = 1500L

    // Press feedback on panel buttons (settings options, New game, Done)
    internal var optPressRect: RectF? = null
    internal var optPressMs = -1L
    internal var panelDownHit = -2           // panel hit id under the finger at ACTION_DOWN

    // Honour the system animator scale (0 means the user asked for no animation) and the
    // grown-ups page's Calm choice: either one gives the still, reduced-motion look
    internal val systemReducedMotion: Boolean = UiScale.animationsOff(context)
    internal var reducedMotion: Boolean = systemReducedMotion || prefs.calmMotion

    internal val floatLabels = mutableListOf<FloatLabel>()
    internal val FLOAT_MS    = 1400L

    // Board entry drop-in animation
    internal var boardEntryMs  = -1L
    internal val BOARD_ENTRY_MS = 640L

    // Sound and haptic engines
    internal val soundEngine  = SoundEngine()
    internal val hapticEngine = HapticEngine(context)

    // -----------------------------------------------------------------------
    // Typeface — Fredoka One; falls back to system bold if unavailable
    // -----------------------------------------------------------------------
    internal val boldTypeface: Typeface =
        try { ResourcesCompat.getFont(context, R.font.fredoka_one) ?: Typeface.DEFAULT_BOLD }
        catch (e: Exception) { Typeface.DEFAULT_BOLD }

    // -----------------------------------------------------------------------
    // Paints (allocated once)
    // -----------------------------------------------------------------------
    internal val fillPaint         = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    internal val strokePaint       = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    internal val outlinePaint      = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
    internal val textPaint         = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; typeface = boldTypeface }
    internal val textOutlinePaint  = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeJoin = Paint.Join.ROUND; typeface = boldTypeface }
    internal val chainOutlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
    internal val chainLinePaint    = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
    internal val hintRingPaint     = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    internal val refusedPaint      = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    internal val dimPaint          = Paint().apply { color = Color.argb(160, 0, 0, 0) }
    internal val shadowPaint       = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    internal val flashPaint        = Paint().apply { style = Paint.Style.FILL }

    // -----------------------------------------------------------------------
    // Scratch objects — allocated once, reused every frame via rewind()/set().
    // NEVER allocate Path/RectF inside the render loop; use these instead.
    // -----------------------------------------------------------------------
    // scratchPath  : transient paths (chain line, sheen clip, star and gear shapes, pentagons,
    //                golden spark, anything used-then-discarded within one draw call)
    // scratchPath2 : clip paths that must survive while addSheen() runs inside them
    //                (donut glaze and ball clips, the board chain line)
    // scratchRectF : any transient RectF used for drawOval/drawRoundRect arguments
    internal val scratchPath  = Path()
    internal val scratchPath2 = Path()
    internal val scratchRectF = RectF()

    // -----------------------------------------------------------------------
    // Precomputed trigonometry — computed once at class init, never recalculated.
    // -----------------------------------------------------------------------
    // 24 points around a circle (15 degree steps) for drippy glaze and sprinkle placement
    internal val c24 = FloatArray(24) { cos(Math.toRadians(it * 15.0)).toFloat() }
    internal val s24 = FloatArray(24) { sin(Math.toRadians(it * 15.0)).toFloat() }
    // Glaze radius multiplier per point: > 1 makes a drip. Bottom of the ring is indices 3..9.
    internal val dripMul = floatArrayOf(1f,1f,1f,1.05f,1.14f,1f,1.18f,1f,1.13f,1.04f,1f,1f,1f,1f,1f,1f,1f,1f,1f,1f,1f,1f,1f,1f)
    // Six rainbow sprinkles: angle index into c24/s24, distance from centre, rotation, colour
    internal val sprinkleA   = intArrayOf(1, 5, 9, 13, 17, 21)
    internal val sprinkleD   = floatArrayOf(0.58f, 0.50f, 0.60f, 0.52f, 0.57f, 0.49f)
    internal val sprinkleRot = floatArrayOf(20f, 70f, -35f, 50f, -60f, 10f)
    internal val SPRINKLE_COLORS = intArrayOf(
        Color.WHITE, Color.rgb(255, 230, 60), Color.rgb(80, 200, 255),
        Color.rgb(120, 230, 90), Color.rgb(200, 160, 255), Color.WHITE)
    // 48 points (7.5 degree steps) with an 8-lobe scallop multiplier for the flower ring
    internal val c48 = FloatArray(48) { cos(Math.toRadians(it * 7.5)).toFloat() }
    internal val s48 = FloatArray(48) { sin(Math.toRadians(it * 7.5)).toFloat() }
    internal val scallopMul = FloatArray(48) { (0.88 + 0.12 * cos(Math.toRadians(it * 7.5 * 8.0))).toFloat() }
    // Sesame seeds on the matcha dip (x, y as fractions of r; rotation degrees)
    internal val seedX   = floatArrayOf(-0.45f, -0.08f, 0.32f, 0.56f, -0.62f)
    internal val seedY   = floatArrayOf(-0.55f, -0.68f, -0.58f, -0.32f, -0.22f)
    internal val seedRot = floatArrayOf(20f, -30f, 10f, 40f, -15f)
    // Powdered sugar on the jelly bun (x, y, radius as fractions of r)
    internal val sugarX = floatArrayOf(-0.55f, -0.30f, -0.05f, -0.42f, -0.18f, 0.15f, -0.62f)
    internal val sugarY = floatArrayOf(-0.35f, -0.62f, -0.48f, -0.05f, -0.22f, -0.70f, 0.10f)
    internal val sugarR = floatArrayOf(0.09f, 0.11f, 0.08f, 0.07f, 0.10f, 0.07f, 0.06f)
    // 5-point star (10 vertices) and 8-tooth gear (32 vertices) for the HUD icons
    internal val starCos = FloatArray(10) { cos(Math.toRadians(-90.0 + it * 36.0)).toFloat() }
    internal val starSin = FloatArray(10) { sin(Math.toRadians(-90.0 + it * 36.0)).toFloat() }
    internal val gearCos = FloatArray(32) { cos(Math.toRadians(it * 11.25)).toFloat() }
    internal val gearSin = FloatArray(32) { sin(Math.toRadians(it * 11.25)).toFloat() }
    // Sprinkles scattered behind everything: the bakery-counter feel, faint so the board stays the hero
    internal val backdropN     = 40
    internal val backdropX     = FloatArray(backdropN)
    internal val backdropY     = FloatArray(backdropN)
    internal val backdropRot   = FloatArray(backdropN)
    internal val backdropColor = IntArray(backdropN)
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
    internal val sparkCos = FloatArray(8) { cos(Math.toRadians(it * 45.0 - 90.0)).toFloat() }
    internal val sparkSin = FloatArray(8) { sin(Math.toRadians(it * 45.0 - 90.0)).toFloat() }

    // -----------------------------------------------------------------------
    // Lifecycle
    // -----------------------------------------------------------------------
    @Volatile internal var renderThread: RenderThread? = null

    init {
        holder.addCallback(this); isFocusable = true
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
        renderThread = RenderThread(this).also { it.start() }
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, w: Int, h: Int) {
        synchronized(holder) { surfaceW = w; surfaceH = h; computeLayout() }
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        // Bank this board's progress before the app goes to the background
        synchronized(holder) { saveSession() }
        // Stop the render loop when the surface goes away (e.g. the app is backgrounded), so no
        // thread spins with nothing to draw to. surfaceCreated starts a new one when we return.
        val t = renderThread; renderThread = null
        t?.running = false
        // The surface is disconnected as soon as this returns, so wait for the thread: it ends
        // only after its last frame is fully drawn (see the end of run). Never pin the UI thread
        // on the GPU though: a thread stuck in a swap ends by itself once the surface is gone.
        try { t?.join(600L) } catch (_: InterruptedException) { }
    }

    // Donuts of the current board already folded into lifetimeDonuts, so saveSession can run
    // any number of times (on background, on board-size change, on New game) without double counting.
    internal var sessionSaved = 0

    // -----------------------------------------------------------------------
    // Frame
    // -----------------------------------------------------------------------
    fun drawFrame(canvas: Canvas) {
        // Frames posted before surfaceChanged lays the board out are cream, never an empty black buffer
        canvas.drawColor(theme.bg)
        if (cellSize == 0f) return
        val now = SystemClock.elapsedRealtime()
        drawBackdrop(canvas)
        advanceLift(now)
        advanceAnimation(now)
        updateHint(now)
        advanceSettingsAnim(now)
        advanceCounter(now)
        drawHUD(canvas, now)
        advancePartyMeter(); drawPartyMeter(canvas, now)
        drawBoardBackground(canvas)
        drawCells(canvas, now)
        drawChainLine(canvas)
        drawHintTrace(canvas, now)
        drawPowerUpFx(canvas, now); drawCenterPing(canvas, now)
        drawFloatLabels(canvas, now)
        advancePrompt(now)
        drawCounter(canvas, now)
        drawPromptChip(canvas, now)
        advanceBoxStrip(now); drawBoxStrip(canvas, now)
        drawNoMovesWarning(canvas, now)
        if (celebrateMs >= 0) drawCelebration(canvas, now)
        checkStickers(now)
        advanceShowSomeone(now)
        checkFriends(now)
        checkToppings(now)
        checkSeason(now)
        if (stickerFlyMs >= 0) drawStickerFly(canvas, now)
        if (friendFlyMs >= 0) drawFriendFly(canvas, now)
        if (toppingFlyMs >= 0) drawToppingFly(canvas, now)
        if (settingsAnim > 0f) drawSettings(canvas, now)
        if (stickersAnim > 0f) drawStickersPanel(canvas, now)
        drawChainFlash(canvas, now)
        drawResetFlash(canvas, now)
        if (tutorialActive && settingsAnim == 0f && stickersAnim == 0f) drawTutorial(canvas, now)
        if (showSomeoneMs >= 0) drawShowSomeone(canvas, now)
    }

    // -----------------------------------------------------------------------
    // Sprite cache: every piece is painted once per layout into a small bitmap and
    // blitted per frame. The vector painters in DonutArt.kt are far too heavy to run 64 times
    // a frame on a software canvas (they cost ~5 fps on the emulator).
    // -----------------------------------------------------------------------
    internal var spriteR    = 0f                          // radius the sprites were painted at
    internal var spriteHalf = 0                           // half side of each sprite, px
    internal val pieceSprites = arrayOfNulls<Bitmap>(12)  // index = type.ordinal * 2 + selected
    internal val ballSprites  = arrayOfNulls<Bitmap>(2)
    internal val bitmapPaint  = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

    // -----------------------------------------------------------------------
    // Touch
    // -----------------------------------------------------------------------
    override fun onTouchEvent(event: MotionEvent): Boolean = handleTouch(event)
}
