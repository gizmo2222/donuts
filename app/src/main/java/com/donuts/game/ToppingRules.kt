package com.donuts.game

/**
 * A topping drawn over every donut's glaze. [key] is the saved form (stable, never the ordinal), [unlockAt] the
 * lifetime donuts that earn it. [PLAIN] is no topping: always there, and the fallback for any bad saved choice.
 */
enum class Topping(val key: String, val unlockAt: Int) {
    PLAIN("plain", 0),
    SPRINKLES("sprinkles", 50),
    CHOC_CHIPS("chips", 150),
    STARS("stars", 300),
    HEARTS("hearts", 600),
    MINI_BALLS("balls", 1000),
    RAINBOW("rainbow", 1500)
}

/**
 * Which toppings Steven has earned and which one he picked (pure, no Android types).
 *
 * Toppings are earned in a fixed order as lifetime donuts pass [Topping.unlockAt]. Like the donut friends,
 * earned toppings are kept forever: the count lives in `Prefs.toppingsEarned`, so a sticker reset (which zeroes
 * lifetime donuts) never takes one back. [next] hands them out one at a time, so a big jump still gives each
 * its own reveal.
 */
object ToppingRules {
    val TOPPINGS: List<Topping> = Topping.values().toList()

    /** Toppings that can be earned (all but [Topping.PLAIN]). */
    val EARNABLE: Int = TOPPINGS.size - 1

    /** Toppings whose lifetime threshold has been reached, 0..[EARNABLE] (PLAIN not counted). */
    fun due(lifetime: Int): Int = TOPPINGS.count { it != Topping.PLAIN && it.unlockAt <= lifetime }

    /** The next topping to reveal when [earned] is behind [due], else null. One at a time, in order. */
    fun next(lifetime: Int, earned: Int): Topping? {
        val e = earned.coerceIn(0, EARNABLE)
        return if (e < due(lifetime)) TOPPINGS[e + 1] else null
    }

    /** What the picker offers: PLAIN, then the first [earned] toppings, in order. */
    fun unlocked(earned: Int): List<Topping> = TOPPINGS.take(earned.coerceIn(0, EARNABLE) + 1)

    fun isUnlocked(t: Topping, earned: Int): Boolean = t.ordinal <= earned.coerceIn(0, EARNABLE)

    /** The saved form of a choice. */
    fun encode(t: Topping): String = t.key

    /** The topping to draw: the saved choice if it is known and earned, else PLAIN. */
    fun chosen(saved: String?, earned: Int): Topping {
        val t = TOPPINGS.firstOrNull { it.key == saved } ?: return Topping.PLAIN
        return if (isUnlocked(t, earned)) t else Topping.PLAIN
    }
}
