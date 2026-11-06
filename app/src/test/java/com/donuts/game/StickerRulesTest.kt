package com.donuts.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StickerRulesTest {

    private val empty = StickerStats(0, 0, 0, 0, false, 0)

    private fun earned(i: Int, s: StickerStats) = StickerRules.isEarned(i, s)

    private fun edge(i: Int, below: StickerStats, at: StickerStats) {
        assertFalse("sticker $i just below", earned(i, below))
        assertTrue("sticker $i at threshold", earned(i, at))
    }

    @Test fun lifetimeStickers() {
        edge(0, empty.copy(lifetime = 9), empty.copy(lifetime = 10))
        edge(1, empty.copy(lifetime = 49), empty.copy(lifetime = 50))
        edge(2, empty.copy(lifetime = 499), empty.copy(lifetime = 500))
    }

    @Test fun chainStickers() {
        edge(3, empty.copy(bestChain = 3), empty.copy(bestChain = 4))
        edge(4, empty.copy(bestChain = 5), empty.copy(bestChain = 6))
        edge(5, empty.copy(bestChain = 7), empty.copy(bestChain = 8))
    }

    @Test fun scoreStickersUseTheBetterBoard() {
        edge(6, empty.copy(highScore6x6 = 19), empty.copy(highScore8x8 = 20))
        edge(7, empty.copy(highScore8x8 = 59), empty.copy(highScore6x6 = 60))
        edge(8, empty.copy(highScore6x6 = 199, highScore8x8 = 199), empty.copy(highScore6x6 = 200))
    }

    @Test fun goldenSticker() {
        edge(9, empty, empty.copy(goldenPopped = true))
    }

    @Test fun bothSizesSticker() {
        assertFalse(earned(10, empty.copy(highScore6x6 = 5)))
        assertFalse(earned(10, empty.copy(highScore8x8 = 5)))
        assertTrue(earned(10, empty.copy(highScore6x6 = 1, highScore8x8 = 1)))
    }

    @Test fun sessionSticker() {
        edge(11, empty.copy(sessions = 9), empty.copy(sessions = 10))
    }

    @Test fun outOfRangeStickerIsNeverEarned() {
        val full = StickerStats(9999, 999, 999, 99, true, 99)
        assertFalse(earned(12, full))
        assertFalse(earned(-1, full))
    }

    @Test fun maskOfEmptyAndFullRecords() {
        assertEquals(0, StickerRules.earnedMask(empty))
        assertEquals(0xFFF, StickerRules.earnedMask(StickerStats(9999, 999, 999, 99, true, 99)))
    }

    @Test fun maskSetsOnlyEarnedBits() {
        val s = empty.copy(lifetime = 10, goldenPopped = true)
        assertEquals((1 shl 0) or (1 shl 9), StickerRules.earnedMask(s))
    }

    @Test fun boxAndSlotCounts() {
        val expected = listOf(
            // lifetime, boxes, slots
            Triple(0, 0, 0), Triple(9, 0, 0), Triple(10, 0, 1),
            Triple(119, 0, 11), Triple(120, 1, 0), Triple(125, 1, 0)
        )
        for ((life, boxes, slots) in expected) {
            assertEquals("boxes at $life", boxes, StickerRules.boxesFilled(life))
            assertEquals("slots at $life", slots, StickerRules.slotsFilled(life))
        }
    }

    @Test fun allZeroStatsEarnNothingAndEmptyTheBox() {
        assertEquals(0, StickerRules.earnedMask(empty))
        assertEquals(0, StickerRules.boxesFilled(0))
        assertEquals(0, StickerRules.slotsFilled(0))
    }

    @Test fun slotAndBoxJustFilledFollowTheBoundaries() {
        assertTrue(StickerRules.slotJustFilled(9, 10))
        assertFalse(StickerRules.slotJustFilled(10, 19))
        assertTrue(StickerRules.slotJustFilled(119, 120))
        assertTrue(StickerRules.boxJustFilled(119, 120))
        assertFalse(StickerRules.boxJustFilled(9, 10))
        assertTrue(StickerRules.slotJustFilled(5, 47))
        assertFalse(StickerRules.boxJustFilled(5, 47))
        assertFalse(StickerRules.slotJustFilled(30, 30))
        assertFalse(StickerRules.boxJustFilled(120, 120))
    }

    @Test fun toAnnounceIsEarnedMinusAnnounced() {
        assertEquals(0b0100, StickerRules.toAnnounce(0b0111, 0b0011))
        assertEquals(0, StickerRules.toAnnounce(0b0011, 0b0111))
        assertEquals(0b0011, StickerRules.toAnnounce(0b0011, 0))
    }

    @Test fun stickerEarnedOutsideTheFrameLoopStillGetsAnnounced() {
        // announced mask saved at 0b01; sticker 1 earned while no frames ran
        val announced = 0b01
        val earned = StickerRules.earnedMask(StickerStats(60, 0, 0, 0, false, 0))
        assertTrue(StickerRules.toAnnounce(earned, announced) and 0b10 != 0)
    }
}
