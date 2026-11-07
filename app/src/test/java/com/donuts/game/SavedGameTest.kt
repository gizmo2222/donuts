package com.donuts.game

import android.os.Bundle
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SavedGameTest {

    private val golden = GameBoard.GOLDEN_BIT

    /** Cell codes with no three-in-a-row; [overrides] maps index to code. */
    private fun codes(size: Int, overrides: Map<Int, Int> = emptyMap()): IntArray =
        IntArray(size * size) { i -> overrides[i] ?: (((i / size) + 2 * (i % size)) % 6) }

    private fun plant(v: GameView, cells: IntArray, cleared: IntArray) {
        assertTrue(v.board.restore(cells, cleared))
    }

    private val cleared = intArrayOf(3, 0, 5, 1, 0, 2) // total 11

    private fun planted(grid: Int = 6, seed: Int = 1): GameView {
        val v = TestViews.gameView(grid = grid, seed = seed)
        plant(v, codes(grid, mapOf(7 to (3 or golden))), cleared)
        return v
    }

    private fun assertSameBoard(a: GameBoard, b: GameBoard) {
        assertArrayEquals(a.cellCodes(), b.cellCodes())
        assertArrayEquals(a.clearedCounts(), b.clearedCounts())
    }

    @Test fun bundleRoundTripKeepsCellsGoldenAndCounter() {
        val v = planted()
        val out = Bundle()
        v.saveState(out)
        val w = TestViews.gameView(seed = 99)
        assertTrue(w.restoreState(out))
        assertSameBoard(v.board, w.board)
        assertTrue(w.board.grid[1][1].isGolden)
        assertEquals(11, w.displayedCount)
        assertEquals(11, w.sessionSaved)
        assertEquals(10, w.lastMilestone)
    }

    @Test fun prefsRoundTripKeepsCellsGoldenAndCounter() {
        val v = planted()
        v.saveToPrefs()
        assertNotNull(v.prefs.savedBoard)
        val w = TestViews.gameView(seed = 99)
        assertTrue(w.restoreFromPrefs())
        assertSameBoard(v.board, w.board)
        assertTrue(w.board.grid[1][1].isGolden)
        assertEquals(11, w.displayedCount)
        assertEquals(11, w.sessionSaved)
    }

    @Test fun restoreStateWithoutBoardIsRejected() {
        val w = TestViews.gameView(seed = 5)
        val before = w.board.cellCodes()
        assertFalse(w.restoreState(Bundle()))
        assertArrayEquals(before, w.board.cellCodes())
    }

    @Test fun restoreStateOfAnotherGridSizeIsRejected() {
        val v = planted(grid = 6)
        val out = Bundle(); v.saveState(out)
        val w = TestViews.gameView(grid = 8, seed = 5)
        val before = w.board.cellCodes()
        assertFalse(w.restoreState(out))
        assertArrayEquals(before, w.board.cellCodes())
    }

    @Test fun missingOrCorruptSavedBoardIsIgnored() {
        val w = TestViews.gameView(seed = 5)
        val before = w.board.cellCodes()
        assertFalse(w.restoreFromPrefs())
        for (bad in listOf("", "garbage", "v2;6;0;1;1", "v1;6;0;1,2,3;0,0,0,0,0,0", "v1;x;0;1;1",
            "v1;6;0;" + (List(36) { "99" }).joinToString(",") + ";0,0,0,0,0,0",
            "v1;6;0;" + codes(6).joinToString(",") + ";0,0,0,0,-1,0",
            "v1;6;0;" + codes(6).joinToString(",") + ";2000000000,2000000000,0,0,0,0",
            "v1;6;0;" + codes(6).joinToString(",") + ";0,0,0")) {
            w.prefs.savedBoard = bad
            assertFalse("should reject: $bad", w.restoreFromPrefs())
            assertArrayEquals(before, w.board.cellCodes())
            assertEquals(0, w.displayedCount)
        }
    }

    @Test fun savedBoardOfOtherSizeIsIgnoredAndCounterUntouched() {
        val v = planted(grid = 8)
        v.saveToPrefs()
        val w = TestViews.gameView(grid = 6, seed = 5)
        w.prefs.gridSize = 6
        assertFalse(w.restoreFromPrefs())
        assertEquals(0, w.displayedCount)
    }

    @Test fun saveMidAnimationRestoresASettledBoard() {
        val v = TestViews.gameView(seed = 1)
        plant(v, codes(6, mapOf(0 to 0, 1 to 0, 2 to 0)), IntArray(6))
        val res = v.board.peekChainClear(listOf(0 to 0, 0 to 1, 0 to 2))!!
        v.pendingResult = res
        v.animPhase = AnimPhase.POPPING
        v.saveToPrefs()
        assertEquals(AnimPhase.IDLE, v.animPhase)
        assertNull(v.pendingResult)
        val total = v.board.donutsCleared.values.sum()
        assertTrue("chain counted, got $total", total >= 3)
        val w = TestViews.gameView(seed = 99)
        assertTrue(w.restoreFromPrefs())
        assertSameBoard(v.board, w.board)
        assertEquals(total, w.displayedCount)
        assertEquals(AnimPhase.IDLE, w.animPhase)
    }

    @Test fun settlingAGoldenChainRecordsGoldenPopped() {
        val v = TestViews.gameView(seed = 1)
        plant(v, codes(6, mapOf(0 to 0, 1 to (0 or golden), 2 to 0)), IntArray(6))
        v.pendingResult = v.board.peekChainClear(listOf(0 to 0, 0 to 1, 0 to 2))!!
        assertFalse(v.prefs.goldenPopped)
        v.settleBoard()
        assertTrue(v.prefs.goldenPopped)
    }

    @Test fun saveSessionBanksOnlyOnce() {
        val v = TestViews.gameView(seed = 1)
        v.prefs.lifetimeDonuts = 100
        v.board.donutsCleared[DonutType.CARAMEL] = 7
        v.saveSession()
        assertEquals(107, v.prefs.lifetimeDonuts)
        assertEquals(7, v.sessionSaved)
        v.saveSession()
        assertEquals(107, v.prefs.lifetimeDonuts)
        v.board.donutsCleared[DonutType.CARAMEL] = 10
        v.saveSession()
        assertEquals(110, v.prefs.lifetimeDonuts)
    }

    @Test fun saveStateTwiceDoesNotDoubleCount() {
        val v = planted()
        v.saveState(Bundle()); v.saveState(Bundle()); v.saveToPrefs()
        assertEquals(11, v.prefs.lifetimeDonuts)
    }

    @Test fun saveSessionUpdatesTheHighScoreOfTheCurrentGridOnly() {
        val v = TestViews.gameView(grid = 6)
        v.board.donutsCleared[DonutType.MATCHA] = 12
        v.saveSession()
        assertEquals(12, v.prefs.highScore6x6)
        assertEquals(0, v.prefs.highScore8x8)
        v.prefs.highScore6x6 = 50
        v.board.donutsCleared[DonutType.MATCHA] = 13
        v.saveSession()
        assertEquals(50, v.prefs.highScore6x6)

        val e = TestViews.gameView(grid = 8)
        e.board.donutsCleared[DonutType.VANILLA] = 4
        e.saveSession()
        assertEquals(4, e.prefs.highScore8x8)
        assertEquals(50, e.prefs.highScore6x6)
    }

    @Test fun lifetimeDonutsIsBankedPlusUnsavedProgress() {
        val v = TestViews.gameView(seed = 1)
        v.prefs.lifetimeDonuts = 20
        assertEquals(20, v.lifetimeDonuts())
        v.board.donutsCleared[DonutType.CHOCOLATE] = 5
        assertEquals(25, v.lifetimeDonuts())
        v.saveSession()
        assertEquals(25, v.lifetimeDonuts())
        v.board.donutsCleared[DonutType.CHOCOLATE] = 8
        assertEquals(28, v.lifetimeDonuts())
    }

    @Test fun rebuildBoardAfterGridChangeGivesNewSizeAndBanksOldSession() {
        val v = planted()
        v.saveToPrefs()
        assertNotNull(v.prefs.savedBoard)
        v.prefs.gridSize = 8
        v.rebuildBoard()
        assertEquals(8, v.board.cols); assertEquals(8, v.board.rows)
        assertEquals(0, v.sessionSaved)
        assertEquals(0, v.displayedCount)
        assertEquals(0, v.board.donutsCleared.values.sum())
        assertNull(v.prefs.savedBoard)
        assertEquals(11, v.prefs.lifetimeDonuts)
        assertEquals(AnimPhase.IDLE, v.animPhase)
    }

    @Test fun rebuildBoardBanksUnsavedProgressFirst() {
        val v = planted()
        v.rebuildBoard()
        assertEquals(11, v.prefs.lifetimeDonuts)
        assertEquals(11, v.prefs.highScore6x6)
        assertEquals(6, v.board.cols)
    }

    @Test fun resetPlayStateClearsTransientState() {
        val v = planted()
        v.dragChain.add(0 to 0)
        v.fingerHeld = true
        v.cascadeCount = 3
        v.hintCells = listOf(0 to 0)
        v.lastMilestone = 10
        v.noMovesWarningMs = 5L
        v.animPhase = AnimPhase.POPPING
        v.resetPlayState()
        assertEquals(0, v.displayedCount)
        assertEquals(0, v.lastMilestone)
        assertEquals(-1L, v.noMovesWarningMs)
        assertTrue(v.dragChain.isEmpty())
        assertFalse(v.fingerHeld)
        assertEquals(0, v.cascadeCount)
        assertTrue(v.hintCells.isEmpty())
        assertEquals(AnimPhase.IDLE, v.animPhase)
    }

    @Test fun prefsHaveDocumentedDefaults() {
        val p = Prefs(TestViews.app())
        assertEquals(5_000L, p.hintDelayMs)
        assertEquals(6, p.gridSize)
        assertFalse(p.tutorialSeen)
        assertTrue(p.soundEnabled); assertTrue(p.hapticEnabled)
        assertEquals(0, p.lifetimeDonuts)
        assertEquals(0, p.highScore6x6); assertEquals(0, p.highScore8x8)
        assertEquals(0, p.bestChainLength)
        assertFalse(p.goldenPopped)
        assertEquals(0, p.sessionCount)
        assertEquals(-1, p.stickersAnnounced)
        assertNull(p.savedBoard)
    }

    @Test fun everyPrefsPropertyRoundTrips() {
        val p = Prefs(TestViews.app())
        p.hintDelayMs = 0L; p.gridSize = 8; p.tutorialSeen = true
        p.soundEnabled = false; p.hapticEnabled = false
        p.lifetimeDonuts = 321; p.highScore6x6 = 11; p.highScore8x8 = 22
        p.bestChainLength = 9; p.goldenPopped = true; p.sessionCount = 4
        p.stickersAnnounced = 5; p.savedBoard = "v1;x"
        val q = Prefs(TestViews.app())
        assertEquals(0L, q.hintDelayMs); assertEquals(8, q.gridSize); assertTrue(q.tutorialSeen)
        assertFalse(q.soundEnabled); assertFalse(q.hapticEnabled)
        assertEquals(321, q.lifetimeDonuts); assertEquals(11, q.highScore6x6); assertEquals(22, q.highScore8x8)
        assertEquals(9, q.bestChainLength); assertTrue(q.goldenPopped); assertEquals(4, q.sessionCount)
        assertEquals(5, q.stickersAnnounced); assertEquals("v1;x", q.savedBoard)
        q.savedBoard = null
        assertNull(Prefs(TestViews.app()).savedBoard)
    }

    // --- mutation-check additions ---

    private val noMoves get() = codes(6)
    private val withMove get() = codes(6, mapOf(0 to 0, 7 to 0, 14 to 0))

    @Test fun goldenOnlyInBonusCellsStillRecordsGoldenPopped() {
        val v = TestViews.gameView(seed = 1)
        plant(v, codes(6, mapOf(0 to 0, 1 to 0, 2 to 0, 3 to 0, 4 to 0, 7 to (4 or golden))), IntArray(6))
        val res = v.board.peekChainClear(listOf(0 to 0, 0 to 1, 0 to 2, 0 to 3, 0 to 4))!!
        assertTrue("golden should be a bonus cell", res.bonusCells.contains(1 to 1))
        assertFalse(res.chainCells.contains(1 to 1))
        v.pendingResult = res
        v.settleBoard()
        assertTrue(v.prefs.goldenPopped)
    }

    @Test fun settleBoardReleasesTheFingerAndDragChain() {
        val v = planted()
        v.fingerHeld = true
        v.dragChain.add(0 to 0)
        v.settleBoard()
        assertFalse(v.fingerHeld)
        assertTrue(v.dragChain.isEmpty())
    }

    @Test fun settleStartsNoMovesWarningOnceOnlyWhenStuck() {
        val v = TestViews.gameView(seed = 1)
        plant(v, withMove, IntArray(6))
        assertTrue(v.board.hasValidMoves())
        TestViews.advance(1000)
        v.settleBoard()
        assertEquals(-1L, v.noMovesWarningMs)

        plant(v, noMoves, IntArray(6))
        assertFalse(v.board.hasValidMoves())
        v.settleBoard()
        val first = v.noMovesWarningMs
        assertEquals(TestViews.now(), first)
        TestViews.advance(500)
        v.settleBoard()
        assertEquals(first, v.noMovesWarningMs)
    }

    @Test fun restoreOnlyWarnsAboutNoMovesWhenStuck() {
        TestViews.advance(1000)
        val ok = TestViews.gameView(seed = 1)
        plant(ok, withMove, IntArray(6))
        val b1 = Bundle(); ok.saveState(b1)
        val w1 = TestViews.gameView(seed = 2)
        assertTrue(w1.restoreState(b1))
        assertEquals(-1L, w1.noMovesWarningMs)

        val stuck = TestViews.gameView(seed = 1)
        plant(stuck, noMoves, IntArray(6))
        val b2 = Bundle(); stuck.saveState(b2)
        val w2 = TestViews.gameView(seed = 2)
        w2.noMovesWarningMs = -1L
        assertTrue(w2.restoreState(b2))
        assertEquals(TestViews.now(), w2.noMovesWarningMs)
    }

    @Test fun resetPlayStateEndsACascade() {
        val v = planted()
        v.isCascade = true
        v.resetPlayState()
        assertFalse(v.isCascade)
    }}
