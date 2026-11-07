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
        assertTrue(v.board.donutsCleared.values.sum() >= 3)
        assertEquals(3, v.prefs.bestChainLength)
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
        assertEquals(0, v.board.donutsCleared.values.sum())
        assertEquals(DonutType.STRAWBERRY, v.board.grid[0][0].type)
    }

    @Test
    fun singleTapWobblesWithoutALabel() {
        val v = planted()
        drag(v, 2 to 2)
        assertEquals(AnimPhase.IDLE, v.animPhase)
        assertEquals(listOf(2 to 2), v.wobbleCells)
        assertTrue(synchronized(v.floatLabels) { v.floatLabels.isEmpty() })
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
        assertEquals(0, v.board.donutsCleared.values.sum())
    }
}
