package com.donuts.game

import kotlin.random.Random

/** How the confetti of a celebration is thrown. */
internal enum class ConfettiStyle { CANNONS, FOUNTAIN, RAIN }

/**
 * One celebration's look: how the confetti flies, which banner colour (an index into
 * [GameTheme.bannerColors]) and whether it is the rare golden shower (gold confetti, sparkles, a high trill).
 */
internal data class CelebrationLook(val style: ConfettiStyle, val banner: Int, val golden: Boolean = false)

/**
 * Variety for celebrations (pure, unit tested). Each celebration changes its confetti style and banner colour
 * from the one before, so two in a row never look the same; about one in [GOLDEN_ONE_IN] is a golden shower,
 * never two in a row. A single tap giggles instead of booping about one time in [GIGGLE_ONE_IN].
 */
internal object CelebrationMix {
    const val GOLDEN_ONE_IN = 10
    const val GIGGLE_ONE_IN = 12
    const val BANNER_COLOURS = 4

    val CLASSIC = CelebrationLook(ConfettiStyle.CANNONS, 0)

    fun next(rnd: Random, last: CelebrationLook?): CelebrationLook {
        val styles = ConfettiStyle.values()
        val style = if (last == null) styles[rnd.nextInt(styles.size)]
                    else styles.filter { it != last.style }[rnd.nextInt(styles.size - 1)]
        val banner = if (last == null) rnd.nextInt(BANNER_COLOURS)
                     else (last.banner + 1 + rnd.nextInt(BANNER_COLOURS - 1)) % BANNER_COLOURS
        val golden = last?.golden != true && rnd.nextInt(GOLDEN_ONE_IN) == 0
        // The golden shower always wears the gold banner
        return CelebrationLook(style, if (golden) 0 else banner, golden)
    }

    fun giggle(rnd: Random): Boolean = rnd.nextInt(GIGGLE_ONE_IN) == 0
}
