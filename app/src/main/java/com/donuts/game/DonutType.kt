package com.donuts.game

import android.graphics.Color

// Six donuts, six silhouettes. bodyColor is the dough, glazeColor is the topping
// (and the colour of the chain line while dragging).
enum class DonutType(
    val bodyColor: Int,
    val glazeColor: Int,
    val label: String
) {
    STRAWBERRY(Color.rgb(255, 140, 165), Color.rgb(235,  25,  80), "Strawberry"),  // drippy ring, rainbow sprinkles
    CHOCOLATE (Color.rgb(165,  95,  45), Color.rgb( 90,  45,  20), "Chocolate"),   // dark glaze, cream stripes
    BLUEBERRY (Color.rgb(135, 185, 255), Color.rgb(105,  70, 220), "Blueberry"),   // filled bun, jam spot, sugar
    VANILLA   (Color.rgb(255, 235, 130), Color.rgb(255, 205,  30), "Vanilla"),     // eight-lobed flower ring
    MATCHA    (Color.rgb( 70, 195,  85), Color.rgb(160, 235,  60), "Matcha"),      // half-dipped ring, sesame
    CARAMEL   (Color.rgb(245, 165,  70), Color.rgb(210, 115,  20), "Caramel");     // square donut, drizzle

    companion object {
        fun random(): DonutType = values().random()
    }
}
