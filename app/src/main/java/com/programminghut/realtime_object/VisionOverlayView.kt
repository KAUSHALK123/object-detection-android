package com.programminghut.realtime_object

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View

class VisionOverlayView(context: Context, attrs: AttributeSet?) : View(context, attrs) {

    private var currentPlan: PathPlan? = null
    private var obstacles: List<VisionObstacle> = emptyList()

    private val corridorPaint = Paint().apply {
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val obstaclePaint = Paint().apply {
        style = Paint.Style.STROKE
        strokeWidth = 8f
        isAntiAlias = true
    }

    private val textPaint = Paint().apply {
        color = Color.WHITE
        textSize = 38f
        typeface = Typeface.DEFAULT_BOLD
        setShadowLayer(5f, 0f, 0f, Color.BLACK)
    }

    private val gridPaint = Paint().apply {
        color = Color.WHITE
        alpha = 40
        strokeWidth = 2f
        style = Paint.Style.STROKE
    }

    fun updateData(plan: PathPlan, obstacles: List<VisionObstacle>) {
        this.currentPlan = plan
        this.obstacles = obstacles
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()

        // 1. Draw Perspective Grid (Ground Plane)
        drawPerspectiveGrid(canvas, w, h)

        // 2. Draw Obstacles with Risk Heatmap
        for (obs in obstacles) {
            val rect = RectF(obs.rect.left * w, obs.rect.top * h, obs.rect.right * w, obs.rect.bottom * h)
            
            val color = when {
                obs.riskScore > 75 -> Color.RED
                obs.riskScore > 40 -> Color.YELLOW
                else -> Color.CYAN
            }
            obstaclePaint.color = color
            
            // Draw Box
            canvas.drawRoundRect(rect, 15f, 15f, obstaclePaint)
            
            // Draw Label in Feet
            val labelText = "${obs.label.uppercase()} - ${String.format("%.1f", obs.distance)} ft"
            canvas.drawText(labelText, rect.left, (rect.top - 15f).coerceAtLeast(40f), textPaint)
        }

        // 3. Draw Autonomous Navigation Corridor
        currentPlan?.let { plan ->
            drawSafeCorridor(canvas, plan, w, h)
        }
    }

    private fun drawPerspectiveGrid(canvas: Canvas, w: Float, h: Float) {
        val horizon = h * 0.4f
        for (i in 0..10) {
            val x = w * i / 10f
            canvas.drawLine(x, h, (w/2 + (x - w/2) * 0.2f), horizon, gridPaint)
        }
        for (i in 0..5) {
            val y = h - (h - horizon) * i / 5f
            canvas.drawLine(0f, y, w, y, gridPaint)
        }
    }

    private fun drawSafeCorridor(canvas: Canvas, plan: PathPlan, w: Float, h: Float) {
        val path = Path()
        val horizon = h * 0.45f
        
        // Base of the corridor at the bottom of the screen
        val bottomCenter = w / 2
        val bottomWidth = w * 0.6f
        
        // Target at the horizon based on steering angle
        // Angle to X-offset conversion: tan(angle) * perspective_depth
        val steeringOffset = (plan.steeringAngle / 30f) * (w * 0.4f)
        val topCenter = (w / 2) + steeringOffset
        val topWidth = w * 0.2f

        path.moveTo(bottomCenter - bottomWidth/2, h)
        path.lineTo(bottomCenter + bottomWidth/2, h)
        path.lineTo(topCenter + topWidth/2, horizon)
        path.lineTo(topCenter - topWidth/2, horizon)
        path.close()

        val corridorColor = when (plan.suggestedAction) {
            Action.STOP_IMMEDIATELY -> Color.RED
            Action.PROCEED_CAREFULLY -> Color.YELLOW
            else -> Color.GREEN
        }
        
        corridorPaint.color = corridorColor
        corridorPaint.alpha = 100 // Transparency
        
        canvas.drawPath(path, corridorPaint)
        
        // Add a "Glow" effect for the safe path
        corridorPaint.style = Paint.Style.STROKE
        corridorPaint.strokeWidth = 12f
        corridorPaint.alpha = 180
        canvas.drawPath(path, corridorPaint)
        corridorPaint.style = Paint.Style.FILL
    }
}
