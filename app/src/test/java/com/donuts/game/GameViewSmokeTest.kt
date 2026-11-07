package com.donuts.game

import android.view.MotionEvent
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class GameViewSmokeTest {
    @Test
    fun buildsLaysOutDrawsAndTakesATouch() {
        val v = TestViews.gameView()
        assertTrue(v.cellSize > 0f)
        val c = TestViews.canvas(v)
        TestViews.frames(v, c, 5)
        val (x, y) = TestViews.cellCenter(v, 0, 0)
        TestViews.touch(v, MotionEvent.ACTION_DOWN, x, y)
        TestViews.touch(v, MotionEvent.ACTION_UP, x, y)
        TestViews.frames(v, c, 5)
    }
}
