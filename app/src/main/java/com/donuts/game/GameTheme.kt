package com.donuts.game

import android.graphics.Color

enum class IconType { DONUT, STAR, DINO, TRUCK }

data class GameTheme(
    val name: String,
    val iconType: IconType,
    val bg: Int,
    val boardBg: Int,
    val textPrimary: Int,
    val textSecondary: Int,
    val holeColor: Int,
    val resetBtn: Int,
    val settingsBtn: Int,
    val hintRing: Int,
    val panelBg: Int,
    val btnSelected: Int,
    val btnUnselected: Int
) {
    companion object {
        val all = listOf(
            // 0 – Donuts (warm cream)
            GameTheme(
                name          = "Donuts",
                iconType      = IconType.DONUT,
                bg            = Color.rgb(255, 240, 220),
                boardBg       = Color.rgb(245, 210, 165),
                textPrimary   = Color.rgb( 80,  40,   0),
                textSecondary = Color.rgb(160,  80,  20),
                holeColor     = Color.rgb(255, 240, 220),
                resetBtn      = Color.rgb(220, 100, 130),
                settingsBtn   = Color.rgb(170, 120,  60),
                hintRing      = Color.rgb(255, 220,   0),
                panelBg       = Color.rgb(255, 248, 235),
                btnSelected   = Color.rgb(220, 100, 130),
                btnUnselected = Color.rgb(200, 170, 130)
            ),
            // 1 – Stars (dark space)
            GameTheme(
                name          = "Stars",
                iconType      = IconType.STAR,
                bg            = Color.rgb(  8,   8,  28),
                boardBg       = Color.rgb( 18,  18,  55),
                textPrimary   = Color.rgb(180, 180, 255),
                textSecondary = Color.rgb(120, 120, 220),
                holeColor     = Color.rgb(  8,   8,  28),
                resetBtn      = Color.rgb( 80,  80, 210),
                settingsBtn   = Color.rgb( 55,  55, 160),
                hintRing      = Color.rgb(100, 210, 255),
                panelBg       = Color.rgb( 20,  20,  60),
                btnSelected   = Color.rgb( 80,  80, 210),
                btnUnselected = Color.rgb( 35,  35,  90)
            ),
            // 2 – Dinos (jungle green)
            GameTheme(
                name          = "Dinos",
                iconType      = IconType.DINO,
                bg            = Color.rgb(218, 242, 206),
                boardBg       = Color.rgb(162, 210, 138),
                textPrimary   = Color.rgb( 25,  75,  15),
                textSecondary = Color.rgb( 55, 125,  40),
                holeColor     = Color.rgb(218, 242, 206),
                resetBtn      = Color.rgb( 75, 155,  55),
                settingsBtn   = Color.rgb( 45, 115,  35),
                hintRing      = Color.rgb(255, 230,  50),
                panelBg       = Color.rgb(238, 252, 228),
                btnSelected   = Color.rgb( 75, 155,  55),
                btnUnselected = Color.rgb(145, 195, 125)
            ),
            // 3 – Trucks (steel blue)
            GameTheme(
                name          = "Trucks",
                iconType      = IconType.TRUCK,
                bg            = Color.rgb(218, 228, 240),
                boardBg       = Color.rgb(165, 188, 212),
                textPrimary   = Color.rgb( 18,  38,  78),
                textSecondary = Color.rgb( 48,  88, 138),
                holeColor     = Color.rgb(218, 228, 240),
                resetBtn      = Color.rgb( 38,  88, 168),
                settingsBtn   = Color.rgb( 28,  68, 138),
                hintRing      = Color.rgb(255, 178,  28),
                panelBg       = Color.rgb(232, 240, 252),
                btnSelected   = Color.rgb( 38,  88, 168),
                btnUnselected = Color.rgb(128, 158, 198)
            )
        )
    }
}
