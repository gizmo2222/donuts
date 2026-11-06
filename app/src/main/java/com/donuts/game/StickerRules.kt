package com.donuts.game

import kotlin.math.max

/** The numbers the sticker rules read, gathered so the rules can be tested without Android. */
data class StickerStats(
    val lifetime: Int,
    val highScore6x6: Int,
    val highScore8x8: Int,
    val bestChain: Int,
    val goldenPopped: Boolean,
    val sessions: Int
)

object StickerRules {
    const val STICKER_COUNT = 12
    const val BOX_SLOTS = 12
    const val BOX_SLOT_DONUTS = 10

    fun isEarned(i: Int, s: StickerStats): Boolean {
        val best = max(s.highScore6x6, s.highScore8x8)
        return when (i) {
            0  -> s.lifetime >= 10
            1  -> s.lifetime >= 50
            2  -> s.lifetime >= 500
            3  -> s.bestChain >= 4
            4  -> s.bestChain >= 6
            5  -> s.bestChain >= 8
            6  -> best >= 20
            7  -> best >= 60
            8  -> best >= 200
            9  -> s.goldenPopped
            10 -> s.highScore6x6 > 0 && s.highScore8x8 > 0
            11 -> s.sessions >= 10
            else -> false
        }
    }

    /** Bit i is set when sticker i is earned. */
    fun earnedMask(s: StickerStats): Int {
        var mask = 0
        for (i in 0 until STICKER_COUNT) if (isEarned(i, s)) mask = mask or (1 shl i)
        return mask
    }

    /** Full donut boxes banked. */
    fun boxesFilled(lifetime: Int): Int = lifetime / (BOX_SLOTS * BOX_SLOT_DONUTS)

    /** Filled slots in the box currently being filled. */
    fun slotsFilled(lifetime: Int): Int = (lifetime % (BOX_SLOTS * BOX_SLOT_DONUTS)) / BOX_SLOT_DONUTS
}
