package com.programminghut.realtime_object

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View

class ArrowOverlayView(context: Context, attrs: AttributeSet?) : View(context, attrs) {

    private var navigationState: NavigationState = NavigationState.SAFE_FORWARD
    
    private val greenPaint = Paint().apply {
        color = Color.GREEN
        style = Paint.Style.FILL
        isAntiAlias = true
        alpha = 180
    }
    
    private val redPaint = Paint().apply {
        color = Color.RED
        style = Paint.Style.FILL
        isAntiAlias = true
        alpha = 180
    }

    private val path = Path()

    fun updateState(state: NavigationState) {
        if (this.navigationState != state) {
            this.navigationState = state
            invalidate()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        val w = width.toFloat()
        val h = height.toFloat()
        val centerX = w / 2
        val centerY = h / 2
        val size = w * 0.2f

        path.reset()

        when (navigationState) {
            NavigationState.SAFE_FORWARD -> {
                drawForwardArrow(canvas, centerX, h * 0.8f, size)
            }
            NavigationState.SAFE_LEFT -> {
                drawLeftArrow(canvas, centerX, centerY, size)
            }
            NavigationState.SAFE_RIGHT -> {
                drawRightArrow(canvas, centerX, centerY, size)
            }
            NavigationState.STOP -> {
                drawStopIcon(canvas, centerX, centerY, size)
            }
        }
    }

    private fun drawForwardArrow(canvas: Canvas, x: Float, y: Float, size: Float) {
        path.moveTo(x, y - size)
        path.lineTo(x - size / 2, y)
        path.lineTo(x - size / 4, y)
        path.lineTo(x - size / 4, y + size / 2)
        path.lineTo(x + size / 4, y + size / 2)
        path.lineTo(x + size / 4, y)
        path.lineTo(x + size / 2, y)
        path.close()
        canvas.drawPath(path, greenPaint)
    }

    private fun drawLeftArrow(canvas: Canvas, x: Float, y: Float, size: Float) {
        path.moveTo(x - size, y)
        path.lineTo(x, y - size / 2)
        path.lineTo(x, y - size / 4)
        path.lineTo(x + size / 2, y - size / 4)
        path.lineTo(x + size / 2, y + size / 4)
        path.lineTo(x, y + size / 4)
        path.lineTo(x, y + size / 2)
        path.close()
        canvas.drawPath(path, greenPaint)
    }

    private fun drawRightArrow(canvas: Canvas, x: Float, y: Float, size: Float) {
        path.moveTo(x + size, y)
        path.lineTo(x, y - size / 2)
        path.lineTo(x, y - size / 4)
        path.lineTo(x - size / 2, y - size / 4)
        path.lineTo(x - size / 2, y + size / 4)
        path.lineTo(x, y + size / 4)
        path.lineTo(x, y + size / 2)
        path.close()
        canvas.drawPath(path, greenPaint)
    }

    private fun drawStopIcon(canvas: Canvas, x: Float, y: Float, size: Float) {
        val rect = RectF(x - size, y - size, x + size, y + size)
        canvas.drawOval(rect, redPaint)
        
        val innerPaint = Paint().apply {
            color = Color.WHITE
            strokeWidth = size / 3
            style = Paint.Style.STROKE
        }
        canvas.drawLine(x - size/2, y, x + size/2, y, innerPaint)
    }
}
