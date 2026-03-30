package com.blackmoondev.donuts

import android.content.Context
import android.graphics.*
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import kotlin.math.abs
import kotlin.math.min

/**
 * Custom SurfaceView that owns the render loop, draws the board, and
 * translates touch gestures into board swaps.
 *
 * Call [onRestart] to wire up a "play again" callback from the hosting Activity.
 */
class GameView(context: Context, private val board: GameBoard) :
    SurfaceView(context), SurfaceHolder.Callback {

    // -----------------------------------------------------------------------
    // Callback
    // -----------------------------------------------------------------------
    var onRestart: (() -> Unit)? = null

    // -----------------------------------------------------------------------
    // Render thread
    // -----------------------------------------------------------------------
    private var thread: RenderThread? = null

    // -----------------------------------------------------------------------
    // Layout geometry (computed in surfaceChanged)
    // -----------------------------------------------------------------------
    private var cellSize = 0f
    private var boardLeft = 0f
    private var boardTop = 0f
    private val hudHeight = 160f   // px reserved above the grid for HUD

    // -----------------------------------------------------------------------
    // Touch state
    // -----------------------------------------------------------------------
    private var touchStartX = 0f
    private var touchStartY = 0f
    private var selRow = -1
    private var selCol = -1

    // -----------------------------------------------------------------------
    // Paint objects (allocated once, reused every frame)
    // -----------------------------------------------------------------------
    private val fillPaint   = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val textPaint   = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style    = Paint.Style.FILL
        typeface = Typeface.DEFAULT_BOLD
        color    = Color.rgb(80, 40, 0)
    }
    private val overlayPaint = Paint().apply { color = Color.argb(200, 0, 0, 0) }

    // -----------------------------------------------------------------------
    // Lifecycle
    // -----------------------------------------------------------------------
    init {
        holder.addCallback(this)
        isFocusable = true
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        thread = RenderThread(holder).apply { start() }
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, w: Int, h: Int) {
        val boardPx = min(w.toFloat(), h - hudHeight) * 0.96f
        cellSize  = boardPx / board.cols
        boardLeft = (w - boardPx) / 2f
        boardTop  = hudHeight + (h - hudHeight - boardPx) / 2f
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        thread?.let {
            it.running = false
            it.join()
        }
    }

    // -----------------------------------------------------------------------
    // Drawing
    // -----------------------------------------------------------------------

    fun drawFrame(canvas: Canvas) {
        // Background
        canvas.drawColor(Color.rgb(255, 240, 220))

        if (cellSize == 0f) return   // geometry not ready yet

        drawHUD(canvas)
        drawBoardBackground(canvas)
        drawCells(canvas)

        if (board.isGameOver || board.hasWon) drawOverlay(canvas)
    }

    private fun drawHUD(canvas: Canvas) {
        val cx = width / 2f

        // Title
        textPaint.textSize  = 52f
        textPaint.textAlign = Paint.Align.CENTER
        canvas.drawText("DONUTS", cx, 60f, textPaint)

        // Score (left)
        textPaint.textSize  = 36f
        textPaint.textAlign = Paint.Align.LEFT
        canvas.drawText("Score: ${board.score}", boardLeft, 120f, textPaint)

        // Moves (right)
        textPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText("Moves: ${board.movesLeft}", boardLeft + board.cols * cellSize, 120f, textPaint)

        // Target score hint (center)
        textPaint.textSize  = 26f
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.color     = Color.rgb(160, 80, 20)
        canvas.drawText("Target: ${board.targetScore}", cx, 148f, textPaint)
        textPaint.color     = Color.rgb(80, 40, 0)
    }

    private fun drawBoardBackground(canvas: Canvas) {
        fillPaint.color = Color.rgb(245, 210, 165)
        val r = RectF(
            boardLeft - 6f, boardTop - 6f,
            boardLeft + board.cols * cellSize + 6f,
            boardTop + board.rows * cellSize + 6f
        )
        canvas.drawRoundRect(r, 18f, 18f, fillPaint)
    }

    private fun drawCells(canvas: Canvas) {
        for (r in 0 until board.rows) {
            for (c in 0 until board.cols) {
                val cx = boardLeft + c * cellSize + cellSize / 2f
                val cy = boardTop  + r * cellSize + cellSize / 2f
                val selected = (r == selRow && c == selCol)
                drawDonut(canvas, cx, cy, cellSize * 0.44f, board.grid[r][c].type, selected)
            }
        }
    }

    /**
     * Draws a single donut: outer body ring + glaze top + optional sprinkles + hole.
     */
    private fun drawDonut(
        canvas: Canvas,
        cx: Float, cy: Float,
        radius: Float,
        type: DonutType,
        selected: Boolean
    ) {
        val outerR  = radius
        val glazeR  = radius * 0.84f
        val innerR  = radius * 0.36f

        // Selection ring
        if (selected) {
            strokePaint.color       = Color.WHITE
            strokePaint.strokeWidth = cellSize * 0.07f
            canvas.drawCircle(cx, cy, outerR + cellSize * 0.06f, strokePaint)
        }

        // Body (ring colour)
        fillPaint.color = type.bodyColor
        canvas.drawCircle(cx, cy, outerR, fillPaint)

        // Glaze top
        fillPaint.color = type.glazeColor
        canvas.drawCircle(cx, cy, glazeR, fillPaint)

        // Sprinkles for visual variety
        drawSprinkles(canvas, cx, cy, glazeR, type)

        // Hole
        fillPaint.color = Color.rgb(255, 240, 220)
        canvas.drawCircle(cx, cy, innerR, fillPaint)
    }

    private fun drawSprinkles(
        canvas: Canvas,
        cx: Float, cy: Float,
        glazeR: Float,
        type: DonutType
    ) {
        val sprinkleColor = when (type) {
            DonutType.STRAWBERRY -> Color.WHITE
            DonutType.VANILLA    -> Color.rgb(255, 120, 160)
            DonutType.CHOCOLATE  -> Color.rgb(220, 180, 120)
            DonutType.BLUEBERRY  -> Color.WHITE
            DonutType.MATCHA     -> Color.rgb(255, 235, 150)
            DonutType.CARAMEL    -> Color.WHITE
        }

        // 5 tiny dots in a loose star pattern around the glaze centre
        fillPaint.color = sprinkleColor
        val dotR  = glazeR * 0.09f
        val dist  = glazeR * 0.48f
        for (i in 0 until 5) {
            val angle = Math.toRadians(i * 72.0 + 18.0)
            val sx = cx + dist * Math.cos(angle).toFloat()
            val sy = cy + dist * Math.sin(angle).toFloat()
            canvas.drawCircle(sx, sy, dotR, fillPaint)
        }
    }

    private fun drawOverlay(canvas: Canvas) {
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), overlayPaint)

        val cx = width / 2f
        val cy = height / 2f

        // Title
        textPaint.color     = if (board.hasWon) Color.rgb(255, 215, 0) else Color.WHITE
        textPaint.textSize  = 90f
        textPaint.textAlign = Paint.Align.CENTER
        canvas.drawText(if (board.hasWon) "YOU WIN!" else "GAME OVER", cx, cy - 60f, textPaint)

        // Score
        textPaint.color    = Color.WHITE
        textPaint.textSize = 54f
        canvas.drawText("Score: ${board.score}", cx, cy + 20f, textPaint)

        // Tap to retry prompt
        textPaint.textSize = 38f
        textPaint.color    = Color.rgb(200, 200, 200)
        canvas.drawText("Tap to play again", cx, cy + 100f, textPaint)

        textPaint.textAlign = Paint.Align.LEFT
        textPaint.color     = Color.rgb(80, 40, 0)
    }

    // -----------------------------------------------------------------------
    // Touch handling
    // -----------------------------------------------------------------------

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> handleDown(event)
            MotionEvent.ACTION_UP   -> handleUp(event)
        }
        return true
    }

    private fun handleDown(event: MotionEvent) {
        touchStartX = event.x
        touchStartY = event.y

        if (board.isGameOver || board.hasWon) return

        val col = cellCol(event.x)
        val row = cellRow(event.y)
        if (inBounds(row, col)) {
            selRow = row
            selCol = col
        }
    }

    private fun handleUp(event: MotionEvent) {
        // Restart on game-over tap
        if (board.isGameOver || board.hasWon) {
            board.reset()
            selRow = -1; selCol = -1
            onRestart?.invoke()
            return
        }

        val dx = event.x - touchStartX
        val dy = event.y - touchStartY
        val minSwipe = cellSize * 0.28f

        if (selRow >= 0 && (abs(dx) > minSwipe || abs(dy) > minSwipe)) {
            val (targetRow, targetCol) = if (abs(dx) > abs(dy)) {
                Pair(selRow, selCol + if (dx > 0) 1 else -1)
            } else {
                Pair(selRow + if (dy > 0) 1 else -1, selCol)
            }

            if (inBounds(targetRow, targetCol)) {
                if (board.swap(selRow, selCol, targetRow, targetCol)) {
                    board.resolveAll()
                }
            }
        }

        selRow = -1
        selCol = -1
    }

    private fun cellCol(x: Float) = ((x - boardLeft) / cellSize).toInt()
    private fun cellRow(y: Float) = ((y - boardTop)  / cellSize).toInt()
    private fun inBounds(r: Int, c: Int) =
        r in 0 until board.rows && c in 0 until board.cols

    // -----------------------------------------------------------------------
    // Render thread
    // -----------------------------------------------------------------------

    inner class RenderThread(private val holder: SurfaceHolder) : Thread("GameRenderThread") {
        @Volatile var running = true

        override fun run() {
            while (running) {
                val canvas = holder.lockCanvas() ?: continue
                try {
                    synchronized(holder) { drawFrame(canvas) }
                } finally {
                    holder.unlockCanvasAndPost(canvas)
                }
                sleep(16L)   // ~60 fps cap
            }
        }
    }
}
