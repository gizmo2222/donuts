package com.donuts.game

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.RectF
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

/** The pal cheers for what Steven does on the board (chains, power-ups, celebrations) and paints each pose in its box. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PalReactionTest : GameInputTestBase() {

    private val five = listOf("SSSSSV", "CBVMAC", "MAMBVC", "VCAVMB", "BMCABA", "AVBMCV")

    @Test fun aChainOfThreeSmilesAndAChainOfFourCheers() {
        val v = planted(five)
        drag(v, 0 to 0, 0 to 1, 0 to 2)
        assertEquals(PalReaction.SMILE, v.palReaction)
        val w = planted(five)
        drag(w, 0 to 0, 0 to 1, 0 to 2, 0 to 3)
        assertEquals(PalReaction.CHEER, w.palReaction)
    }

    @Test fun aPowerUpWows() {
        val v = planted(five)
        drag(v, 0 to 0, 0 to 1, 0 to 2, 0 to 3, 0 to 4)
        assertEquals(PalReaction.WOW, v.palReaction)
        assertTrue(v.palReactMs >= 0L)
    }

    @Test fun aTapOrATwoLeaveThePalAlone() {
        val v = planted(five)
        drag(v, 0 to 0)
        assertEquals(PalReaction.NONE, v.palReaction)
        val w = planted(five)
        drag(w, 0 to 0, 0 to 1)
        TestViews.settleLift(w)
        assertEquals(PalReaction.NONE, w.palReaction)
    }

    @Test fun aCelebrationThrowsAPartyThatASmallChainCannotCutShort() {
        val v = planted(five)
        val now = TestViews.now()
        v.startCelebration(now, "100!")
        assertEquals(PalReaction.PARTY, v.palReaction)
        v.palReact(PalReaction.SMILE, now + 100L)
        assertEquals(PalReaction.PARTY, v.palReaction)
        assertEquals(now, v.palReactMs)
        v.palReact(PalReaction.SMILE, now + PalReaction.PARTY.ms)
        assertEquals(PalReaction.SMILE, v.palReaction)
    }

    @Test fun framesDrawThroughAReactionWithReducedMotionOnAndOff() {
        for (reduced in listOf(false, true)) {
            val v = planted(five)
            v.reducedMotion = reduced
            drag(v, 0 to 0, 0 to 1, 0 to 2, 0 to 3, 0 to 4)
            runUntilIdle(v)
            TestViews.frames(v, TestViews.canvas(v), 60)
        }
    }

    @Test fun newGameForgetsTheReaction() {
        val v = planted(five)
        v.palReact(PalReaction.WOW, TestViews.now())
        v.startOver()
        assertEquals(PalReaction.NONE, v.palReaction)
        assertEquals(-1L, v.palReactMs)
    }

    /** Every frame of a cheer and a party, arms up and shimmy included, stays in the pal's box (scaled by its squash, raised by its lift). */
    @Test fun everyReactionPosePaintsInsideItsBox() {
        val v = TestViews.gameView()
        val r = 60f; val cx = 120f; val footY = 230f
        val bmp = Bitmap.createBitmap(240, 260, Bitmap.Config.ARGB_8888)
        var happyFaces = 0
        for (reaction in listOf(PalReaction.CHEER, PalReaction.PARTY)) for (step in 0..12) {
            val pose = PalMotion.at(10_000L + reaction.ms * step / 12, false, reaction, 10_000L)
            bmp.eraseColor(Color.TRANSPARENT)
            v.drawPal(Canvas(bmp), cx, footY, r, pose)
            val half = r * PAL_HALF_W * pose.squashX
            val box = RectF(cx - half, footY - r * (PAL_TALL + pose.bob) * pose.squashY, cx + half, footY + r * 0.12f)
            for (y in 0 until bmp.height) for (x in 0 until bmp.width) {
                if (Color.alpha(bmp.getPixel(x, y)) == 0) continue
                assertTrue("$reaction step $step painted at $x,$y outside $box",
                    x + 1 >= box.left && x - 1 <= box.right && y + 1 >= box.top && y - 1 <= box.bottom)
            }
            if (pose.joy > PalMotion.HAPPY_EYES) happyFaces++
        }
        assertTrue(happyFaces >= 4)
    }

    /** The cheering arms reach above the shoulders: more paint lands up there than in the resting pose. */
    @Test fun cheeringArmsGoUp() {
        val v = TestViews.gameView()
        val r = 60f; val cx = 120f; val footY = 230f
        fun highPixels(pose: PalPose): Int {
            val bmp = Bitmap.createBitmap(240, 260, Bitmap.Config.ARGB_8888)
            v.drawPal(Canvas(bmp), cx, footY, r, pose)
            var n = 0
            val top = (footY - r * 1.4f).toInt()
            for (y in 0 until top) for (x in 0 until bmp.width) {
                if (Math.abs(x - cx) > r * 0.75f && Color.alpha(bmp.getPixel(x, y)) != 0) n++
            }
            return n
        }
        val rest = highPixels(PalMotion.STILL)
        val up = highPixels(PalMotion.STILL.copy(arms = 1f))
        assertTrue("rest $rest up $up", up > rest + 100)
    }
}
