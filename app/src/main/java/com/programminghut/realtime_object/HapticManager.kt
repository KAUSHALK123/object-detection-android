package com.programminghut.realtime_object

import android.content.Context
import android.os.*

/**
 * Pro-Level Haptic Language Engine.
 * Translates spatial and risk data into a "tactile language" for the user.
 */
class HapticManager(context: Context) {

    private val vibrator: Vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
        vibratorManager.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }

    private var currentLevel: RiskAssessmentEngine.HazardLevel = RiskAssessmentEngine.HazardLevel.NONE

    fun playFeedback(level: RiskAssessmentEngine.HazardLevel, steeringAngle: Float) {
        // Haptic feedback disabled as requested
        return
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
