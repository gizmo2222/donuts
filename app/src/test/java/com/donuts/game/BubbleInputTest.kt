package com.donuts.game

import android.graphics.Bitmap
import android.graphics.Canvas
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

/** The bubble donut in play (B37): a tap pops it, a long chain leaves one, and it is drawn. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BubbleInputTest : GameInputTestBase() {

    @Test
    fun aTapOnABubblePopsItsThreeByThreeAndIsTalliedAsABubble() {
        val v = planted()
        v.board.markBubble(2, 2)
        drag(v, 2 to 2)
        assertEquals(AnimPhase.POPPING, v.animPhase)
        assertEquals(PowerUp.BUBBLE, v.powerFx?.kind)
        assertEquals(PowerUp.BUBBLE, v.pendingResult?.powerUp)
        val block = (1..3).flatMap { r -> (1..3).map { c -> r to c } }.toSet()
        assertEquals(block, v.popCells.map { it.row to it.col }.toSet())
        assertTrue(v.wobbleCells.isEmpty())
        assertEquals(1, v.playTally.powerUps(PowerUp.BUBBLE))
        assertEquals(0, v.playTally.chains(1))          // a bubble tap is not a try
        runUntilIdle(v)
        assertTrue(v.board.bubbles().isEmpty())
        assertTrue(TestViews.total(v) >= 9)
    }

    @Test
    fun aTapBesideABubbleStillWobbles() {
        val v = planted()
        v.fxRandom = TestViews.fixedRandom(1)           // not the rare giggle
        v.board.markBubble(2, 2)
        drag(v, 4 to 4)
        assertEquals(AnimPhase.IDLE, v.animPhase)
        assertEquals(listOf(4 to 4), v.wobbleCells)
        assertEquals(listOf(2 to 2), v.board.bubbles())
    }

    @Test
    fun aChainOfSixLeavesABubbleThatSpringsInAfterTheDrop() {
        val v = planted(listOf("SSSSSS", "CBVMAC", "MAMBVC", "VCAVMB", "BMCABA", "AVBMCV"))
        drag(v, 0 to 0, 0 to 1, 0 to 2, 0 to 3, 0 to 4, 0 to 5)
        assertEquals(0 to 5, v.pendingResult?.bubbleAt)
        runUntilIdle(v)
        assertEquals(1, v.board.bubbles().size)
        // The newest bubble pops in once the drop is over and then forgets it was new
        TestViews.frames(v, TestViews.canvas(v), ((BUBBLE_IN_MS + v.DROP_MS) / 16 + 4).toInt())
        assertNull(v.bubbleBornAt)
    }

    @Test
    fun aBubbleIsDrawnOverItsDonutWithFullAndCalmMotion() {
        for (calm in listOf(false, true)) {
            val plain = shot(planted(), calm)
            val v = planted(); v.board.markBubble(2, 2)
            val bubbled = shot(v, calm)
            val (x, y) = TestViews.cellCenter(v, 2, 2)
            val h = (v.cellSize / 2).toInt()
            var diff = 0
            for (px in x.toInt() - h until x.toInt() + h) for (py in y.toInt() - h until y.toInt() + h)
                if (plain.getPixel(px, py) != bubbled.getPixel(px, py)) diff++
            assertTrue("calm=$calm: only $diff pixels changed", diff > h * h / 2)
        }
    }

    private fun shot(v: GameView, calm: Boolean): Bitmap {
        v.reducedMotion = calm
        val bmp = Bitmap.createBitmap(v.surfaceW, v.surfaceH, Bitmap.Config.ARGB_8888)
        v.drawFrame(Canvas(bmp))
        return bmp
    }
}
