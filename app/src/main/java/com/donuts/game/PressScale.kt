package com.donuts.game

// The press dip every chunky button shares (pure, unit tested).

object PressScale {
    /** The smallest scale, at the moment of the tap. */
    const val LOW = 0.86f

    /**
     * The scale [elapsedMs] after a tap on a button whose dip lasts [durationMs]: [LOW] at the tap,
     * easing out (quint) back to 1. A button that was never [pressed] stays at 1.
     */
    fun at(elapsedMs: Long, durationMs: Long, pressed: Boolean = true): Float {
        if (!pressed) return 1f
        val t = (elapsedMs.toFloat() / durationMs).coerceIn(0f, 1f)
        val x = 1f - t
        return LOW + 0.14f * (1f - x * x * x * x * x)
    }
}
