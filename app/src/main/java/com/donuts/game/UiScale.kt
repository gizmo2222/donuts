package com.donuts.game

import android.content.Context
import kotlin.math.min

/**
 * Density-independent sizing for the canvas-drawn views.
 *
 * [u] is "pixels per design-dp": every size in the UI is written as `Ndp * u`.
 * It starts at the display density (so 1 design-dp == 1 real dp on a phone) and
 * grows up to 1.5x on large screens, so chrome, text, and touch targets scale
 * with the device instead of staying phone-sized on a tablet.
 */
class UiScale(context: Context) {
    val density: Float = context.resources.displayMetrics.density

    /** Pixels per design-dp for the current surface size. */
    var u: Float = density
        private set

    /** Recompute for a surface of [wPx] × [hPx]. Phones (≤ 400dp short side) get 1.0x; tablets up to 1.5x. */
    fun update(wPx: Int, hPx: Int) {
        val shortDp = min(wPx, hPx) / density
        u = density * (shortDp / 400f).coerceIn(1f, 1.5f)
    }
}
