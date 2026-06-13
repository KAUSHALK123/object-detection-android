package com.programminghut.realtime_object

import android.content.Context
import android.os.*

class HapticManager(context: Context) {

    private val vibrator: Vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
        vibratorManager.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }

    fun playFeedback(level: RiskAssessmentEngine.HazardLevel) {
        when (level) {
            RiskAssessmentEngine.HazardLevel.CRITICAL -> pulse(longArrayOf(0, 500, 100, 500), 0) // Continuous danger
            RiskAssessmentEngine.HazardLevel.HIGH -> pulse(longArrayOf(0, 300, 100, 300), -1)
            RiskAssessmentEngine.HazardLevel.MEDIUM -> pulse(longArrayOf(0, 150), -1)
            RiskAssessmentEngine.HazardLevel.LOW -> pulse(longArrayOf(0, 50), -1)
            RiskAssessmentEngine.HazardLevel.NONE -> vibrator.cancel()
        }
    }

    private fun pulse(pattern: LongArray, repeat: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createWaveform(pattern, repeat))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(pattern, repeat)
        }
    }
    
    fun stop() {
        vibrator.cancel()
    }
}
