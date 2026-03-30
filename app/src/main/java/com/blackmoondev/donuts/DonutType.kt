package com.blackmoondev.donuts

import android.graphics.Color

enum class DonutType(
    val bodyColor: Int,
    val glazeColor: Int,
    val label: String
) {
    STRAWBERRY(
        bodyColor  = Color.rgb(255, 182, 193),
        glazeColor = Color.rgb(220,  80, 110),
        label      = "Strawberry"
    ),
    CHOCOLATE(
        bodyColor  = Color.rgb(150,  90,  50),
        glazeColor = Color.rgb( 80,  40,  10),
        label      = "Chocolate"
    ),
    BLUEBERRY(
        bodyColor  = Color.rgb(130, 130, 230),
        glazeColor = Color.rgb( 60,  60, 180),
        label      = "Blueberry"
    ),
    VANILLA(
        bodyColor  = Color.rgb(255, 235, 180),
        glazeColor = Color.rgb(230, 200, 120),
        label      = "Vanilla"
    ),
    MATCHA(
        bodyColor  = Color.rgb(120, 190, 120),
        glazeColor = Color.rgb( 60, 140,  60),
        label      = "Matcha"
    ),
    CARAMEL(
        bodyColor  = Color.rgb(220, 160,  80),
        glazeColor = Color.rgb(170, 110,  30),
        label      = "Caramel"
    );

    companion object {
        fun random(): DonutType = values().random()
    }
}
