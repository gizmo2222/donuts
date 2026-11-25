package com.donuts.game

import android.view.MotionEvent
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class GameInputTest : GameInputTestBase() {
    // ---- chains on the board ----

    @Test
    fun dragOfThreeSameTypeClearsAndStartsPopping() {
        val v = planted()
        drag(v, 0 to 0, 0 to 1, 0 to 2, release = false)
        assertEquals(listOf(0 to 0, 0 to 1, 0 to 2), v.dragChain)
        at(v, UP, 0, 2)
        assertEquals(AnimPhase.POPPING, v.animPhase)
        assertEquals(setOf(0 to 0, 0 to 1, 0 to 2), v.popCells.map { it.row to it.col }.toSet())
        assertNotNull(v.pendingResult)
        assertTrue(v.dragChain.isEmpty())
        runUntilIdle(v)
        assertTrue(TestViews.total(v) >= 3)
        assertEquals(3, v.prefs.bestChainLength)
    }

    @Test
    fun batchedHistoricalPointsOfOneMoveAllJoinTheChain() {
        val v = planted()
        at(v, DOWN, 0, 0)
        val (x1, y1) = TestViews.cellCenter(v, 0, 1)
        val (x2, y2) = TestViews.cellCenter(v, 0, 2)
        val t = TestViews.now()
        // One MOVE whose history holds the middle cell and whose current point is the last cell.
        val e = MotionEvent.obtain(t, t, MOVE, x1, y1, 0)
        e.addBatch(t, x1, y1, 1f, 1f, 0)
        e.addBatch(t, x2, y2, 1f, 1f, 0)
        assertEquals(2, e.historySize)
        send(v, e)
        assertEquals(listOf(0 to 0, 0 to 1, 0 to 2), v.dragChain)
    }

    @Test
    fun diagonalStepsJoinTheChain() {
        val v = planted(listOf("SCBVMA", "CSMBAV", "BMSVCA", "VACMBC", "MBAVSC", "ACVMBA"))
        drag(v, 0 to 0, 1 to 1, 2 to 2)
        assertEquals(AnimPhase.POPPING, v.animPhase)
    }

    @Test
    fun chainOfTwoDoesNotClearAndWobbles() {
        val v = planted()
        drag(v, 0 to 0, 0 to 1)
        assertEquals(AnimPhase.IDLE, v.animPhase)
        assertNull(v.pendingResult)
        assertTrue(v.popCells.isEmpty())
        assertEquals(listOf(0 to 0, 0 to 1), v.wobbleCells)
        assertTrue(synchronized(v.floatLabels) { v.floatLabels.any { it.text == "3!" } })
        assertEquals(0, TestViews.total(v))
        assertEquals(DonutType.STRAWBERRY, v.board.grid[0][0].type)
    }

    @Test
    fun singleTapWobblesWithoutALabel() {
        val v = planted()
        v.fxRandom = TestViews.fixedRandom(1)      // not the rare giggle
        drag(v, 2 to 2)
        assertEquals(AnimPhase.IDLE, v.animPhase)
        assertEquals(listOf(2 to 2), v.wobbleCells)
        assertFalse(v.wobbleGiggle)
        assertTrue(synchronized(v.floatLabels) { v.floatLabels.isEmpty() })
    }

    @Test
    fun aRareSingleTapGigglesWithAWordButTwoCellsNeverDo() {
        val v = planted()
        v.fxRandom = TestViews.fixedRandom(0)      // the giggle's one-in-twelve comes up
        drag(v, 2 to 2)
        assertTrue(v.wobbleGiggle)
        assertEquals(listOf(Praise.GIGGLE), synchronized(v.floatLabels) { v.floatLabels.map { it.text } })
        // The giggle wiggles twice as long as the plain wobble, then stops
        assertNotEquals(0f, v.nearMissOffset(2, 2, v.wobbleMs + v.WOBBLE_MS + 20))
        v.nearMissOffset(2, 2, v.wobbleMs + v.WOBBLE_MS * 2)
        assertEquals(-1L, v.wobbleMs)
        // A two-cell chain teaches "3!" and never giggles, whatever the dice say
        synchronized(v.floatLabels) { v.floatLabels.clear() }
        drag(v, 0 to 0, 0 to 1)
        assertFalse(v.wobbleGiggle)
        assertEquals(listOf("3!"), synchronized(v.floatLabels) { v.floatLabels.map { it.text } })
    }

    @Test
    fun wrongTypeNeighbourIsNotAddedAndShakes() {
        val v = planted()
        drag(v, 0 to 0, 0 to 1, 1 to 1, release = false)   // (1,1) is blueberry
        assertEquals(listOf(0 to 0, 0 to 1), v.dragChain)
        assertEquals(1 to 1, v.rejectedCell)
        assertEquals(1 to 1, v.shakeCell)
        at(v, MOVE, 0, 2)                                   // a right one still joins afterwards
        assertEquals(3, v.dragChain.size)
        assertNull(v.rejectedCell)
    }

    @Test
    fun nonAdjacentCellIsNotAdded() {
        val v = planted()
        drag(v, 0 to 0, 0 to 2, release = false)            // skips (0,1)
        assertEquals(listOf(0 to 0), v.dragChain)
    }

    @Test
    fun cellAlreadyInChainIsNotAddedTwice() {
        val v = planted(listOf("SSBVMA", "SSMBAV", "BMCVCA", "VACMBC", "MBAVSC", "ACVMBA"))
        drag(v, 0 to 0, 0 to 1, 1 to 1, 1 to 0, release = false)
        assertEquals(listOf(0 to 0, 0 to 1, 1 to 1, 1 to 0), v.dragChain)
        at(v, MOVE, 0, 0)                          // adjacent and same type, but already in the chain
        assertEquals(4, v.dragChain.size)
    }

    @Test
    fun draggingBackOverThePreviousCellRemovesTheLast() {
        val v = planted()
        drag(v, 0 to 0, 0 to 1, 0 to 2, release = false)
        assertEquals(3, v.dragChain.size)
        at(v, MOVE, 0, 1)
        assertEquals(listOf(0 to 0, 0 to 1), v.dragChain)
        assertFalse(v.chainPings.containsKey(0 to 2))
        at(v, MOVE, 0, 0)
        assertEquals(listOf(0 to 0), v.dragChain)
        at(v, UP, 0, 0)
        assertEquals(AnimPhase.IDLE, v.animPhase)
    }

    @Test
    fun goldenCellIsWildInAChain() {
        val v = planted()
        v.board.grid[0][2] = GameCell(DonutType.CARAMEL, 0, 2, isGolden = true)
        drag(v, 0 to 0, 0 to 1, 0 to 2)
        assertEquals(AnimPhase.POPPING, v.animPhase)
    }

    @Test
    fun goldenChainGetsTheGoldenWordInGold() {
        val v = planted()
        v.board.grid[0][2] = GameCell(DonutType.CARAMEL, 0, 2, isGolden = true)
        drag(v, 0 to 0, 0 to 1, 0 to 2)
        val label = synchronized(v.floatLabels) { v.floatLabels.single() }
        assertEquals("GOLDEN!", label.text)
        assertEquals(GameTheme.goldenLabel, label.color)
    }

    @Test
    fun plainChainOfThreeGetsNoWord() {
        val v = planted()
        drag(v, 0 to 0, 0 to 1, 0 to 2)
        assertTrue(synchronized(v.floatLabels) { v.floatLabels.isEmpty() })
    }

    @Test
    fun touchesOutsideTheBoardMakeNoChain() {
        val v = planted()
        TestViews.touch(v, DOWN, v.boardLeft - 5f, v.boardTop + 20f)
        assertTrue(v.dragChain.isEmpty())
        TestViews.touch(v, UP, v.boardLeft - 5f, v.boardTop + 20f)
        TestViews.touch(v, DOWN, v.boardLeft + 20f, v.boardTop - 5f)
        assertTrue(v.dragChain.isEmpty())
        TestViews.touch(v, UP, v.boardLeft + 20f, v.boardTop - 5f)
        assertEquals(AnimPhase.IDLE, v.animPhase)
        assertTrue(v.wobbleCells.isEmpty())
    }

    @Test
    fun dragLeavingTheBoardKeepsTheChain() {
        val v = planted()
        drag(v, 0 to 0, 0 to 1, release = false)
        TestViews.touch(v, MOVE, v.boardLeft - 50f, v.boardTop - 50f)
        TestViews.touch(v, MOVE, v.boardLeft + v.cellSize * 20, v.boardTop + v.cellSize * 20)
        assertEquals(listOf(0 to 0, 0 to 1), v.dragChain)
        at(v, MOVE, 0, 2)
        at(v, UP, 0, 2)
        assertEquals(AnimPhase.POPPING, v.animPhase)
    }

    @Test
    fun cancelDropsTheChainWithoutClearing() {
        val v = planted()
        drag(v, 0 to 0, 0 to 1, 0 to 2, release = false)
        at(v, MotionEvent.ACTION_CANCEL, 0, 2)
        assertTrue(v.dragChain.isEmpty())
        assertEquals(AnimPhase.IDLE, v.animPhase)
        assertNull(v.pendingResult)
    }

    // ---- fingers ----

    @Test
    fun aSecondFingerCannotHijackTheChain() {
        val v = planted()
        val (x0, y0) = TestViews.cellCenter(v, 0, 0)
        val (x1, y1) = TestViews.cellCenter(v, 0, 1)
        val (x2, y2) = TestViews.cellCenter(v, 0, 2)
        val (x5, y5) = TestViews.cellCenter(v, 5, 5)
        send(v, pointers(DOWN, 0, Triple(0, x0, y0)))
        send(v, pointers(MOVE, 0, Triple(0, x1, y1)))
        assertEquals(2, v.dragChain.size)
        send(v, pointers(MotionEvent.ACTION_POINTER_DOWN, 1, Triple(0, x1, y1), Triple(1, x5, y5)))
        assertEquals(listOf(0 to 0, 0 to 1), v.dragChain)
        // second finger slides over the next strawberry while the first stays put: nothing joins
        send(v, pointers(MOVE, 0, Triple(0, x1, y1), Triple(1, x2, y2)))
        assertEquals(listOf(0 to 0, 0 to 1), v.dragChain)
        // second finger lifts: the chain is not released
        send(v, pointers(MotionEvent.ACTION_POINTER_UP, 1, Triple(0, x1, y1), Triple(1, x2, y2)))
        assertEquals(listOf(0 to 0, 0 to 1), v.dragChain)
        assertEquals(AnimPhase.IDLE, v.animPhase)
        // the owner still drives it
        send(v, pointers(MOVE, 0, Triple(0, x2, y2)))
        assertEquals(3, v.dragChain.size)
        send(v, pointers(UP, 0, Triple(0, x2, y2)))
        assertEquals(AnimPhase.POPPING, v.animPhase)
    }

    @Test
    fun liftingTheOwnerFingerReleasesTheChainEvenWithAnotherDown() {
        val v = planted()
        val (x0, y0) = TestViews.cellCenter(v, 0, 0)
        val (x1, y1) = TestViews.cellCenter(v, 0, 1)
        val (x2, y2) = TestViews.cellCenter(v, 0, 2)
        val (x5, y5) = TestViews.cellCenter(v, 5, 5)
        send(v, pointers(DOWN, 0, Triple(0, x0, y0)))
        send(v, pointers(MOVE, 0, Triple(0, x1, y1)))
        send(v, pointers(MOVE, 0, Triple(0, x2, y2)))
        send(v, pointers(MotionEvent.ACTION_POINTER_DOWN, 1, Triple(0, x2, y2), Triple(1, x5, y5)))
        send(v, pointers(MotionEvent.ACTION_POINTER_UP, 0, Triple(0, x2, y2), Triple(1, x5, y5)))
        assertEquals(AnimPhase.POPPING, v.animPhase)
        assertEquals(ChainPointer.NONE, v.chainPointer.activeId)
        // the remaining finger's later lift is ignored and does not start anything
        send(v, pointers(UP, 0, Triple(1, x5, y5)))
        assertEquals(AnimPhase.POPPING, v.animPhase)
    }

    @Test
    fun strayMoveAndUpWithoutADownDoNothing() {
        val v = planted()
        at(v, MOVE, 0, 0); at(v, UP, 0, 0)
        assertTrue(v.dragChain.isEmpty())
        assertEquals(AnimPhase.IDLE, v.animPhase)
        assertTrue(v.wobbleCells.isEmpty())
    }

    // ---- input during animation ----

    @Test
    fun touchDuringAnimationIsHeldAndStartsTheDragWhenBoardIsIdle() {
        val v = planted()
        drag(v, 0 to 0, 0 to 1, 0 to 2)
        assertEquals(AnimPhase.POPPING, v.animPhase)
        at(v, DOWN, 4, 4)
        assertTrue(v.fingerHeld)
        assertTrue(v.dragChain.isEmpty())
        at(v, MOVE, 4, 4)
        assertTrue(v.dragChain.isEmpty())
        runUntilIdle(v)
        at(v, MOVE, 4, 4)
        assertFalse(v.fingerHeld)
        assertEquals(listOf(4 to 4), v.dragChain)
    }

    @Test
    fun liftingDuringAnimationDropsTheHeldFinger() {
        val v = planted()
        drag(v, 0 to 0, 0 to 1, 0 to 2)
        at(v, DOWN, 4, 4)
        at(v, UP, 4, 4)
        assertFalse(v.fingerHeld)
        runUntilIdle(v)
        at(v, MOVE, 4, 4)
        assertTrue(v.dragChain.isEmpty())
    }

    // ---- back button ----

    @Test
    fun backClosesAnOpenPanelFirstThenFallsThrough() {
        val v = planted()
        assertFalse(v.onBackPressed())
        openSettings(v)
        tap(v, v.settingsResetRect)
        assertTrue(v.onBackPressed())
        assertFalse(v.settingsOpen)
        assertFalse(v.newGameConfirm.isArmed(TestViews.now()))
        assertFalse(v.onBackPressed())

        val w = planted()
        openStickers(w)
        assertTrue(w.onBackPressed())
        assertFalse(w.stickersOpen)
        assertFalse(w.onBackPressed())
    }

    // ---- startOver ----

    @Test
    fun startOverClearsDragAndAnimationState() {
        val v = planted()
        drag(v, 0 to 0, 0 to 1, release = false)
        v.startOver()
        assertTrue(v.dragChain.isEmpty())
        assertTrue(v.chainPings.isEmpty())
        assertNull(v.pendingResult)
        assertNull(v.prefs.savedBoard)
        assertEquals(0, TestViews.total(v))
    }

    // ---- loop the ring ----

    // A 2x3 block of strawberries at the top left, and two loose ones at (3,5) and (5,1).
    private val ringLayout = listOf(
        "SSSCBV",
        "SSSMAC",
        "MAMBVC",
        "VCAVMS",
        "BMCABA",
        "ASBMCV"
    )
    private val ring6 = arrayOf(0 to 0, 0 to 1, 0 to 2, 1 to 2, 1 to 1, 1 to 0)

    @Test
    fun aSixCellRingClosedOnItsStartClearsEveryCellOfThatType() {
        val v = planted(ringLayout)
        drag(v, *ring6, release = false)
        at(v, MOVE, 0, 0)
        assertTrue(v.chainLooped)
        assertEquals(ring6.toList(), v.dragChain)       // the first cell is not added again
        at(v, UP, 0, 0)
        assertEquals(PowerUp.RING, v.pendingResult?.powerUp)
        assertEquals(PowerUp.RING, v.powerFx?.kind)
        assertEquals(ring6.toSet() + setOf(3 to 5, 5 to 1), v.popCells.map { it.row to it.col }.toSet())
        assertFalse(v.chainLooped)
        assertEquals(6, v.prefs.bestChainLength)
        synchronized(v.floatLabels) { assertTrue(v.floatLabels.any { it.text == "RING!" }) }
    }

    @Test
    fun aFiveCellClosedPathDoesNotLoop() {
        val v = planted(ringLayout)
        drag(v, *ring6.take(5).toTypedArray(), release = false)
        at(v, MOVE, 0, 0)
        assertFalse(v.chainLooped)
        assertEquals(ring6.take(5), v.dragChain)
        at(v, UP, 0, 0)
        assertEquals(PowerUp.BOMB, v.pendingResult?.powerUp)
    }

    @Test
    fun whileLoopedOtherMovesAreIgnoredAndBackOntoTheLastCellUnloops() {
        val v = planted(ringLayout)
        drag(v, *ring6, release = false)
        at(v, MOVE, 0, 0)
        assertTrue(v.chainLooped)
        at(v, MOVE, 0, 1)                                // a move along the ring changes nothing
        assertTrue(v.chainLooped)
        assertEquals(ring6.toList(), v.dragChain)
        at(v, MOVE, 1, 0)                                // back onto the last cell: the ring opens
        assertFalse(v.chainLooped)
        assertEquals(ring6.toList(), v.dragChain)
        at(v, UP, 1, 0)
        assertEquals(PowerUp.BOMB, v.pendingResult?.powerUp)
    }

    @Test
    fun cancelLeavesTheChainUnlooped() {
        val v = planted(ringLayout)
        drag(v, *ring6, release = false)
        at(v, MOVE, 0, 0)
        assertTrue(v.chainLooped)
        at(v, MotionEvent.ACTION_CANCEL, 0, 0)
        assertFalse(v.chainLooped)
        assertTrue(v.dragChain.isEmpty())
        assertNull(v.pendingResult)
    }

    @Test
    fun aSecondFingerOnTheStartDoesNotCloseTheRing() {
        val v = planted(ringLayout)
        val pts = ring6.map { (r, c) -> TestViews.cellCenter(v, r, c) }
        send(v, pointers(DOWN, 0, Triple(0, pts[0].first, pts[0].second)))
        for (p in pts.drop(1)) send(v, pointers(MOVE, 0, Triple(0, p.first, p.second)))
        val last = pts.last(); val start = pts[0]
        send(v, pointers(MotionEvent.ACTION_POINTER_DOWN, 1, Triple(0, last.first, last.second), Triple(1, start.first, start.second)))
        send(v, pointers(MOVE, 0, Triple(0, last.first, last.second), Triple(1, start.first, start.second)))
        assertFalse(v.chainLooped)
        assertEquals(6, v.dragChain.size)
    }

    @Test
    fun startOverClearsALoopedChain() {
        val v = planted(ringLayout)
        drag(v, *ring6, release = false)
        at(v, MOVE, 0, 0)
        v.startOver()
        assertFalse(v.chainLooped)
    }
}
