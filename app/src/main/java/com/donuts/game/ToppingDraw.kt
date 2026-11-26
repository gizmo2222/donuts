package com.donuts.game

import android.graphics.*
import android.os.SystemClock
import kotlin.math.*

// Toppings in play: wearing one, the reveal when a new one is earned, the stickers panel tabs and the picker page.
// Part of GameView: these are extension functions on it, split out of GameView.kt.

private const val TOP_STAY = 0.62f      // share of the fly spent at the board centre
private const val TOP_POP  = 0.25f      // share of the stay spent popping in
private const val TOP_TAP_MS = 500L
private const val SEASON_CHECK_MS = 60_000L   // how often the date is looked at
private val SEASON_TILE_COLOR = Color.rgb(240, 90, 60)
private val TOP_TILE_COLORS = intArrayOf(
    Color.rgb(80, 190, 255), Color.rgb(255, 120, 170), Color.rgb(70, 200, 140), Color.rgb(255, 170, 40),
    Color.rgb(170, 110, 255), Color.rgb(60, 140, 240), Color.rgb(255, 200, 40)
)

/** Puts [t] on every donut: the field, the saved choice and the sprites. Safe from touch or render thread (both hold the holder lock). */
internal fun GameView.wearTopping(t: Topping) {
    topping = t
    prefs.topping = ToppingRules.encode(t)
    prefs.seasonLook = ""
    seasonWorn = null
    buildSprites()
}

/** Puts seasonal look [s] on every donut, over (not instead of) his saved topping. */
internal fun GameView.wearSeason(s: Season) {
    seasonWorn = s
    prefs.seasonLook = s.key
    buildSprites()
}

/**
 * Looks at the date about once a minute: which season it is, and whether its look is still worn (the donuts go back
 * to his topping when it ends). The first time a season comes round each year it is revealed like a new topping and
 * put on, once nothing else is showing.
 */
internal fun GameView.checkSeason(now: Long) {
    if (seasonCheckMs < 0 || now - seasonCheckMs >= SEASON_CHECK_MS) {
        seasonCheckMs = now
        val d = today()
        season = SeasonRules.at(d.monthValue, d.dayOfMonth)
        seasonId = season?.let { SeasonRules.id(it, d.year, d.monthValue) } ?: ""
        val w = SeasonRules.worn(prefs.seasonLook, season)
        if (w != seasonWorn) { seasonWorn = w; buildSprites() }
    }
    val s = season ?: return
    if (prefs.seasonSeen == seasonId) return
    if (toppingFlyMs >= 0 || friendFlyMs >= 0 || stickerFlyMs >= 0 || showSomeoneMs >= 0 || showSomeonePending != SHOW_NONE) return
    if (panelBusy() || tutorialActive) return
    prefs.seasonSeen = seasonId
    wearSeason(s)
    toppingFlySeason = s; toppingFlyMs = now
    hintCells = emptyList()
    if (prefs.soundEnabled)  soundEngine.playFriend(s.ordinal)
    if (prefs.hapticEnabled) hapticEngine.milestone()
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
    toppingFlyTop = t; toppingFlySeason = null; toppingFlyMs = now
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
    val look = toppingFlySeason
    drawPieceVector(canvas, cx, cy, r, DonutType.STRAWBERRY, false, 255, toppingFlyTop, look)
    if (t < TOP_STAY) {
        val sz = 30f * u
        val head = if (look != null) seasonCheer(look) else "NEW TOPPING!"
        val name = if (look != null) seasonName(look) else toppingName(toppingFlyTop)
        drawOutlinedText(canvas, head, bx, by - big * 1.62f - sz * 0.5f, sz, Color.rgb(255, 215, 50))
        drawOutlinedText(canvas, name, bx, by + big * 1.45f + sz * 0.6f, sz, Color.WHITE)
    }
}

// ---- Stickers panel tabs ----

/** True when the toppings tab shows: a topping is earned, or a season's look is on offer. */
internal fun GameView.hasToppingsTab(): Boolean = prefs.toppingsEarned > 0 || season != null

/** The panel pages that have a tab right now: 0 stickers, 1 friends (once one is met), 2 toppings (see [hasToppingsTab]). */
internal fun GameView.panelTabPages(): List<Int> {
    val pages = ArrayList<Int>(3)
    pages.add(0)
    if (prefs.friendsMet > 0) pages.add(1)
    if (hasToppingsTab()) pages.add(2)
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
    if (hasToppingsTab())
        drawPrettyButton(canvas, toppingsTabRect, if (page == 2) bright else dim, "Toppings", 22f * u, pressScaleFor(toppingsTabRect, now))
}

/** Tiles on the toppings page: Plain and each earned topping, in order. */
internal fun GameView.toppingTiles(): List<Topping> = ToppingRules.unlocked(prefs.toppingsEarned)

/** How many tiles the toppings page has: the toppings, then the season's look while one lasts. */
internal fun GameView.pickerTileCount(): Int = toppingTiles().size + (if (season != null) 1 else 0)

/** A tap on tile [i]: wear that topping (or the season's look, the last tile), with a happy blip and a hop. */
internal fun GameView.tapToppingTile(i: Int) {
    val tiles = toppingTiles()
    val s = season
    if (i == tiles.size && s != null) wearSeason(s)
    else wearTopping(tiles.getOrNull(i) ?: return)
    toppingTapIdx = i; toppingTapMs = SystemClock.elapsedRealtime()
    if (prefs.soundEnabled) soundEngine.playPromptDone()
}

/** The toppings page: a strawberry donut in each earned topping with its name; the worn one has a ring and a check. */
internal fun GameView.drawToppingsPage(canvas: Canvas, now: Long) {
    val tiles = toppingTiles()
    for (i in 0 until pickerTileCount()) {
        if (i >= stickerRects.size) break
        val rect = stickerRects[i]
        val look = if (i < tiles.size) null else season
        val worn = if (look != null) seasonWorn == look else seasonWorn == null && tiles[i] == topping
        val rr = 10f * u; val g = 2.5f * u
        fillPaint.color = ink(220)
        canvas.drawRoundRect(scratchRectF.apply { set(rect.left - g, rect.top - g, rect.right + g, rect.bottom + g) }, rr + g, rr + g, fillPaint)
        fillPaint.color = if (look != null) SEASON_TILE_COLOR else TOP_TILE_COLORS[i % TOP_TILE_COLORS.size]; fillPaint.alpha = 255
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
        drawPieceVector(canvas, rect.centerX(), cy, side * 0.30f, DonutType.STRAWBERRY, false, 255, if (look != null) topping else tiles[i], look)
        if (worn) drawCheckBadge(canvas, rect.right - side * 0.16f, rect.top + side * 0.16f, side * 0.1f)
        fillPaint.alpha = 255; strokePaint.alpha = 255
        drawLabel(canvas, if (look != null) seasonName(look) else toppingName(tiles[i]), rect.centerX(), rect.bottom - side * 0.09f, side * 0.15f, side - 14f * u, Color.WHITE, outlined = true)
    }
}
