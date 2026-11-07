package com.donuts.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

    @Test fun spotIsAlwaysAnEarlierChainCellNextToTheLast() {
        // Random walks on an 8x8 board, as a finger draws them (8-neighbour steps, no repeats)
        val rnd = java.util.Random(7)
        repeat(500) {
            val chain = mutableListOf(rnd.nextInt(8) to rnd.nextInt(8))
            val len = 2 + rnd.nextInt(12)
            while (chain.size < len) {
                val (r, c) = chain.last()
                val next = (r + rnd.nextInt(3) - 1) to (c + rnd.nextInt(3) - 1)
                if (next.first !in 0..7 || next.second !in 0..7 || next in chain) {
                    if (rnd.nextInt(20) == 0) break else continue
                }
                chain += next
            }
            if (chain.size < 2) return@repeat
            val spot = ChainBadge.spot(chain)!!
            val (lr, lc) = chain.last()
            assertTrue(spot in chain.dropLast(1))
            assertTrue(Math.abs(spot.first - lr) <= 1 && Math.abs(spot.second - lc) <= 1)
        }
    }

    @Test fun loopBackPicksPreferredDirection() {
        // last (1,1); chain also has (0,1) above and (1,0) left
        assertEquals(0 to 1, ChainBadge.spot(listOf(1 to 0, 0 to 0, 0 to 1, 1 to 1)))
    }

    private val dirs = listOf(-1 to 0, -1 to -1, -1 to 1, 0 to -1, 0 to 1, 1 to -1, 1 to 1, 1 to 0)

    @Test fun eachDirectionIsFoundWhenItIsTheOnlyNeighbour() {
        for ((dr, dc) in dirs) {
            val other = (5 + dr) to (5 + dc)
            assertEquals("dir $dr,$dc", other, ChainBadge.spot(listOf(other, 5 to 5)))
        }
    }

    @Test fun preferenceOrderHoldsWhenSeveralNeighboursAreInTheChain() {
        for (i in dirs.indices) {
            // neighbours i.. are in the chain: the first of them in preference order wins
            val chain = dirs.drop(i).map { (5 + it.first) to (5 + it.second) } + (5 to 5)
            assertEquals(chain.first(), ChainBadge.spot(chain))
        }
    }

    @Test fun twoCellChainIsTheSmallestThatHasASpot() {
        assertEquals(0 to 0, ChainBadge.spot(listOf(0 to 0, 0 to 1)))
    }

    @Test fun nonAdjacentEarlierCellsGiveNull() {
        assertNull(ChainBadge.spot(listOf(0 to 0, 5 to 5)))
        assertNull(ChainBadge.spot(listOf(5 to 3, 5 to 5)))   // two columns away
        assertNull(ChainBadge.spot(listOf(3 to 5, 5 to 5)))   // two rows away
    }
}