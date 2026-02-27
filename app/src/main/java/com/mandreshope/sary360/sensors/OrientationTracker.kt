package com.mandreshope.sary360.sensors

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager

class OrientationTracker(
    context: Context,
    private val onOrientationChanged: (yaw: Float, pitch: Float) -> Unit
) : SensorEventListener {
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

    fun start() {
        rotationSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
    }

    fun stop() {
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type == Sensor.TYPE_ROTATION_VECTOR) {
            val rotationMatrix = FloatArray(9)
            SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)

            // Remap coordinate system for a device held in portrait (back camera facing forward)
            val remappedMatrix = FloatArray(9)
            SensorManager.remapCoordinateSystem(
                rotationMatrix,
                SensorManager.AXIS_X,
                SensorManager.AXIS_Z,
                remappedMatrix
            )

            val orientation = FloatArray(3)
            SensorManager.getOrientation(remappedMatrix, orientation)

            // Convert to degrees
            var yaw = Math.toDegrees(orientation[0].toDouble()).toFloat() // Azimuth
            val pitch = Math.toDegrees(orientation[1].toDouble()).toFloat() // Pitch

            // Normalize yaw to 0-360
            if (yaw < 0) yaw += 360f

            onOrientationChanged(yaw, pitch)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
