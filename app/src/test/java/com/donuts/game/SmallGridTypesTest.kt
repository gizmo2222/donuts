package com.donuts.game

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.random.Random

/** B29: the 6x6 game board plays five donut types (no caramel), the 8x8 one all six. */
@RunWith(RobolectricTestRunner::class)
class SmallGridTypesTest : GameInputTestBase() {

    private val caramel = DonutType.CARAMEL

    private fun noCaramel(b: GameBoard) = b.grid.flatten().none { it.type == caramel }

    @Test fun sixBySixDrawsFiveTypesAndEightByEightAllSix() {
        assertEquals(5, GameBoard.typesFor(6))
        assertEquals(6, GameBoard.typesFor(8))
        assertEquals(DonutType.values().last(), caramel)        // the type left out is the last one
        val small = GameBoard.forGrid(6, Random(4))
        assertEquals(6, small.rows); assertEquals(6, small.cols); assertEquals(5, small.typeCount)
        assertEquals(6, GameBoard.forGrid(8, Random(4)).typeCount)
        assertTrue(noCaramel(small))
        repeat(100) {
            small.clearChain(ChainResult(listOf(0 to 0, 0 to 1, 0 to 2), emptyList(), PowerUp.NONE, small.grid[0][0].type))
            assertTrue(noCaramel(small))
        }
    }

    @Test fun theGridButtonBuildsAFiveTypeSixBySix() {
        val v = planted()
        openGrownUps(v)
        tap(v, v.gridRects[1])
        assertEquals(6, v.board.typeCount)
        tap(v, v.gridRects[0])
        assertEquals(6, v.board.rows)
        assertEquals(5, v.board.typeCount)
        assertTrue(noCaramel(v.board))
    }

    @Test fun aCaramelPromptOnAFiveTypeBoardIsSwappedForAnother() {
        val v = TestViews.gameView()
        v.board = GameBoard.forGrid(6, Random(2))
        val p = Prompt(caramel.ordinal, 4)
        v.prompt = p; v.promptSeen = -1; v.savePrompt(p)
        TestViews.frames(v, TestViews.canvas(v), 1)
        val now = v.prompt!!
        assertTrue(now.type < 5)
        assertEquals(0, now.done)
        assertEquals(now, PicturePrompts.decode(v.prefs.picturePrompt, 5)?.first)
    }

    @Test fun aFreshPromptOnAFiveTypeBoardNeverAsksForCaramel() {
        repeat(20) { seed ->
            val v = TestViews.gameView(seed = seed)
            v.prefs.picturePrompt = null
            v.board = GameBoard.forGrid(6, Random(seed))
            v.promptRandom = Random(seed)
            TestViews.frames(v, TestViews.canvas(v), 1)
            assertTrue(v.prompt!!.type < 5)
        }
    }

    @Test fun aSixBySixSavedWithCaramelBeforeTheChangeComesBackAsItWas() {
        val v = TestViews.gameView()
        val old = GameBoard(6, 6, Random(3))                    // the six-type 6x6 of 2.0.5
        assertFalse(noCaramel(old))
        val saved = SavedBoard(6, old.cellCodes(), old.clearedCounts(), 0)
        assertTrue(v.applySaved(saved))
        assertArrayEquals(old.cellCodes(), v.board.cellCodes())
        assertEquals(5, v.board.typeCount)                      // and refills from now on bring no caramel
    }
}
