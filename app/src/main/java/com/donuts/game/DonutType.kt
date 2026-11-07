package com.donuts.game

import android.graphics.Color

// Six donuts, six silhouettes. bodyColor is the dough, glazeColor is the topping
// (and the colour of the chain line while dragging).
enum class DonutType(
    val bodyColor: Int,
    val glazeColor: Int,
    val labelColor: Int      // colour of the BOOM/WHOOSH/KABOOM label (chocolate glaze is too dark to read)
) {
    STRAWBERRY(Color.rgb(255, 140, 165), Color.rgb(235,  25,  80), Color.rgb(235,  25,  80)),  // drippy ring, rainbow sprinkles
    CHOCOLATE (Color.rgb(165,  95,  45), Color.rgb( 90,  45,  20), Color.rgb(200, 120,  60)),  // dark glaze, cream stripes
    BLUEBERRY (Color.rgb(135, 185, 255), Color.rgb(105,  70, 220), Color.rgb(120,  90, 235)),  // filled bun, jam spot, sugar
    VANILLA   (Color.rgb(255, 235, 130), Color.rgb(255, 205,  30), Color.rgb(255, 205,  30)),  // eight-lobed flower ring
    MATCHA    (Color.rgb( 70, 195,  85), Color.rgb(160, 235,  60), Color.rgb(130, 215,  50)),  // half-dipped ring, sesame
    CARAMEL   (Color.rgb(245, 165,  70), Color.rgb(210, 115,  20), Color.rgb(240, 140,  30));  // square donut, drizzle
}
