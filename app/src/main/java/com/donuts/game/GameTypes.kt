package com.donuts.game

import android.graphics.RectF

// Small value types used by GameView and its extension files.

internal enum class AnimPhase { IDLE, POPPING, DROPPING }

internal data class AnimCell(
    val row: Int, val col: Int, val type: DonutType,
    val fromRow: Int = row, val isGolden: Boolean = false,
    val delayMs: Long = 0L            // pop starts this long after animStartMs (power-ups)
)

/** A power-up in flight: which cells have already popped and when the whole show ends. */
internal class PowerUpFx(
    val kind: PowerUp, val originRow: Int, val originCol: Int,
    val color: Int, val startMs: Long, val totalMs: Long
) { val popped = mutableSetOf<Pair<Int, Int>>() }

internal data class DigitFlip(val from: Char, val to: Char, val startMs: Long)

internal class Particle(
    var x: Float, var y: Float,
    var vx: Float, var vy: Float,
    val color: Int, val radius: Float,
    val rotSpeed: Float, val ring: Boolean = false, var rot: Float = 0f
)

internal data class FloatLabel(
    val text: String, val cx: Float, val cy: Float,
    val color: Int, val startMs: Long
)

/** The donut-box strip under the counter: where it sits and its pop timers. */
internal class BoxStrip {
    val rect = RectF()
    var shownLifetime = -1      // lifetime donuts as the player sees them; -1 until the first frame
    var slotPopMs = -1L
    var boxPopMs = -1L
}
