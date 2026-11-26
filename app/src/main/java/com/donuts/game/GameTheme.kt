package com.donuts.game

import android.graphics.Color

// The warm dark brown of every outline, shadow, scrim and face line, at any alpha.
fun ink(alpha: Int = 255): Int = Color.argb(alpha, 28, 12, 0)
val INK = ink()

// The one look: a warm cream bakery. Pieces bring the colour; the chrome stays quiet.
object GameTheme {
    val bg            = Color.rgb(255, 240, 220)
    val boardBg       = Color.rgb(245, 210, 165)
    val textPrimary   = Color.rgb( 80,  40,   0)
    val textSecondary = Color.rgb(160,  80,  20)
    val holeColor     = Color.rgb(255, 240, 220)
    val panelBg       = Color.rgb(255, 248, 235)
    val accent        = Color.rgb(220, 100, 130)   // selected option
    val accentMuted   = Color.rgb(200, 170, 130)   // unselected option
    val chrome        = Color.rgb(170, 120,  60)   // stickers / settings buttons
    val goldenLabel   = Color.rgb(255, 214,  64)   // the GOLDEN! float word
    // Celebration banners take turns: gold, strawberry pink, mint, sky. All light, so the brown words read.
    val bannerColors  = intArrayOf(
        Color.rgb(255, 210,  50), Color.rgb(255, 160, 205), Color.rgb(130, 228, 170), Color.rgb(140, 205, 255))
    // The rare golden shower's confetti
    val goldColors    = intArrayOf(
        Color.rgb(255, 210,  50), Color.rgb(255, 236, 140), Color.WHITE, Color.rgb(240, 165,  30))
}
