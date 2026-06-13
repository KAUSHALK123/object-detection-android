package com.programminghut.realtime_object

import android.graphics.RectF

/**
 * Advanced Imperial Depth Estimator
 * Uses a pinhole camera model to estimate distance in feet.
 */
class DepthEstimator {

    // Real-world heights in feet
    private val realWorldHeightsFeet = mapOf(
        "person" to 5.6f,
        "car" to 4.9f,
        "chair" to 3.0f,
        "bus" to 10.0f,
        "truck" to 10.0f,
        "bicycle" to 3.3f,
        "dog" to 1.6f,
        "dining table" to 2.6f,
        "stop sign" to 8.2f,
        "traffic light" to 10.0f,
        "bench" to 2.6f,
        "potted plant" to 2.0f
    )

    private val defaultHeightFeet = 3.3f // ~1 meter

    fun estimateDistance(label: String, rect: RectF): Float {
        val objectHeightInImage = rect.height().coerceAtLeast(0.01f)
        val knownHeight = realWorldHeightsFeet[label] ?: defaultHeightFeet
        
        // Empirical constant: FocalLength_Factor = (Distance * ImageHeight) / RealHeight
        // Calibrated for typical phone cameras (approx 3.5mm-4mm focal length)
        val focalLengthFactor = 1.1f 
        
        return (knownHeight * focalLengthFactor) / objectHeightInImage
    }
}
