package com.programminghut.realtime_object

import android.graphics.RectF
import kotlin.math.abs

data class PathPlan(
    val steeringAngle: Float, // -30 to 30 degrees
    val confidence: Float,
    val suggestedAction: Action,
    val occupancyMap: FloatArray // For visualization
)

enum class Action { MOVE_FORWARD, STEER_LEFT, STEER_RIGHT, STOP_IMMEDIATELY, PROCEED_CAREFULLY }

class SafePathPlanner {

    private val slices = 20 // Higher resolution polar map

    fun planPath(obstacles: List<VisionObstacle>): PathPlan {
        val occupancy = FloatArray(slices) { 0f }

        if (obstacles.isEmpty()) {
            return PathPlan(0f, 1.0f, Action.MOVE_FORWARD, occupancy)
        }

        // 1. Build the Polar Obstacle Density Map
        for (obs in obstacles) {
            // Map horizontal position to slices
            val startSlice = (obs.rect.left * slices).toInt().coerceIn(0, slices - 1)
            val endSlice = (obs.rect.right * slices).toInt().coerceIn(0, slices - 1)
            
            // Influence is proportional to risk and inverse distance
            // Closer/higher risk objects "spread" more influence to simulate user width
            val influence = (obs.riskScore / 100f).coerceIn(0f, 1f)
            val proximityBuffer = if (obs.distance < 4f) 2 else 1 // Buffer slices for close objects

            val bufferedStart = (startSlice - proximityBuffer).coerceIn(0, slices - 1)
            val bufferedEnd = (endSlice + proximityBuffer).coerceIn(0, slices - 1)

            for (i in bufferedStart..bufferedEnd) {
                // Decay influence slightly at the edges of the buffer
                occupancy[i] = maxOf(occupancy[i], influence)
            }
        }

        // 2. Find the "Maximum Free Corridor"
        // We look for a sequence of slices with the lowest average occupancy
        val windowSize = 6 // Target corridor width (approx 30% of FOV)
        var minDensity = 100f
        var bestCenterIndex = slices / 2

        for (i in 0..(slices - windowSize)) {
            var windowDensity = 0f
            for (j in i until (i + windowSize)) {
                windowDensity += occupancy[j]
            }
            
            // Add a "Central Bias" to prefer walking straight
            val centerBias = abs((i + windowSize / 2f) - slices / 2f) * 0.1f
            val totalCost = windowDensity / windowSize + centerBias

            if (totalCost < minDensity) {
                minDensity = totalCost
                bestCenterIndex = i + windowSize / 2
            }
        }

        // 3. Generate Steering Logic
        val centerOccupancy = occupancy[slices / 2]
        val steeringAngle = (bestCenterIndex - slices / 2f) * (60f / slices) // Map to -30..+30
        
        val suggestedAction = when {
            centerOccupancy > 0.85f && minDensity > 0.7f -> Action.STOP_IMMEDIATELY
            abs(steeringAngle) > 12f -> {
                if (steeringAngle < 0) Action.STEER_LEFT else Action.STEER_RIGHT
            }
            centerOccupancy > 0.4f -> Action.PROCEED_CAREFULLY
            else -> Action.MOVE_FORWARD
        }

        return PathPlan(
            steeringAngle = steeringAngle,
            confidence = (1.0f - minDensity).coerceIn(0.1f, 1.0f),
            suggestedAction = suggestedAction,
            occupancyMap = occupancy
        )
    }
}

data class VisionObstacle(
    val label: String,
    val rect: RectF,
    val distance: Float,
    val riskScore: Float
)
