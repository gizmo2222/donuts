package com.donuts.game

import kotlin.math.PI
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * The donut pal's pose at a moment: [bob] lifts the body (a share of its radius, 0 = feet down),
 * [squashX]/[squashY] squash it on landing and stretch it at the top, [blink] is how shut the eyes are
 * (0 open, 1 shut), [look] where they glance (-1 left .. 1 right), [arms] how high the arms are thrown
 * (0 resting, 1 up in a cheer), [joy] how big the smile is (past [PalMotion.HAPPY_EYES] the eyes close
 * into happy arcs) and [tilt] a happy shimmy in degrees about the body's centre.
 */
data class PalPose(val bob: Float, val squashX: Float, val squashY: Float, val blink: Float, val look: Float,
                   val arms: Float = 0f, val joy: Float = 0f, val tilt: Float = 0f)

/**
 * How the pal answers what Steven just did, weakest first: [SMILE] a chain of three, [CHEER] a chain of
 * four, [WOW] any power-up (five and up, or a ring), [PARTY] a milestone, a full box or a sprinkle party.
 * Each lasts [ms] with [hops] hops up to [jump] (a share of the body radius), arms up to [arms], a smile
 * of [joy] and a shimmy of [wiggle] degrees.
 */
enum class PalReaction(val ms: Long, val hops: Int, val jump: Float, val arms: Float, val joy: Float, val wiggle: Float) {
    NONE(0L, 0, 0f, 0f, 0f, 0f),
    SMILE(500L, 1, 0.22f, 0.35f, 0.45f, 0f),
    CHEER(800L, 2, 0.32f, 1f, 0.75f, 3f),
    WOW(900L, 2, 0.45f, 1f, 1f, 5f),
    PARTY(1500L, 3, 0.50f, 1f, 1f, 6f),
}

/** The pal's idle bob, blink and glance from the clock, and its reactions on top (pure, unit tested). */
object PalMotion {
    const val BOB_MS = 1400L          // one hop up and back down
    const val BOB_HEIGHT = 0.16f      // highest idle lift, as a share of the body radius
    const val SQUASH = 0.08f          // widest squash on an idle landing
    const val REACT_SQUASH = 0.09f    // widest squash on a reaction landing
    const val BLINK_EVERY_MS = 3300L
    const val BLINK_MS = 150L
    const val LOOK_MS = 5200L         // one slow glance left and right
    const val HAPPY_EYES = 0.6f       // a smile past this closes the eyes into happy arcs
    const val RISE = 0.12f            // share of a reaction spent throwing the arms up
    const val FALL = 0.25f            // share spent letting them down again

    /** The highest any pose lifts the pal, reactions included. */
    val MAX_LIFT = max(BOB_HEIGHT, PalReaction.values().maxOf { it.jump })
    /** The most any pose stretches the pal upward. */
    val MAX_STRETCH = 1f + max(SQUASH, REACT_SQUASH) * 0.5f
    /** The widest shimmy, in degrees. */
    val MAX_TILT = PalReaction.values().maxOf { it.wiggle }

    /**
     * The pose under reduced motion: feet down, eyes open and turned toward the number beside it, a warm
     * smile, arms resting. It never moves; a reaction only swaps in its arms and smile while it lasts.
     */
    val STILL = PalPose(0f, 1f, 1f, 0f, 0.5f, arms = 0f, joy = 0.25f, tilt = 0f)

    /** What the pal does for a released chain of [chainLen] donuts that clears with [powerUp]. */
    fun reactionFor(chainLen: Int, powerUp: PowerUp): PalReaction = when {
        powerUp != PowerUp.NONE -> PalReaction.WOW
        chainLen >= 4 -> PalReaction.CHEER
        chainLen == 3 -> PalReaction.SMILE
        else -> PalReaction.NONE
    }

    /** Whether [next] should start now over [current] (started at [startMs]): a weaker one never cuts a stronger one short. */
    fun replaces(current: PalReaction, startMs: Long, next: PalReaction, nowMs: Long): Boolean =
        next != PalReaction.NONE && (!running(current, startMs, nowMs) || next.ordinal >= current.ordinal)

    fun running(reaction: PalReaction, startMs: Long, nowMs: Long): Boolean =
        reaction != PalReaction.NONE && startMs >= 0L && nowMs >= startMs && nowMs - startMs < reaction.ms

    fun at(nowMs: Long, reducedMotion: Boolean, reaction: PalReaction = PalReaction.NONE, reactMs: Long = -1L): PalPose {
        val active = running(reaction, reactMs, nowMs)
        if (reducedMotion || nowMs < 0L) {
            return if (active) STILL.copy(arms = reaction.arms, joy = max(STILL.joy, reaction.joy)) else STILL
        }
        val idle = idle(nowMs)
        if (!active) return idle
        val p = (nowMs - reactMs).toFloat() / reaction.ms
        // Arms, smile and shimmy rise fast, hold, then ease back into the idle pose
        val e = smooth(min(p / RISE, (1f - p) / FALL).coerceIn(0f, 1f))
        val hopsDone = p * reaction.hops
        val n = hopsDone.toInt()
        val frac = hopsDone - n
        val liftN = sin(frac * PI.toFloat())                       // 0 at each landing, 1 at each top
        val lift = reaction.jump * liftN * (1f - 0.25f * n / reaction.hops)
        val land = max(0f, 1f - liftN / 0.30f)
        val rx = 1f + REACT_SQUASH * land - REACT_SQUASH * 0.5f * liftN
        val ry = 1f - REACT_SQUASH * land + REACT_SQUASH * 0.5f * liftN
        return PalPose(
            bob = max(lift, idle.bob * (1f - e)),
            squashX = idle.squashX + (rx - idle.squashX) * e,
            squashY = idle.squashY + (ry - idle.squashY) * e,
            blink = idle.blink * (1f - e),                         // eyes wide while it cheers
            look = idle.look * (1f - e),                           // and on Steven
            arms = reaction.arms * e,
            joy = reaction.joy * e,
            tilt = reaction.wiggle * e * sin(hopsDone * 2f * PI.toFloat()))
    }

    private fun idle(nowMs: Long): PalPose {
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

    private fun smooth(x: Float) = x * x * (3f - 2f * x)
}
