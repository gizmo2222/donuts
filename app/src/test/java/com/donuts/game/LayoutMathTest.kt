package com.donuts.game

import org.junit.Assert.assertEquals
import org.junit.Test

class LayoutMathTest {
    @Test fun spareShareIsCapped() {
        assertEquals(25f, LayoutMath.spareShare(100f, 0.25f, 32f), 0.001f)
        assertEquals(32f, LayoutMath.spareShare(400f, 0.25f, 32f), 0.001f)
    }

    @Test fun noSpareMeansNoChange() {
        assertEquals(0f, LayoutMath.spareShare(0f, 0.25f, 32f), 0.001f)
        assertEquals(0f, LayoutMath.spareShare(-20f, 0.25f, 32f), 0.001f)
    }

    @Test fun gutterHasAFloor() {
        assertEquals(14f, LayoutMath.gutter(60f, 14f), 0.001f)
        assertEquals(24f, LayoutMath.gutter(200f, 14f), 0.001f)
    }

    @Test fun fitSideFillsTheSpace() {
        for (avail in listOf(200f, 400f, 900f)) {
            val s = LayoutMath.fitSide(avail, 3, 14f)
            assertEquals(avail, 3 * s + 2 * LayoutMath.gutter(s, 14f), 0.01f)
        }
    }
}
