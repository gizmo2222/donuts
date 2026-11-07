package com.donuts.game

import android.view.MotionEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Per-frame animation state (GameAnimation.kt), driven through a laid-out GameView and the Robolectric clock. */
@RunWith(RobolectricTestRunner::class)
class GameAnimationTest {

    // ---------------------------------------------------------------- clear phases

    @Test
    fun plainChainWithoutGoldenCellsLeavesGoldenPoppedUnset() {
        val v = TestViews.quietView()
        val c = TestViews.canvas(v)
        TestViews.frames(v, c, 3)
        TestViews.playHintChain(v)
        assertTrue(v.popCells.none { it.isGolden })
        TestViews.frames(v, c, 120)
        org.junit.Assert.assertFalse(v.prefs.goldenPopped)
    }

    @Test
    fun chainClearGoesPoppingDroppingIdleAndSettlesTheBoard() {
        val v = TestViews.quietView()
        val c = TestViews.canvas(v)
        TestViews.frames(v, c, 3)
        assertEquals(AnimPhase.IDLE, v.animPhase)
        val before = TestViews.total(v)

        TestViews.playHintChain(v)
        assertEquals(AnimPhase.POPPING, v.animPhase)
        assertTrue(v.popCells.size >= 3)

        val seen = linkedSetOf<AnimPhase>()
        var frames = 0
        while (frames++ < 800) {
            TestViews.frames(v, c, 1)
            seen.add(v.animPhase)
            if (v.animPhase == AnimPhase.IDLE) break
        }
        assertTrue("phases seen: $seen", AnimPhase.DROPPING in seen)
        assertEquals(AnimPhase.IDLE, v.animPhase)
        assertTrue(v.popCells.isEmpty())
        assertTrue(v.dropCells.isEmpty())
        assertTrue(v.dropColMask.isEmpty())
        assertNull(v.pendingResult)
        assertTrue("no auto-matches left", v.board.findMatches().isEmpty())

        val cleared = TestViews.total(v) - before
        assertTrue("cleared $cleared", cleared >= 3)
        TestViews.frames(v, c, 120)
        assertEquals(TestViews.total(v), v.displayedCount)
    }

    @Test
    fun poppingHoldsForPopMsBeforeDropping() {
        val v = TestViews.quietView()
        val c = TestViews.canvas(v)
        TestViews.frames(v, c, 2)
        TestViews.playHintChain(v)
        TestViews.advance(v.POP_MS - 40)
        v.drawFrame(c)
        assertEquals(AnimPhase.POPPING, v.animPhase)
        TestViews.advance(60)
        v.drawFrame(c)
        assertEquals(AnimPhase.DROPPING, v.animPhase)
        assertTrue(v.dropCells.isNotEmpty())
    }

    @Test
    fun landedColumnsAreForgottenAfterLandMs() {
        val v = TestViews.quietView()
        val now = TestViews.now()
        v.landMs = now; v.landedCols.add(1)
        v.advanceAnimation(now + v.LAND_MS - 1)
        assertEquals(now, v.landMs)
        v.advanceAnimation(now + v.LAND_MS + 1)
        assertEquals(-1L, v.landMs)
        assertTrue(v.landedCols.isEmpty())
    }

    @Test
    fun noMovesWarningShufflesAfterTheDelayAndLeavesAPlayableBoard() {
        val v = TestViews.quietView()
        val now = TestViews.now()
        v.noMovesWarningMs = now
        v.advanceAnimation(now + v.NO_MOVES_DELAY_MS - 1)
        assertEquals(now, v.noMovesWarningMs)
        v.advanceAnimation(now + v.NO_MOVES_DELAY_MS)
        assertEquals(-1L, v.noMovesWarningMs)
        assertEquals(now + v.NO_MOVES_DELAY_MS, v.shuffleAnimMs)
        assertTrue(v.hintCells.isEmpty())
        assertTrue(v.board.findMatches().isEmpty())
        assertTrue(v.board.hasValidMoves())
        // the shuffle flag clears itself later
        v.advanceAnimation(now + v.NO_MOVES_DELAY_MS + v.SHUFFLE_ANIM_MS + v.SHUFFLE_MAX_DELAY + 1)
        assertEquals(-1L, v.shuffleAnimMs)
    }

    // ---------------------------------------------------------------- counter

    @Test
    fun counterRollsTowardTheRealTotalWithoutOvershooting() {
        val v = TestViews.quietView()
        v.board.donutsCleared[DonutType.values()[0]] = 20
        var now = TestViews.now()
        var last = 0
        var steps = 0
        while (v.displayedCount < 20 && steps++ < 50) {
            v.advanceCounter(now); now += 16
            assertTrue(v.displayedCount >= last)
            assertTrue(v.displayedCount <= 20)
            last = v.displayedCount
        }
        assertEquals(20, v.displayedCount)
        assertTrue("took $steps steps, expected a roll not a jump", steps > 1)
        assertEquals(10, v.lastMilestone)  // 10 crossed on the way, 25 not
    }

    @Test
    fun counterFirstStepIsAQuarterOfTheGap() {
        val v = TestViews.quietView()
        v.board.donutsCleared[DonutType.values()[0]] = 8
        v.advanceCounter(TestViews.now())
        assertEquals(2, v.displayedCount)
        v.advanceCounter(TestViews.now())
        assertEquals(3, v.displayedCount)   // (8-2)/4 = 1 -> at least one
    }

    @Test
    fun counterResetsWhenTheBoardCountDrops() {
        val v = TestViews.quietView()
        v.board.donutsCleared[DonutType.values()[0]] = 12
        repeat(30) { v.advanceCounter(TestViews.now()) }
        assertEquals(12, v.displayedCount)
        assertEquals(10, v.lastMilestone)
        v.board.donutsCleared.keys.forEach { v.board.donutsCleared[it] = 0 }
        v.advanceCounter(TestViews.now())
        assertEquals(0, v.displayedCount)
        assertEquals(-1, v.prevDisplayCount)
        assertEquals(0, v.lastMilestone)
        assertTrue(v.digitFlips.isEmpty())
    }

    @Test
    fun counterRecordsADigitFlipWhenADigitChanges() {
        val v = TestViews.quietView()
        v.board.donutsCleared[DonutType.values()[0]] = 40
        val now = TestViews.now()
        v.advanceCounter(now)          // first step: prevDisplayCount was -1, no flips recorded
        assertTrue(v.digitFlips.isEmpty())
        v.advanceCounter(now + 16)     // 10 -> 17: ones digit changes
        assertTrue(v.digitFlips.isNotEmpty())
        val flip = v.digitFlips[0]
        assertNotNull(flip)
        assertEquals('0', flip!!.from)
        assertEquals('7', flip.to)
        assertEquals(now + 16, flip.startMs)
    }

    // ---------------------------------------------------------------- celebrations

    @Test
    fun milestoneStartsABannerWithConfetti() {
        val v = TestViews.quietView()
        v.board.donutsCleared[DonutType.values()[0]] = 10
        repeat(10) { v.advanceCounter(TestViews.now()) }
        assertEquals("10 DONUTS!", v.celebrateLabel)
        assertTrue(v.celebrateMs >= 0)
        assertTrue(v.particles.isNotEmpty())
    }

    @Test
    fun hundredDonutMilestoneGetsStarsAroundIt() {
        val v = TestViews.quietView()
        v.board.donutsCleared[DonutType.values()[0]] = 100
        v.displayedCount = 60; v.lastMilestone = 50     // only the 100 mark is still ahead
        repeat(60) { v.advanceCounter(TestViews.now()) }
        assertEquals(100, v.lastMilestone)
        assertTrue(v.celebrateLabel.contains("100 DONUTS!"))
        assertTrue(v.celebrateLabel.startsWith("★"))
    }

    @Test
    fun secondMilestoneQueuesBehindTheFirstThenShowsAfterItsFirstPart() {
        val v = TestViews.quietView()
        val t0 = TestViews.now()
        v.celebrate(t0, "10 DONUTS!")
        v.celebrate(t0 + 100, "25 DONUTS!")
        assertEquals("10 DONUTS!", v.celebrateLabel)

        v.checkStickers(t0 + BANNER_FIRST_PART_MS - 1)
        assertEquals("still the first banner", "10 DONUTS!", v.celebrateLabel)

        v.checkStickers(t0 + BANNER_FIRST_PART_MS)
        assertEquals("25 DONUTS!", v.celebrateLabel)
        assertEquals(t0 + BANNER_FIRST_PART_MS, v.celebrateMs)
    }

    @Test
    fun spawnParticlesFillsBothCannonsAtTheBoardBottom() {
        val v = TestViews.quietView()
        v.spawnParticles()
        val expected = if (v.reducedMotion) 24 else 140
        assertEquals(expected, v.particles.size)
        val bottom = v.boardTop + v.board.rows * v.cellSize
        assertTrue(v.particles.all { it.y == bottom })
        val left = v.particles.filter { it.vx > 0 }
        val right = v.particles.filter { it.vx < 0 }
        assertTrue(left.isNotEmpty() && right.isNotEmpty())
        assertTrue("all fired upward", v.particles.all { it.vy < 0 })
        // calling again replaces rather than adds
        v.spawnParticles()
        assertEquals(expected, v.particles.size)
    }

    @Test
    fun confettiMovesThenExpiresWithTheBanner() {
        val v = TestViews.quietView()
        val c = TestViews.canvas(v)
        val bottom = v.boardTop + v.board.rows * v.cellSize
        v.celebrate(TestViews.now(), "YAY!")
        assertTrue(v.particles.isNotEmpty())
        TestViews.frames(v, c, 10)
        assertTrue("confetti flew up", v.particles.any { it.y < bottom })
        TestViews.frames(v, c, 20, 100L)       // 2.0 s + 160 ms
        assertTrue(v.celebrateMs >= 0)
        TestViews.advance(v.CELEBRATE_MS)
        v.drawFrame(c)
        assertEquals(-1L, v.celebrateMs)
        assertTrue(v.particles.isEmpty())
    }

    // ---------------------------------------------------------------- hint

    @Test
    fun hintAppearsOnlyAfterTheIdleDelay() {
        val v = TestViews.quietView()
        val c = TestViews.canvas(v)
        assertEquals(5_000L, v.prefs.hintDelayMs)
        TestViews.advance(4_900); v.drawFrame(c)
        assertTrue(v.hintCells.isEmpty())
        TestViews.advance(200); v.drawFrame(c)
        assertEquals(v.board.findHint(), v.hintCells)
        assertEquals(3, v.hintCells.size)
    }

    @Test
    fun touchingTheBoardClearsTheHintAndRestartsTheWait() {
        val v = TestViews.quietView()
        val c = TestViews.canvas(v)
        TestViews.advance(5_100); v.drawFrame(c)
        assertTrue(v.hintCells.isNotEmpty())

        val (x, y) = TestViews.cellCenter(v, 0, 0)
        TestViews.touch(v, MotionEvent.ACTION_DOWN, x, y)
        assertTrue(v.hintCells.isEmpty())
        TestViews.touch(v, MotionEvent.ACTION_UP, x, y)

        TestViews.advance(3_000); v.drawFrame(c)
        assertTrue(v.hintCells.isEmpty())
        TestViews.advance(2_100); v.drawFrame(c)
        assertTrue(v.hintCells.isNotEmpty())
    }

    @Test
    fun hintsAreOffWhenTheDelayIsZero() {
        val v = TestViews.quietView()
        v.prefs.hintDelayMs = 0L
        val c = TestViews.canvas(v)
        TestViews.advance(60_000); v.drawFrame(c)
        assertTrue(v.hintCells.isEmpty())
    }

    @Test
    fun hintStaysHiddenWhileTheTutorialRuns() {
        val v = TestViews.quietView(tutorialSeen = false)
        val c = TestViews.canvas(v)
        assertTrue(v.tutorialActive)
        TestViews.advance(60_000); v.drawFrame(c)
        assertTrue(v.hintCells.isEmpty())
    }

    @Test
    fun hintWaitsForTheAnimationToFinish() {
        val v = TestViews.quietView()
        v.animPhase = AnimPhase.POPPING
        v.lastActionMs = TestViews.now() - 60_000
        v.updateHint(TestViews.now())
        assertTrue(v.hintCells.isEmpty())
    }

    // ---------------------------------------------------------------- easing

    @Test
    fun easeOutQuintRunsZeroToOneAndOnlyRises() {
        val v = TestViews.quietView()
        assertEquals(0f, v.easeOutQuint(0f), 1e-6f)
        assertEquals(1f, v.easeOutQuint(1f), 1e-6f)
        assertTrue("fast start", v.easeOutQuint(0.5f) > 0.9f)
        var last = -1f
        for (i in 0..100) {
            val e = v.easeOutQuint(i / 100f)
            assertTrue(e >= last); assertTrue(e <= 1f)
            last = e
        }
    }

    @Test
    fun easeOutBackOvershootsThenSettlesAtOne() {
        val v = TestViews.quietView()
        assertEquals(0f, v.easeOutBack(0f), 1e-5f)
        assertEquals(1f, v.easeOutBack(1f), 1e-5f)
        val peak = (0..100).maxOf { v.easeOutBack(it / 100f) }
        assertTrue("peak $peak", peak > 1.05f && peak < 1.2f)
        assertTrue(v.easeOutBack(0.5f) > 1f)
        // after the peak it comes back down toward 1
        assertTrue(v.easeOutBack(0.95f) < v.easeOutBack(0.6f))
    }
}
