package com.donuts.game

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.*

/**
 * Synthesized sound effects — no audio files required.
 * All sounds are generated from PCM math and played on background threads.
 */
class SoundEngine {

    private val sampleRate = 44100

    private fun waveSample(phase: Double): Double = sin(phase)

    // -----------------------------------------------------------------------
    // Public API
    // -----------------------------------------------------------------------

    /** Blip when a cell joins the drag chain. Pitch rises with chain length. */
    fun playConnectBlip(chainLength: Int) {
        val freq = (350f + chainLength * 55f).coerceAtMost(2000f)
        playTone(freq, 0.07f, 0.35f)
    }

    /** Chirp when the chain is cleared / donuts pop. */
    fun playPopClear() {
        playSweep(520f, 1300f, 0.13f, 0.45f)
    }

    /** Gentle falling "boop" when a chain is let go too short. Friendly, never a buzzer. */
    fun playBoop() {
        playSweep(440f, 300f, 0.12f, 0.28f)
    }

    /** Soft thud when new donuts drop and land. */
    fun playDropLand() {
        playTone(90f, 0.08f, 0.30f)
    }

    /**
     * Happy arpeggio on milestone, with a twist per confetti style ([twist] is its ordinal): 0 climbs C-E-G,
     * 1 climbs and whoops up, 2 tumbles down and hops back up. [golden] adds a high sparkly trill.
     */
    fun playMilestone(twist: Int = 0, golden: Boolean = false) {
        val notes = MILESTONE_TUNES[Math.floorMod(twist, MILESTONE_TUNES.size)]
        Thread {
            for (n in notes.indices step 3) {
                if (notes[n] == notes[n + 1]) playToneBlocking(notes[n], notes[n + 2], 0.42f)
                else playSweepBlocking(notes[n], notes[n + 1], notes[n + 2], 0.40f)
                Thread.sleep(80)
            }
            if (golden) for (k in 0 until 4) {
                playToneBlocking(if (k % 2 == 0) 1047f else 1319f, 0.06f, 0.30f)   // C6, E6
                Thread.sleep(30)
            }
        }.start()
    }

    /** A rare giggle on a lone tap instead of the boop: three quick bouncy "hee"s. */
    fun playGiggle() {
        Thread {
            for (k in 0 until 3) {
                playSweepBlocking(700f + k * 60f, 980f + k * 60f, 0.07f, 0.30f)
                Thread.sleep(35)
            }
        }.start()
    }

    /**
     * A short jingle of its own for donut friend [idx] (0..11), played when he is met and when his tile is
     * tapped. Each note is (start Hz, end Hz, seconds); a note whose ends differ slides between them.
     */
    fun playFriend(idx: Int) {
        val tune = FRIEND_TUNES[Math.floorMod(idx, FRIEND_TUNES.size)]
        Thread {
            for (n in tune.indices step 3) {
                if (tune[n] == tune[n + 1]) playToneBlocking(tune[n], tune[n + 2], 0.40f)
                else playSweepBlocking(tune[n], tune[n + 1], tune[n + 2], 0.38f)
                Thread.sleep(30)
            }
        }.start()
    }

    /** Downward whoosh when the board shuffles. */
    fun playShuffle() {
        playSweep(900f, 280f, 0.28f, 0.38f)
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private fun playTone(freq: Float, durationSec: Float, volume: Float) {
        Thread { playToneBlocking(freq, durationSec, volume) }.start()
    }

    private fun playToneBlocking(freq: Float, durationSec: Float, volume: Float) {
        val numSamples = (sampleRate * durationSec).toInt()
        val buf = ShortArray(numSamples)
        for (i in 0 until numSamples) {
            val t        = i.toFloat() / sampleRate
            val env      = (1f - t / durationSec).pow(0.6f)
            buf[i] = (waveSample(2.0 * PI * freq * t) * env * volume * Short.MAX_VALUE).toInt().toShort()
        }
        playPCM(buf)
    }

    private fun playSweep(freqStart: Float, freqEnd: Float, durationSec: Float, volume: Float) {
        Thread { playSweepBlocking(freqStart, freqEnd, durationSec, volume) }.start()
    }

    private fun playSweepBlocking(freqStart: Float, freqEnd: Float, durationSec: Float, volume: Float) {
        val numSamples = (sampleRate * durationSec).toInt()
        val buf  = ShortArray(numSamples)
        var phase = 0.0
        for (i in 0 until numSamples) {
            val t    = i.toFloat() / numSamples
            val freq = freqStart + (freqEnd - freqStart) * t
            phase   += 2.0 * PI * freq / sampleRate
            val env  = (1f - t * 0.7f)
            buf[i] = (waveSample(phase) * env * volume * Short.MAX_VALUE).toInt().toShort()
        }
        playPCM(buf)
    }

    private fun playPCM(samples: ShortArray) {
        val track = try {
            AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(sampleRate)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(samples.size * 2)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()
        } catch (_: Exception) { return }
        try {
            track.write(samples, 0, samples.size)
            track.play()
            Thread.sleep((samples.size * 1000L / sampleRate) + 40L)
            track.stop()
        } catch (_: Exception) {
            // Never crash the game over a sound
        } finally {
            try { track.release() } catch (_: Exception) {}
        }
    }
}

// Milestone tunes by confetti style, as (start Hz, end Hz, seconds) per note
private val MILESTONE_TUNES: Array<FloatArray> = arrayOf(
    floatArrayOf(523f, 523f, 0.09f, 659f, 659f, 0.09f, 784f, 784f, 0.14f),                         // cannons: C-E-G
    floatArrayOf(523f, 523f, 0.08f, 659f, 659f, 0.08f, 784f, 1175f, 0.16f),                        // fountain: whoop up
    floatArrayOf(784f, 784f, 0.08f, 659f, 659f, 0.08f, 523f, 523f, 0.08f, 1047f, 1047f, 0.14f))    // rain: down, hop up

private val FRIEND_TUNES: Array<FloatArray> = arrayOf(
    floatArrayOf(1047f, 1047f, 0.06f, 1319f, 1319f, 0.06f, 1568f, 1568f, 0.10f),                     // Sprinkle: sparkly up
    floatArrayOf(420f, 620f, 0.09f, 620f, 420f, 0.09f, 784f, 784f, 0.12f),                           // Jelly: a wobble
    floatArrayOf(300f, 700f, 0.10f, 350f, 800f, 0.08f, 880f, 880f, 0.10f),                           // Bouncy: boing boing
    floatArrayOf(900f, 520f, 0.10f, 520f, 1040f, 0.14f),                                             // Swirl: down and round
    floatArrayOf(392f, 392f, 0.08f, 392f, 392f, 0.08f, 523f, 523f, 0.16f),                           // Choco: ta-ta-daa
    floatArrayOf(220f, 900f, 0.08f, 784f, 784f, 0.07f, 1047f, 1047f, 0.12f),                         // Kicky: a kick, then goal
    floatArrayOf(659f, 659f, 0.07f, 784f, 784f, 0.07f, 659f, 659f, 0.07f, 1047f, 1047f, 0.12f),      // Berry: a skip
    floatArrayOf(520f, 260f, 0.09f, 460f, 230f, 0.09f, 300f, 720f, 0.10f),                           // Gloop: blub blub bloop
    floatArrayOf(300f, 1600f, 0.18f, 1319f, 1319f, 0.08f),                                           // Zoomy: zoom
    floatArrayOf(523f, 523f, 0.05f, 523f, 523f, 0.05f, 400f, 820f, 0.14f),                           // Puff: puff puff whoosh
    floatArrayOf(1200f, 1200f, 0.04f, 1500f, 1500f, 0.04f, 1350f, 1350f, 0.04f, 1760f, 1760f, 0.06f), // Fizz: bubbles
    floatArrayOf(392f, 392f, 0.08f, 523f, 523f, 0.08f, 659f, 659f, 0.08f, 784f, 784f, 0.18f)         // Captain Glaze: fanfare
)
