package com.mandreshope.sary360.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View

class CaptureOverlayView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val dotPaint = Paint().apply {
        color = Color.WHITE
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val targetPaint = Paint().apply {
        color = Color.GREEN
        style = Paint.Style.STROKE
        strokeWidth = 5f
        isAntiAlias = true
    }

    private val crosshairPaint = Paint().apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 3f
        isAntiAlias = true
    }

    var currentYaw: Float = 0f
    var currentPitch: Float = 0f
    var targetYaw: Float = 0f
    var targetPitch: Float = 0f
    var isTargetReached: Boolean = false

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val centerX = width / 2f
        val centerY = height / 2f

        // Draw crosshair
        canvas.drawLine(centerX - 50, centerY, centerX + 50, centerY, crosshairPaint)
        canvas.drawLine(centerX, centerY - 50, centerX, centerY + 50, crosshairPaint)

        // Draw target dot based on relative angles
        // Simple mapping: 1 degree = 20 pixels (tweak as needed)
        val scale = 20f
        val deltaYaw = normalizeAngle(targetYaw - currentYaw)
        val deltaPitch = targetPitch - currentPitch

        val targetX = centerX + deltaYaw * scale
        val targetY = centerY - deltaPitch * scale // Screen Y is inverted

        if (isTargetReached) {
            dotPaint.color = Color.GREEN
            canvas.drawCircle(centerX, centerY, 30f, dotPaint)
        } else {
            dotPaint.color = Color.WHITE
            canvas.drawCircle(targetX, targetY, 20f, dotPaint)
            canvas.drawCircle(centerX, centerY, 40f, targetPaint)
        }
    }

    private fun normalizeAngle(angle: Float): Float {
        var a = angle
        while (a <= -180) a += 360
        while (a > 180) a -= 360
        return a
    }

    fun updateOrientation(yaw: Float, pitch: Float) {
        currentYaw = yaw
        currentPitch = pitch
        invalidate()
    }

    fun setTarget(yaw: Float, pitch: Float, reached: Boolean) {
        targetYaw = yaw
        targetPitch = pitch
        isTargetReached = reached
        invalidate()
    }
}
