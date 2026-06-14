package com.programminghut.realtime_object

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import java.util.Locale

/**
 * Pro-Designer Digital Eye Overlay.
 * Renders a "Safe Walking Lane" on the ground plane.
 */
class VisionOverlayView(context: Context, attrs: AttributeSet?) : View(context, attrs) {

    private var currentPlan: PathPlan? = null
    private var obstacles: List<NavigationMemory.TrackedObstacle> = emptyList()
    
    private var rollDegrees = 0f
    private var pitchDegrees = 0f
    
    private val lanePaint = Paint().apply {
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val laneEdgePaint = Paint().apply {
        style = Paint.Style.STROKE
        strokeWidth = 15f
        isAntiAlias = true
    }

    private val stopBarPaint = Paint().apply {
        color = Color.parseColor("#FF1744")
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val gridPaint = Paint().apply {
        color = Color.parseColor("#30FFFFFF")
        strokeWidth = 2f
        style = Paint.Style.STROKE
    }

    private val textPaint = Paint().apply {
        color = Color.WHITE
        textSize = 40f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        setShadowLayer(8f, 0f, 0f, Color.BLACK)
    }

    private val corridorPath = Path()

    fun updateData(plan: PathPlan, obstacles: List<NavigationMemory.TrackedObstacle>) {
        this.currentPlan = plan
        this.obstacles = obstacles
        invalidate()
    }
    
    fun updateOrientation(rollRad: Float, pitchRad: Float) {
        this.rollDegrees = Math.toDegrees(rollRad.toDouble()).toFloat()
        this.pitchDegrees = Math.toDegrees(pitchRad.toDouble()).toFloat()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w == 0f || h == 0f) return

        // 1. Scene Background Contrast
        canvas.drawColor(Color.argb(30, 0, 0, 0))

        // 2. Ground-Locked Navigation Layer
        canvas.save()
        // Stabilize based on Gyro
        canvas.rotate(-rollDegrees, w / 2, h / 2)
        val pitchOffset = (pitchDegrees * 22f).coerceIn(-h * 0.35f, h * 0.35f)
        canvas.translate(0f, pitchOffset)

        drawGroundGrid(canvas, w, h)

        currentPlan?.let { plan ->
            if (plan.isCollisionImminent || plan.suggestedAction == Action.STOP_IMMEDIATELY) {
                drawStopZone(canvas, w, h)
            } else {
                drawSafeWalkingLane(canvas, plan, w, h)
            }
        }

        canvas.restore()

        // 3. Floating Hazard Indicators
        drawHazardMarkers(canvas, w, h)
    }

    private fun drawGroundGrid(canvas: Canvas, w: Float, h: Float) {
        val horizon = h * 0.45f
        // Perspective lines meeting at horizon
        for (i in 0..10) {
            val x = w * i / 10f
            canvas.drawLine(x, h, (w/2 + (x - w/2) * 0.1f), horizon, gridPaint)
        }
        // Distance markers
        for (i in 0..6) {
            val y = h - (h - horizon) * (i / 6f)
            canvas.drawLine(0f, y, w, y, gridPaint)
        }
    }

    private fun drawSafeWalkingLane(canvas: Canvas, plan: PathPlan, w: Float, h: Float) {
        corridorPath.reset()
        val horizon = h * 0.46f
        
        // The "Road" base - where the user's feet are
        val baseWidth = w * 0.85f
        val topWidth = w * 0.12f
        
        // Steering projection
        val steeringOffset = (plan.steeringAngle / 35f) * (w * 0.48f)
        val topCenter = (w / 2) + steeringOffset

        corridorPath.moveTo(w/2 - baseWidth/2, h)
        corridorPath.lineTo(w/2 + baseWidth/2, h)
        corridorPath.lineTo(topCenter + topWidth/2, horizon)
        corridorPath.lineTo(topCenter - topWidth/2, horizon)
        corridorPath.close()

        val laneColor = if (plan.suggestedAction == Action.PROCEED_CAREFULLY) 
            Color.parseColor("#FFEA00") else Color.parseColor("#00E676")
        
        lanePaint.color = laneColor
        lanePaint.alpha = 110
        canvas.drawPath(corridorPath, lanePaint)
        
        laneEdgePaint.color = laneColor
        laneEdgePaint.alpha = 230
        canvas.drawPath(corridorPath, laneEdgePaint)
        
        // Center-line dash for the "road" feel
        val dashPaint = Paint(laneEdgePaint).apply { 
            strokeWidth = 6f
            pathEffect = DashPathEffect(floatArrayOf(40f, 40f), 0f)
        }
        canvas.drawLine(w/2, h, topCenter, horizon, dashPaint)
    }

    private fun drawStopZone(canvas: Canvas, w: Float, h: Float) {
        // A big Red "Stop Bar" at the base of the screen
        val barHeight = h * 0.15f
        canvas.drawRect(0f, h - barHeight, w, h, stopBarPaint)
        
        // Visual "Wall" effect
        stopBarPaint.alpha = 100
        canvas.drawRect(0f, h * 0.45f, w, h - barHeight, stopBarPaint)
        
        textPaint.textSize = 100f
        textPaint.color = Color.WHITE
        textPaint.textAlign = Paint.Align.CENTER
        canvas.drawText("STOP", w / 2, h * 0.93f, textPaint)
        textPaint.textAlign = Paint.Align.LEFT // Reset
        textPaint.textSize = 40f
    }

    private fun drawHazardMarkers(canvas: Canvas, w: Float, h: Float) {
        val hazardPaint = Paint().apply {
            style = Paint.Style.STROKE
            strokeWidth = 10f
            isAntiAlias = true
        }

        for (obs in obstacles) {
            val rect = RectF(obs.rect.left * w, obs.rect.top * h, obs.rect.right * w, obs.rect.bottom * h)
            val isUrgent = obs.riskScore > 75 || obs.distance < 4f
            
            hazardPaint.color = if (isUrgent) Color.parseColor("#FF1744") else Color.CYAN
            
            // Draw clean corner brackets
            val l = 35f
            canvas.drawLine(rect.left, rect.top + l, rect.left, rect.top, hazardPaint)
            canvas.drawLine(rect.left, rect.top, rect.left + l, rect.top, hazardPaint)
            
            canvas.drawLine(rect.right - l, rect.top, rect.right, rect.top, hazardPaint)
            canvas.drawLine(rect.right, rect.top, rect.right, rect.top + l, hazardPaint)
            
            canvas.drawLine(rect.right, rect.bottom - l, rect.right, rect.bottom, hazardPaint)
            canvas.drawLine(rect.right, rect.bottom, rect.right - l, rect.bottom, hazardPaint)
            
            canvas.drawLine(rect.left + l, rect.bottom, rect.left, rect.bottom, hazardPaint)
            canvas.drawLine(rect.left, rect.bottom, rect.left, rect.bottom - l, hazardPaint)

            val labelText = "${obs.label.uppercase()} ${String.format(Locale.US, "%.1f", obs.distance)}FT"
            canvas.drawText(labelText, rect.left, rect.top - 15f, textPaint)
        }
    }
}
