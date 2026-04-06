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

    // -----------------------------------------------------------------------
    // Public API
    // -----------------------------------------------------------------------

    /** Short tick — each cell added to the drag chain. */
    fun tick() {
        vibrator.vibrate(VibrationEffect.createOneShot(16, VibrationEffect.DEFAULT_AMPLITUDE))
    }

    /** Satisfying heavy click — chain popped / donuts cleared. */
    fun pop() {
        vibrator.vibrate(VibrationEffect.createOneShot(45, 200))
    }

    /** Triple-pulse celebration — milestone reached. */
    fun milestone() {
        vibrator.vibrate(
            VibrationEffect.createWaveform(
                longArrayOf(0, 35, 55, 50, 55, 80),
                intArrayOf(0, 110, 0, 190, 0, 255),
                -1
            )
        )
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
