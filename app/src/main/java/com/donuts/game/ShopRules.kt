package com.donuts.game

import kotlin.math.max

/**
 * One piece of Steven's donut shop on the home screen, earned when lifetime donuts reach [unlockAt].
 * The spot is where it sits in the scene box as fractions (0..1) of its width and height.
 */
enum class ShopPiece(val key: String, val unlockAt: Int,
                     val left: Float, val top: Float, val right: Float, val bottom: Float) {
    SIGN("sign", 25, 0.33f, 0.31f, 0.67f, 0.45f),
    AWNING("awning", 100, 0.28f, 0.45f, 0.72f, 0.58f),
    WINDOW_DONUTS("window", 250, 0.37f, 0.66f, 0.51f, 0.80f),
    LAMP("lamp", 450, 0.06f, 0.08f, 0.20f, 0.92f),
    BALL("ball", 800, 0.74f, 0.72f, 0.86f, 0.96f),
    ROOF_DONUT("roof", 1200, 0.42f, 0.00f, 0.58f, 0.25f),
}

/** Which shop pieces Steven has, from lifetime donuts. Pieces stay once earned, even after Reset stickers. Pure. */
object ShopRules {
    /** Every piece in the order they are earned. */
    val PIECES = ShopPiece.values().toList()
    val COUNT = PIECES.size

    /** The shop building itself (wall, door, window, pavement), always drawn, as fractions of the scene box. */
    const val SHOP_LEFT = 0.30f
    const val SHOP_TOP = 0.24f
    const val SHOP_RIGHT = 0.70f
    const val GROUND_TOP = 0.90f

    /** How many pieces [lifetime] donuts have earned. */
    fun due(lifetime: Int): Int = PIECES.count { lifetime >= it.unlockAt }

    /** Pieces to show: never fewer than [saved], so a lifetime reset keeps the shop as built. */
    fun kept(lifetime: Int, saved: Int): Int = max(due(lifetime), saved.coerceIn(0, COUNT))

    /** Whether [piece] is in a shop with [earned] pieces. */
    fun isShown(piece: ShopPiece, earned: Int): Boolean = piece.ordinal < earned

    /** The pieces shown in a shop with [earned] pieces. */
    fun shown(earned: Int): List<ShopPiece> = PIECES.take(earned.coerceIn(0, COUNT))

    /** The next piece to earn and the lifetime it needs, or null when the shop is complete. */
    fun next(earned: Int): ShopPiece? = PIECES.getOrNull(earned.coerceAtLeast(0))
}
