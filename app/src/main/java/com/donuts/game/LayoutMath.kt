package com.donuts.game

import kotlin.math.max
import kotlin.math.min

/** Pure layout arithmetic, kept free of Android types so it can be unit tested. */
object LayoutMath {
    /** The share [frac] of leftover [spare] height given to one piece of chrome, never below 0 or above [capPx]. */
    fun spareShare(spare: Float, frac: Float, capPx: Float): Float =
        (spare * frac).coerceIn(0f, max(0f, capPx))

    /** Gap between sticker tiles: 12% of the tile side, never under [minGut]. */
    fun gutter(side: Float, minGut: Float): Float = max(minGut, side * 0.12f)

    /** Index of the cell under [pos] on a grid starting at [origin]; floors, so left of the origin is negative. */
    fun cellIndex(pos: Float, origin: Float, cell: Float): Int =
        kotlin.math.floor((pos - origin) / cell).toInt()

    /** Board side: the smaller of the two room measures, never negative. */
    fun boardSide(roomW: Float, roomH: Float): Float = min(roomW, roomH).coerceAtLeast(0f)

    /** Panel-local y for a touch at [screenY] while the panel is drawn translated down by [slideY]. */
    fun panelLocalY(screenY: Float, slideY: Float): Float = screenY - slideY

    /** Largest tile side such that [n] tiles plus their gutters fit in [avail]. */
    fun fitSide(avail: Float, n: Int, minGut: Float): Float {
        val gaps = n - 1
        val withMin = (avail - gaps * minGut) / n
        val side = if (withMin * 0.12f <= minGut) withMin else avail / (n + gaps * 0.12f)
        return max(0f, side)
    }
}
