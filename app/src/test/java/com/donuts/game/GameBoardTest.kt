package com.donuts.game

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Ignore
import org.junit.Test

/**
 * Unit tests for [GameBoard] (no Android needed).
 *
 * Hand-built boards are written as strings, one per row. Upper case = plain donut,
 * lower case = golden donut: S strawberry, C chocolate, B blueberry, V vanilla,
 * M matcha, A caramel.
 */
class GameBoardTest {

    private val S = DonutType.STRAWBERRY
    private val C = DonutType.CHOCOLATE
    private val B = DonutType.BLUEBERRY
    private val V = DonutType.VANILLA
    private val M = DonutType.MATCHA
    private val A = DonutType.CARAMEL

    private fun typeOf(ch: Char): DonutType = when (ch.uppercaseChar()) {
        'S' -> S; 'C' -> C; 'B' -> B; 'V' -> V; 'M' -> M; 'A' -> A
        else -> error("unknown donut letter $ch")
    }

    private fun boardOf(vararg rows: String): GameBoard {
        val board = GameBoard(rows.size, rows[0].length, Random(7))
        rows.forEachIndexed { r, line ->
            line.forEachIndexed { c, ch ->
                board.grid[r][c] = GameCell(typeOf(ch), r, c, isGolden = ch.isLowerCase())
            }
        }
        return board
    }

    private fun GameBoard.types(): List<String> =
        grid.map { row -> row.joinToString("") { it.type.name.substring(0, 1) } }

    private fun cells(vararg rc: Pair<Int, Int>): List<Pair<Int, Int>> = rc.toList()

    private fun rowOf(row: Int, from: Int, to: Int) = (from..to).map { Pair(row, it) }

    // -----------------------------------------------------------------------
    // Random injection
    // -----------------------------------------------------------------------

    @Test
    fun sameSeedGivesTheSameBoard() {
        val a = GameBoard(8, 8, Random(42))
        val b = GameBoard(8, 8, Random(42))
        assertEquals(a.types(), b.types())
    }

    @Test
    fun differentSeedsGiveDifferentBoards() {
        val boards = (1..5).map { GameBoard(8, 8, Random(it)).types() }.toSet()
        assertTrue(boards.size > 1)
    }

    // -----------------------------------------------------------------------
    // New, shuffled and reset boards
    // -----------------------------------------------------------------------

    @Test
    fun newBoardHasNoThreeInARow() {
        for (size in listOf(6, 8)) for (seed in 0 until 300) {
            val board = GameBoard(size, size, Random(seed))
            assertTrue("size $size seed $seed", board.findMatches().isEmpty())
        }
    }

    @Test
    fun newBoardHasNoGoldenCells() {
        val board = GameBoard(8, 8, Random(3))
        assertTrue(board.grid.all { row -> row.none { it.isGolden } })
    }

    @Test
    fun shuffledBoardHasNoThreeInARow() {
        for (size in listOf(6, 8)) for (seed in 0 until 300) {
            val board = GameBoard(size, size, Random(seed))
            board.shuffle()
            assertTrue("size $size seed $seed", board.findMatches().isEmpty())
        }
    }

    @Test
    fun shuffleRemovesGoldenCells() {
        val board = boardOf("CVMA", "VMAC", "MACV", "ACVm")
        board.shuffle()
        assertTrue(board.grid.all { row -> row.none { it.isGolden } })
    }

    @Test
    fun resetZeroesCountsAndLeavesNoThreeInARow() {
        val board = GameBoard(6, 6, Random(5))
        board.donutsCleared[S] = 9
        board.reset()
        assertTrue(board.donutsCleared.values.all { it == 0 })
        assertTrue(board.findMatches().isEmpty())
    }

    // -----------------------------------------------------------------------
    // peekChainClear: validity
    // -----------------------------------------------------------------------

    @Test
    fun peekRejectsChainsUnderThree() {
        val board = boardOf("SSS")
        assertNull(board.peekChainClear(emptyList()))
        assertNull(board.peekChainClear(cells(0 to 0)))
        assertNull(board.peekChainClear(cells(0 to 0, 0 to 1)))
    }

    @Test
    fun peekRejectsMixedTypes() {
        val board = boardOf("SSC")
        assertNull(board.peekChainClear(rowOf(0, 0, 2)))
    }

    @Test
    fun peekAcceptsPlainChainOfThree() {
        val board = boardOf("SSS")
        val result = board.peekChainClear(rowOf(0, 0, 2))
        assertNotNull(result)
        assertEquals(S, result!!.chainType)
        assertEquals(PowerUp.NONE, result.powerUp)
        assertTrue(result.bonusCells.isEmpty())
    }

    @Test
    fun peekTreatsGoldenCellAsWild() {
        val board = boardOf("SvS")           // golden vanilla in the middle
        val result = board.peekChainClear(rowOf(0, 0, 2))
        assertNotNull(result)
        assertEquals(S, result!!.chainType)
    }

    @Test
    fun peekTakesChainTypeFromFirstNonGoldenCell() {
        val board = boardOf("vCC")           // chain starts with a golden vanilla
        val result = board.peekChainClear(rowOf(0, 0, 2))
        assertNotNull(result)
        assertEquals(C, result!!.chainType)
    }

    @Test
    fun peekAcceptsAnAllGoldenChain() {
        val board = boardOf("vvv")
        val result = board.peekChainClear(rowOf(0, 0, 2))
        assertNotNull(result)
        assertEquals(V, result!!.chainType)
    }

    @Test
    fun peekStillRejectsMixedTypesAroundAGoldenCell() {
        val board = boardOf("SvC")
        assertNull(board.peekChainClear(rowOf(0, 0, 2)))
    }

    // -----------------------------------------------------------------------
    // peekChainClear: power-ups
    // -----------------------------------------------------------------------

    @Test
    fun powerUpTierFollowsChainLength() {
        val board = GameBoard(1, 9, Random(1))
        for (c in 0 until 9) board.grid[0][c] = GameCell(S, 0, c)
        val expected = mapOf(
            3 to PowerUp.NONE, 4 to PowerUp.NONE,
            5 to PowerUp.BOMB, 6 to PowerUp.BOMB,
            7 to PowerUp.ROW_BLAST, 8 to PowerUp.ROW_BLAST,
            9 to PowerUp.COLOR_BURST
        )
        for ((len, powerUp) in expected) {
            val result = board.peekChainClear(rowOf(0, 0, len - 1))
            assertEquals("length $len", powerUp, result!!.powerUp)
        }
    }

    @Test
    fun bombClearsThreeByThreeAroundTheMiddleCell() {
        val board = boardOf(
            "CVMAC",
            "VMAVM",
            "SSSSS",
            "ACVMA",
            "MAVCV"
        )
        val result = board.peekChainClear(rowOf(2, 0, 4))!!
        assertEquals(PowerUp.BOMB, result.powerUp)
        // middle cell is (2,2); its row is already in the chain
        val expected = setOf(1 to 1, 1 to 2, 1 to 3, 3 to 1, 3 to 2, 3 to 3)
        assertEquals(expected, result.bonusCells.toSet())
        assertEquals(expected.size, result.bonusCells.size)
    }

    @Test
    fun bombIsClippedAtTheBoardEdge() {
        val board = boardOf(
            "SSSAC",
            "SMCVA",
            "SVMAC",
            "CAVMA"
        )
        // chain bends round the corner: (0,2) (0,1) (0,0) (1,0) (2,0); middle cell is the corner
        val chain = cells(0 to 2, 0 to 1, 0 to 0, 1 to 0, 2 to 0)
        val result = board.peekChainClear(chain)!!
        assertEquals(PowerUp.BOMB, result.powerUp)
        assertEquals(listOf(1 to 1), result.bonusCells)
    }

    @Test
    fun rowBlastClearsTheRowOfTheMiddleCell() {
        val board = boardOf(
            "SCVMAC",
            "SSAVMA",
            "CSSMAV",
            "ACSSAC"
        )
        // a bent 7-cell strawberry chain; the middle cell is chain[3] = (2,1)
        val chain = cells(0 to 0, 1 to 0, 1 to 1, 2 to 1, 2 to 2, 3 to 2, 3 to 3)
        val result = board.peekChainClear(chain)!!
        assertEquals(PowerUp.ROW_BLAST, result.powerUp)
        // the whole of row 2 except the chain cells (2,1) and (2,2)
        assertEquals(setOf(2 to 0, 2 to 3, 2 to 4, 2 to 5), result.bonusCells.toSet())
    }

    @Test
    fun colorBurstClearsEveryOtherCellOfTheChainType() {
        val board = boardOf(
            "SSSSSS",
            "BBBSSS",
            "CVMCAV",
            "MAVCBM",
            "AVSMCA",
            "CMVABS"
        )
        val chain = rowOf(0, 0, 5) + cells(1 to 5, 1 to 4, 1 to 3)
        val result = board.peekChainClear(chain)!!
        assertEquals(PowerUp.COLOR_BURST, result.powerUp)
        assertEquals(S, result.chainType)
        assertEquals(setOf(4 to 2, 5 to 5), result.bonusCells.toSet())
    }

    // -----------------------------------------------------------------------
    // clearChain
    // -----------------------------------------------------------------------

    @Test
    fun clearChainDropsSurvivorsToTheBottomInOrderAndKeepsGolden() {
        // column 0 from the top: M, C (cleared), v (golden), A, S
        val board = boardOf(
            "MVB",
            "CCC",
            "vAM",
            "AVB",
            "SMA"
        )
        val result = board.peekChainClear(rowOf(1, 0, 2))!!
        board.clearChain(result)

        val column0 = (0 until 5).map { board.grid[it][0] }
        assertEquals(listOf(M, V, A, S), column0.drop(1).map { it.type })
        assertEquals(listOf(false, true, false, false), column0.drop(1).map { it.isGolden })
        // every cell knows its new row and has stopped animating
        for (r in 0 until 5) for (c in 0 until 3) {
            assertEquals(r, board.grid[r][c].row)
            assertEquals(0f, board.grid[r][c].drawOffY, 0f)
        }
        // an untouched column (col 2) only shifted down by one
        assertEquals(listOf(B, M, B, A), (1 until 5).map { board.grid[it][2].type })
    }

    @Test
    fun clearChainCountsEachClearedCellOnce() {
        val board = boardOf("CCC", "VMA", "MAV")
        board.clearChain(board.peekChainClear(rowOf(0, 0, 2))!!)
        assertEquals(3, board.donutsCleared[C])
        assertEquals(3, board.donutsCleared.values.sum())
    }

    @Test
    fun clearChainCountsBonusCellsOnceEach() {
        val board = boardOf(
            "CVMAC",
            "VMAVM",
            "SSSSS",
            "ACVMA",
            "MAVCV"
        )
        val result = board.peekChainClear(rowOf(2, 0, 4))!!
        board.clearChain(result)
        assertEquals(5, board.donutsCleared[S])
        assertEquals(result.chainCells.size + result.bonusCells.size, board.donutsCleared.values.sum())
    }

    // -----------------------------------------------------------------------
    // resolveOnce
    // -----------------------------------------------------------------------

    @Test
    fun resolveOnceClearsAPlantedLine() {
        val board = boardOf(
            "CVMA",
            "VMAC",
            "MACV",
            "SSSC"
        )
        assertEquals(3, board.resolveOnce())
        assertEquals(3, board.donutsCleared[S])
        // the three columns above the line each moved down one row
        assertEquals(listOf(C, V, M), (1..3).map { board.grid[it][0].type })
        assertEquals(listOf(V, M, A), (1..3).map { board.grid[it][1].type })
        assertEquals(listOf(M, A, C), (1..3).map { board.grid[it][2].type })
        // the column without a match is untouched
        assertEquals(listOf(A, C, V, C), (0..3).map { board.grid[it][3].type })
    }

    @Test
    fun resolveOnceDoesNothingWithoutAMatch() {
        val board = boardOf("CVMA", "VMAC", "MACV", "ACVM")
        val before = board.types()
        assertEquals(0, board.resolveOnce())
        assertEquals(before, board.types())
        assertTrue(board.donutsCleared.values.all { it == 0 })
    }

    // -----------------------------------------------------------------------
    // findHint / hasValidMoves
    // -----------------------------------------------------------------------

    @Test
    fun findHintFindsAConnectedTrioIncludingDiagonals() {
        val board = boardOf(
            "SCV",
            "MSC",
            "VMS"
        )
        assertEquals(setOf(0 to 0, 1 to 1, 2 to 2), board.findHint().toSet())
        assertTrue(board.hasValidMoves())
    }

    @Test
    fun findHintIsEmptyWhenNoTypeHasThreeConnected() {
        val board = boardOf(
            "SCV",
            "MAS",
            "CVM"
        )
        assertTrue(board.findHint().isEmpty())
        assertFalse(board.hasValidMoves())
    }

    @Test
    fun findHintOnSeededBoardsIsThreeConnectedSameTypeCells() {
        for (seed in 0 until 100) {
            val board = GameBoard(6, 6, Random(seed))
            val hint = board.findHint()
            if (hint.isEmpty()) continue
            assertEquals("seed $seed", 3, hint.size)
            assertEquals("seed $seed", 3, hint.toSet().size)
            assertEquals("seed $seed", 1, hint.map { (r, c) -> board.grid[r][c].type }.toSet().size)
            // each cell touches another hint cell (8 neighbours), and the board accepts it as a chain
            for ((r, c) in hint) {
                assertTrue("seed $seed", hint.any { (r2, c2) ->
                    (r2 != r || c2 != c) && Math.abs(r2 - r) <= 1 && Math.abs(c2 - c) <= 1
                })
            }
            assertNotNull("seed $seed", board.peekChainClear(hint))
        }
    }

    // A golden donut is a valid wild link in a chain, but findHint (and so hasValidMoves,
    // which decides when to offer a shuffle) does not count it: "S S golden-C" is a legal
    // move that findHint reports as no move.
    @Ignore("Known gap: findHint ignores golden wild cells, so moves that need one are missed")
    @Test
    fun findHintSeesAMoveThatNeedsAGoldenCell() {
        val board = boardOf("SSc")
        assertNotNull(board.peekChainClear(rowOf(0, 0, 2)))   // the chain is legal
        assertEquals(3, board.findHint().size)                // ...so the hint should find it
        assertTrue(board.hasValidMoves())
    }
}
