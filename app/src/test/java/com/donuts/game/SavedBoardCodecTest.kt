package com.donuts.game

import kotlin.random.Random
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SavedBoardCodecTest {

    private fun seeded(n: Int): GameBoard {
        val b = GameBoard(n, n, Random(7))
        b.grid[0][0] = b.grid[0][0].copy(isGolden = true)
        DonutType.values().forEachIndexed { i, t -> b.donutsCleared[t] = i * 2 + 1 }
        return b
    }

    @Test fun roundTripThroughBoard() {
        for (n in intArrayOf(6, 8)) {
            val src = seeded(n)
            val text = SavedBoard(n, src.cellCodes(), src.clearedCounts(), 5).encode()
            val back = SavedBoard.decode(text)
            assertNotNull(back)
            assertEquals(n, back!!.size)
            assertEquals(5, back.sessionSaved)
            val dst = GameBoard(n, n, Random(1))
            assertTrue(dst.restore(back.cells, back.cleared))
            assertArrayEquals(src.cellCodes(), dst.cellCodes())
            assertArrayEquals(src.clearedCounts(), dst.clearedCounts())
        }
    }

    @Test fun nullEmptyGarbageGiveNull() {
        assertNull(SavedBoard.decode(null))
        assertNull(SavedBoard.decode(""))
        assertNull(SavedBoard.decode("hello"))
        assertNull(SavedBoard.decode("v1;6;0;a,b;1"))
    }

    @Test fun wrongVersionOrCellCountGivesNull() {
        val b = seeded(6)
        val good = SavedBoard(6, b.cellCodes(), b.clearedCounts(), 0).encode()
        assertNull(SavedBoard.decode(good.replaceFirst("v1", "v2")))
        assertNull(SavedBoard.decode(SavedBoard(6, b.cellCodes().copyOf(35), b.clearedCounts(), 0).encode()))
        assertNull(SavedBoard.decode(SavedBoard(8, b.cellCodes(), b.clearedCounts(), 0).encode()))
    }
}
