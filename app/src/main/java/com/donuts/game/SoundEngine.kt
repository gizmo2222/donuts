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

    // -----------------------------------------------------------------------
    // Public API
    // -----------------------------------------------------------------------

    /** Blip when a cell joins the drag chain. Pitch rises with chain length. */
    fun playConnectBlip(chainLength: Int) {
        val freq = (350f + chainLength * 55f).coerceAtMost(1400f)
        playTone(freq, 0.07f, 0.35f)
    }

    /** Chirp when the chain is cleared / donuts pop. */
    fun playPopClear() {
        playSweep(520f, 1300f, 0.13f, 0.45f)
    }

    /** Soft thud when new donuts drop and land. */
    fun playDropLand() {
        playTone(90f, 0.08f, 0.30f)
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
            buf[i] = (sin(2.0 * PI * freq * t) * env * volume * Short.MAX_VALUE).toInt().toShort()
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
                buf[i] = (sin(phase) * env * volume * Short.MAX_VALUE).toInt().toShort()
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
