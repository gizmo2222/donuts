package com.donuts.game

/** One donut friend. [isBall] friends are sports balls, not donuts. */
data class Friend(val name: String, val isBall: Boolean)

/**
 * Which donut friend a box slot fill reveals. The order is fixed, never random, and met friends are
 * kept forever. Friend i is met once the box slots ever filled (across all boxes) reach [REVEAL_AT] i.
 */
object FriendRules {
    val FRIENDS: List<Friend> = listOf(
        Friend("Sprinkle", false), Friend("Jelly", false), Friend("Bouncy", true),
        Friend("Swirl", false), Friend("Choco", false), Friend("Kicky", true),
        Friend("Berry", false), Friend("Gloop", false), Friend("Zoomy", true),
        Friend("Puff", false), Friend("Fizz", false), Friend("Captain Glaze", false)
    )
    const val FRIEND_COUNT = 12

    /** Total box slots ever filled at which each friend is met. */
    val REVEAL_AT = intArrayOf(1, 2, 4, 6, 9, 12, 16, 20, 25, 30, 36, 42)

    /** Box slots filled over all boxes, from lifetime donuts. */
    fun slotsEver(lifetime: Int): Int = maxOf(lifetime, 0) / StickerRules.BOX_SLOT_DONUTS

    /** Friends whose slot has been reached, 0..12. */
    fun due(lifetime: Int): Int {
        val slots = slotsEver(lifetime)
        return REVEAL_AT.count { it <= slots }
    }

    /**
     * Index of the next friend to reveal when [met] is behind [due], else null. One at a time, in order.
     * Only the first [drawn] friends have art, so none past them is revealed (the game passes DRAWN).
     */
    fun next(lifetime: Int, met: Int, drawn: Int = FRIEND_COUNT): Int? {
        val m = met.coerceIn(0, FRIEND_COUNT)
        return if (m < due(lifetime) && m < drawn) m else null
    }
}
