package com.donuts.game

import android.graphics.RectF
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The picture prompt in a running GameView: pops count down, a finished one is followed by another, prefs keep it. */
@RunWith(RobolectricTestRunner::class)
class PicturePromptViewTest {

    private fun settle(v: GameView, c: android.graphics.Canvas) {
        var n = 0
        do { TestViews.frames(v, c, 1) } while (v.animPhase != AnimPhase.IDLE && n++ < 800)
    }

    /** The type of the hint trio that [TestViews.playHintChain] will clear. */
    private fun hintType(v: GameView): Int {
        val h = v.board.findHint()
        val orders = listOf(listOf(0, 1, 2), listOf(0, 2, 1), listOf(1, 0, 2), listOf(1, 2, 0), listOf(2, 0, 1), listOf(2, 1, 0))
        return orders.firstNotNullOf { o -> v.board.peekChainClear(o.map { h[it] }) }.chainType.ordinal
    }

    private fun cleared(v: GameView, type: Int) = v.board.donutsCleared[DonutType.values()[type]] ?: 0

    private fun aim(v: GameView, p: Prompt) { v.prompt = p; v.promptSeen = -1; v.savePrompt(p) }

    @Test fun theFirstFramePicksAnEasyPromptAndSavesIt() {
        val v = TestViews.quietView()
        val c = TestViews.canvas(v)
        TestViews.frames(v, c, 1)
        val p = v.prompt!!
        assertEquals(PicturePrompts.FIRST_GOAL, p.goal)
        assertEquals(0, p.done)
        assertEquals(Pair(p, 0), PicturePrompts.decode(v.prefs.picturePrompt, DonutType.values().size))
        assertFalse(v.promptRect.isEmpty)
    }

    @Test fun poppingThePromptTypeCountsDownAndPrefsKeepIt() {
        val v = TestViews.quietView()
        val c = TestViews.canvas(v)
        TestViews.frames(v, c, 2)
        val t = hintType(v)
        aim(v, Prompt(t, PicturePrompts.MAX_GOAL))
        TestViews.frames(v, c, 1)
        val before = cleared(v, t)
        TestViews.playHintChain(v)
        settle(v, c)
        val done = v.prompt!!.done
        assertEquals(minOf(PicturePrompts.MAX_GOAL, cleared(v, t) - before), done)
        assertTrue(done >= 3)
        assertEquals(v.prompt, PicturePrompts.decode(v.prefs.picturePrompt, 6)!!.first)
    }

    @Test fun aFinishedPromptShowsItsStarThenADifferentTypeFollows() {
        val v = TestViews.quietView()
        val c = TestViews.canvas(v)
        TestViews.frames(v, c, 2)
        val t = hintType(v)
        aim(v, Prompt(t, 3, 1))
        TestViews.frames(v, c, 1)
        TestViews.playHintChain(v)
        settle(v, c)
        assertTrue(v.prompt!!.complete)
        assertEquals(t, v.prompt!!.type)
        assertEquals(1, v.promptsFinished)
        assertTrue(v.floatLabels.any { it.text == "YAY!" })
        TestViews.frames(v, c, (v.PROMPT_NEXT_MS / 200).toInt() + 2, 200L)
        val next = v.prompt!!
        assertNotEquals(t, next.type)
        assertEquals(0, next.done)
        assertFalse(next.complete)
        assertEquals(Pair(next, 1), PicturePrompts.decode(v.prefs.picturePrompt, 6))
    }

    @Test fun ignoringThePromptCostsNothingAndNewGameKeepsIt() {
        val v = TestViews.quietView()
        val c = TestViews.canvas(v)
        TestViews.frames(v, c, 2)
        val other = (hintType(v) + 1) % 6
        aim(v, Prompt(other, 5, 2))
        TestViews.frames(v, c, 1)
        TestViews.frames(v, c, 10, 3000L)      // half a minute idle: no timer runs out
        assertEquals(Prompt(other, 5, 2), v.prompt)
        v.startOver()
        TestViews.frames(v, c, 3)
        assertEquals(Prompt(other, 5, 2), v.prompt)
        assertEquals(cleared(v, other), v.promptSeen)
    }

    @Test fun aNewViewRestoresThePromptFromPrefs() {
        val v = TestViews.quietView()
        v.prefs.picturePrompt = PicturePrompts.encode(Prompt(2, 5, 1), 4)
        val w = TestViews.quietView()
        TestViews.frames(w, TestViews.canvas(w), 1)
        assertEquals(Prompt(2, 5, 1), w.prompt)
        assertEquals(4, w.promptsFinished)
    }

    @Test fun theTutorialHidesTheChipAndItsChainDoesNotCount() {
        val v = TestViews.quietView(tutorialSeen = false)
        val c = TestViews.canvas(v)
        TestViews.frames(v, c, 2)
        assertTrue(v.promptRect.isEmpty)
        val p = v.prompt!!
        TestViews.playHintChain(v)
        settle(v, c)
        assertFalse(v.tutorialActive)
        TestViews.frames(v, c, 2)
        assertEquals(p, v.prompt)
        assertFalse(v.promptRect.isEmpty)
    }

    @Test fun theChipSitsBesideTheCounterInsideTheScreenAtEverySize() {
        for ((w, h) in listOf(1080 to 2400, 2400 to 1080, 720 to 1280, 2560 to 1600, 1600 to 2560)) {
            for (grid in listOf(6, 8)) {
                val v = TestViews.gameView(w = w, h = h, grid = grid)
                v.prefs.soundEnabled = false
                v.displayedCount = 9999            // the widest counter Steven is likely to see
                TestViews.frames(v, TestViews.canvas(v), 1)
                val r = RectF(v.promptRect)
                val tag = "${w}x$h grid $grid"
                assertFalse(tag, r.isEmpty)
                val boardBottom = v.boardTop + v.board.rows * v.cellSize
                assertTrue(tag, r.left >= v.insetL && r.right <= v.surfaceW - v.insetR)
                assertTrue(tag, r.top >= boardBottom && r.bottom <= v.boxStrip.rect.top)
                assertTrue(tag, r.height() >= 40f * v.u)      // big enough to read at a glance
            }
        }
    }
}
