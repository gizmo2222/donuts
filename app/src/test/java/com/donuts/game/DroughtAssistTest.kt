package com.donuts.game

import com.donuts.game.BoardFixtures.boardOf
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** [DroughtAssist] counting, [GameBoard.hasChainOf] and the nudged refill. Letters as in [BoardFixtures]. */
class DroughtAssistTest {

    // ---------------------------------------------------------------- the counter

    @Test
    fun dryChainsCountUpToTheLimitAndALongChainEndsTheDrought() {
        var dry = 0
        repeat(DroughtAssist.DRY_CHAINS - 1) { dry = DroughtAssist.after(dry, longPossible = false) }
        assertEquals(DroughtAssist.DRY_CHAINS - 1, dry)
        assertEquals(0.0, DroughtAssist.nudge(dry), 0.0)
        dry = DroughtAssist.after(dry, longPossible = false)
        assertEquals(DroughtAssist.NUDGE, DroughtAssist.nudge(dry), 0.0)
        repeat(10) { dry = DroughtAssist.after(dry, longPossible = false) }
        assertEquals("stops at the limit", DroughtAssist.DRY_CHAINS, dry)
        assertEquals(0, DroughtAssist.after(dry, longPossible = true))
        assertEquals(1, DroughtAssist.after(-3, longPossible = false))
    }

    // ---------------------------------------------------------------- hasChainOf

    /** 5x5, type = (col - row) mod 6: same types touch only along the down-right diagonal; the main one is S. */
    private val diagonal = arrayOf("SCBVM", "ASCBV", "MASCB", "VMASC", "BVMAS")

    @Test
    fun aDiagonalOfFiveIsALongChainAndNothingLonger() {
        val b = boardOf(*diagonal)
        assertTrue(b.hasChainOf(5))
        assertFalse(b.hasChainOf(6))
    }

    @Test
    fun breakingTheDiagonalLeavesOnlyChainsOfFour() {
        val rows = diagonal.copyOf(); rows[2] = "MAVCB"
        val b = boardOf(*rows)
        assertFalse(b.hasChainOf(5))
        assertTrue(b.hasChainOf(4))
    }

    @Test
    fun goldenCellsAreWildButCannotJoinTwoTypes() {
        assertTrue(boardOf("SSsSS").hasChainOf(5))
        assertTrue(boardOf("sssss").hasChainOf(5))
        assertFalse(boardOf("SSsCC").hasChainOf(5))
        assertTrue(boardOf("SSsCC").hasChainOf(3))
        assertFalse(boardOf("SCSCS").hasChainOf(2))
    }

    @Test
    fun aRealBoardAgreesWithTheHintAboutThrees() {
        for (seed in 1..30) {
            val b = GameBoard(6, 6, Random(seed))
            assertEquals("seed $seed", b.findHint().isNotEmpty(), b.hasChainOf(3))
        }
    }

    // ---------------------------------------------------------------- the nudged refill

    private fun clearTopMiddle(b: GameBoard, type: DonutType) =
        b.clearChain(ChainResult(listOf(Pair(0, 1)), emptyList(), PowerUp.NONE, type))

    @Test
    fun aFullNudgeCopiesANeighbourButNeverMakesAStraightThree() {
        // Copying S into the gap would make S S S across the top, so only C or B may come in
        for (seed in 1..40) {
            val b = boardOf("SAS", "CBC", random = Random(seed))
            b.goldenChance = 0.0; b.nudge = 1.0
            clearTopMiddle(b, DonutType.CARAMEL)
            val t = b.grid[0][1].type
            assertTrue("seed $seed gave $t", t == DonutType.CHOCOLATE || t == DonutType.BLUEBERRY)
            assertEquals(1, b.donutsCleared[DonutType.CARAMEL])
        }
    }

    @Test
    fun goldenNeighboursAreNotCopied() {
        for (seed in 1..40) {
            val b = boardOf("sA", "CB", random = Random(seed))
            b.goldenChance = 0.0; b.nudge = 1.0
            clearTopMiddle(b, DonutType.CARAMEL)
            val t = b.grid[0][1].type
            assertTrue("seed $seed gave $t", t == DonutType.CHOCOLATE || t == DonutType.BLUEBERRY)
            assertTrue("the golden cell stays", b.grid[0][0].isGolden)
        }
    }

    @Test
    fun noNudgeRefillsExactlyAsBefore() {
        val plain = GameBoard(6, 6, Random(5))
        val zero = GameBoard(6, 6, Random(5)).also { it.nudge = 0.0 }
        repeat(8) {
            val hint = plain.findHint()
            val res = plain.peekChainClear(hint) ?: return
            plain.clearChain(res); zero.clearChain(zero.peekChainClear(hint)!!)
            assertEquals(BoardFixtures.letters(plain), BoardFixtures.letters(zero))
        }
    }

    @Test
    fun aNudgedRefillMakesLongChainsMoreOften() {
        fun longShare(nudge: Double): Int {
            var long = 0
            for (seed in 1..60) {
                val b = GameBoard(6, 6, Random(seed)); b.nudge = nudge
                repeat(6) {
                    val res = b.peekChainClear(b.findHint())
                    if (res != null) { b.clearChain(res); b.settle() }
                    if (b.hasChainOf(DroughtAssist.LONG)) long++
                }
            }
            return long
        }
        val plain = longShare(0.0); val nudged = longShare(DroughtAssist.NUDGE)
        assertTrue("plain $plain, nudged $nudged", nudged > plain)
    }
}
