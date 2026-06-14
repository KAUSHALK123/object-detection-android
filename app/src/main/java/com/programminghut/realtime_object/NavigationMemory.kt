package com.programminghut.realtime_object

import android.graphics.RectF
import java.util.*
import kotlin.math.tan

/**
 * Advanced Navigation Memory with Ego-Motion Compensation.
 * Tracks obstacles across frames and compensates for phone rotation (head/body movement).
 */
class NavigationMemory {

    data class TrackedObstacle(
        val id: String,
        val label: String,
        var rect: RectF,
        var distance: Float,
        var riskScore: Float,
        var lastSeen: Long,
        var velocityX: Float = 0f,
        var velocityY: Float = 0f,
        var firstSeen: Long = System.currentTimeMillis(),
        var framesSeen: Int = 1
    )

    private val trackedItems = mutableMapOf<String, TrackedObstacle>()
    private val PERSISTENCE_MS = 2500L 
    private val IOU_THRESHOLD = 0.35f
    private val MIN_CONFIRMATION_FRAMES = 2
    
    // Memory of the last orientation to calculate Delta
    private var lastAzimuth = 0f
    private var lastPitch = 0f
    private var lastRoll = 0f

    /**
     * Updates memory with new detections and compensates for camera rotation.
     */
    fun update(
        currentObstacles: List<VisionObstacle>,
        azimuth: Float,
        pitch: Float,
        roll: Float
    ): List<TrackedObstacle> {
        val now = System.currentTimeMillis()
        
        // 1. Calculate Ego-Motion (Rotation Delta)
        val dAzimuth = azimuth - lastAzimuth
        val dPitch = pitch - lastPitch
        
        // 2. Compensate existing tracks for camera movement
        // We shift the 'expected' position of stored objects based on how the phone turned
        compensateEgoMotion(dAzimuth, dPitch)
        
        lastAzimuth = azimuth
        lastPitch = pitch
        lastRoll = roll

        val matchedIds = mutableSetOf<String>()

        for (newObs in currentObstacles) {
            var bestMatch: TrackedObstacle? = null
            var maxIou = 0f

            for (stored in trackedItems.values) {
                if (stored.label == newObs.label) {
                    val iou = calculateIOU(stored.rect, newObs.rect)
                    if (iou > maxIou && iou > IOU_THRESHOLD) {
                        maxIou = iou
                        bestMatch = stored
                    }
                }
            }

            if (bestMatch != null) {
                val dt = (now - bestMatch.lastSeen) / 1000f
                if (dt > 0) {
                    // Velocity is now corrected for ego-motion
                    bestMatch.velocityX = (newObs.rect.centerX() - bestMatch.rect.centerX()) / dt
                    bestMatch.velocityY = (newObs.rect.centerY() - bestMatch.rect.centerY()) / dt
                }
                bestMatch.rect = newObs.rect
                bestMatch.distance = newObs.distance
                bestMatch.riskScore = newObs.riskScore
                bestMatch.lastSeen = now
                bestMatch.framesSeen++
                matchedIds.add(bestMatch.id)
            } else {
                val newId = UUID.randomUUID().toString()
                val newItem = TrackedObstacle(newId, newObs.label, newObs.rect, newObs.distance, newObs.riskScore, now)
                trackedItems[newId] = newItem
                matchedIds.add(newId)
            }
        }

        val iterator = trackedItems.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (now - entry.value.lastSeen > PERSISTENCE_MS) {
                iterator.remove()
            }
        }

        // Return only stable obstacles
        return trackedItems.values.filter { it.framesSeen >= MIN_CONFIRMATION_FRAMES }
    }

    private fun compensateEgoMotion(dAzimuth: Float, dPitch: Float) {
        // Approximate pixel shift based on FOV (Assuming ~60 deg FOV)
        // 1 radian = ~57 degrees. 
        // Shift = delta_angle / FOV_radians
        val horizontalShift = dAzimuth / 1.0f 
        val verticalShift = dPitch / 1.0f

        for (item in trackedItems.values) {
            item.rect.offset(-horizontalShift, verticalShift)
        }
    }

    private fun calculateIOU(r1: RectF, r2: RectF): Float {
        val intersection = RectF()
        if (!intersection.setIntersect(r1, r2)) return 0f
        val interArea = intersection.width() * intersection.height()
        val unionArea = (r1.width() * r1.height()) + (r2.width() * r2.height()) - interArea
        return if (unionArea > 0) interArea / unionArea else 0f
    }
}
