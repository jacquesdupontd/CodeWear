package com.vibecoder.pebblecode.haptics

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

class HapticsManager(context: Context) {
    private val vibrator: Vibrator = if (Build.VERSION.SDK_INT >= 31) {
        (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }

    /** Short pulse - action feedback, new suggestion */
    fun shortPulse() {
        vibrator.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
    }

    /** Double pulse - question, ready timeout */
    fun doublePulse() {
        vibrator.vibrate(
            VibrationEffect.createWaveform(
                longArrayOf(0, 80, 120, 80),
                intArrayOf(0, 200, 0, 200),
                -1
            )
        )
    }
}
