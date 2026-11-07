package com.donuts.game

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * More [GameBoard] tests: gravity and spawn rules, flood fill edges, power-up tier boundaries,
 * per-type clear counts, settle, shuffle and restore with bad input. Same board notation as
 * [GameBoardTest]: upper case = plain, lower case = golden.
 */
class GameBoardMoreTest {

    private val S = DonutType.STRAWBERRY
    private val C = DonutType.CHOCOLATE
    private val B = DonutType.BLUEBERRY
    private val V = DonutType.VANILLA

    /** A Random whose ints are always 0 (so the first donut type) and whose doubles are [d]. */
    private class Fixed(private val d: Double) : Random() {
        override fun nextBits(bitCount: Int): Int = 0
        override fun nextDouble(): Double = d
    }

    private fun typeOf(ch: Char): DonutType = when (ch.uppercaseChar()) {
        'S' -> DonutType.STRAWBERRY; 'C' -> DonutType.CHOCOLATE; 'B' -> DonutType.BLUEBERRY
        'V' -> DonutType.VANILLA; 'M' -> DonutType.MATCHA; 'A' -> DonutType.CARAMEL
        else -> error("unknown donut letter $ch")
    }

    private fun boardOf(random: Random, vararg rows: String): GameBoard {
        val board = GameBoard(rows.size, rows[0].length, random)
        rows.forEachIndexed { r, line ->
            line.forEachIndexed { c, ch ->
                board.grid[r][c] = GameCell(typeOf(ch), r, c, isGolden = ch.isLowerCase())
            }
        }
        return board
    }

    private fun boardOf(vararg rows: String): GameBoard = boardOf(Random(7), *rows)

    private fun wholeBoard(b: GameBoard) =
        (0 until b.rows).flatMap { r -> (0 until b.cols).map { c -> Pair(r, c) } }

    private fun row(r: Int, from: Int, to: Int) = (from..to).map { Pair(r, it) }

    private fun letters(b: GameBoard) =
        b.grid.map { r -> r.joinToString("") { it.type.name.take(1) } }

    /** Three rows, ten columns; the middle row is all strawberry, with two more strawberries around it. */
    private fun tierBoard() = boardOf("CBCBCSCBCB", "SSSSSSSSSS", "CBCBCBCSCB")

    // ---------------------------------------------------------------- construction

    @Test
    fun defaultConstructorGivesAnEightByEightBoardWithoutMatches() {
        val b = GameBoard()
        assertEquals(8, b.rows)
        assertEquals(8, b.cols)
        assertTrue(b.findMatches().isEmpty())
        assertTrue(b.grid.all { r -> r.all { !it.isGolden } })
    }

    // ---------------------------------------------------------------- gravity and spawning

    @Test
    fun clearChainDropsSurvivorsAndResetsRowsAndOffsets() {
        val b = boardOf("CSB", "VSC", "BsV", "CSB")
        b.grid[0][0].drawOffY = 12f
        // Clear column 1 rows 1..3 (S, golden s, S): chain type S with a golden wild.
        val result = b.peekChainClear(listOf(Pair(1, 1), Pair(2, 1), Pair(3, 1)))!!
        assertTrue(b.clearChain(result))
        // Column 1: three new cells on top, the old top S survives at the bottom.
        assertEquals(S, b.grid[3][1].type)
        assertFalse(b.grid[3][1].isGolden)
        // Other columns keep their order.
        assertEquals(listOf(C, V, B, C), (0..3).map { b.grid[it][0].type })
        assertEquals(listOf(B, C, V, B), (0..3).map { b.grid[it][2].type })
        for (r in 0 until b.rows) for (c in 0 until b.cols) {
            assertEquals(r, b.grid[r][c].row)
            assertEquals(c, b.grid[r][c].col)
            assertEquals(0f, b.grid[r][c].drawOffY, 0f)
        }
    }

    @Test
    fun survivorsKeepTheirGoldenFlagWhenTheyFall() {
        val b = boardOf("sCB", "SCV", "SBC", "SVB")
        // Clear the three bottom S in column 0; the golden s on top falls to the bottom.
        val result = b.peekChainClear(listOf(Pair(1, 0), Pair(2, 0), Pair(3, 0)))!!
        b.clearChain(result)
        assertTrue(b.grid[3][0].isGolden)
        assertEquals(S, b.grid[3][0].type)
    }

    @Test
    fun spawnedCellsAreGoldenBelowTheChanceAndPlainAtOrAboveIt() {
        val allGolden = boardOf(Fixed(0.0), "SSS", "SSS", "SSS")
        allGolden.clearChain(ChainResult(wholeBoard(allGolden), emptyList(), PowerUp.NONE, S))
        assertTrue(allGolden.grid.flatten().all { it.isGolden })

        val justUnder = boardOf(Fixed(GameBoard.GOLDEN_CHANCE - 0.0001), "SSS", "SSS", "SSS")
        justUnder.clearChain(ChainResult(wholeBoard(justUnder), emptyList(), PowerUp.NONE, S))
        assertTrue(justUnder.grid.flatten().all { it.isGolden })

        val atChance = boardOf(Fixed(GameBoard.GOLDEN_CHANCE), "SSS", "SSS", "SSS")
        atChance.clearChain(ChainResult(wholeBoard(atChance), emptyList(), PowerUp.NONE, S))
        assertTrue(atChance.grid.flatten().none { it.isGolden })
    }

    @Test
    fun goldenSpawnRateIsAboutTheChanceOverManyRefills() {
        val b = GameBoard(8, 8, Random(2024))
        var golden = 0
        var total = 0
        repeat(300) {
            b.clearChain(ChainResult(wholeBoard(b), emptyList(), PowerUp.NONE, S))
            golden += b.grid.flatten().count { it.isGolden }
            total += b.rows * b.cols
        }
        val rate = golden.toDouble() / total
        assertTrue("rate $rate", rate in 0.03..0.05)
    }

    @Test
    fun resolveOnceRefillsOnlyTheClearedCells() {
        // Fixed(0.5) spawns plain strawberries, all in the top row after the bottom row clears.
        val b = boardOf(Fixed(0.5), "CBC", "BCB", "SSS")
        assertEquals(3, b.resolveOnce())
        assertEquals(listOf("SSS", "CBC", "BCB"), letters(b))
        assertTrue(b.grid.flatten().none { it.isGolden })
        assertEquals(3, b.donutsCleared[S])
    }

    // ---------------------------------------------------------------- flood fill (via findHint)

    @Test
    fun hintWalksADiagonalFromTheTopLeftCornerToTheBottomRightCorner() {
        val b = boardOf("SCB", "CSV", "BVS")
        assertEquals(listOf(Pair(0, 0), Pair(1, 1), Pair(2, 2)), b.findHint())
    }

    @Test
    fun hintFindsAnAntiDiagonalThroughTheOtherCorners() {
        val b = boardOf("BCS", "VSC", "SVB")
        assertEquals(setOf(Pair(0, 2), Pair(1, 1), Pair(2, 0)), b.findHint().toSet())
    }

    @Test
    fun hintDoesNotWrapAroundTheBoardEdges() {
        // S at (0,2), (1,0) and (2,1): (0,2) and (1,0) are two apart, so no group of three.
        val b = boardOf("CBS", "SMA", "VSB")
        assertTrue(b.findHint().isEmpty())
        assertFalse(b.hasValidMoves())
    }

    @Test
    fun hasValidMovesIsFalseWhenNoTypeHasThreeConnected() {
        assertFalse(boardOf("SCB", "VMA", "SCB").hasValidMoves())
    }

    @Test
    fun hasValidMovesIsTrueWithAnL() {
        assertTrue(boardOf("SSB", "CSV", "BVM").hasValidMoves())
    }

    // ---------------------------------------------------------------- power-up tiers

    private fun tierFor(n: Int): ChainResult = tierBoard().peekChainClear(row(1, 0, n - 1))!!

    @Test
    fun chainOfThreeAndFourHaveNoPowerUp() {
        for (n in 3..4) {
            val r = tierFor(n)
            assertEquals(PowerUp.NONE, r.powerUp)
            assertTrue(r.bonusCells.isEmpty())
            assertEquals(S, r.chainType)
        }
    }

    @Test
    fun chainOfFiveIsABombAroundTheMiddleCell() {
        val r = tierFor(5)
        assertEquals(PowerUp.BOMB, r.powerUp)
        // Middle is (1,2): rows 0 and 2, columns 1..3.
        assertEquals(setOf(Pair(0, 1), Pair(0, 2), Pair(0, 3), Pair(2, 1), Pair(2, 2), Pair(2, 3)), r.bonusCells.toSet())
        assertEquals(6, r.bonusCells.size)
    }

    @Test
    fun chainOfSixIsStillABombCentredOnIndexThree() {
        val r = tierFor(6)
        assertEquals(PowerUp.BOMB, r.powerUp)
        assertEquals(setOf(Pair(0, 2), Pair(0, 3), Pair(0, 4), Pair(2, 2), Pair(2, 3), Pair(2, 4)), r.bonusCells.toSet())
    }

    @Test
    fun chainOfSevenIsARowBlastOfTheRestOfTheRow() {
        val r = tierFor(7)
        assertEquals(PowerUp.ROW_BLAST, r.powerUp)
        assertEquals(row(1, 7, 9), r.bonusCells)
    }

    @Test
    fun chainOfEightIsStillARowBlast() {
        val r = tierFor(8)
        assertEquals(PowerUp.ROW_BLAST, r.powerUp)
        assertEquals(row(1, 8, 9), r.bonusCells)
    }

    @Test
    fun chainOfNineIsAColorBurstOfEveryOtherCellOfTheType() {
        val r = tierFor(9)
        assertEquals(PowerUp.COLOR_BURST, r.powerUp)
        // Row-major order: the strawberry at (0,5), the last one in the middle row, then (2,7).
        assertEquals(listOf(Pair(0, 5), Pair(1, 9), Pair(2, 7)), r.bonusCells)
    }

    @Test
    fun chainOfTenColorBurstLeavesOnlyTheLooseStrawberries() {
        val r = tierFor(10)
        assertEquals(PowerUp.COLOR_BURST, r.powerUp)
        assertEquals(listOf(Pair(0, 5), Pair(2, 7)), r.bonusCells)
    }

    @Test
    fun chainTypeComesFromTheFirstNonGoldenCellEvenWhenGoldenIsFirst() {
        val b = boardOf("cBV", "SBV", "SMA")
        // Golden C wild first, then S, S: the chain type is S.
        val r = b.peekChainClear(listOf(Pair(0, 0), Pair(1, 0), Pair(2, 0)))
        assertNotNull(r)
        assertEquals(S, r!!.chainType)
    }

    // ---------------------------------------------------------------- cleared counts

    @Test
    fun clearedCountsAreSplitPerTypeForChainAndBonusCells() {
        val b = tierBoard()
        b.clearChain(b.peekChainClear(row(1, 0, 4))!!)
        // Chain: five S. Bomb: row 0 cols 1..3 = B, C, B and row 2 cols 1..3 = B, C, B.
        val expected = IntArray(DonutType.values().size)
        expected[S.ordinal] = 5
        expected[B.ordinal] = 4
        expected[C.ordinal] = 2
        assertEquals(expected.toList(), b.clearedCounts().toList())
        assertEquals(11, b.clearedCounts().sum())
    }

    @Test
    fun goldenWildCellsCountUnderTheirOwnType() {
        val b = boardOf("SvS", "BCB", "CBC")
        b.clearChain(b.peekChainClear(row(0, 0, 2))!!)
        val counts = b.clearedCounts()
        assertEquals(2, counts[S.ordinal])
        assertEquals(1, counts[V.ordinal])
        assertEquals(3, counts.sum())
    }

    @Test
    fun clearedCountsAddUpAcrossSeveralClears() {
        val b = tierBoard()
        b.clearChain(b.peekChainClear(row(1, 0, 2))!!)
        assertEquals(3, b.clearedCounts().sum())
        // Clear the whole board as a chain: all 30 cells count once more.
        b.clearChain(ChainResult(wholeBoard(b), emptyList(), PowerUp.NONE, S))
        assertEquals(33, b.clearedCounts().sum())
    }

    // ---------------------------------------------------------------- settle

    @Test
    fun settleClearsPlantedMatchesAndCountsThem() {
        val b = boardOf("CBCB", "BCBC", "SSSV", "CBCB")
        assertTrue(b.findMatches().isNotEmpty())
        b.settle()
        assertTrue(b.findMatches().isEmpty())
        assertTrue(b.clearedCounts().sum() >= 3)
    }

    @Test
    fun settleLeavesAQuietBoardAlone() {
        val b = boardOf("SCB", "VMA", "SCB")
        val before = b.cellCodes().toList()
        b.settle()
        assertEquals(before, b.cellCodes().toList())
        assertEquals(0, b.clearedCounts().sum())
    }

    @Test
    fun settleGivesUpAfterFiftyPassesOnABoardThatAlwaysMatches() {
        // Every spawn is a plain strawberry, so every pass clears the whole 4x4 board again.
        val b = boardOf(Fixed(0.5), "SSSS", "SSSS", "SSSS", "SSSS")
        b.settle()
        assertEquals(50 * 16, b.clearedCounts().sum())
        assertTrue(b.findMatches().isNotEmpty())
    }

    // ---------------------------------------------------------------- shuffle

    @Test
    fun shuffleKeepsCountsAndSizeAndRewritesEveryCellPosition() {
        val b = GameBoard(8, 8, Random(5))
        b.clearChain(ChainResult(row(0, 0, 4), emptyList(), PowerUp.NONE, b.grid[0][0].type))
        val counts = b.clearedCounts().toList()
        b.shuffle()
        assertEquals(counts, b.clearedCounts().toList())
        assertEquals(8, b.grid.size)
        for (r in 0 until 8) for (c in 0 until 8) {
            assertEquals(r, b.grid[r][c].row)
            assertEquals(c, b.grid[r][c].col)
            assertEquals(0f, b.grid[r][c].drawOffY, 0f)
        }
        assertTrue(b.findMatches().isEmpty())
    }

    // ---------------------------------------------------------------- restore

    private fun snapshot(b: GameBoard) = Pair(b.cellCodes().toList(), b.clearedCounts().toList())

    private fun goodCleared() = IntArray(DonutType.values().size)

    @Test
    fun restoreRoundTripsTypesGoldenFlagsAndCounts() {
        val a = boardOf("SvB", "CMA", "bVS")
        a.donutsCleared[S] = 4
        a.donutsCleared[DonutType.CARAMEL] = 9
        val b = boardOf("SCB", "VMA", "SCB")
        assertTrue(b.restore(a.cellCodes(), a.clearedCounts()))
        assertEquals(snapshot(a), snapshot(b))
        assertTrue(b.grid[0][1].isGolden)
        assertTrue(b.grid[2][0].isGolden)
        assertFalse(b.grid[0][0].isGolden)
    }

    @Test
    fun restoreRejectsWrongSizesAndChangesNothing() {
        val b = boardOf("SCB", "VMA", "SCB")
        val before = snapshot(b)
        assertFalse(b.restore(IntArray(8), goodCleared()))
        assertFalse(b.restore(IntArray(10), goodCleared()))
        assertFalse(b.restore(IntArray(0), goodCleared()))
        assertFalse(b.restore(b.cellCodes(), IntArray(5)))
        assertFalse(b.restore(b.cellCodes(), IntArray(7)))
        assertEquals(before, snapshot(b))
    }

    @Test
    fun restoreRejectsUnknownCodesAndNegativeCountsAndChangesNothing() {
        val b = boardOf("SCB", "VMA", "SCB")
        val before = snapshot(b)
        for (bad in listOf(6, -1, 0x200, 0x100 or 6, 99)) {
            val codes = b.cellCodes()
            codes[4] = bad
            assertFalse("code $bad", b.restore(codes, goodCleared()))
        }
        val negative = goodCleared()
        negative[2] = -1
        assertFalse(b.restore(b.cellCodes(), negative))
        // Each count is fine but the total wraps past Int.MAX_VALUE
        assertFalse(b.restore(b.cellCodes(), intArrayOf(2_000_000_000, 2_000_000_000, 0, 0, 0, 0)))
        assertEquals(before, snapshot(b))
    }

    @Test
    fun restoreAcceptsEveryTypeWithAndWithoutTheGoldenBit() {
        val b = boardOf("SCBVMA", "SCBVMA")
        val codes = IntArray(12) { i -> (i % 6) or (if (i >= 6) GameBoard.GOLDEN_BIT else 0) }
        assertTrue(b.restore(codes, goodCleared()))
        for (i in 0 until 12) {
            val cell = b.grid[i / 6][i % 6]
            assertEquals(DonutType.values()[i % 6], cell.type)
            assertEquals(i >= 6, cell.isGolden)
        }
        assertEquals(codes.toList(), b.cellCodes().toList())
    }

    // ---------------------------------------------------------------- mutation-check additions

    @Test
    fun repairOfAWholeBoardOfOneTypePicksTheFirstTypeThatDoesNotMakeThree() {
        // Fixed random: every starting cell is the first type (strawberry) and every choice is the
        // first allowed one, so the repair is fully predictable.
        val b = GameBoard(3, 3, Fixed(0.99))
        assertEquals(listOf("SSC", "SSC", "CCS"), letters(b))
        assertTrue(b.findMatches().isEmpty())
    }

    @Test
    fun bombAtTheRightEdgeIsClippedToTheBoard() {
        val b = boardOf("CBS", "BCS", "CBS", "BCS", "CBS")
        val chain = (0..4).map { Pair(it, 2) }
        val result = b.peekChainClear(chain)!!
        assertEquals(PowerUp.BOMB, result.powerUp)
        // middle cell (2,2): the 3x3 block is columns 1..2 only, minus the chain cells
        assertEquals(listOf(Pair(1, 1), Pair(2, 1), Pair(3, 1)), result.bonusCells)
    }

    @Test
    fun bombAtTheBottomEdgeIsClippedToTheBoard() {
        val b = boardOf("CBCBC", "BCBCB", "SSSSS")
        val result = b.peekChainClear(row(2, 0, 4))!!
        assertEquals(PowerUp.BOMB, result.powerUp)
        // middle cell (2,2): the 3x3 block is rows 1..2 only, minus the chain cells
        assertEquals(listOf(Pair(1, 1), Pair(1, 2), Pair(1, 3)), result.bonusCells)
    }

    @Test
    fun hintFollowsAVerticalOnlyGroup() {
        val b = boardOf("S", "S", "S")
        assertEquals(listOf(Pair(0, 0), Pair(1, 0), Pair(2, 0)), b.findHint())
    }

    @Test
    fun hintListsTheStartCellThenItsRowNeighbourThenTheCellBelow() {
        val b = boardOf("SS", "SS")
        assertEquals(listOf(Pair(0, 0), Pair(0, 1), Pair(1, 0)), b.findHint())
    }
}
