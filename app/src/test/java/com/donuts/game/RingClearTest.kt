package com.donuts.game

import com.donuts.game.BoardFixtures.boardOf
import org.junit.Assert.*
import org.junit.Test

/** [GameBoard.peekChainClear] with a closed ring (no Android needed). S = strawberry, C = chocolate. */
class RingClearTest {
    private val ring6 = listOf(0 to 0, 0 to 1, 0 to 2, 1 to 2, 1 to 1, 1 to 0)

    private fun board() = boardOf(
        "SSSC",
        "SSSC",
        "CCCS",
        "SCCC")

    @Test
    fun aRingClearsEveryOtherCellOfItsType() {
        val r = board().peekChainClear(ring6, looped = true)!!
        assertEquals(PowerUp.RING, r.powerUp)
        assertEquals(DonutType.STRAWBERRY, r.chainType)
        assertEquals(ring6, r.chainCells)
        assertEquals(setOf(2 to 3, 3 to 0), r.bonusCells.toSet())
    }

    @Test
    fun theSameChainUnloopedIsABomb() =
        assertEquals(PowerUp.BOMB, board().peekChainClear(ring6)!!.powerUp)

    @Test
    fun aRingWinsOverEveryLengthTier() {
        val b = boardOf("SSSSS", "SSSSS", "CCCCS")
        val ring10 = (0..4).map { 0 to it } + (4 downTo 0).map { 1 to it }
        assertEquals(PowerUp.COLOR_BURST, b.peekChainClear(ring10)!!.powerUp)
        val r = b.peekChainClear(ring10, looped = true)!!
        assertEquals(PowerUp.RING, r.powerUp)
        assertEquals(listOf(2 to 4), r.bonusCells)
    }

    @Test
    fun aGoldenFirstCellTakesTheTypeOfTheRestOfTheRing() {
        val b = boardOf("cCCS", "CCCS", "SSSC")
        val r = b.peekChainClear(ring6, looped = true)!!
        assertEquals(DonutType.CHOCOLATE, r.chainType)
        assertEquals(setOf(2 to 3), r.bonusCells.toSet())
    }

    @Test
    fun aMixedRingIsRejected() {
        val b = boardOf("SSSC", "SSCC", "CCCC")
        assertNull(b.peekChainClear(ring6, looped = true))
    }
}
