package com.donuts.game

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The drought assist inside the game: counted when the board is still, fed to the refill, reset by New game. */
@RunWith(RobolectricTestRunner::class)
class DroughtAssistViewTest {

    private fun playToIdle(v: GameView, c: android.graphics.Canvas) {
        TestViews.playHintChain(v)
        var frames = 0
        while (frames++ < 800) { TestViews.frames(v, c, 1); if (v.animPhase == AnimPhase.IDLE) break }
        assertEquals(AnimPhase.IDLE, v.animPhase)
    }

    @Test
    fun aDroughtNudgesTheRefillAndIsCountedWhenTheBoardIsStill() {
        val v = TestViews.quietView()
        val c = TestViews.canvas(v)
        TestViews.frames(v, c, 3)
        v.droughtDry = DroughtAssist.DRY_CHAINS
        playToIdle(v, c)
        assertEquals(DroughtAssist.NUDGE, v.board.nudge, 0.0)
        val expected = if (v.board.hasChainOf(DroughtAssist.LONG)) 0 else DroughtAssist.DRY_CHAINS
        assertEquals(expected, v.droughtDry)
    }

    @Test
    fun aChainOnABoardWithoutALongChainCountsOneDryChain() {
        val v = TestViews.quietView()
        val c = TestViews.canvas(v)
        TestViews.frames(v, c, 3)
        playToIdle(v, c)
        assertEquals(0.0, v.board.nudge, 0.0)
        assertEquals(if (v.board.hasChainOf(DroughtAssist.LONG)) 0 else 1, v.droughtDry)
    }

    @Test
    fun newGameEndsTheDroughtAndTheNextClearIsPlain() {
        val v = TestViews.quietView()
        val c = TestViews.canvas(v)
        TestViews.frames(v, c, 3)
        v.droughtDry = DroughtAssist.DRY_CHAINS
        v.startOver()
        assertEquals(0, v.droughtDry)
        TestViews.frames(v, c, 60)
        playToIdle(v, c)
        assertEquals(0.0, v.board.nudge, 0.0)
    }
}
