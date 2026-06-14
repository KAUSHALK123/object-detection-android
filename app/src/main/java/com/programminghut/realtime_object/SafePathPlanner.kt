package com.programminghut.realtime_object

import kotlin.math.abs

class SafePathPlanner {

    private var lastSteeringAngle = 0f
    private val STEERING_SMOOTHING = 0.65f

    /**
     * Enhanced Proactive Path Planner.
     * Calculates optimal steering angle based on obstacle density and proximity.
     */
    fun planPath(obstacles: List<NavigationMemory.TrackedObstacle>): PathPlan {
        // CORRIDOR: The space directly in front of the user
        val corridorLeft = 0.25f
        val corridorRight = 0.75f
        
        // 1. Immediate Collision Check
        val criticalObstacles = obstacles.filter { 
            it.distance < 4.5f && it.rect.centerX() in 0.25f..0.75f
        }
        
        if (criticalObstacles.isNotEmpty()) {
            val nearest = criticalObstacles.minBy { it.distance }
            if (nearest.distance < 3.5f) {
                return PathPlan(0f, 1.0f, Action.STOP_IMMEDIATELY, FloatArray(0), true)
            }
        }

        // 2. Continuous Steering Calculation
        // We calculate a "Force" from each obstacle that pushes the user away
        var totalSteerForce = 0f
        var weightSum = 0f

        for (obs in obstacles) {
            if (obs.distance > 15.0f) continue // Ignore far away objects
            
            val centerX = obs.rect.centerX()
            // Map 0..1 to -1..1 (Left to Right)
            val normalizedPos = (centerX - 0.5f) * 2.0f
            
            // Influence is higher for closer objects and objects in the center
            val proximityWeight = (15.0f - obs.distance) / 15.0f
            val centralityWeight = 1.0f - abs(normalizedPos)
            val weight = proximityWeight * centralityWeight
            
            // Push away from the obstacle
            totalSteerForce += (-normalizedPos) * weight
            weightSum += weight
        }

        val rawSteeringAngle = if (weightSum > 0) {
            (totalSteerForce / weightSum) * 45f 
        } else {
            0f
        }
        
        val finalSteeringAngle = (rawSteeringAngle * (1f - STEERING_SMOOTHING)) + (lastSteeringAngle * STEERING_SMOOTHING)
        lastSteeringAngle = finalSteeringAngle

        // 3. Action Determination
        val centralBlockage = obstacles.any { 
            it.rect.centerX() in corridorLeft..corridorRight && it.distance < 7.0f 
        }

        val action = when {
            criticalObstacles.isNotEmpty() -> Action.PROCEED_CAREFULLY
            centralBlockage && abs(finalSteeringAngle) > 10f -> {
                if (finalSteeringAngle < 0) Action.STEER_LEFT else Action.STEER_RIGHT
            }
            centralBlockage -> Action.PROCEED_CAREFULLY
            else -> Action.MOVE_FORWARD
        }

        return PathPlan(
            steeringAngle = finalSteeringAngle.coerceIn(-45f, 45f),
            confidence = (1.0f - (weightSum / 5f)).coerceIn(0.1f, 1.0f),
            suggestedAction = action,
            occupancyMap = FloatArray(0),
            isCollisionImminent = criticalObstacles.any { it.distance < 3.5f }
        )
    }
}
