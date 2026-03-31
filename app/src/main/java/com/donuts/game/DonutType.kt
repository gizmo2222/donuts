package com.donuts.game

import android.graphics.Color

enum class DonutType(
    val bodyColor: Int,
    val glazeColor: Int,
    val label: String
) {
    STRAWBERRY(
        bodyColor  = Color.rgb(255,  85, 125),
        glazeColor = Color.rgb(230,   0,  50),
        label      = "Strawberry"
    ),
    CHOCOLATE(
        bodyColor  = Color.rgb(170, 100,  45),
        glazeColor = Color.rgb( 88,  42,   5),
        label      = "Chocolate"
    ),
    BLUEBERRY(
        bodyColor  = Color.rgb( 75,  75, 255),
        glazeColor = Color.rgb( 22,  10, 215),
        label      = "Blueberry"
    ),
    VANILLA(
        bodyColor  = Color.rgb(255, 228,  45),
        glazeColor = Color.rgb(215, 168,   0),
        label      = "Vanilla"
    ),
    MATCHA(
        bodyColor  = Color.rgb( 45, 210,  45),
        glazeColor = Color.rgb(  5, 155,   5),
        label      = "Matcha"
    ),
    CARAMEL(
        bodyColor  = Color.rgb(255, 138,   0),
        glazeColor = Color.rgb(195,  82,   0),
        label      = "Caramel"
    );

    companion object {
        fun random(): DonutType = values().random()
    }
}
