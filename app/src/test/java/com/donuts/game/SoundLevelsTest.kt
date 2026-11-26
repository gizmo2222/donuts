package com.donuts.game

import com.donuts.game.SoundLevels.Kind
import com.donuts.game.SoundLevels.Level
import com.donuts.game.SoundLevels.LOUDNESS_CAP
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

/** The named sound levels, their kinds and the loudness cap (B27). */
class SoundLevelsTest {

    private val capPeak = (LOUDNESS_CAP * Short.MAX_VALUE).toInt()
    private fun peak(buf: ShortArray): Int = buf.maxOfOrNull { abs(it.toInt()) } ?: 0

    @Test
    fun theCapIsHalfOfFullScale() {
        assertEquals(0.50f, LOUDNESS_CAP, 0f)
    }

    @Test
    fun everyNamedLevelIsAudibleAndUnderTheCap() {
        for (l in Level.values()) assertTrue("$l = ${l.volume}", l.volume > 0f && l.volume <= LOUDNESS_CAP)
    }

    @Test
    fun everyKindHasLevelsAndFeedbackStaysSofterThanClearsAndCheers() {
        for (k in Kind.values()) assertTrue("$k has no level", Level.values().any { it.kind == k })
        val feedback = SoundLevels.loudest(Kind.FEEDBACK)
        assertTrue(feedback < SoundLevels.loudest(Kind.CLEAR))
        assertTrue(feedback < SoundLevels.loudest(Kind.CHEER))
        assertTrue(SoundLevels.softest(Kind.FEEDBACK) <= feedback)
    }

    @Test
    fun cappedClampsLoudNegativeAndNotANumber() {
        assertEquals(LOUDNESS_CAP, SoundLevels.capped(1f), 0f)
        assertEquals(LOUDNESS_CAP, SoundLevels.capped(Float.POSITIVE_INFINITY), 0f)
        assertEquals(0f, SoundLevels.capped(-0.2f), 0f)
        assertEquals(0f, SoundLevels.capped(Float.NaN), 0f)
        assertEquals(0.3f, SoundLevels.capped(0.3f), 0f)
    }

    @Test
    fun everyLevelPlaysNearItsVolumeAndNeverPastTheCap() {
        for (l in Level.values()) {
            val want = l.volume * Short.MAX_VALUE
            for (buf in listOf(SoundLevels.tone(440f, 0.08f, l.volume), SoundLevels.sweep(520f, 1300f, 0.1f, l.volume))) {
                val p = peak(buf)
                assertTrue("$l peak $p", p <= capPeak)
                assertTrue("$l peak $p is far under $want", p >= 0.9f * want)
            }
        }
    }

    @Test
    fun aTooLoudVolumeIsHeldAtTheCapAndANegativeOneIsSilent() {
        for (v in listOf(1f, 5f, Float.MAX_VALUE)) {
            val t = SoundLevels.tone(90f, 0.08f, v)
            val s = SoundLevels.sweep(900f, 280f, 0.28f, v)
            assertTrue(peak(t) in (capPeak * 9 / 10)..capPeak)
            assertTrue(peak(s) in (capPeak * 9 / 10)..capPeak)
        }
        assertEquals(0, peak(SoundLevels.tone(440f, 0.05f, -1f)))
        assertEquals(0, peak(SoundLevels.sweep(440f, 300f, 0.05f, Float.NaN)))
    }

    @Test
    fun lengthsFollowTheSampleRateAndAZeroLengthIsEmpty() {
        assertEquals(44100, SoundLevels.tone(440f, 1f, 0.3f).size)
        assertEquals(4000, SoundLevels.sweep(440f, 300f, 0.5f, 0.3f, rate = 8000).size)
        assertEquals(0, SoundLevels.tone(440f, 0f, 0.3f).size)
    }

    @Test
    fun waveIsASineAtQuarterPoints() {
        assertEquals(0.0, SoundLevels.wave(0.0), 1e-9)
        assertEquals(1.0, SoundLevels.wave(Math.PI / 2), 1e-9)
        assertEquals(-1.0, SoundLevels.wave(Math.PI * 1.5), 1e-9)
        for (i in 0..2000) assertTrue(SoundLevels.wave(i * 0.01) in -1.0..1.0)
    }
}
