package com.donuts.game

import org.junit.Assert.*
import org.junit.Test

class PraiseTest {
    @Test
    fun powerUpWordsComeFirstEvenWithAGoldenCell() {
        assertEquals("RING!", Praise.chainLabel(PowerUp.RING, 6, hasGolden = true))
        assertEquals("KABOOM!", Praise.chainLabel(PowerUp.COLOR_BURST, 9, hasGolden = true))
        assertEquals("WHOOSH!", Praise.chainLabel(PowerUp.ROW_BLAST, 7, hasGolden = true))
        assertEquals("BOOM!", Praise.chainLabel(PowerUp.BOMB, 5, hasGolden = true))
    }

    @Test
    fun aGoldenCellNamesItselfOnShortChains() {
        assertEquals("GOLDEN!", Praise.chainLabel(PowerUp.NONE, 3, hasGolden = true))
        assertEquals("GOLDEN!", Praise.chainLabel(PowerUp.NONE, 4, hasGolden = true))
    }

    @Test
    fun plainChainsGetNiceAtFourAndNothingAtThree() {
        assertEquals("NICE!", Praise.chainLabel(PowerUp.NONE, 4, hasGolden = false))
        assertNull(Praise.chainLabel(PowerUp.NONE, 3, hasGolden = false))
    }

    @Test
    fun theCheerComesOnEverySecondBigMoment() {
        assertEquals((0..5).map { it % 2 == 1 }, (0..5).map { Praise.cheers(it) })
        assertEquals("BOX FULL!", Praise.banner("BOX FULL!", 0))
        assertEquals("BOX FULL!\nGreat job, Steven!", Praise.banner("BOX FULL!", 1))
    }

    @Test
    fun floatWordsStayShortEnoughForAnEightByEightCell() {
        val words = listOf(PowerUp.COLOR_BURST, PowerUp.ROW_BLAST, PowerUp.BOMB, PowerUp.NONE)
            .flatMap { p -> listOf(true, false).mapNotNull { Praise.chainLabel(p, 4, it) } } + Praise.GIGGLE
        assertTrue(words.all { it.length <= 8 })
    }
}
