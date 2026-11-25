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
    if (friendFlyMs >= 0 || stickerFlyMs >= 0 || showSomeoneMs >= 0 || showSomeonePending != SHOW_NONE) return
    if (panelBusy() || tutorialActive) return
    val idx = FriendRules.next(displayedLifetime(), prefs.friendsMet, DRAWN) ?: return
    prefs.friendsMet = idx + 1
    friendFlyIdx = idx; friendFlyMs = now
    hintCells = emptyList()
    if (prefs.soundEnabled)  soundEngine.playMilestone()
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
