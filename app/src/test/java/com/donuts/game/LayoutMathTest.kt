package com.donuts.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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

    @Test fun fitSideNeverGoesNegative() {
        assertEquals(0f, LayoutMath.fitSide(-50f, 4, 14f), 0f)
        assertEquals(0f, LayoutMath.fitSide(10f, 4, 14f), 0f)
    }

    @Test fun cellIndexFloorsNegatives() {
        assertEquals(-1, LayoutMath.cellIndex(95f, 100f, 40f))
        assertEquals(-1, LayoutMath.cellIndex(61f, 100f, 40f))
        assertEquals(-2, LayoutMath.cellIndex(59f, 100f, 40f))
        assertEquals(0, LayoutMath.cellIndex(100f, 100f, 40f))
        assertEquals(2, LayoutMath.cellIndex(185f, 100f, 40f))
    }

    @Test fun boardSideIsNeverNegative() {
        // a 200dp-tall window leaves less than zero room once the chrome is taken off
        assertEquals(0f, LayoutMath.boardSide(300f, 200f - 2 * 16f - 400f), 0f)
        assertEquals(120f, LayoutMath.boardSide(300f, 120f), 0f)
    }

    @Test fun panelLocalYSubtractsTheSlide() {
        assertEquals(100f, LayoutMath.panelLocalY(400f, 300f), 0f)
        assertEquals(400f, LayoutMath.panelLocalY(400f, 0f), 0f)
    }

    @Test fun spareShareBoundaries() {
        assertEquals(0f, LayoutMath.spareShare(100f, 0f, 32f), 0f)
        assertEquals(32f, LayoutMath.spareShare(128f, 0.25f, 32f), 0.001f)   // exactly at the cap
        assertEquals(31.75f, LayoutMath.spareShare(127f, 0.25f, 32f), 0.001f)
        assertEquals(0f, LayoutMath.spareShare(100f, 0.5f, 0f), 0f)
        assertEquals(0f, LayoutMath.spareShare(100f, 0.5f, -5f), 0f)         // a negative cap never throws
    }

    @Test fun gutterSwitchesAtTheFloor() {
        assertEquals(12f, LayoutMath.gutter(100f, 12f), 0.001f)   // 12% of 100 equals the floor
        assertEquals(12f, LayoutMath.gutter(0f, 12f), 0f)
        assertEquals(13.2f, LayoutMath.gutter(110f, 12f), 0.001f)
        assertEquals(12f, LayoutMath.gutter(99f, 12f), 0.001f)
    }

    @Test fun fitSideSingleTileTakesEverything() {
        assertEquals(250f, LayoutMath.fitSide(250f, 1, 14f), 0.001f)
    }

    @Test fun fitSideUsesMinimumGutterWhenTilesAreSmall() {
        val s = LayoutMath.fitSide(100f, 3, 14f)   // tiles of 24, gutters of 14
        assertEquals(24f, s, 0.001f)
    }

    @Test fun fitSideUsesProportionalGutterWhenTilesAreBig() {
        val s = LayoutMath.fitSide(1000f, 4, 14f)
        assertEquals(1000f / (4 + 3 * 0.12f), s, 0.001f)
    }

    @Test fun fitSideIsContinuousAcrossTheSwitch() {
        // withMin * 0.12 == minGut at withMin = 100 (minGut 12, 3 tiles: avail 324)
        val below = LayoutMath.fitSide(323f, 3, 12f)
        val above = LayoutMath.fitSide(325f, 3, 12f)
        assertTrue(above > below)
        assertEquals(100f, LayoutMath.fitSide(324f, 3, 12f), 0.05f)
    }
}
