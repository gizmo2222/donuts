package com.donuts.game

import com.donuts.game.BoardFixtures.boardOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Unit tests for [PopTiming] (no Android needed). S = strawberry, C = chocolate. */
class PopTimingTest {

    private fun resultOf(board: GameBoard, chain: List<Pair<Int, Int>>): ChainResult =
        board.peekChainClear(chain)!!

    private fun checkEveryCellHasAnEntry(r: ChainResult, d: Map<Pair<Int, Int>, Long>) {
        for (cell in r.chainCells + r.bonusCells) assertTrue("missing $cell", d.containsKey(cell))
    }

    @Test
    fun plainChainPopsAllAtOnce() {
        val board = boardOf("SSSSC", "CCCCC", "CCCCC")
        val r = resultOf(board, (0..3).map { 0 to it })
        assertEquals(PowerUp.NONE, r.powerUp)
        val d = PopTiming.delays(r)
        checkEveryCellHasAnEntry(r, d)
        assertTrue(d.values.all { it == 0L })
        assertEquals(280L, PopTiming.totalMs(d, 280L))
    }

    @Test
    fun bombOriginSwellsFirstThenNeighboursPopTogether() {
        val board = boardOf("CCCCCCC", "CSSSSSC", "CCCCCCC")
        val r = resultOf(board, (1..5).map { 1 to it })
        assertEquals(PowerUp.BOMB, r.powerUp)
        val lead = PopTiming.leadMs(PowerUp.BOMB)
        val d = PopTiming.delays(r)
        checkEveryCellHasAnEntry(r, d)
        assertEquals(lead, d[PopTiming.origin(r)])
        for (cell in r.chainCells) if (cell != PopTiming.origin(r)) assertEquals(0L, d[cell])
        assertTrue(r.bonusCells.isNotEmpty())
        for (cell in r.bonusCells) assertEquals(lead + PopTiming.STEP_MS, d[cell])
        assertEquals(lead + PopTiming.STEP_MS + 280L, PopTiming.totalMs(d, 280L))
    }

    @Test
    fun rowBlastGrowsWithColumnDistanceAndMatchesBothSides() {
        // Seven chain cells; the middle one (index 3) sits in row 1, so row 1 is blasted.
        val board = boardOf("SSSSSSCCCCC", "CCCCCSCCCCC", "CCCCCCCCCCC")
        val chain = listOf(0 to 0, 0 to 1, 0 to 2, 1 to 5, 0 to 3, 0 to 4, 0 to 5)
        val r = resultOf(board, chain)
        assertEquals(PowerUp.ROW_BLAST, r.powerUp)
        val lead = PopTiming.leadMs(PowerUp.ROW_BLAST)
        val d = PopTiming.delays(r)
        checkEveryCellHasAnEntry(r, d)
        for (dc in 1..5) {
            assertEquals(lead + dc * PopTiming.STEP_MS, d[1 to 5 - dc])
            assertEquals(d[1 to 5 - dc], d[1 to 5 + dc])
        }
        assertTrue(d[1 to 1]!! > d[1 to 2]!!)
    }

    @Test
    fun colorBurstBonusStartsAfterLeadAndRipplesOutward() {
        val board = boardOf(
            "SSSSSSSSSC", "CCCCCCCCCC", "CCCCSCCCCC", "CCCCCCCCCC", "SCCCCCCCCS")
        val r = resultOf(board, (0..8).map { 0 to it })
        assertEquals(PowerUp.COLOR_BURST, r.powerUp)
        val lead = PopTiming.leadMs(PowerUp.COLOR_BURST)
        val d = PopTiming.delays(r)
        checkEveryCellHasAnEntry(r, d)
        assertTrue(r.bonusCells.all { d[it]!! >= lead })
        val (or, oc) = PopTiming.origin(r)
        val sorted = r.bonusCells.sortedBy { maxOf(Math.abs(it.first - or), Math.abs(it.second - oc)) }
        val times = sorted.map { d[it]!! }
        assertEquals(times.sorted(), times)
        assertTrue(times.last() > times.first())
    }

    private fun result(chain: List<Pair<Int, Int>>, bonus: List<Pair<Int, Int>>, p: PowerUp) =
        ChainResult(chain, bonus, p, DonutType.STRAWBERRY)

    @Test fun leadTimes() {
        assertEquals(0L, PopTiming.leadMs(PowerUp.NONE))
        assertEquals(160L, PopTiming.leadMs(PowerUp.BOMB))
        assertEquals(60L, PopTiming.leadMs(PowerUp.ROW_BLAST))
        assertEquals(320L, PopTiming.leadMs(PowerUp.COLOR_BURST))
    }

    @Test fun originIsTheMiddleIndexForOddAndEvenLengths() {
        val three = listOf(0 to 0, 0 to 1, 0 to 2)
        assertEquals(0 to 1, PopTiming.origin(result(three, emptyList(), PowerUp.NONE)))
        val four = three + (0 to 3)
        assertEquals(0 to 2, PopTiming.origin(result(four, emptyList(), PowerUp.NONE)))
        val five = four + (0 to 4)
        assertEquals(0 to 2, PopTiming.origin(result(five, emptyList(), PowerUp.NONE)))
    }

    @Test fun bombOriginIsDelayedEvenWithNoBonusCells() {
        val chain = (0..4).map { 3 to it }
        val d = PopTiming.delays(result(chain, emptyList(), PowerUp.BOMB))
        assertEquals(160L, d[3 to 2])
        assertEquals(0L, d[3 to 0])
        assertEquals(0L, d[3 to 4])
    }

    @Test fun nonBombOriginIsNotDelayed() {
        val chain = (0..6).map { 3 to it }
        for (p in listOf(PowerUp.NONE, PowerUp.ROW_BLAST, PowerUp.COLOR_BURST)) {
            assertEquals(p.name, 0L, PopTiming.delays(result(chain, emptyList(), p))[3 to 3])
        }
    }

    @Test fun rowBlastUsesColumnDistanceOnly() {
        val chain = (0..6).map { 2 to it }          // origin (2,3)
        val bonus = listOf(2 to 5, 7 to 5, 0 to 3)  // same column distance in other rows; same column
        val d = PopTiming.delays(result(chain, bonus, PowerUp.ROW_BLAST))
        assertEquals(60L + 2 * 60L, d[2 to 5])
        assertEquals(60L + 2 * 60L, d[7 to 5])
        assertEquals(60L, d[0 to 3])
    }

    @Test fun bombAndBurstUseChebyshevDistance() {
        val chain = (0..8).map { 4 to it }          // origin (4,4)
        val bonus = listOf(4 to 6, 9 to 4, 7 to 7, 3 to 5)
        for (p in listOf(PowerUp.BOMB, PowerUp.COLOR_BURST)) {
            val lead = PopTiming.leadMs(p)
            val d = PopTiming.delays(result(chain, bonus, p))
            assertEquals(lead + 2 * 60L, d[4 to 6])   // column dominates
            assertEquals(lead + 5 * 60L, d[9 to 4])   // row dominates
            assertEquals(lead + 3 * 60L, d[7 to 7])   // equal row and column
            assertEquals(lead + 1 * 60L, d[3 to 5])
        }
    }

    @Test fun bonusDistanceIsSymmetricAroundOrigin() {
        val chain = (0..4).map { 4 to it }          // origin (4,2)
        val d = PopTiming.delays(result(chain, listOf(2 to 2, 6 to 2, 4 to 0, 4 to 4), PowerUp.BOMB))
        assertEquals(d[2 to 2], d[6 to 2])
        assertEquals(d[4 to 0], d[4 to 4])
        assertEquals(d[2 to 2], d[4 to 0])
    }

    @Test fun totalMsOfNothingIsJustThePop() {
        assertEquals(280L, PopTiming.totalMs(emptyMap(), 280L))
        assertEquals(0L, PopTiming.totalMs(emptyMap(), 0L))
    }

    @Test fun totalMsUsesTheLargestDelay() {
        val d = mapOf((0 to 0) to 0L, (0 to 1) to 500L, (0 to 2) to 120L)
        assertEquals(780L, PopTiming.totalMs(d, 280L))
    }
}
