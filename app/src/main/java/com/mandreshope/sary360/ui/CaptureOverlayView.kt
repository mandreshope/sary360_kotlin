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

    private val targetRingPaint = Paint().apply {
        color = Color.parseColor("#B3FFFFFF") // Semi-transparent white
        style = Paint.Style.STROKE
        strokeWidth = 20f
        isAntiAlias = true
    }

    private val dotPaint = Paint().apply {
        color = Color.parseColor("#5A9FF5") // Google Blue color
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    var currentYaw: Float = 0f
    var currentPitch: Float = 0f
    var targetYaw: Float = 0f
    var targetPitch: Float = 0f
    var isTargetReached: Boolean = false
    var isSessionActive: Boolean = false

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (!isSessionActive) return

        val centerX = width / 2f
        val centerY = height / 2f

        // Mapping: 1 degree approx equals to a percentage of screen width to allow natural movement
        val scale = width / 60f // Assuming a ~60 degree horizontal FOV

        var deltaYaw = normalizeAngle(targetYaw - currentYaw)
        if (Math.abs(targetPitch) >= 80f) {
            deltaYaw = 0f // Keep the dot centered horizontally for zenith/nadir
        }
        // If phones are held in portrait, yaw corresponds to X. If held in landscape, we might need rotation. 
        // We assume portrait for now and simple panning
        
        val deltaPitch = targetPitch - currentPitch

        val targetX = centerX + deltaYaw * scale
        val targetY = centerY - deltaPitch * scale // Screen Y is inverted, pitch up means negative delta Y? 

        val ringRadius = 100f
        val dotRadius = 75f

        // Draw the target dot (blue circle)
        if (!isTargetReached) {
            canvas.drawCircle(targetX, targetY, dotRadius, dotPaint)
        }

        // Draw the center white ring
        if (isTargetReached) {
            targetRingPaint.color = Color.parseColor("#4CAF50") // Turn green upon reaching target
        } else {
            targetRingPaint.color = Color.parseColor("#B3FFFFFF")
        }
        canvas.drawCircle(centerX, centerY, ringRadius, targetRingPaint)
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
