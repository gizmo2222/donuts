package com.donuts.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Panel slides, stickers earned and the box strip (GameAnimation.kt), split from GameAnimationTest. */
@RunWith(RobolectricTestRunner::class)
class GameAnimationPanelsTest {

    // ---------------------------------------------------------------- panels

    @Test
    fun settingsPanelSlidesOpenAndShutToItsEndValues() {
        val v = TestViews.quietView()
        var now = TestViews.now()
        v.advanceSettingsAnim(now)
        assertEquals(0f, v.settingsAnim, 0f)

        v.settingsOpen = true
        var last = v.settingsAnim
        var openSteps = 0
        while (v.settingsAnim < 1f && openSteps++ < 100) {
            now += 16; v.advanceSettingsAnim(now)
            assertTrue(v.settingsAnim >= last); last = v.settingsAnim
        }
        assertEquals(1f, v.settingsAnim, 0f)
        assertTrue("open took $openSteps steps", openSteps in 10..16)

        v.settingsOpen = false
        var closeSteps = 0
        while (v.settingsAnim > 0f && closeSteps++ < 100) {
            now += 16; v.advanceSettingsAnim(now)
            assertTrue(v.settingsAnim <= last); last = v.settingsAnim
        }
        assertEquals(0f, v.settingsAnim, 0f)
        assertTrue("close ($closeSteps) is quicker than open ($openSteps)", closeSteps < openSteps)
    }

    @Test
    fun stickersPanelAnimatesIndependentlyOfSettings() {
        val v = TestViews.quietView()
        var now = TestViews.now()
        v.advanceSettingsAnim(now)
        v.stickersOpen = true
        var steps = 0
        while (v.stickersAnim < 1f && steps++ < 100) { now += 16; v.advanceSettingsAnim(now) }
        assertEquals(1f, v.stickersAnim, 0f)
        assertEquals(0f, v.settingsAnim, 0f)
    }

    @Test
    fun settingsPanelStaysFullyOpenOnceOpen() {
        val v = TestViews.quietView()
        var now = TestViews.now()
        v.advanceSettingsAnim(now)
        v.settingsOpen = true
        repeat(40) { now += 16; v.advanceSettingsAnim(now) }
        assertEquals(1f, v.settingsAnim, 0f)
    }

    @Test
    fun stickersPanelStaysFullyOpenOnceOpen() {
        val v = TestViews.quietView()
        var now = TestViews.now()
        v.advanceSettingsAnim(now)
        v.stickersOpen = true
        repeat(40) { now += 16; v.advanceSettingsAnim(now) }
        assertEquals(1f, v.stickersAnim, 0f)
    }

    @Test
    fun aLongFrameGapMovesThePanelByNoMoreThanFiftyMs() {
        val v = TestViews.quietView()
        val now = TestViews.now()
        v.advanceSettingsAnim(now)
        v.settingsOpen = true
        v.advanceSettingsAnim(now + 10_000)
        assertEquals(50f / v.SETTINGS_OPEN_MS, v.settingsAnim, 1e-4f)
    }

    // ---------------------------------------------------------------- stickers

    @Test
    fun firstFrameSeedsAnnouncedMaskFromWhatIsAlreadyEarned() {
        val v = TestViews.quietView()
        v.prefs.bestChainLength = 6          // stickers 3 and 4 already earned
        assertEquals(-1, v.prefs.stickersAnnounced)
        v.checkStickers(TestViews.now())
        val mask = (1 shl 3) or (1 shl 4)
        assertEquals(mask, v.earnedMask)
        assertEquals(mask, v.prefs.stickersAnnounced)
        assertEquals(-1L, v.stickerFlyMs)
        assertEquals(-1, v.stickerFlyIdx)
    }

    @Test
    fun newlyEarnedStickerIsAnnouncedOnceAndNotAgain() {
        val v = TestViews.quietView()
        val now = TestViews.now()
        v.checkStickers(now)                 // seeds, nothing earned
        assertEquals(-1L, v.stickerFlyMs)

        v.prefs.bestChainLength = 4          // sticker 3
        v.checkStickers(now + 16)
        assertEquals(3, v.stickerFlyIdx)
        assertEquals(now + 16, v.stickerFlyMs)
        assertTrue(v.earnedMask and (1 shl 3) != 0)
        assertTrue(v.prefs.stickersAnnounced and (1 shl 3) != 0)

        v.stickerFlyMs = -1L                 // the fly finished
        v.stickerFlyIdx = -1
        v.checkStickers(now + 5_000)
        assertEquals(-1L, v.stickerFlyMs)
        assertEquals(-1, v.stickerFlyIdx)
    }

    @Test
    fun twoStickersAtOnceFlyOneAfterTheOther() {
        val v = TestViews.quietView()
        v.prefs.stickersAnnounced = 0        // an old install: earned but never shown
        v.prefs.bestChainLength = 6
        val now = TestViews.now()
        v.checkStickers(now)
        assertEquals(3, v.stickerFlyIdx)
        v.checkStickers(now + 16)            // still flying: no second one yet
        assertEquals(3, v.stickerFlyIdx)
        assertEquals(now, v.stickerFlyMs)
        v.stickerFlyMs = -1L
        v.checkStickers(now + 2_000)
        assertEquals(4, v.stickerFlyIdx)
        v.stickerFlyMs = -1L
        v.checkStickers(now + 4_000)
        assertEquals(-1L, v.stickerFlyMs)
    }

    @Test
    fun earningAllTwelveStickersSetsTheParty() {
        val v = TestViews.quietView()
        v.prefs.stickersAnnounced = 0b0111_1111_1111   // all but the last, already shown
        v.prefs.lifetimeDonuts = 500
        v.prefs.highScore6x6 = 200
        v.prefs.highScore8x8 = 1
        v.prefs.bestChainLength = 8
        v.prefs.goldenPopped = true
        v.prefs.sessionCount = 10
        v.checkStickers(TestViews.now())
        assertEquals(11, v.stickerFlyIdx)
        assertTrue(v.allStickersParty)
    }

    @Test
    fun fullDonutBoxCelebratesAndPulsesTheStar() {
        val v = TestViews.quietView()
        val now = TestViews.now()
        v.prefs.lifetimeDonuts = 119
        v.checkStickers(now)                 // seeds boxesSeen = 0
        assertEquals(0, v.boxesSeen)
        assertEquals(-1L, v.celebrateMs)

        v.prefs.lifetimeDonuts = 120
        v.checkStickers(now + 16)
        assertEquals("BOX FULL!", v.celebrateLabel)
        assertEquals(1, v.boxesSeen)
        assertEquals(now + 16, v.starPulseMs)

        v.checkStickers(now + 32)            // same box: not again
        assertEquals(now + 16, v.starPulseMs)
    }

    @Test
    fun starPulseSwellsThenReturnsToOne() {
        val v = TestViews.quietView()
        val now = TestViews.now()
        assertEquals(1f, v.starPulse(now), 0f)
        v.starPulseMs = now
        assertEquals(1f, v.starPulse(now), 1e-4f)
        assertEquals(1.35f, v.starPulse(now + 225), 1e-3f)
        assertEquals(1f, v.starPulse(now + 450), 0f)
        assertEquals(-1L, v.starPulseMs)
    }

    // ---------------------------------------------------------------- box strip

    @Test
    fun boxStripFirstFrameAndResetsDoNotPop() {
        val v = TestViews.quietView()
        val now = TestViews.now()
        v.displayedCount = 35
        v.advanceBoxStrip(now)
        assertEquals(35, v.boxStrip.shownLifetime)
        assertEquals(-1L, v.boxStrip.slotPopMs)
        v.displayedCount = 0
        v.advanceBoxStrip(now + 16)
        assertEquals(0, v.boxStrip.shownLifetime)
        assertEquals(-1L, v.boxStrip.slotPopMs)
    }

    @Test
    fun boxStripPopsWhenASlotFillsAndWhenTheBoxFills() {
        val v = TestViews.quietView()
        val now = TestViews.now()
        v.advanceBoxStrip(now)               // first frame: shown = 0
        v.displayedCount = 9
        v.advanceBoxStrip(now + 16)
        assertEquals(-1L, v.boxStrip.slotPopMs)
        v.displayedCount = 10
        v.advanceBoxStrip(now + 32)
        assertEquals(now + 32, v.boxStrip.slotPopMs)
        assertEquals(-1L, v.boxStrip.boxPopMs)
        v.displayedCount = 11
        v.advanceBoxStrip(now + 48)
        assertEquals("same slot", now + 32, v.boxStrip.slotPopMs)

        v.displayedCount = 119
        v.advanceBoxStrip(now + 64)
        assertEquals(-1L, v.boxStrip.boxPopMs)
        v.displayedCount = 120
        v.advanceBoxStrip(now + 80)
        assertEquals(now + 80, v.boxStrip.boxPopMs)
        assertEquals(now + 80, v.boxStrip.slotPopMs)
    }

    @Test
    fun boxStripCountsBankedDonutsButNotTheSessionAlreadySaved() {
        val v = TestViews.quietView()
        v.prefs.lifetimeDonuts = 50
        v.sessionSaved = 20
        v.displayedCount = 25
        v.advanceBoxStrip(TestViews.now())
        assertEquals(55, v.boxStrip.shownLifetime)
    }

    // ---------------------------------------------------------------- mutation-check additions

    /** Plants [n] same-type cells at the left of the bottom row. */
    private fun plantRow(v: GameView, n: Int, type: DonutType = DonutType.values()[0]) {
        val r = v.board.rows - 1
        for (c in 0 until n) v.board.grid[r][c] = GameCell(type, r, c)
    }

    @Test
    fun droppingEndsExactlyAtDropMsAndAQuietBoardRaisesNoWarning() {
        val v = TestViews.quietView()
        val now = TestViews.now()
        v.animPhase = AnimPhase.DROPPING; v.animStartMs = now
        v.advanceAnimation(now + v.DROP_MS - 1)
        assertEquals(AnimPhase.DROPPING, v.animPhase)
        v.advanceAnimation(now + v.DROP_MS)
        assertEquals(AnimPhase.IDLE, v.animPhase)
        assertEquals(-1L, v.noMovesWarningMs)
    }

    @Test
    fun shuffleFlagSurvivesUntilItsFullDurationHasPassed() {
        val v = TestViews.quietView()
        val now = TestViews.now()
        v.shuffleAnimMs = now
        v.advanceAnimation(now + v.SHUFFLE_ANIM_MS + v.SHUFFLE_MAX_DELAY)
        assertEquals(now, v.shuffleAnimMs)
        v.advanceAnimation(now + v.SHUFFLE_ANIM_MS + v.SHUFFLE_MAX_DELAY + 1)
        assertEquals(-1L, v.shuffleAnimMs)
    }

    @Test
    fun counterAtItsTargetDoesNotPulse() {
        val v = TestViews.quietView()
        v.displayedCount = TestViews.total(v)
        v.advanceCounter(TestViews.now())
        assertEquals(-1L, v.counterPulseMs)
    }

    @Test
    fun anEarnedMaskOfZeroIsKeptNotReloadedFromPrefs() {
        val v = TestViews.quietView()
        v.earnedMask = 0
        v.prefs.stickersAnnounced = 5
        v.stickerFlyMs = 0L  // a fly in progress: checkStickers returns before announcing
        v.checkStickers(TestViews.now())
        assertEquals(0, v.earnedMask)
    }

    @Test
    fun confettiSpinStaysWithinFivePerFrameBothWays() {
        val v = TestViews.quietView()
        v.spawnParticles()
        assertTrue(v.particles.isNotEmpty())
        assertTrue(v.particles.all { it.rotSpeed >= -5f && it.rotSpeed <= 5f })
    }

    @Test
    fun powerUpPopsOnlyRecordCellsWhoseDelayHasPassed() {
        val v = TestViews.quietView()
        val now = TestViews.now()
        val t = DonutType.values()[0]
        v.powerFx = PowerUpFx(PowerUp.BOMB, 1, 1, 0, 600L)
        v.popCells.clear()
        v.popCells.add(AnimCell(1, 1, t))                      // origin: no delay, never recorded
        v.popCells.add(AnimCell(1, 2, t, delayMs = 100L))
        v.popCells.add(AnimCell(2, 2, t, delayMs = 300L))
        v.animPhase = AnimPhase.POPPING; v.animStartMs = now
        v.advancePowerPops(now + 50)
        assertTrue(v.powerFx!!.popped.isEmpty())
        v.advancePowerPops(now + 100)
        assertEquals(setOf(Pair(1, 2)), v.powerFx!!.popped)
        v.advancePowerPops(now + 300)
        assertEquals(setOf(Pair(1, 2), Pair(2, 2)), v.powerFx!!.popped)
    }

    @Test
    fun powerUpClearDropsOneNewCellForEveryClearedCellIncludingBonusOnes() {
        val v = TestViews.quietView()
        plantRow(v, 5)
        val bottom = v.board.rows - 1
        val res = v.board.peekChainClear((0 until 5).map { Pair(bottom, it) })!!
        assertEquals(PowerUp.BOMB, res.powerUp)
        assertTrue(res.bonusCells.isNotEmpty())
        val cleared = (res.chainCells + res.bonusCells).toSet()
        val now = TestViews.now()
        v.pendingResult = res
        v.powerFx = null
        v.animPhase = AnimPhase.POPPING; v.animStartMs = now
        v.advanceAnimation(now + v.POP_MS)
        assertEquals(AnimPhase.DROPPING, v.animPhase)
        assertEquals(false, v.prefs.goldenPopped)  // no golden cell was in the clear
        for (c in 0 until v.board.cols) {
            val k = cleared.count { it.second == c }
            val col = v.dropCells.filter { it.col == c }
            assertEquals("column $c", if (k == 0) 0 else v.board.rows, col.size)
            val fresh = col.filter { it.row < k }
            assertEquals(k, fresh.size)
            // new cells start k rows above their landing row, so they all fall the same distance
            fresh.forEach { assertEquals(it.row - k, it.fromRow) }
            col.filter { it.row >= k }.forEach { assertTrue(it.fromRow >= 0 && it.fromRow <= it.row) }
        }
    }

    @Test
    fun cascadeAfterALandStartsAComboLabelOverTheMatchedCells() {
        val v = TestViews.quietView()
        plantRow(v, 3)
        val matches = v.board.findMatches()
        assertTrue(matches.isNotEmpty())
        val now = TestViews.now()
        v.animPhase = AnimPhase.DROPPING; v.animStartMs = now
        v.advanceAnimation(now + v.DROP_MS)
        assertEquals(AnimPhase.POPPING, v.animPhase)
        assertTrue(v.isCascade)
        assertEquals(1, v.cascadeCount)
        assertEquals(matches.map { Pair(it.first, it.second) }.toSet(), v.popCells.map { Pair(it.row, it.col) }.toSet())
        val label = v.floatLabels.single()
        assertEquals("×2", label.text)
        val cx = matches.map { (_, c) -> v.boardLeft + c * v.cellSize + v.cellSize / 2f }.average().toFloat()
        val cy = matches.map { (r, _) -> v.boardTop + r * v.cellSize + v.cellSize / 2f }.average().toFloat()
        assertEquals(cx, label.cx, 0.01f)
        assertEquals(cy, label.cy, 0.01f)
        // finishing the cascade pop is not a player golden pop
        v.advanceAnimation(now + v.POP_MS * 2)
        assertEquals(false, v.prefs.goldenPopped)
    }
}
