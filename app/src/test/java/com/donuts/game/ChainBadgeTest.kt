package com.donuts.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChainBadgeTest {
    @Test fun emptyAndSingleAreNull() {
        assertNull(ChainBadge.spot(emptyList()))
        assertNull(ChainBadge.spot(listOf(2 to 2)))
    }

    @Test fun horizontalChainPicksPreviousCell() {
        assertEquals(2 to 2, ChainBadge.spot(listOf(2 to 1, 2 to 2, 2 to 3)))
    }

    @Test fun lShapePrefersCellAbove() {
        // last (2,2): chain has (1,2) above and (2,1) to the left
        assertEquals(1 to 2, ChainBadge.spot(listOf(2 to 1, 1 to 1, 1 to 2, 2 to 2)))
    }

    @Test fun nonChainNeighbourIsNeverPicked() {
        // (1,3) sits above the last cell (2,3) but is not in the chain; (2,2) is
        assertEquals(2 to 2, ChainBadge.spot(listOf(2 to 1, 2 to 2, 2 to 3)))
        // last (4,3): (3,3) above is in the chain, (4,4) is not a neighbour pick candidate
        val spot = ChainBadge.spot(listOf(3 to 3, 4 to 3))
        assertEquals(3 to 3, spot)
    }

    @Test fun loopBackPicksPreferredDirection() {
        // last (1,1); chain also has (0,1) above and (1,0) left
        assertEquals(0 to 1, ChainBadge.spot(listOf(1 to 0, 0 to 0, 0 to 1, 1 to 1)))
    }
}