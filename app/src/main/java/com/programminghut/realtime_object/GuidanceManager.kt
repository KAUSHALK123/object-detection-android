package com.programminghut.realtime_object

import android.speech.tts.TextToSpeech
import java.util.*
import kotlin.math.abs

class GuidanceManager(private val tts: TextToSpeech?) {

    private var lastGuidanceTime: Long = 0
    private var lastAction: Action? = null
    private var lastObstacleAnnounced = ""
    private var lastAnnouncementType = ""
    
    private val REASSURANCE_INTERVAL = 8000L // Reassure path is clear every 8s
    private val OBSTACLE_COOLDOWN = 4000L

    fun provideGuidance(plan: PathPlan, primaryObstacle: VisionObstacle?) {
        val currentTime = System.currentTimeMillis()
        
        // 1. Critical Emergency Stop (High Priority)
        if (plan.suggestedAction == Action.STOP_IMMEDIATELY) {
            val obsName = primaryObstacle?.label ?: "obstacle"
            val message = "STOP! $obsName directly ahead."
            speak(message, currentTime, "CRITICAL")
            return
        }

        // 2. Obstacle-Specific Avoidance
        if (primaryObstacle != null && primaryObstacle.riskScore > 40f) {
            val dist = primaryObstacle.distance.toInt()
            val label = primaryObstacle.label
            
            // Generate directional instruction
            val direction = if (plan.steeringAngle < -5f) "slightly left" 
                           else if (plan.steeringAngle > 5f) "slightly right" 
                           else "carefully forward"

            val message = when {
                dist <= 3 -> "Watch out, $label very close. Move $direction."
                dist <= 6 -> "$label ahead in $dist feet. Steer $direction."
                else -> "$label at $dist feet."
            }
            
            // Only announce if it's a new obstacle or enough time has passed
            if (label != lastObstacleAnnounced || currentTime - lastGuidanceTime > OBSTACLE_COOLDOWN) {
                speak(message, currentTime, "OBSTACLE")
                lastObstacleAnnounced = label
                return
            }
        }

        // 3. General Path Navigation (if no high-risk obstacle)
        if (currentTime - lastGuidanceTime > REASSURANCE_INTERVAL) {
            val message = when (plan.suggestedAction) {
                Action.MOVE_FORWARD -> "Path is clear. Continue forward."
                Action.STEER_LEFT -> "Path curves left. Follow the clear space."
                Action.STEER_RIGHT -> "Path curves right. Follow the clear space."
                Action.PROCEED_CAREFULLY -> "Navigating through narrow space. Keep going."
                else -> ""
            }
            if (message.isNotEmpty()) {
                speak(message, currentTime, "REASSURANCE")
            }
        }
    }

    private fun speak(message: String, time: Long, type: String) {
        // Don't repeat the exact same non-critical message too quickly
        if (type != "CRITICAL" && message == lastAnnouncementType && time - lastGuidanceTime < 2000) return

        tts?.speak(message, TextToSpeech.QUEUE_FLUSH, null, null)
        lastGuidanceTime = time
        lastAnnouncementType = message
    }
}
