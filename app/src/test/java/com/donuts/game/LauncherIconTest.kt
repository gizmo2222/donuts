package com.donuts.game

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.AdaptiveIconDrawable
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

// The launcher icons live once in mipmap-anydpi (minSdk 26 always takes the adaptive icon), so every
// density and the lowest and newest supported API must still load both icons as adaptive icons that paint.
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LauncherIconTest {

    private val densities = listOf("mdpi", "hdpi", "xhdpi", "xxhdpi", "xxxhdpi")

    private fun checkIcons() {
        val app = RuntimeEnvironment.getApplication()
        val info = app.packageManager.getApplicationInfo(app.packageName, 0)
        assertEquals("manifest icon", R.mipmap.ic_launcher, info.icon)
        for (density in densities) {
            RuntimeEnvironment.setQualifiers(density)
            for (id in listOf(R.mipmap.ic_launcher, R.mipmap.ic_launcher_round)) {
                val d = app.getDrawable(id)
                assertTrue("$density icon $id is adaptive", d is AdaptiveIconDrawable)
                val icon = d as AdaptiveIconDrawable
                assertTrue("$density icon $id has both layers", icon.background != null && icon.foreground != null)
                val bmp = Bitmap.createBitmap(108, 108, Bitmap.Config.ARGB_8888)
                icon.setBounds(0, 0, 108, 108)
                icon.draw(Canvas(bmp))
                val centre = bmp.getPixel(54, 54)
                assertTrue("$density icon $id paints its middle", Color.alpha(centre) > 0)
            }
        }
    }

    @Test @Config(sdk = [26])
    fun theIconsLoadAsAdaptiveIconsOnApi26AtEveryDensity() = checkIcons()

    @Test @Config(sdk = [34])
    fun theIconsLoadAsAdaptiveIconsOnApi34AtEveryDensity() = checkIcons()
}
