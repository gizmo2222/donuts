package com.donuts.game

import android.graphics.*
import kotlin.math.*

// Meeting a donut friend: each box slot fill reveals the next one, in order, and it flies into the star.
// Part of GameView: these are extension functions on it, split out of GameView.kt.

// Share of the fly spent at the board centre (pop in, then hop); the rest is the trip to the star
private const val FRIEND_STAY = 0.62f
private const val FRIEND_POP  = 0.25f      // share of the stay spent popping in

/**
 * Starts the next friend's reveal once nothing else is showing: no fly, no Show someone card, no panel, no
 * tutorial. One friend per reveal; a player far past several slots meets them one after another.
 */
internal fun GameView.checkFriends(now: Long) {
    if (friendFlyMs >= 0 || toppingFlyMs >= 0 || stickerFlyMs >= 0 || showSomeoneMs >= 0 || showSomeonePending != SHOW_NONE) return
    if (panelBusy() || tutorialActive) return
    val idx = FriendRules.next(displayedLifetime(), prefs.friendsMet, DRAWN) ?: return
    prefs.friendsMet = idx + 1
    friendFlyIdx = idx; friendFlyMs = now
    hintCells = emptyList()
    if (prefs.soundEnabled)  soundEngine.playFriend(idx)
    if (prefs.hapticEnabled) hapticEngine.milestone()
}

/** The new friend pops in big at the board centre, hops for joy, then shrinks into the star button. */
internal fun GameView.drawFriendFly(canvas: Canvas, now: Long) {
    val t = (now - friendFlyMs).toFloat() / FRIEND_FLY_MS
    if (t >= 1f) { friendFlyMs = -1L; starPulseMs = now; return }
    val bx = boardLeft + board.cols * cellSize / 2f
    val by = boardTop + board.rows * cellSize / 2f
    val big = min(board.cols, board.rows) * cellSize * 0.21f
    var cx = bx; var cy = by; val r: Float
    var sx = 1f; var sy = 1f
    if (t < FRIEND_STAY) {
        val p = t / FRIEND_STAY
        val grow = min(1f, p / FRIEND_POP)
        r = big * (if (reducedMotion) 1f else easeOutBack(grow))
        if (!reducedMotion && p > FRIEND_POP) {
            // Two happy hops, squashing a little as he lands and stretching as he rises
            val h = abs(sin((p - FRIEND_POP) / (1f - FRIEND_POP) * 2f * PI.toFloat()))
            cy = by - h * big * 0.16f
            val ease = min(1f, min(p - FRIEND_POP, 1f - p) / 0.06f)     // no jump into or out of the hops
            val land = (1f - h).pow(4) * ease
            sx = 1f + 0.10f * land - 0.04f * h; sy = 1f - 0.10f * land + 0.05f * h
        }
        // A warm glow behind him and a gold ring bursting outward
        fillPaint.color = Color.argb((170 * min(1f, p * 4f)).toInt(), 255, 248, 220)
        canvas.drawCircle(bx, by, big * 1.55f * (if (reducedMotion) 1f else min(1f, grow * 1.3f)), fillPaint)
        if (p < 0.5f) {
            val q = min(1f, p / 0.5f)
            strokePaint.color = Color.argb(((1f - q) * 220).toInt(), 255, 205, 40); strokePaint.strokeWidth = 6f * u
            canvas.drawCircle(bx, by, big * (0.7f + q * 1.3f), strokePaint)
        }
        fillPaint.alpha = 255; strokePaint.alpha = 255
    } else {
        val p = easeOutQuint((t - FRIEND_STAY) / (1f - FRIEND_STAY))
        cx = bx + (stickersBtnRect.centerX() - bx) * p
        cy = by + (stickersBtnRect.centerY() - by) * p
        r = big * (1f - 0.82f * p)
    }
    if (r <= 1f) return
    canvas.save()
    canvas.scale(sx, sy, cx, cy + r * 1.18f)       // squash about his bottom edge
    drawFriend(canvas, friendFlyIdx, cx, cy, r)
    canvas.restore()
    if (t < FRIEND_STAY) {
        val sz = 30f * u
        drawOutlinedText(canvas, "NEW FRIEND!", bx, by - big * 1.62f - sz * 0.5f, sz, Color.rgb(255, 215, 50))
        val name = FriendRules.FRIENDS.getOrNull(friendFlyIdx)?.name ?: return
        drawOutlinedText(canvas, name, bx, by + big * 1.45f + sz * 0.6f, sz, Color.WHITE)
    }
}

// One tile colour per friend, each setting him off: 4 Choco mint, 5 Kicky grass, 6 Berry sun, 7 Gloop magenta,
// 8 Zoomy sky, 9 Puff teal, 10 Fizz orange, 11 Captain Glaze sea blue
private val FRIEND_TILE_COLORS = intArrayOf(
    Color.rgb(255, 120, 170), Color.rgb(80, 190, 255), Color.rgb(255, 170, 40), Color.rgb(170, 110, 255),
    Color.rgb(70, 200, 140), Color.rgb(60, 170, 60), Color.rgb(255, 200, 40), Color.rgb(230, 60, 160),
    Color.rgb(60, 140, 240), Color.rgb(30, 180, 190), Color.rgb(255, 130, 40), Color.rgb(50, 90, 200)
)
private const val FRIEND_TAP_MS = 500L

/** The friends page: the met friends (up to [DRAWN]) on the first sticker tiles, each with its name. Nothing for the rest. */
internal fun GameView.drawFriendsPage(canvas: Canvas, now: Long) {
    val met = min(prefs.friendsMet, DRAWN)
    for (i in 0 until met) {
        val rect = stickerRects[i]
        val rr = 10f * u; val g = 2.5f * u
        fillPaint.color = ink(220)
        canvas.drawRoundRect(scratchRectF.apply { set(rect.left - g, rect.top - g, rect.right + g, rect.bottom + g) }, rr + g, rr + g, fillPaint)
        fillPaint.color = FRIEND_TILE_COLORS[i % FRIEND_TILE_COLORS.size]; fillPaint.alpha = 255
        canvas.drawRoundRect(rect, rr, rr, fillPaint)
        canvas.save()
        canvas.clipRect(rect.left, rect.top, rect.right, rect.centerY())
        fillPaint.color = Color.argb(70, 255, 255, 255)
        canvas.drawRoundRect(rect, rr, rr, fillPaint)
        canvas.restore()
        val side = rect.width()
        var cy = rect.centerY() - side * 0.08f
        var sx = 1f; var sy = 1f
        val t = if (i == friendTapIdx) (now - friendTapMs).toFloat() / FRIEND_TAP_MS else 2f
        if (t in 0f..1f && !reducedMotion) {
            val h = sin(t * PI.toFloat())                    // one hop up and down
            cy -= h * side * 0.14f
            val land = (1f - h).pow(4) * min(1f, min(t, 1f - t) / 0.12f)
            sx = 1f + 0.12f * land - 0.05f * h; sy = 1f - 0.12f * land + 0.06f * h
        }
        val r = side * 0.27f
        canvas.save()
        canvas.scale(sx, sy, rect.centerX(), cy + r * 1.18f)
        drawFriend(canvas, i, rect.centerX(), cy, r)
        canvas.restore()
        fillPaint.alpha = 255; strokePaint.alpha = 255
        val name = FriendRules.FRIENDS.getOrNull(i)?.name ?: continue
        drawLabel(canvas, name, rect.centerX(), rect.bottom - side * 0.09f, side * 0.15f, side - 14f * u, Color.WHITE, outlined = true)
    }
}
