package com.donuts.game

import android.graphics.Bitmap
import android.graphics.Canvas
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

/** How each celebration look throws its confetti and paints its banner (GameAnimation.kt, EffectsDraw.kt). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CelebrationLookTest {

    private fun spawn(look: CelebrationLook, seed: Int = 1): GameView =
        TestViews.gameView(seed = seed).also { it.spawnParticles(look) }

    @Test
    fun theSameSeedThrowsTheSameConfetti() {
        val look = CelebrationLook(ConfettiStyle.RAIN, 2)
        val a = spawn(look, 4).particles; val b = spawn(look, 4).particles; val c = spawn(look, 5).particles
        assertEquals(a.map { listOf(it.x, it.y, it.vx, it.vy, it.radius) }, b.map { listOf(it.x, it.y, it.vx, it.vy, it.radius) })
        assertNotEquals(a.map { it.x }, c.map { it.x })
    }

    @Test
    fun theFountainFiresUpFromTheMiddleOfTheBoardBottom() {
        val v = spawn(CelebrationLook(ConfettiStyle.FOUNTAIN, 1))
        val mid = v.boardLeft + v.board.cols * v.cellSize / 2f
        val bottom = v.boardTop + v.board.rows * v.cellSize
        assertTrue(v.particles.all { kotlin.math.abs(it.x - mid) < 0.5f && it.y == bottom && it.vy < 0 && it.fall == 0f })
        assertTrue(v.particles.any { it.vx < 0 } && v.particles.any { it.vx > 0 })
    }

    @Test
    fun rainStartsAboveTheBoardAndFluttersDownNoFasterThanItsOwnSpeed() {
        val v = spawn(CelebrationLook(ConfettiStyle.RAIN, 3))
        val left = v.boardLeft; val right = v.boardLeft + v.board.cols * v.cellSize
        assertTrue(v.particles.all { it.y < v.boardTop && it.x in left..right && it.fall > 0f && it.vy > 0f })
        val c = TestViews.canvas(v)
        v.celebrateMs = TestViews.now(); v.physicsMs = TestViews.now()
        TestViews.frames(v, c, 40)
        assertTrue(v.particles.isNotEmpty())
        assertTrue("capped at its fall speed", v.particles.all { it.vy <= it.fall })
        assertTrue("some reached the board", v.particles.any { it.y > v.boardTop })
    }

    @Test
    fun theGoldenShowerThrowsOnlyGoldAndWhite() {
        val v = spawn(CelebrationLook(ConfettiStyle.CANNONS, 0, golden = true))
        assertTrue(v.particles.all { it.color in GameTheme.goldColors })
        val plain = spawn(CelebrationLook(ConfettiStyle.CANNONS, 0))
        assertTrue(plain.particles.any { it.color !in GameTheme.goldColors })
    }

    @Test
    fun startCelebrationKeepsTheLookAndTheBannerWearsItsColour() {
        for (banner in 0 until CelebrationMix.BANNER_COLOURS) {
            val v = TestViews.quietView()
            val look = CelebrationLook(ConfettiStyle.RAIN, banner, golden = banner == 0)
            v.startCelebration(TestViews.now(), "WOW!", look)
            assertEquals(look, v.celebrateLook)
            val bmp = Bitmap.createBitmap(v.surfaceW, v.surfaceH, Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp)
            TestViews.advance(1000); v.drawFrame(c)                // the banner is fully in
            val bx = (v.boardLeft + v.board.cols * v.cellSize / 2f).toInt()
            val by = (v.boardTop + v.board.rows * v.cellSize * 0.5f + 28f * v.u).toInt()   // under the word
            assertEquals("banner $banner", GameTheme.bannerColors[banner] and 0xFFFFFF, bmp.getPixel(bx, by) and 0xFFFFFF)
        }
    }

    @Test
    fun celebrationsInARowChangeTheirLook() {
        val v = TestViews.quietView()
        val looks = (0 until 6).map {
            v.startCelebration(TestViews.now(), "WOW!")
            v.celebrateLook!!
        }
        looks.zipWithNext().forEach { (a, b) -> assertNotEquals(a.style, b.style) }
    }
}
