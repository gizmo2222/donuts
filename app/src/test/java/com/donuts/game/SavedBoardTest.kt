package com.donuts.game

import kotlin.random.Random
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SavedBoardTest {

    private fun seeded(rows: Int, cols: Int, seed: Int): GameBoard {
        val b = GameBoard(rows, cols, Random(seed))
        // Make the saved data interesting: golden flags and cleared counts
        b.grid[0][0] = b.grid[0][0].copy(isGolden = true)
        b.grid[rows - 1][cols - 1] = b.grid[rows - 1][cols - 1].copy(isGolden = true)
        DonutType.values().forEachIndexed { i, t -> b.donutsCleared[t] = i * 3 + 1 }
        return b
    }

    private fun roundTrip(rows: Int, cols: Int) {
        val src = seeded(rows, cols, 11)
        val dst = GameBoard(rows, cols, Random(99))
        assertTrue(dst.restore(src.cellCodes(), src.clearedCounts()))
        for (r in 0 until rows) for (c in 0 until cols) {
            assertEquals(src.grid[r][c].type, dst.grid[r][c].type)
            assertEquals(src.grid[r][c].isGolden, dst.grid[r][c].isGolden)
            assertEquals(r, dst.grid[r][c].row)
            assertEquals(c, dst.grid[r][c].col)
        }
        assertArrayEquals(src.clearedCounts(), dst.clearedCounts())
        assertTrue(dst.grid[0][0].isGolden)
    }

    @Test fun roundTrip6x6() = roundTrip(6, 6)
    @Test fun roundTrip8x8() = roundTrip(8, 8)

    private fun assertUnchanged(b: GameBoard, codes: IntArray, cleared: IntArray) {
        assertArrayEquals(codes, b.cellCodes())
        assertArrayEquals(cleared, b.clearedCounts())
    }

    @Test fun restoreRejectsWrongLengths() {
        val b = seeded(6, 6, 3)
        val codes = b.cellCodes(); val cleared = b.clearedCounts()
        val other = GameBoard(6, 6, Random(5))
        val before = other.cellCodes(); val beforeCleared = other.clearedCounts()
        assertFalse(other.restore(codes.copyOf(35), cleared))
        assertFalse(other.restore(codes + 0, cleared))
        assertFalse(other.restore(codes, cleared.copyOf(cleared.size - 1)))
        assertUnchanged(other, before, beforeCleared)
    }

    @Test fun restoreRejectsOutOfRangeCode() {
        val b = seeded(6, 6, 3)
        val cleared = b.clearedCounts()
        val other = GameBoard(6, 6, Random(5))
        val before = other.cellCodes(); val beforeCleared = other.clearedCounts()
        val bad = b.cellCodes().also { it[10] = DonutType.values().size }
        assertFalse(other.restore(bad, cleared))
        val badNeg = b.cellCodes().also { it[10] = -1 }
        assertFalse(other.restore(badNeg, cleared))
        assertFalse(other.restore(b.cellCodes(), cleared.also { it[0] = -1 }))
        assertUnchanged(other, before, beforeCleared)
    }

    /** A 6x6 board where no two touching cells (even diagonally) share a type, so no chain exists. */
    private fun quietBoard(): GameBoard {
        val b = GameBoard(6, 6, Random(1))
        val t = DonutType.values()
        for (r in 0 until 6) for (c in 0 until 6)
            b.grid[r][c] = GameCell(t[(r % 2) * 3 + c % 3], r, c)
        return b
    }

    @Test fun noValidMovesWhenNothingConnects() {
        assertFalse(quietBoard().hasValidMoves())
    }

    @Test fun validMovesOnPlantedTrio() {
        val b = quietBoard()
        val t = DonutType.STRAWBERRY
        for (c in 0..2) b.grid[2][c] = GameCell(t, 2, c)
        assertTrue(b.hasValidMoves())
    }
}
