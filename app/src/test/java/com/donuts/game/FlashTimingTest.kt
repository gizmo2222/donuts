package com.donuts.game

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** B12: the full-screen flashes, driven through the view as fast as it allows, stay at three a second or fewer. */
@RunWith(RobolectricTestRunner::class)
class FlashTimingTest : GameInputTestBase() {

    private val sixInARow = listOf("SSSSSS", "CBVMAC", "MAMBVC", "VCAVMB", "BMCABA", "AVBMCV")

    private fun plantSix(v: GameView) = sixInARow.forEachIndexed { r, line ->
        line.forEachIndexed { c, ch -> v.board.grid[r][c] = GameCell(BoardFixtures.typeOf(ch), r, c) }
    }

    private fun clearSix(v: GameView) = drag(v, *Array(6) { 0 to it })

    @Test fun hammeringNewGameFlashesAtMostThreeTimesASecond() {
        val v = TestViews.gameView()
        val starts = mutableListOf<Long>()
        repeat(60) {   // a New game every 50 ms for three seconds
            v.startOver()
            if (v.resetFlashMs >= 0 && starts.lastOrNull() != v.resetFlashMs) starts += v.resetFlashMs
            TestViews.advance(50)
        }
        assertTrue(starts.size >= 6)
        for (s in starts) assertTrue(starts.count { it >= s && it < s + 1000L } <= 3)
    }

    @Test fun aBigChainRightAfterNewGameSkipsItsFlash() {
        val v = TestViews.gameView()
        v.startOver()
        assertTrue(v.resetFlashMs >= 0)
        plantSix(v)
        clearSix(v)
        assertEquals(AnimPhase.POPPING, v.animPhase)
        assertEquals(-1L, v.chainFlashMs)
    }

    @Test fun aBigChainAfterTheGapStillFlashesAndNewGameRightAfterItDoesNot() {
        val v = TestViews.gameView()
        v.startOver()
        TestViews.advance(FlashGate.MIN_GAP_MS)
        plantSix(v)
        clearSix(v)
        assertTrue(v.chainFlashMs >= 0)
        val flashAt = v.lastFullFlashMs
        TestViews.advance(100)
        v.startOver()
        assertEquals(flashAt, v.lastFullFlashMs)
    }
}
