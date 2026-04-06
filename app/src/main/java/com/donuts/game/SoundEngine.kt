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

    var packIndex: Int = 0

    private fun waveSample(phase: Double): Double = when (packIndex) {
        1    -> sin(phase)  // Space: pure sine
        2    -> (2.0 / Math.PI) * asin(sin(phase).coerceIn(-1.0, 1.0))  // Triangle
        3    -> if (sin(phase) >= 0) 0.7 else -0.7  // Square (reduced amplitude)
        else -> sin(phase)  // Bubbly: sine
    }

    // -----------------------------------------------------------------------
    // Public API
    // -----------------------------------------------------------------------

    /** Blip when a cell joins the drag chain. Pitch rises with chain length. */
    fun playConnectBlip(chainLength: Int) {
        val (base, step) = when (packIndex) {
            1    -> Pair(200f, 40f)   // Space
            2    -> Pair(450f, 90f)   // Wild
            3    -> Pair(250f, 35f)   // Mech
            else -> Pair(350f, 55f)   // Bubbly
        }
        val freq = (base + chainLength * step).coerceAtMost(2000f)
        playTone(freq, 0.07f, 0.35f)
    }

    /** Chirp when the chain is cleared / donuts pop. */
    fun playPopClear() {
        val (s, e) = when (packIndex) {
            1    -> Pair(300f, 900f)
            2    -> Pair(700f, 1800f)
            3    -> Pair(200f, 600f)
            else -> Pair(520f, 1300f)
        }
        playSweep(s, e, 0.13f, 0.45f)
    }

    /** Soft thud when new donuts drop and land. */
    fun playDropLand() {
        val freq = when (packIndex) { 1 -> 60f; 2 -> 120f; 3 -> 55f; else -> 90f }
        playTone(freq, 0.08f, 0.30f)
    }

    /** Happy arpeggio on milestone. */
    fun playMilestone() {
        Thread {
            playToneBlocking(523f, 0.09f, 0.40f)   // C5
            Thread.sleep(80)
            playToneBlocking(659f, 0.09f, 0.40f)   // E5
            Thread.sleep(80)
            playToneBlocking(784f, 0.14f, 0.50f)   // G5
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
        Thread {
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
        }.start()
    }

    private fun playPCM(samples: ShortArray) {
        try {
            val track = AudioTrack.Builder()
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
            track.write(samples, 0, samples.size)
            track.play()
            Thread.sleep((samples.size * 1000L / sampleRate) + 40L)
            track.stop()
            track.release()
        } catch (_: Exception) {
            // Never crash the game over a sound
        }
    }
}
