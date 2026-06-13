package com.programminghut.realtime_object

import android.graphics.RectF

enum class NavigationState {
    SAFE_FORWARD,
    SAFE_LEFT,
    SAFE_RIGHT,
    STOP
}

class SafePathAnalyzer {

    // Thresholds for considering a zone "blocked"
    private val BLOCK_AREA_THRESHOLD = 0.12f 
    private val CRITICAL_BLOCK_THRESHOLD = 0.35f

    fun analyze(detections: List<ProcessedObstacle>): NavigationState {
        val leftOccupancy = calculateOccupancy(detections, 0f, 0.33f)
        val centerOccupancy = calculateOccupancy(detections, 0.33f, 0.66f)
        val rightOccupancy = calculateOccupancy(detections, 0.66f, 1.0f)

        val leftBlocked = leftOccupancy > BLOCK_AREA_THRESHOLD
        val centerBlocked = centerOccupancy > BLOCK_AREA_THRESHOLD
        val rightBlocked = rightOccupancy > BLOCK_AREA_THRESHOLD

        // Rule-based decision engine
        return when {
            // All directions blocked
            leftBlocked && centerBlocked && rightBlocked -> NavigationState.STOP
            
            // Center is blocked, choose best alternative
            centerBlocked -> {
                if (!leftBlocked) {
                    NavigationState.SAFE_LEFT
                } else if (!rightBlocked) {
                    NavigationState.SAFE_RIGHT
                } else {
                    NavigationState.STOP
                }
            }
            
            // Center is clear, but check if we should steer away from side obstacles
            !centerBlocked -> {
                if (leftBlocked && !rightBlocked && leftOccupancy > 0.25f) {
                    NavigationState.SAFE_RIGHT // Steer away from heavy left obstacle
                } else if (rightBlocked && !leftBlocked && rightOccupancy > 0.25f) {
                    NavigationState.SAFE_LEFT // Steer away from heavy right obstacle
                } else {
                    NavigationState.SAFE_FORWARD
                }
            }
            
            else -> NavigationState.SAFE_FORWARD
        }
    }

    private fun calculateOccupancy(detections: List<ProcessedObstacle>, minX: Float, maxX: Float): Float {
        var totalArea = 0f
        for (obs in detections) {
            val rect = obs.rect
            val overlapLeft = maxOf(rect.left, minX)
            val overlapRight = minOf(rect.right, maxX)
            
            if (overlapLeft < overlapRight) {
                val width = overlapRight - overlapLeft
                val height = rect.bottom - rect.top
                totalArea += (width * height)
            }
        }
        return totalArea
    }

    data class ProcessedObstacle(
        val label: String,
        val rect: RectF
    )
}
