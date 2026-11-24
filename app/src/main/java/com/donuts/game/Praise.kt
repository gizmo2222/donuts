package com.donuts.game

/**
 * Praise that names what he did. The float word for a chain, and the cheer line that every second
 * big moment (a milestone or a full box) adds under its banner. Words describe the act and never
 * compare or rank. Pure, no Android types.
 */
object Praise {
    /** The cheer under every second milestone or box banner. Approved by Nick, 2026-11-24. */
    const val CHEER = "Great job, Steven!"

    /** Splits a banner label into its main line and its cheer line. */
    const val LINE_BREAK = '\n'

    /**
     * The float word for a chain of [chainLen] that fired [powerUp] and held a golden cell when
     * [hasGolden]: the power-up's sound word first, then the golden word, then NICE! for a 4-chain.
     */
    fun chainLabel(powerUp: PowerUp, chainLen: Int, hasGolden: Boolean): String? = when {
        powerUp == PowerUp.COLOR_BURST -> "KABOOM!"
        powerUp == PowerUp.ROW_BLAST   -> "WHOOSH!"
        powerUp == PowerUp.BOMB        -> "BOOM!"
        hasGolden                      -> "GOLDEN!"
        chainLen == 4                  -> "NICE!"
        else                           -> null
    }

    /** True for the big moment numbered [index] (0-based) that gets the cheer: the 2nd, 4th, 6th... */
    fun cheers(index: Int): Boolean = index % 2 == 1

    /** The banner text for big moment [index]: [label], plus the cheer line on every second one. */
    fun banner(label: String, index: Int): String =
        if (cheers(index)) "$label$LINE_BREAK$CHEER" else label
}
