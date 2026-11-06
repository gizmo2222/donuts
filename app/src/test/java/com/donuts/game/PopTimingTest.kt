package com.donuts.game

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Unit tests for [PopTiming] (no Android needed). S = strawberry, C = chocolate. */
class PopTimingTest {

    private fun boardOf(vararg rows: String): GameBoard {
        val board = GameBoard(rows.size, rows[0].length, Random(7))
        rows.forEachIndexed { r, line ->
            line.forEachIndexed { c, ch ->
                val t = if (ch == 'S') DonutType.STRAWBERRY else DonutType.CHOCOLATE
                board.grid[r][c] = GameCell(t, r, c)
            }
        }
        return board
    }

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
}
