package com.donuts.game

import kotlin.random.Random

/**
 * The grown-ups gate in front of New game, Reset stickers and the grid size: a times-table
 * question written in words ("What is seven times eight?"), answered with two digits on a
 * keypad. An early reader cannot read the words and a guess is one in ninety. Every answer is
 * two digits (3..9 times 4..9), so the second digit always decides. No Android types.
 */
internal class ParentGate(private val random: Random = Random.Default) {
    var a = 0; private set
    var b = 0; private set
    /** The digits typed so far for this question, at most one (the second decides). */
    var entry = ""; private set

    val answer: Int get() = a * b
    val question: String get() = "What is ${WORDS[a]} times ${WORDS[b]}?"

    init { next() }

    /** A new question, never the same pair as the last one, with nothing typed. */
    fun next() {
        val oldA = a; val oldB = b
        do { a = 3 + random.nextInt(7); b = 4 + random.nextInt(6) } while (a == oldA && b == oldB)
        entry = ""
    }

    /**
     * Type one digit. The second digit answers: [OPEN] when it is right, [WRONG] when not (and a
     * new question is asked). Anything else is [TYPING].
     */
    fun press(digit: Int): Int {
        if (digit !in 0..9) return TYPING
        if (entry.isEmpty()) {
            if (digit == 0) return TYPING        // no answer starts with 0; ignore it rather than waste a try
            entry = digit.toString(); return TYPING
        }
        val typed = entry.toInt() * 10 + digit
        return if (typed == answer) { entry = ""; OPEN } else { next(); WRONG }
    }

    companion object {
        const val TYPING = 0
        const val OPEN   = 1
        const val WRONG  = 2
        val WORDS = arrayOf("zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine")
    }
}
