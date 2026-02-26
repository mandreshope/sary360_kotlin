package com.mandreshope.sary360

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.mandreshope.sary360.databinding.ActivityCaptureBinding
import com.mandreshope.sary360.sensors.OrientationTracker
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class CaptureActivity : AppCompatActivity() {
    private lateinit var binding: ActivityCaptureBinding
    private lateinit var cameraExecutor: ExecutorService
    private var imageCapture: ImageCapture? = null
    private lateinit var orientationTracker: OrientationTracker
    
    private var currentYaw = 0f
    private var currentPitch = 0f
    private var isSessionStarted = false
    
    // Simple shot plan: 8 shots every 45 degrees at 0 pitch
    private val shotPlan = listOf(0f, 45f, 90f, 135f, 180f, 225f, 270f, 315f)
    private var nextShotIndex = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCaptureBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (allPermissionsGranted()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
        }

        cameraExecutor = Executors.newSingleThreadExecutor()
        
        orientationTracker = OrientationTracker(this) { yaw, pitch ->
            currentYaw = yaw
            currentPitch = pitch
            updateUI()
        }
        
        binding.btnStart.setOnClickListener {
            isSessionStarted = true
            binding.btnStart.isEnabled = false
            Toast.makeText(this, "Session Started. Align with target.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateUI() {
        if (!isSessionStarted) return
        
        val targetYaw = shotPlan[nextShotIndex]
        val targetPitch = 0f
        
        binding.txtStatus.text = "Target: Yaw ${targetYaw.toInt()}°, Pitch ${targetPitch.toInt()}°\nCurrent: Yaw ${currentYaw.toInt()}°, Pitch ${currentPitch.toInt()}°"
        
        // Auto capture if within tolerance
        if (Math.abs(currentYaw - targetYaw) < 3f && Math.abs(currentPitch - targetPitch) < 3f) {
            takePhoto()
        }
    }

    private fun takePhoto() {
        val imageCapture = imageCapture ?: return
        val photoFile = File(getExternalFilesDir("photospheres"), "shot_${nextShotIndex}.jpg")

        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

        imageCapture.takePicture(
            outputOptions, ContextCompat.getMainExecutor(this), object : ImageCapture.OnImageSavedCallback {
                override fun onError(exc: ImageCaptureException) {
                    Log.e("Capture", "Photo capture failed: ${exc.message}", exc)
                }

                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    nextShotIndex++
                    if (nextShotIndex >= shotPlan.size) {
                        Toast.makeText(baseContext, "All shots captured! Stitching...", Toast.LENGTH_SHORT).show()
                        finish() // In real app, go to stitching screen
                    } else {
                        Toast.makeText(baseContext, "Shot ${nextShotIndex}/${shotPlan.size} saved", Toast.LENGTH_SHORT).show()
                    }
                }
            })
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(binding.viewFinder.surfaceProvider)
            }
            imageCapture = ImageCapture.Builder().build()
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageCapture)
            } catch(exc: Exception) {
                Log.e("Capture", "Use case binding failed", exc)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    override fun onResume() {
        super.onResume()
        orientationTracker.start()
    }

    override fun onPause() {
        super.onPause()
        orientationTracker.stop()
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    companion object {
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
    }
}
