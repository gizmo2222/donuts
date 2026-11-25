package com.donuts.game

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Forgiving hit zones through real touches: corner cuts, wobbly straight steps, the backtrack undo. */
@RunWith(RobolectricTestRunner::class)
class HitZoneInputTest : GameInputTestBase() {
    private val diagonal = listOf("SCBVMA", "CSMBAV", "BMSVCA", "VACMBC", "MBAVSC", "ACVMBA")

    /** A touch at (u, v) in cell units on the board. */
    private fun cellAt(v: GameView, action: Int, u: Float, w: Float) =
        TestViews.touch(v, action, v.boardLeft + u * v.cellSize, v.boardTop + w * v.cellSize)

    @Test
    fun cuttingTheCornerOfAWrongDonutJoinsTheDiagonalWithoutAShake() {
        val v = planted(diagonal)                             // (0,1) and (1,0) are chocolate
        at(v, DOWN, 0, 0)
        cellAt(v, MOVE, 1.05f, 0.95f)                         // inside (0,1)'s square, by its corner
        assertEquals(listOf(0 to 0, 1 to 1), v.dragChain)
        assertNull(v.shakeCell)
        at(v, MOVE, 2, 2)
        at(v, UP, 2, 2)
        assertEquals(AnimPhase.POPPING, v.animPhase)
    }

    @Test
    fun aWobblyStraightDragStillJoinsTheStraightCells() {
        val v = planted()                                     // row 0 starts S S S
        at(v, DOWN, 0, 0)
        cellAt(v, MOVE, 0.8f, 0.75f)
        cellAt(v, MOVE, 1.2f, 0.85f)                          // low in (0,1), near blueberry (1,1)
        assertEquals(listOf(0 to 0, 0 to 1), v.dragChain)
        assertNull(v.shakeCell)
        cellAt(v, MOVE, 2.2f, 0.2f)
        assertEquals(listOf(0 to 0, 0 to 1, 0 to 2), v.dragChain)
    }

    @Test
    fun goingBackStillUndoesAndJitterDoesNotRedoIt() {
        val v = planted()
        drag(v, 0 to 0, 0 to 1, 0 to 2, release = false)
        cellAt(v, MOVE, 1.9f, 0.5f)                           // back into (0,1)
        assertEquals(listOf(0 to 0, 0 to 1), v.dragChain)
        repeat(4) { cellAt(v, MOVE, 1.92f + (it % 2) * 0.02f, 0.5f) }
        assertEquals(listOf(0 to 0, 0 to 1), v.dragChain)
        cellAt(v, MOVE, 2.4f, 0.5f)                           // forward again re-adds it
        assertEquals(listOf(0 to 0, 0 to 1, 0 to 2), v.dragChain)
    }

    @Test
    fun aFingerThatSkipsACellStillAddsNothing() {
        val v = planted()
        at(v, DOWN, 0, 0)
        cellAt(v, MOVE, 2.1f, 0.5f)                           // past (0,1) in one sample
        assertEquals(listOf(0 to 0), v.dragChain)
    }
}
