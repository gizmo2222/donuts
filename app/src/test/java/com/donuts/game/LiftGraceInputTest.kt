package com.donuts.game

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Lift grace on the planted board: a lifted chain waits, and a finger coming back near its end takes it back. */
@RunWith(RobolectricTestRunner::class)
class LiftGraceInputTest : GameInputTestBase() {
    /** Sends a touch at a cell's centre without letting a lifted chain's wait run out. */
    private fun raw(v: GameView, action: Int, row: Int, col: Int) {
        val (x, y) = TestViews.cellCenter(v, row, col)
        TestViews.touchRaw(v, action, x, y)
    }

    /** Two strawberries dragged, then the finger lifts: the chain is waiting. */
    private fun liftedTwo(): GameView {
        val v = planted()
        raw(v, DOWN, 0, 0); raw(v, MOVE, 0, 1); raw(v, UP, 0, 1)
        assertTrue(v.liftMs >= 0)
        assertEquals(listOf(0 to 0, 0 to 1), v.dragChain)
        assertTrue(v.wobbleCells.isEmpty())
        return v
    }

    @Test
    fun aLiftedTwoWaitsAndTheFingerBackOnItsEndCarriesOn() {
        val v = liftedTwo()
        TestViews.advance(LiftGrace.LONG_MS - 50); v.advanceLift(TestViews.now())
        assertEquals(2, v.dragChain.size)
        raw(v, DOWN, 0, 1)
        assertEquals(-1L, v.liftMs)
        assertEquals(listOf(0 to 0, 0 to 1), v.dragChain)
        raw(v, MOVE, 0, 2)
        assertEquals(3, v.dragChain.size)
        raw(v, UP, 0, 2)
        assertEquals(AnimPhase.IDLE, v.animPhase)       // three pop, after the short wait
        TestViews.frames(v, TestViews.canvas(v), 10)
        assertEquals(-1L, v.liftMs)
        assertNotEquals(AnimPhase.IDLE, v.animPhase)
        assertTrue(v.dragChain.isEmpty())
    }

    @Test
    fun aTouchOnTheNextDonutJoinsItStraightAway() {
        val v = liftedTwo()
        raw(v, DOWN, 0, 2)
        assertEquals(listOf(0 to 0, 0 to 1, 0 to 2), v.dragChain)
    }

    @Test
    fun whenTheWaitRunsOutTheTwoWobbleAsBefore() {
        val v = liftedTwo()
        TestViews.advance(LiftGrace.LONG_MS - 1); v.advanceLift(TestViews.now())
        assertTrue(v.liftMs >= 0)
        TestViews.advance(1); v.advanceLift(TestViews.now())
        assertEquals(-1L, v.liftMs)
        assertTrue(v.dragChain.isEmpty())
        assertEquals(listOf(0 to 0, 0 to 1), v.wobbleCells)
    }

    @Test
    fun aChainThatPopsWaitsOnlyTheShortGrace() {
        val v = planted()
        raw(v, DOWN, 0, 0); raw(v, MOVE, 0, 1); raw(v, MOVE, 0, 2); raw(v, UP, 0, 2)
        assertEquals(LiftGrace.SHORT_MS, v.liftGraceMs)
        assertEquals(AnimPhase.IDLE, v.animPhase)
        TestViews.advance(LiftGrace.SHORT_MS); v.advanceLift(TestViews.now())
        assertEquals(AnimPhase.POPPING, v.animPhase)
    }

    @Test
    fun aTouchOutOfReachReleasesTheOldChainAndStartsANewOne() {
        val v = liftedTwo()
        raw(v, DOWN, 5, 5)
        assertEquals(-1L, v.liftMs)
        assertEquals(listOf(0 to 0, 0 to 1), v.wobbleCells)
        assertEquals(listOf(5 to 5), v.dragChain)
    }

    @Test
    fun aStrayMoveWhileTheChainWaitsDoesNotGrowIt() {
        val v = liftedTwo()
        raw(v, MOVE, 0, 2)
        assertEquals(2, v.dragChain.size)
        assertTrue(v.liftMs >= 0)
    }

    @Test
    fun aSingleTapIsAnsweredAtOnce() {
        val v = planted()
        raw(v, DOWN, 3, 3); raw(v, UP, 3, 3)
        assertEquals(-1L, v.liftMs)
        assertEquals(listOf(3 to 3), v.wobbleCells)
        assertTrue(v.dragChain.isEmpty())
    }

    @Test
    fun theStarButtonWhileAChainWaitsReleasesItAndOpensStickers() {
        val v = liftedTwo()
        TestViews.touchRaw(v, DOWN, v.stickersBtnRect.centerX(), v.stickersBtnRect.centerY())
        assertEquals(-1L, v.liftMs)
        assertEquals(listOf(0 to 0, 0 to 1), v.wobbleCells)
        TestViews.touchRaw(v, UP, v.stickersBtnRect.centerX(), v.stickersBtnRect.centerY())   // buttons act on release
        assertTrue(v.stickersOpen)
    }

    @Test
    fun settlingTheBoardWhileAChainWaitsBanksItsDonuts() {
        val v = planted()
        raw(v, DOWN, 0, 0); raw(v, MOVE, 0, 1); raw(v, MOVE, 0, 2); raw(v, UP, 0, 2)
        assertTrue(v.liftMs >= 0)
        v.settleBoard()
        assertEquals(-1L, v.liftMs)
        assertTrue(v.dragChain.isEmpty())
        assertTrue(TestViews.total(v) >= 3)
    }
}
