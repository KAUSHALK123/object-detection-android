package com.programminghut.realtime_object

import android.graphics.RectF
import java.util.*

enum class Priority { HIGH, MEDIUM, LOW, NONE }
enum class Distance { VERY_CLOSE, CLOSE, MEDIUM, FAR }
enum class Position { LEFT, CENTER, RIGHT }

data class NavigationInstruction(
    val message: String,
    val isCritical: Boolean = false
)

class NavigationGuidanceEngine {

    private val obstaclePriorityMap = mapOf(
        "car" to Priority.HIGH,
        "bus" to Priority.HIGH,
        "truck" to Priority.HIGH,
        "motorcycle" to Priority.HIGH,
        "traffic light" to Priority.HIGH,
        "stop sign" to Priority.HIGH,
        "person" to Priority.MEDIUM,
        "bicycle" to Priority.MEDIUM,
        "dog" to Priority.MEDIUM,
        "chair" to Priority.LOW,
        "couch" to Priority.LOW,
        "dining table" to Priority.LOW,
        "bench" to Priority.LOW
    )

    fun processDetections(
        labels: List<String>,
        classes: FloatArray,
        scores: FloatArray,
        locations: FloatArray
    ): NavigationInstruction? {
        val detectedObstacles = mutableListOf<ProcessedObstacle>()

        for (i in scores.indices) {
            if (scores[i] > 0.45f) {
                val label = labels[classes[i].toInt()]
                val priority = obstaclePriorityMap[label] ?: Priority.NONE
                
                if (priority != Priority.NONE) {
                    val rect = RectF(
                        locations[i * 4 + 1], // left
                        locations[i * 4],     // top
                        locations[i * 4 + 3], // right
                        locations[i * 4 + 2]  // bottom
                    )
                    detectedObstacles.add(ProcessedObstacle(label, priority, rect))
                }
            }
        }

        if (detectedObstacles.isEmpty()) return null

        // 1. Prioritization: Get the most important obstacle
        val topObstacle = detectedObstacles
            .sortedWith(compareByDescending<ProcessedObstacle> { it.priority }.thenByDescending { it.area })
            .first()

        // 2. Distance Estimation
        val distance = estimateDistance(topObstacle.area)

        // 3. Navigation Decision Engine
        return generateInstruction(topObstacle, distance)
    }

    private fun estimateDistance(area: Float): Distance {
        return when {
            area > 0.40f -> Distance.VERY_CLOSE
            area > 0.15f -> Distance.CLOSE
            area > 0.05f -> Distance.MEDIUM
            else -> Distance.FAR
        }
    }

    private fun generateInstruction(obstacle: ProcessedObstacle, distance: Distance): NavigationInstruction {
        val pos = getPosition(obstacle.rect)
        val label = obstacle.label

        return when (distance) {
            Distance.VERY_CLOSE -> {
                when (pos) {
                    Position.CENTER -> NavigationInstruction("Stop immediately. $label directly ahead.", true)
                    Position.LEFT -> NavigationInstruction("Warning. $label very close on your left. Move right.", true)
                    Position.RIGHT -> NavigationInstruction("Warning. $label very close on your right. Move left.", true)
                }
            }
            Distance.CLOSE -> {
                when (pos) {
                    Position.CENTER -> NavigationInstruction("Obstacle ahead. Move " + (if (Math.random() > 0.5) "right" else "left") + ".", false)
                    Position.LEFT -> NavigationInstruction("$label on your left. Keep right.", false)
                    Position.RIGHT -> NavigationInstruction("$label on your right. Keep left.", false)
                }
            }
            Distance.MEDIUM -> {
                NavigationInstruction("$label approaching " + when(pos) {
                    Position.LEFT -> "from the left"
                    Position.RIGHT -> "from the right"
                    Position.CENTER -> "straight ahead"
                }, false)
            }
            Distance.FAR -> {
                // Often we don't need to announce far objects unless they are high priority
                if (obstacle.priority == Priority.HIGH) {
                    NavigationInstruction("$label detected in the distance.", false)
                } else {
                    NavigationInstruction("", false) // Ignore
                }
            }
        }
    }

    private fun getPosition(rect: RectF): Position {
        val centerX = rect.centerX()
        return when {
            centerX < 0.33f -> Position.LEFT
            centerX > 0.66f -> Position.RIGHT
            else -> Position.CENTER
        }
    }

    private data class ProcessedObstacle(
        val label: String,
        val priority: Priority,
        val rect: RectF
    ) {
        val area: Float get() = (rect.right - rect.left) * (rect.bottom - rect.top)
    }
}
