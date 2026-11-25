package com.donuts.game

import kotlin.random.Random

/**
 * A soft goal shown as a picture: pop [goal] donuts of the [type]-th [DonutType]; [done] are popped so far.
 * There is no timer and nothing is lost when Steven ignores it.
 */
data class Prompt(val type: Int, val goal: Int, val done: Int = 0) {
    val left: Int get() = (goal - done).coerceAtLeast(0)
    val complete: Boolean get() = done >= goal
}

/** Which prompt comes next, how pops count toward it, and its text form for `Prefs` (pure, unit tested). */
object PicturePrompts {
    /** The very first prompt is the easiest: one chain of three. */
    const val FIRST_GOAL = 3
    const val MIN_GOAL = 3
    const val MAX_GOAL = 6

    /** A new prompt for a type other than [lastType] (-1 for none), easy at first, then 3 to 6 donuts. */
    fun next(random: Random, lastType: Int, typeCount: Int, finished: Int): Prompt {
        val choices = (0 until typeCount).filter { it != lastType }
        val type = choices[random.nextInt(choices.size)]
        val goal = if (finished == 0) FIRST_GOAL else random.nextInt(MIN_GOAL, MAX_GOAL + 1)
        return Prompt(type, goal)
    }

    /** [p] after [popped] more donuts of its type; never past the goal, and a finished prompt stays as it is. */
    fun after(p: Prompt, popped: Int): Prompt =
        if (popped <= 0 || p.complete) p else p.copy(done = (p.done + popped).coerceAtMost(p.goal))

    fun encode(p: Prompt, finished: Int): String = "p1;${p.type};${p.goal};${p.done};$finished"

    /** The prompt and the finished count from [encode]'s text, or null when the text is missing or bad. */
    fun decode(text: String?, typeCount: Int): Pair<Prompt, Int>? {
        val parts = text?.split(';') ?: return null
        if (parts.size != 5 || parts[0] != "p1") return null
        val n = parts.drop(1).map { it.toIntOrNull() ?: return null }
        val (type, goal, done, finished) = n
        if (type !in 0 until typeCount || goal !in MIN_GOAL..MAX_GOAL || done !in 0..goal || finished < 0) return null
        return Pair(Prompt(type, goal, done), finished)
    }
}
