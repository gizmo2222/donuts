package com.donuts.game

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import kotlin.random.Random

// What shows on a cold start before the game draws: the window background (and the splash on API 31+)
// must be the same cream as the first frame, and a frame drawn before layout must not be an empty buffer.
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WindowThemeTest {

    private fun themeColor(a: Activity, attr: Int): Int {
        val t = a.theme.obtainStyledAttributes(intArrayOf(attr))
        try { return t.getColor(0, 0) } finally { t.recycle() }
    }

    private fun themeFlag(a: Activity, attr: Int): Boolean {
        val t = a.theme.obtainStyledAttributes(intArrayOf(attr))
        try { return t.getBoolean(0, false) } finally { t.recycle() }
    }

    private fun checkWindow(a: Activity) {
        assertTrue("plain ComponentActivity, no appcompat", a is ComponentActivity)
        assertEquals("window background is the game's cream", GameTheme.bg, themeColor(a, android.R.attr.windowBackground))
        assertEquals(GameTheme.bg, themeColor(a, android.R.attr.statusBarColor))
        assertEquals(GameTheme.bg, themeColor(a, android.R.attr.navigationBarColor))
        assertTrue(themeFlag(a, android.R.attr.windowLightStatusBar))
        assertTrue(themeFlag(a, android.R.attr.windowLightNavigationBar))
        assertTrue("no action bar", a.actionBar == null || !a.actionBar!!.isShowing)
    }

    @Test fun bothScreensUseTheCreamPlatformTheme() {
        val main = Robolectric.buildActivity(MainActivity::class.java).create().start().resume()
        checkWindow(main.get())
        main.pause().stop().destroy()
        val game = Robolectric.buildActivity(GameActivity::class.java).create().start().resume()
        checkWindow(game.get())
        game.pause().stop().destroy()
    }

    @Test fun aFrameDrawnBeforeLayoutIsCreamNotBlack() {
        val prefs = Prefs(TestViews.app())
        val v = GameView(TestViews.app(), GameBoard(6, 6, Random(1)), prefs)
        v.cellSize = 0f   // the state between surfaceCreated and surfaceChanged
        val bmp = Bitmap.createBitmap(40, 40, Bitmap.Config.ARGB_8888)
        v.drawFrame(Canvas(bmp))
        for (x in intArrayOf(0, 20, 39)) for (y in intArrayOf(0, 20, 39))
            assertEquals("pixel $x,$y", GameTheme.bg, bmp.getPixel(x, y))
    }
}
