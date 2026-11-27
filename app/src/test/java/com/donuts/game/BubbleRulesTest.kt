package com.donuts.game

import com.donuts.game.BoardFixtures.boardOf
import com.donuts.game.BoardFixtures.rowOf
import org.junit.Assert.*
import org.junit.Test

/** [BubbleRules] and the bubble helpers on [GameBoard] (no Android needed). S C B V M A as in [BoardFixtures]. */
class BubbleRulesTest {
    private fun quiet() = boardOf(
        "SCBVMA",
        "CBVMAS",
        "BVMASC",
        "VMASCB",
        "MASCBV",
        "ASCBVM")

    @Test
    fun onlySixOrMoreOrARingEarnsABubble() {
        assertFalse(BubbleRules.earns(5, looped = false))
        assertTrue(BubbleRules.earns(6, looped = false))
        assertTrue(BubbleRules.earns(4, looped = true))
    }

    @Test
    fun aChainOfSixLeavesABubbleOnItsLastCellAfterTheDrop() {
        val b = boardOf("SSSSSS", "CBVMAC", "BVMACB")
        val chain = rowOf(0, 0, 5)
        val r = b.withBubbles(b.peekChainClear(chain)!!)
        assertEquals(0 to 5, r.bubbleAt)
        assertTrue(r.bursting.isEmpty())
        b.clearChain(r)
        assertEquals(listOf(0 to 5), b.bubbles())
    }

    @Test
    fun aChainOfFiveLeavesNoBubble() {
        val b = boardOf("SSSSSC", "CBVMAC", "BVMACB")
        assertNull(b.withBubbles(b.peekChainClear(rowOf(0, 0, 4))!!).bubbleAt)
    }

    @Test
    fun noThirdBubbleWhileTwoAreLeftOnTheBoard() {
        val b = boardOf("SSSSSS", "CBVMAC", "BVMACB")
        b.markBubble(2, 0); b.markBubble(2, 5)
        assertNull(b.withBubbles(b.peekChainClear(rowOf(0, 0, 5))!!).bubbleAt)
    }

    @Test
    fun aTapOnABubblePopsItsThreeByThree() {
        val b = quiet()
        b.markBubble(2, 2)
        val r = b.peekBubblePop(2, 2)!!
        assertEquals(PowerUp.BUBBLE, r.powerUp)
        assertEquals(listOf(2 to 2), r.chainCells)
        assertEquals(8, r.bonusCells.size)
        assertEquals(b.around(2, 2).toSet(), (r.chainCells + r.bonusCells).toSet())
        assertEquals(listOf(2 to 2), r.bursting)
    }

    @Test
    fun aBubbleInTheCornerPopsOnlyWhatIsOnTheBoard() {
        val b = quiet()
        b.markBubble(0, 0)
        val r = b.peekBubblePop(0, 0)!!
        assertEquals(setOf(0 to 0, 0 to 1, 1 to 0, 1 to 1), (r.chainCells + r.bonusCells).toSet())
    }

    @Test
    fun aTapOnAPlainDonutOrOffTheBoardIsNoPop() {
        val b = quiet()
        assertNull(b.peekBubblePop(2, 2))
        assertNull(b.peekBubblePop(-1, 0))
        assertNull(b.peekBubblePop(0, 6))
    }

    @Test
    fun aBurstReachesTheNextBubbleAndItBurstsToo() {
        val b = quiet()
        b.markBubble(1, 1); b.markBubble(2, 2)
        val r = b.peekBubblePop(1, 1)!!
        assertEquals(listOf(1 to 1, 2 to 2), r.bursting)
        assertEquals((b.around(1, 1) + b.around(2, 2)).toSet(), (r.chainCells + r.bonusCells).toSet())
    }

    @Test
    fun aBubbleTakenIntoAPlainChainBurstsAndMakesItABubbleClear() {
        val b = boardOf("SSSCBV", "CBVMAC", "BVMACB")
        b.markBubble(0, 1)
        val r = b.withBubbles(b.peekChainClear(rowOf(0, 0, 2))!!)
        assertEquals(PowerUp.BUBBLE, r.powerUp)
        assertEquals(listOf(0 to 1), r.bursting)
        assertEquals(setOf(1 to 0, 1 to 1, 1 to 2), r.bonusCells.toSet())
        assertNull(r.bubbleAt)
    }

    @Test
    fun aPowerUpKeepsItsKindWhenItBurstsABubble() {
        val b = boardOf("SSSSSC", "CBVMAC", "BVMACB")
        b.markBubble(2, 5)
        val r = b.withBubbles(b.peekChainClear(rowOf(0, 0, 4))!!)
        assertEquals(PowerUp.BOMB, r.powerUp)
        assertTrue(r.bursting.isEmpty())
    }

    @Test
    fun cascadesSkipBubbles() {
        val b = boardOf("SSSC", "CBVM", "BVMA")
        b.markBubble(0, 1)
        assertTrue(b.findMatches().isEmpty())
        b.grid[0][1] = b.grid[0][1].copy(isBubble = false)
        assertFalse(b.findMatches().isEmpty())
    }

    @Test
    fun shuffleKeepsBubblesAndResetClearsThem() {
        val b = GameBoard(6, 6, kotlin.random.Random(3))
        b.markBubble(1, 2); b.markBubble(4, 4)
        b.shuffle()
        assertEquals(listOf(1 to 2, 4 to 4), b.bubbles())
        b.reset()
        assertTrue(b.bubbles().isEmpty())
    }

    @Test
    fun bubblesAreSavedAndRestored() {
        val b = GameBoard(6, 6, kotlin.random.Random(5))
        b.markBubble(3, 1)
        b.grid[0][0] = b.grid[0][0].copy(isGolden = true, isBubble = true)
        val back = GameBoard(6, 6, kotlin.random.Random(9))
        assertTrue(back.restore(b.cellCodes(), b.clearedCounts()))
        assertEquals(listOf(0 to 0, 3 to 1), back.bubbles())
        assertTrue(back.grid[0][0].isGolden)
        assertEquals(b.grid.map { row -> row.map { it.type } }, back.grid.map { row -> row.map { it.type } })
    }
}
