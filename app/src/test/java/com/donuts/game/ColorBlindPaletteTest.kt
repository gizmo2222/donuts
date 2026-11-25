package com.donuts.game

import android.graphics.Color
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.math.cbrt
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * B9: no two donuts may share both their dough and their glaze colour for a colour-blind player.
 * The same simulation as `tools\colorblind.ps1` (Machado et al. 2009, full severity, linear RGB) and
 * CIE76 Delta E. Greyscale is left out on purpose: there the six silhouettes and markings tell them apart.
 */
@RunWith(RobolectricTestRunner::class)
class ColorBlindPaletteTest {

    private val modes = mapOf(
        "protan" to doubleArrayOf(0.152286, 1.052583, -0.204868, 0.114503, 0.786281, 0.099216, -0.003882, -0.048116, 1.051998),
        "deutan" to doubleArrayOf(0.367322, 0.860646, -0.227968, 0.280085, 0.672501, 0.047413, -0.011820, 0.042940, 0.968881),
        "tritan" to doubleArrayOf(1.255528, -0.076749, -0.178779, -0.078411, 0.930809, 0.147602, 0.004733, 0.691367, 0.303900),
    )

    private fun lin(c: Int): Double {
        val v = c / 255.0
        return if (v <= 0.04045) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
    }

    private fun f(t: Double) = if (t > 0.008856) cbrt(t) else 7.787 * t + 16.0 / 116

    private fun lab(color: Int, m: DoubleArray?): DoubleArray {
        val l = doubleArrayOf(lin(Color.red(color)), lin(Color.green(color)), lin(Color.blue(color)))
        val s = if (m == null) l else DoubleArray(3) { i ->
            (m[i * 3] * l[0] + m[i * 3 + 1] * l[1] + m[i * 3 + 2] * l[2]).coerceIn(0.0, 1.0)
        }
        val x = 0.4124 * s[0] + 0.3576 * s[1] + 0.1805 * s[2]
        val y = 0.2126 * s[0] + 0.7152 * s[1] + 0.0722 * s[2]
        val z = 0.0193 * s[0] + 0.1192 * s[1] + 0.9505 * s[2]
        val fx = f(x / 0.95047); val fy = f(y); val fz = f(z / 1.08883)
        return doubleArrayOf(116 * fy - 16, 500 * (fx - fy), 200 * (fy - fz))
    }

    private fun deltaE(a: Int, b: Int, m: DoubleArray?): Double {
        val p = lab(a, m); val q = lab(b, m)
        return sqrt((p[0] - q[0]).pow(2) + (p[1] - q[1]).pow(2) + (p[2] - q[2]).pow(2))
    }

    @Test fun noTwoDonutsShareBothColoursUnderAnyColourBlindness() {
        val types = DonutType.values()
        val clashes = mutableListOf<String>()
        for ((name, m) in modes) for (i in types.indices) for (j in i + 1 until types.size) {
            val a = types[i]; val b = types[j]
            val body = deltaE(a.bodyColor, b.bodyColor, m)
            val glaze = deltaE(a.glazeColor, b.glazeColor, m)
            // Today's closest pair is VANILLA/MATCHA under protanopia at about 18.7 (body); 15 leaves room.
            if (maxOf(body, glaze) < MIN_DELTA) clashes += "$name ${a.name}/${b.name} body %.1f glaze %.1f".format(body, glaze)
        }
        assertTrue("donut colours that collide: $clashes", clashes.isEmpty())
    }

    @Test fun theSimulationSeesRedAndGreenAsCloseUnderProtanopiaButNotNormally() {
        val red = Color.rgb(200, 60, 40); val green = Color.rgb(120, 110, 40)
        assertTrue(deltaE(red, green, null) > 40)
        assertTrue(deltaE(red, green, modes.getValue("protan")) < 15)
    }

    private companion object { const val MIN_DELTA = 15.0 }
}
