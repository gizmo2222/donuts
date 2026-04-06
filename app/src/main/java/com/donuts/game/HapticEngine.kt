package com.donuts.game

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * Haptic feedback for game events.
 * minSdk 26 so VibrationEffect is always available.
 */
class HapticEngine(context: Context) {

    private val vibrator: Vibrator =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager)
                .defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

    var theme: Int = 1

    // -----------------------------------------------------------------------
    // Public API
    // -----------------------------------------------------------------------

    /** Short tick — each cell added to the drag chain. */
    fun tick() {
        val (dur, amp) = when (theme) {
            0    -> Pair(10L, 60)
            2    -> Pair(22L, 255)
            else -> Pair(16L, VibrationEffect.DEFAULT_AMPLITUDE)
        }
        vibrator.vibrate(VibrationEffect.createOneShot(dur, amp))
    }

    /** Satisfying heavy click — chain popped / donuts cleared. */
    fun pop() {
        when (theme) {
            0    -> vibrator.vibrate(VibrationEffect.createOneShot(25, 100))
            2    -> vibrator.vibrate(VibrationEffect.createWaveform(
                        longArrayOf(0, 30, 20, 40), intArrayOf(0, 200, 0, 255), -1))
            else -> vibrator.vibrate(VibrationEffect.createOneShot(45, 200))
        }
    }

    /** Triple-pulse celebration — milestone reached. */
    fun milestone() {
        when (theme) {
            0    -> vibrator.vibrate(VibrationEffect.createOneShot(30, 80))
            2    -> vibrator.vibrate(
                        VibrationEffect.createWaveform(
                            longArrayOf(0, 50, 30, 70, 30, 100),
                            intArrayOf(0, 180, 0, 220, 0, 255),
                            -1
                        ))
            else -> vibrator.vibrate(
                        VibrationEffect.createWaveform(
                            longArrayOf(0, 35, 55, 50, 55, 80),
                            intArrayOf(0, 110, 0, 190, 0, 255),
                            -1
                        ))
        }
    }

    /** Rapid triple buzz — board shuffle. */
    fun shuffle() {
        vibrator.vibrate(
            VibrationEffect.createWaveform(
                longArrayOf(0, 22, 22, 22, 22, 30),
                intArrayOf(0, 85, 0, 85, 0, 130),
                -1
            )
        )
    }
}
