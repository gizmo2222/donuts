package com.donuts.game

import android.view.MotionEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Edges of GameInput that the mutation check (B35) found untested: guards, flags and the card's timing. */
@RunWith(RobolectricTestRunner::class)
class GameInputMoreTest : GameInputTestBase() {

    private val sixInARow = listOf("SSSSSS", "CBVMAC", "MAMBVC", "VCAVMB", "BMCABA", "AVBMCV")

    private fun raw(v: GameView, action: Int, x: Float = 10f, y: Float = 10f): Boolean {
        val t = TestViews.now()
        val e = MotionEvent.obtain(t, t, action, x, y, 0)
        try { return v.handleTouch(e) } finally { e.recycle() }
    }

    @Test
    fun aHeldFingerStartsNoDragOnceAPanelHasOpened() {
        val v = planted()
        drag(v, 0 to 0, 0 to 1, 0 to 2)
        at(v, DOWN, 4, 4)
        assertTrue(v.fingerHeld)
        v.stickersOpen = true
        v.startHeldDrag()
        assertFalse(v.fingerHeld)
        assertTrue(v.dragChain.isEmpty())
    }

    @Test
    fun aChainThatPopsIsNotACascade() {
        val v = planted()
        v.isCascade = true
        drag(v, 0 to 0, 0 to 1, 0 to 2)
        assertFalse(v.isCascade)
    }

    @Test
    fun theFirstChainEndsTheTutorialForGood() {
        val v = planted()
        v.tutorialActive = true; v.prefs.tutorialSeen = false
        drag(v, 0 to 0, 0 to 1, 0 to 2)
        assertFalse(v.tutorialActive)
        assertTrue(v.prefs.tutorialSeen)
    }

    @Test
    fun aChainOfExactlySixFlashesAndAChainOfThreeDoesNot() {
        val three = planted()
        TestViews.advance(1000)
        drag(three, 0 to 0, 0 to 1, 0 to 2)
        assertEquals(-1L, three.chainFlashMs)

        val six = planted(sixInARow)
        TestViews.advance(1000)
        drag(six, *Array(6) { 0 to it })
        assertTrue(six.chainFlashMs >= 0)
    }

    @Test
    fun aTapIsNotCountedAsAGoldenChain() {
        val v = planted()
        drag(v, 0 to 0)
        drag(v, 0 to 0, 0 to 1)
        assertEquals(0, v.playTally.golden)
    }

    @Test
    fun aThreeThatCannotPopWaitsTheLongGrace() {
        val v = planted()
        v.dragChain.addAll(listOf(0 to 1, 0 to 2, 0 to 3))   // S S C: three cells, but no clear
        v.beginLift()
        assertEquals(LiftGrace.LONG_MS, v.liftGraceMs)
    }

    @Test
    fun aChainLiftedAtTimeZeroIsStillWaiting() {
        val popped = planted()
        popped.dragChain.addAll(listOf(0 to 0, 0 to 1, 0 to 2))
        popped.liftMs = 0L
        popped.finishLift()
        assertEquals(-1L, popped.liftMs)
        assertEquals(AnimPhase.POPPING, popped.animPhase)

        val resumed = planted()
        resumed.dragChain.addAll(listOf(0 to 0, 0 to 1))
        resumed.liftMs = 0L
        val (x, y) = TestViews.cellCenter(resumed, 0, 2)
        assertTrue(resumed.resumeLift(x, y))
        assertEquals(3, resumed.dragChain.size)
    }

    @Test
    fun otherTouchKindsAreSwallowedAndChangeNothing() {
        val v = planted()
        assertTrue(raw(v, MotionEvent.ACTION_HOVER_MOVE))
        drag(v, 0 to 0, 0 to 1, 0 to 2)
        at(v, DOWN, 4, 4)
        assertTrue(v.fingerHeld)
        raw(v, MotionEvent.ACTION_OUTSIDE)
        assertTrue(v.fingerHeld)
    }

    @Test
    fun theCardSwallowsATouchFromTheMomentItOpens() {
        val v = planted()
        TestViews.advance(1000)
        v.showSomeoneMs = 0L
        at(v, DOWN, 0, 0)
        assertTrue(v.dragChain.isEmpty())
        assertEquals(-1L, v.showSomeoneMs)
    }

    @Test
    fun theCardClosesExactlyAtTheGuardAndNotBefore() {
        val v = planted()
        TestViews.advance(1000)
        v.showSomeoneMs = TestViews.now()
        at(v, DOWN, 0, 0)
        assertTrue(v.showSomeoneMs >= 0)

        v.showSomeoneMs = TestViews.now() - SHOW_CARD_GUARD_MS
        at(v, DOWN, 0, 0)
        assertEquals(-1L, v.showSomeoneMs)
        assertTrue(v.dragChain.isEmpty())
    }

    private fun ret(v: GameView, e: MotionEvent): Boolean =
        try { v.onTouchEvent(e) } finally { e.recycle(); TestViews.settleLift(v) }

    @Test
    fun everyTouchTheGameTakesAnswersTrue() {
        val v = planted()
        val (x0, y0) = TestViews.cellCenter(v, 0, 0)
        val (x5, y5) = TestViews.cellCenter(v, 5, 5)
        assertTrue(ret(v, pointers(DOWN, 0, Triple(0, x0, y0))))
        assertTrue(ret(v, pointers(MotionEvent.ACTION_POINTER_DOWN, 1, Triple(0, x0, y0), Triple(1, x5, y5))))
        assertTrue(ret(v, pointers(MotionEvent.ACTION_POINTER_UP, 1, Triple(0, x0, y0), Triple(1, x5, y5))))
        assertTrue(ret(v, pointers(UP, 0, Triple(0, x0, y0))))
        assertTrue(ret(v, pointers(UP, 0, Triple(0, x0, y0))))      // no finger owns it any more

        val w = planted()
        drag(w, 0 to 0, 0 to 1, release = false)
        val (x1, y1) = TestViews.cellCenter(w, 0, 1)
        val (x2, y2) = TestViews.cellCenter(w, 0, 2)
        TestViews.touchRaw(w, UP, x1, y1)
        assertTrue(TestViews.touchRaw(w, DOWN, x2, y2))                // the finger came back for the chain

        val card = planted()
        card.showSomeoneMs = TestViews.now()
        assertTrue(TestViews.touchRaw(card, DOWN, x0, y0))                  // the card takes it
    }

    @Test
    fun aFingerLeftBehindDoesNotBecomeTheOwner() {
        val v = planted()
        val (x0, y0) = TestViews.cellCenter(v, 0, 0)
        val (x5, y5) = TestViews.cellCenter(v, 5, 5)
        send(v, pointers(DOWN, 0, Triple(0, x0, y0)))
        send(v, pointers(MotionEvent.ACTION_POINTER_DOWN, 1, Triple(0, x0, y0), Triple(1, x5, y5)))
        send(v, pointers(MotionEvent.ACTION_POINTER_UP, 0, Triple(0, x0, y0), Triple(1, x5, y5)))
        send(v, pointers(MOVE, 0, Triple(1, x5, y5)))
        assertEquals(ChainPointer.NONE, v.chainPointer.activeId)
    }

    @Test
    fun aLiftForgetsTheOwner() {
        val v = planted()
        drag(v, 0 to 0)
        assertEquals(ChainPointer.NONE, v.chainPointer.activeId)
    }

    @Test
    fun aMoveWithNoOwnerStillDrawsButALiftWithNoOwnerIsIgnored() {
        val v = planted()
        at(v, DOWN, 0, 0)
        v.chainPointer.reset()
        at(v, MOVE, 0, 1)
        assertEquals(2, v.dragChain.size)
        at(v, MOVE, 0, 2)
        at(v, UP, 0, 2)
        assertEquals(AnimPhase.IDLE, v.animPhase)
    }

    @Test
    fun aPanelThatHasNotSlidYetStillSwallowsBoardTouches() {
        val gear = planted()
        tap(gear, gear.settingsBtnRect)
        assertEquals(0f, gear.settingsAnim, 0f)
        at(gear, DOWN, 0, 0)
        assertTrue(gear.dragChain.isEmpty())

        val star = planted()
        tap(star, star.stickersBtnRect)
        assertEquals(0f, star.stickersAnim, 0f)
        at(star, DOWN, 0, 0)
        assertTrue(star.dragChain.isEmpty())

        val closing = planted()
        closing.settingsAnim = 0.5f
        at(closing, DOWN, 0, 0)
        assertTrue(closing.dragChain.isEmpty())
    }

    @Test
    fun aClosingSettingsPanelStillPressesItsOwnButtons() {
        val v = planted()
        openSettings(v)
        v.settingsOpen = false                     // it starts to slide away, still fully in place
        TestViews.touch(v, DOWN, v.soundRects[1].centerX(), v.soundRects[1].centerY())
        assertEquals(1, v.panelDownHit)
    }

    @Test
    fun withOnlyStickersThereAreNoTabsToTap() {
        val v = planted()
        openStickers(v)
        assertNotEquals(12, v.stickersHit(v.stickersTabRect.centerX(), v.stickersTabRect.centerY()))
    }

    @Test
    fun doneMeansTheOpenPanelsOwnButton() {
        val v = planted()
        openStickers(v)
        assertSame(v.stickersCloseRect, v.panelHitRect(7))

        val w = planted()
        tap(w, w.settingsBtnRect)                  // open, not slid in yet
        assertEquals(0f, w.settingsAnim, 0f)
        assertSame(w.settingsCloseRect, w.panelHitRect(7))
    }

    @Test
    fun theGateOpensWithoutAnOldShake() {
        val v = planted()
        openSettings(v)
        v.gateWrongMs = 5L
        tap(v, v.settingsGrownRect)
        assertEquals(-1L, v.gateWrongMs)
    }

    @Test
    fun resetStickersCancelsAWaitingAllTwelveParty() {
        val v = planted()
        v.allStickersParty = true
        v.resetStickers()
        assertFalse(v.allStickersParty)
    }

    @Test
    fun theStarAndGearOpenOnTheFingerDownAndAnswerTrue() {
        val v = planted()
        assertTrue(TestViews.touchRaw(v, DOWN, v.settingsBtnRect.centerX(), v.settingsBtnRect.centerY()))
        assertTrue(v.settingsOpen)

        val w = planted()
        assertTrue(TestViews.touchRaw(w, DOWN, w.stickersBtnRect.centerX(), w.stickersBtnRect.centerY()))
        assertTrue(w.stickersOpen)
    }

    @Test
    fun aDragOntoTheStarDoesNotOpenIt() {
        val v = planted()
        at(v, DOWN, 0, 0)
        TestViews.touchRaw(v, MOVE, v.stickersBtnRect.centerX(), v.stickersBtnRect.centerY())
        assertFalse(v.stickersOpen)
    }

    @Test
    fun aTouchWhileTheBoardMovesAnswersTrue() {
        val v = planted()
        drag(v, 0 to 0, 0 to 1, 0 to 2)
        val (x, y) = TestViews.cellCenter(v, 4, 4)
        assertTrue(TestViews.touchRaw(v, DOWN, x, y))
    }

    @Test
    fun aStrayMoveRightAsTheChainIsLiftedDoesNotGrowIt() {
        val v = planted()
        v.dragChain.addAll(listOf(0 to 0, 0 to 1))
        v.liftMs = 0L
        val (x, y) = TestViews.cellCenter(v, 0, 2)
        assertTrue(TestViews.touchRaw(v, MOVE, x, y))
        assertEquals(2, v.dragChain.size)
    }

    @Test
    fun aCancelLetsGoOfTheHeldFinger() {
        val v = planted()
        at(v, DOWN, 0, 0)
        v.fingerHeld = true
        at(v, MotionEvent.ACTION_CANCEL, 0, 0)
        assertFalse(v.fingerHeld)
    }

    @Test
    fun cellUnitsAreMeasuredFromTheBoardCornerAndZeroBeforeLayout() {
        val v = planted()
        assertTrue(v.boardLeft > 0f || v.boardTop > 0f)
        assertEquals(1.5f, v.cellU(v.boardLeft + v.cellSize * 1.5f), 1e-4f)
        assertEquals(2.5f, v.cellV(v.boardTop + v.cellSize * 2.5f), 1e-4f)
        v.cellSize = 0f
        assertEquals(0f, v.cellU(5f), 0f)
        assertEquals(0f, v.cellV(5f), 0f)
    }

    @Test
    fun theBoardEdgeIsOutOfBoundsAndOnlyTouchingCellsAreNeighbours() {
        val v = planted()
        assertFalse(v.inBounds(v.board.rows, 0))
        assertFalse(v.inBounds(0, v.board.cols))
        assertTrue(v.inBounds(v.board.rows - 1, v.board.cols - 1))
        assertFalse(v.adjacent8(0, 0, 0, 5))
        assertFalse(v.adjacent8(0, 0, 5, 0))
        assertFalse(v.adjacent8(0, 0, 5, 5))
        assertTrue(v.adjacent8(0, 0, 1, 1))
    }

    @Test
    fun backClosesACardThatJustOpened() {
        val v = planted()
        v.showSomeoneMs = 0L
        assertTrue(v.onBackPressed())
        assertEquals(-1L, v.showSomeoneMs)
    }
}
