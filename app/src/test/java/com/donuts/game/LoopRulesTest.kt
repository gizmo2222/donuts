package com.donuts.game

import org.junit.Assert.*
import org.junit.Test

/** Unit tests for [LoopRules] (no Android needed). */
class LoopRulesTest {
    // A 2x3 ring: along row 0, back along row 1, ending beside the start.
    private val ring6 = listOf(0 to 0, 0 to 1, 0 to 2, 1 to 2, 1 to 1, 1 to 0)

    @Test fun theMinimumIsSixSoAShortLShapeIsNoRing() = assertEquals(6, LoopRules.MIN_LOOP)

    @Test fun aSixCellRingClosesOnItsFirstCell() = assertTrue(LoopRules.closes(ring6, 0 to 0))

    @Test fun aFiveCellClosedPathIsTooShort() =
        assertFalse(LoopRules.closes(ring6.take(5), 0 to 0))

    @Test fun onlyTheFirstCellClosesTheRing() {
        assertFalse(LoopRules.closes(ring6, 0 to 1))
        assertFalse(LoopRules.closes(ring6, 2 to 0))
    }

    @Test fun theLastCellMustTouchTheFirst() =
        assertFalse(LoopRules.closes((0..5).map { 0 to it }, 0 to 0))

    @Test fun aDiagonalTouchCloses() {
        val chain = listOf(1 to 1, 0 to 1, 0 to 2, 1 to 3, 2 to 2, 2 to 1, 2 to 0)
        assertTrue(LoopRules.closes(chain, 1 to 1))
    }

    @Test fun anEmptyChainNeverCloses() = assertFalse(LoopRules.closes(emptyList(), 0 to 0))
}
