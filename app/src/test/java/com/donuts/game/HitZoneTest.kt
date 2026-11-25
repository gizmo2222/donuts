package com.donuts.game

import org.junit.Assert.*
import org.junit.Test

/** Unit tests for [HitZone] (no Android needed). Points are in cell units; cell (r, c) has its centre at (c+0.5, r+0.5). */
class HitZoneTest {
    private val w6 = HitZone.diagWeight(6)
    private val w8 = HitZone.diagWeight(8)
    private val r = 0.70710677f

    private fun t(u: Float, v: Float, last: Pair<Int, Int>, aimU: Float = 0f, aimV: Float = 0f, w: Float = w6, n: Int = 6) =
        HitZone.target(u, v, last.first, last.second, n, n, aimU, aimV, w)

    @Test
    fun theFingerOnTheLastCellAimsAtItAndACentreAimsAtThatCell() {
        assertEquals(2 to 2, t(2.5f, 2.5f, 2 to 2))
        assertEquals(2 to 3, t(3.5f, 2.5f, 2 to 2))
        assertEquals(3 to 3, t(3.5f, 3.5f, 2 to 2))
        assertEquals(1 to 1, t(1.5f, 1.5f, 2 to 2))
    }

    @Test
    fun withoutAnAimAStraightStepJoinsAtTheSharedEdgeAsBefore() {
        assertEquals(2 to 2, t(2.95f, 2.5f, 2 to 2))
        assertEquals(2 to 3, t(3.05f, 2.5f, 2 to 2))
        assertEquals(2 to 2, t(2.5f, 2.05f, 2 to 2))
        assertEquals(1 to 2, t(2.5f, 1.95f, 2 to 2))
    }

    @Test
    fun theAimLeadsTheFingerIntoTheCellItIsHeadingFor() {
        assertEquals(2 to 2, t(2.9f, 2.5f, 2 to 2))
        assertEquals(2 to 3, t(2.9f, 2.5f, 2 to 2, aimU = 1f))           // 0.15 ahead crosses the edge
        assertEquals(2 to 2, t(2.9f, 2.5f, 2 to 2, aimU = -1f))          // heading back stays home
    }

    @Test
    fun cuttingACornerReachesTheDiagonalNotTheStraightCellBesideIt() {
        // Inside (0,1)'s square, just past the corner it shares with (1,1): the diagonal wins.
        assertEquals(1 to 1, t(1.05f, 0.95f, 0 to 0))
        assertEquals(1 to 1, t(1.05f, 0.95f, 0 to 0, aimU = r, aimV = r))
        // Still in the last cell, on the diagonal past its 0.44 mark (1/(1+weight)): the diagonal already wins.
        assertEquals(1 to 1, t(0.96f, 0.96f, 0 to 0))
        assertEquals(0 to 0, t(0.8f, 0.8f, 0 to 0))
    }

    @Test
    fun aWobblyStraightStepIsNotStolenByTheDiagonal() {
        for (wob in listOf(-0.3f, -0.2f, 0f, 0.2f, 0.3f)) {
            assertEquals(0 to 1, t(1.1f, 0.5f + wob, 0 to 0, aimU = 1f))
            assertEquals(0 to 1, t(1.1f, 0.5f + wob, 0 to 0, aimU = 1f, w = w8, n = 8))
            assertEquals(1 to 0, t(0.5f + wob, 1.1f, 0 to 0, aimV = 1f))
        }
        assertEquals(0 to 1, t(1.1f, 0.9f, 0 to 0, aimU = 1f))           // 0.4 off the line on 6x6
    }

    @Test
    fun goingBackTowardThePreviousCellAimsAtIt() {
        assertEquals(0 to 0, t(0.95f, 0.5f, 0 to 1, aimU = -1f))
        assertEquals(0 to 1, t(1.2f, 0.5f, 0 to 1, aimU = -1f))
        assertEquals(1 to 1, t(1.5f, 1.05f, 0 to 1, aimV = 1f))
    }

    @Test
    fun beyondTheThreeByThreeBlockAimsAtNothing() {
        assertNull(t(4.5f, 2.5f, 2 to 2))
        assertNull(t(2.5f, 0.9f, 2 to 2))
        assertNotNull(t(3.9f, 3.9f, 2 to 2))
    }

    @Test
    fun cellsOffTheBoardAreNeverAimedAt() {
        assertEquals(0 to 0, t(-0.4f, -0.4f, 0 to 0, aimU = -r, aimV = -r))
        assertEquals(5 to 5, t(6.4f, 5.5f, 5 to 5, aimU = 1f))
        assertEquals(4 to 5, t(6.4f, 4.6f, 5 to 5))
    }

    @Test
    fun eightByEightLeansHarderOnDiagonals() {
        assertTrue(w8 > w6)
        assertEquals(w6, HitZone.diagWeight(7), 0f)
        assertTrue(w6 > 1f)
        // A point the 6x6 weight leaves to the straight cell goes diagonal on 8x8.
        assertEquals(0 to 1, t(1.1f, 0.92f, 0 to 0, aimU = 1f))
        assertEquals(1 to 1, t(1.1f, 0.92f, 0 to 0, aimU = 1f, w = w8, n = 8))
    }

    @Test
    fun theAimChangesOnlyAfterAStepAndIsAUnitVector() {
        val still = HitZone.aim(1.05f, 1f, 1f, 1f, 0f, -1f)
        assertEquals(Triple(0f, -1f, false), still)
        val (au, av, moved) = HitZone.aim(1.3f, 1.4f, 1f, 1f, 0f, -1f)
        assertTrue(moved)
        assertEquals(0.6f, au, 1e-5f)
        assertEquals(0.8f, av, 1e-5f)
    }
}
