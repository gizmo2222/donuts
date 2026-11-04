package com.donuts.game

// Small value types used by GameView and its extension files.

internal enum class AnimPhase { IDLE, POPPING, DROPPING }

internal data class AnimCell(
    val row: Int, val col: Int, val type: DonutType,
    val fromRow: Int = row, val isGolden: Boolean = false
)

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
