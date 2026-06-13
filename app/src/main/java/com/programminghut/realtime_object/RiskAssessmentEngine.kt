package com.programminghut.realtime_object

import android.graphics.RectF
import kotlin.math.abs

class RiskAssessmentEngine {

    enum class HazardLevel { NONE, LOW, MEDIUM, HIGH, CRITICAL }

    // Priorities based on mobility research (Physical size + potential speed)
    private val priorityMap = mapOf(
        "car" to 1.5f,
        "bus" to 2.0f,
        "truck" to 2.0f,
        "motorcycle" to 1.5f,
        "person" to 1.2f,
        "bicycle" to 1.2f,
        "dog" to 0.8f,
        "chair" to 0.6f,
        "dining table" to 0.7f,
        "stop sign" to 1.0f,
        "traffic light" to 1.0f,
        "bench" to 0.7f
    )

    fun assessRisk(
        label: String,
        rect: RectF,
        distanceFeet: Float
    ): Float {
        val basePriority = priorityMap[label] ?: 0.5f

        // Distance Decay: Risk increases significantly below 6 feet
        // Formula: Risk = (1 / (dist + epsilon))
        val distanceFactor = if (distanceFeet < 2f) 4.0f 
                            else if (distanceFeet < 6f) 2.5f 
                            else if (distanceFeet < 12f) 1.2f 
                            else 0.5f

        // Path Alignment: Central obstacles are high risk
        val centerX = rect.centerX()
        val centerDeviation = abs(centerX - 0.5f)
        val pathWeight = when {
            centerDeviation < 0.15f -> 2.5f // Directly in front
            centerDeviation < 0.30f -> 1.5f // Mostly in front
            else -> 0.6f                   // Periphery
        }

        // Final score 0 - 100
        return (basePriority * distanceFactor * pathWeight * 10f).coerceIn(0f, 100f)
    }

    fun getHazardLevel(riskScore: Float): HazardLevel {
        return when {
            riskScore > 85 -> HazardLevel.CRITICAL
            riskScore > 60 -> HazardLevel.HIGH
            riskScore > 35 -> HazardLevel.MEDIUM
            riskScore > 15 -> HazardLevel.LOW
            else -> HazardLevel.NONE
        }
    }
}
