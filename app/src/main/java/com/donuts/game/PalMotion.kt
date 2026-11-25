package com.donuts.game

import kotlin.math.PI
import kotlin.math.max
import kotlin.math.sin

/**
 * The donut pal's idle pose at a moment: [bob] lifts the body (a share of its radius, 0 = feet down),
 * [squashX]/[squashY] squash it on landing and stretch it at the top, [blink] is how shut the eyes are
 * (0 open, 1 shut) and [look] where they glance (-1 left .. 1 right).
 */
data class PalPose(val bob: Float, val squashX: Float, val squashY: Float, val blink: Float, val look: Float)

/** The pal's idle bob, squash, blink and glance from the clock alone (pure, unit tested). */
object PalMotion {
    const val BOB_MS = 1400L          // one hop up and back down
    const val BOB_HEIGHT = 0.16f      // highest lift, as a share of the body radius
    const val SQUASH = 0.08f          // widest squash on landing
    const val BLINK_EVERY_MS = 3300L
    const val BLINK_MS = 150L
    const val LOOK_MS = 5200L         // one slow glance left and right

    /** Standing still with eyes open: the pose under reduced motion (B23b gives it a proper still pose). */
    val STILL = PalPose(0f, 1f, 1f, 0f, 0f)

    fun at(nowMs: Long, reducedMotion: Boolean): PalPose {
        if (reducedMotion || nowMs < 0L) return STILL
        // A soft hop: |sin| lifts and lands, so the landing has a little snap
        val t = (nowMs % BOB_MS).toFloat() / BOB_MS
        val lift = sin(t * PI.toFloat())                 // 0 at both ends, 1 at the top
        // Squash in the last and first tenth of the hop (the feet are down), stretch near the top
        val land = max(0f, 1f - lift / 0.30f)            // 1 on the floor, 0 once off it
        val sx = 1f + SQUASH * land - SQUASH * 0.5f * lift
        val sy = 1f - SQUASH * land + SQUASH * 0.5f * lift
        val b = (nowMs % BLINK_EVERY_MS).toFloat()
        val blink = if (b < BLINK_MS) sin(b / BLINK_MS * PI.toFloat()) else 0f
        val look = sin((nowMs % LOOK_MS).toFloat() / LOOK_MS * 2f * PI.toFloat())
        return PalPose(BOB_HEIGHT * lift, sx, sy, blink, look)
    }
}
