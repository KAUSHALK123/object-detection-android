package com.programminghut.realtime_object

import android.graphics.RectF

class NavigationEngine {

    private val pathAnalyzer = SafePathAnalyzer()
    private val obstacleLabels = setOf(
        "person", "bicycle", "car", "motorcycle", "bus", "train", "truck", 
        "stop sign", "fire hydrant", "chair", "couch", "potted plant", "dining table"
    )

    fun getNavigationState(
        labels: List<String>,
        classes: FloatArray,
        scores: FloatArray,
        locations: FloatArray
    ): NavigationState {
        val obstacles = mutableListOf<SafePathAnalyzer.ProcessedObstacle>()

        for (i in scores.indices) {
            if (scores[i] > 0.45f) {
                val label = labels[classes[i].toInt()]
                if (obstacleLabels.contains(label)) {
                    val rect = RectF(
                        locations[i * 4 + 1], // left
                        locations[i * 4],     // top
                        locations[i * 4 + 3], // right
                        locations[i * 4 + 2]  // bottom
                    )
                    obstacles.add(SafePathAnalyzer.ProcessedObstacle(label, rect))
                }
            }
        }

        return pathAnalyzer.analyze(obstacles)
    }
}
