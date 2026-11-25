package com.donuts.game

import android.graphics.*
import android.os.SystemClock
import kotlin.math.*

// Toppings in play: wearing one, the reveal when a new one is earned, the stickers panel tabs and the picker page.
// Part of GameView: these are extension functions on it, split out of GameView.kt.

private const val TOP_STAY = 0.62f      // share of the fly spent at the board centre
private const val TOP_POP  = 0.25f      // share of the stay spent popping in
private const val TOP_TAP_MS = 500L
private val TOP_TILE_COLORS = intArrayOf(
    Color.rgb(80, 190, 255), Color.rgb(255, 120, 170), Color.rgb(70, 200, 140), Color.rgb(255, 170, 40),
    Color.rgb(170, 110, 255), Color.rgb(60, 140, 240), Color.rgb(255, 200, 40)
)

/** Puts [t] on every donut: the field, the saved choice and the sprites. Safe from touch or render thread (both hold the holder lock). */
internal fun GameView.wearTopping(t: Topping) {
    topping = t
    prefs.topping = ToppingRules.encode(t)
    buildSprites()
}

/**
 * Starts the next topping's reveal once nothing else is showing (same guards as a friend, and no friend flying).
 * The new topping is worn at once; a player far past several thresholds earns them one after another.
 */
internal fun GameView.checkToppings(now: Long) {
    if (toppingFlyMs >= 0 || friendFlyMs >= 0 || stickerFlyMs >= 0 || showSomeoneMs >= 0 || showSomeonePending != SHOW_NONE) return
    if (panelBusy() || tutorialActive) return
    val t = ToppingRules.next(displayedLifetime(), prefs.toppingsEarned) ?: return
    prefs.toppingsEarned = prefs.toppingsEarned + 1
    wearTopping(t)
    toppingFlyTop = t; toppingFlyMs = now
    hintCells = emptyList()
    if (prefs.soundEnabled)  soundEngine.playFriend(t.ordinal)
    if (prefs.hapticEnabled) hapticEngine.milestone()
}

/** A big strawberry donut in the new topping pops in at the board centre, then shrinks into the star button. */
internal fun GameView.drawToppingFly(canvas: Canvas, now: Long) {
    val t = (now - toppingFlyMs).toFloat() / TOPPING_FLY_MS
    if (t >= 1f) { toppingFlyMs = -1L; starPulseMs = now; return }
    val bx = boardLeft + board.cols * cellSize / 2f
    val by = boardTop + board.rows * cellSize / 2f
    val big = min(board.cols, board.rows) * cellSize * 0.21f
    var cx = bx; var cy = by; val r: Float
    if (t < TOP_STAY) {
        val p = t / TOP_STAY
        val grow = min(1f, p / TOP_POP)
        r = big * (if (reducedMotion) 1f else easeOutBack(grow))
        if (!reducedMotion && p > TOP_POP) cy = by - abs(sin((p - TOP_POP) / (1f - TOP_POP) * 2f * PI.toFloat())) * big * 0.14f
        fillPaint.color = Color.argb((170 * min(1f, p * 4f)).toInt(), 255, 248, 220)
        canvas.drawCircle(bx, by, big * 1.55f * (if (reducedMotion) 1f else min(1f, grow * 1.3f)), fillPaint)
        fillPaint.alpha = 255
    } else {
        val p = easeOutQuint((t - TOP_STAY) / (1f - TOP_STAY))
        cx = bx + (stickersBtnRect.centerX() - bx) * p
        cy = by + (stickersBtnRect.centerY() - by) * p
        r = big * (1f - 0.82f * p)
    }
    if (r <= 1f) return
    drawPieceVector(canvas, cx, cy, r, DonutType.STRAWBERRY, false, 255, toppingFlyTop)
    if (t < TOP_STAY) {
        val sz = 30f * u
        drawOutlinedText(canvas, "NEW TOPPING!", bx, by - big * 1.62f - sz * 0.5f, sz, Color.rgb(255, 215, 50))
        drawOutlinedText(canvas, toppingName(toppingFlyTop), bx, by + big * 1.45f + sz * 0.6f, sz, Color.WHITE)
    }
}

// ---- Stickers panel tabs ----

/** The panel pages that have a tab right now: 0 stickers, 1 friends (once one is met), 2 toppings (once one is earned). */
internal fun GameView.panelTabPages(): List<Int> {
    val pages = ArrayList<Int>(3)
    pages.add(0)
    if (prefs.friendsMet > 0) pages.add(1)
    if (prefs.toppingsEarned > 0) pages.add(2)
    return pages
}

/** The page shown: the chosen one if it has a tab, else stickers. */
internal fun GameView.panelPageNow(): Int = if (stickersPage in panelTabPages()) stickersPage else 0

/** Lays the present tabs evenly along the title row; an absent tab keeps a two-tab place (never hit while absent). */
internal fun GameView.placePanelTabs() {
    val pad = 10f * u
    val pages = panelTabPages()
    val n = max(2, pages.size)
    val tabW = (stickerPanelRect.width() - pad * 2f - pad * (n - 1)) / n
    val top = stickerPanelRect.top + 4f * u; val bot = stickerPanelRect.top + 52f * u
    fun slot(i: Int) = RectF(stickerPanelRect.left + pad + i * (tabW + pad), top, stickerPanelRect.left + pad + i * (tabW + pad) + tabW, bot)
    stickersTabRect = slot(0)
    friendsTabRect  = slot(1)
    toppingsTabRect = slot(if (2 in pages) pages.indexOf(2) else 1)
}

/** The chunky page tabs that replace the "My Stickers" title; the current one is bright. */
internal fun GameView.drawPanelTabs(canvas: Canvas, now: Long) {
    val page = panelPageNow()
    val bright = Color.rgb(255, 150, 30); val dim = Color.rgb(140, 100, 60)
    drawPrettyButton(canvas, stickersTabRect, if (page == 0) bright else dim, "Stickers", 22f * u, pressScaleFor(stickersTabRect, now))
    if (prefs.friendsMet > 0)
        drawPrettyButton(canvas, friendsTabRect, if (page == 1) bright else dim, "Friends", 22f * u, pressScaleFor(friendsTabRect, now))
    if (prefs.toppingsEarned > 0)
        drawPrettyButton(canvas, toppingsTabRect, if (page == 2) bright else dim, "Toppings", 22f * u, pressScaleFor(toppingsTabRect, now))
}

/** Tiles on the toppings page: Plain and each earned topping, in order. */
internal fun GameView.toppingTiles(): List<Topping> = ToppingRules.unlocked(prefs.toppingsEarned)

/** A tap on tile [i]: wear that topping, with a happy blip and a hop. */
internal fun GameView.tapToppingTile(i: Int) {
    val t = toppingTiles().getOrNull(i) ?: return
    wearTopping(t)
    toppingTapIdx = i; toppingTapMs = SystemClock.elapsedRealtime()
    if (prefs.soundEnabled) soundEngine.playPromptDone()
}

/** The toppings page: a strawberry donut in each earned topping with its name; the worn one has a ring and a check. */
internal fun GameView.drawToppingsPage(canvas: Canvas, now: Long) {
    val tiles = toppingTiles()
    for (i in tiles.indices) {
        if (i >= stickerRects.size) break
        val rect = stickerRects[i]
        val worn = tiles[i] == topping
        val rr = 10f * u; val g = 2.5f * u
        fillPaint.color = Color.argb(220, 28, 12, 0)
        canvas.drawRoundRect(scratchRectF.apply { set(rect.left - g, rect.top - g, rect.right + g, rect.bottom + g) }, rr + g, rr + g, fillPaint)
        fillPaint.color = TOP_TILE_COLORS[i % TOP_TILE_COLORS.size]; fillPaint.alpha = 255
        canvas.drawRoundRect(rect, rr, rr, fillPaint)
        canvas.save()
        canvas.clipRect(rect.left, rect.top, rect.right, rect.centerY())
        fillPaint.color = Color.argb(70, 255, 255, 255)
        canvas.drawRoundRect(rect, rr, rr, fillPaint)
        canvas.restore()
        if (worn) {
            strokePaint.color = Color.WHITE; strokePaint.strokeWidth = 4f * u
            canvas.drawRoundRect(scratchRectF.apply { set(rect.left - g, rect.top - g, rect.right + g, rect.bottom + g) }, rr + g, rr + g, strokePaint)
            strokePaint.alpha = 255
        }
        val side = rect.width()
        var cy = rect.centerY() - side * 0.08f
        val t = if (i == toppingTapIdx) (now - toppingTapMs).toFloat() / TOP_TAP_MS else 2f
        if (t in 0f..1f && !reducedMotion) cy -= sin(t * PI.toFloat()) * side * 0.12f
        drawPieceVector(canvas, rect.centerX(), cy, side * 0.30f, DonutType.STRAWBERRY, false, 255, tiles[i])
        if (worn) drawCheckBadge(canvas, rect.right - side * 0.16f, rect.top + side * 0.16f, side * 0.1f)
        fillPaint.alpha = 255; strokePaint.alpha = 255
        drawLabel(canvas, toppingName(tiles[i]), rect.centerX(), rect.bottom - side * 0.09f, side * 0.15f, side - 14f * u, Color.WHITE, outlined = true)
    }
}
