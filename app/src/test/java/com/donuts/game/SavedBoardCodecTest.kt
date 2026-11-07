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

    private fun cellsText(n: Int) = (0 until n).joinToString(",") { "1" }

    @Test fun decodeRejectsMalformedWithoutThrowing() {
        val bad = listOf(
            " ", ";", ";;;;", "v1", "v1;2;0;1,1,1,1", "v1;2;0;1,1,1,1;;extra",
            "v1;x;0;1,1,1,1;", "v1;2;y;1,1,1,1;", "v1;2;0;1,1,1,z;", "v1;2;0;1,1,1,1;q",
            "v1;2;0;1,1,1,;", "v1;2;0;,1,1,1;", "v1;2;0;1,1,1,1,;", "v1;2.5;0;1,1,1,1;",
            "v1;2;0;1,1,1,99999999999;", "V1;2;0;1,1,1,1;", "v0;2;0;1,1,1,1;", "v10;2;0;1,1,1,1;"
        )
        for (s in bad) assertNull("'$s'", SavedBoard.decode(s))
    }

    @Test fun decodeRejectsNonPositiveOrMismatchedSize() {
        assertNull(SavedBoard.decode("v1;0;0;;"))
        assertNull(SavedBoard.decode("v1;-2;0;${cellsText(4)};"))
        assertNull(SavedBoard.decode("v1;2;0;${cellsText(3)};"))
        assertNull(SavedBoard.decode("v1;2;0;${cellsText(5)};"))
        assertNull(SavedBoard.decode("v1;1;0;;"))
    }

    @Test fun decodeAcceptsSmallestBoardAndEmptyCleared() {
        val b = SavedBoard.decode("v1;1;0;7;")!!
        assertEquals(1, b.size)
        assertArrayEquals(intArrayOf(7), b.cells)
        assertEquals(0, b.cleared.size)
        assertEquals(0, b.sessionSaved)
    }

    @Test fun decodeKeepsNegativeNumbersAndAnyClearedLength() {
        val b = SavedBoard.decode("v1;2;-3;1,-1,2,3;4,5,6")!!
        assertEquals(-3, b.sessionSaved)
        assertArrayEquals(intArrayOf(1, -1, 2, 3), b.cells)
        assertArrayEquals(intArrayOf(4, 5, 6), b.cleared)
    }

    @Test fun encodeDecodeRoundTripOfPlainData() {
        val sb = SavedBoard(3, IntArray(9) { it * 2 }, intArrayOf(), 12)
        assertEquals("v1;3;12;0,2,4,6,8,10,12,14,16;", sb.encode())
        val back = SavedBoard.decode(sb.encode())!!
        assertEquals(3, back.size)
        assertEquals(12, back.sessionSaved)
        assertArrayEquals(sb.cells, back.cells)
        assertEquals(0, back.cleared.size)
    }
}
