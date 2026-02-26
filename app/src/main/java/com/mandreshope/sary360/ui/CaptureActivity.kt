package com.mandreshope.sary360.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.mandreshope.sary360.data.AppDatabase
import com.mandreshope.sary360.data.SphereSession
import com.mandreshope.sary360.databinding.ActivityCaptureBinding
import com.mandreshope.sary360.sensors.OrientationTracker
import com.mandreshope.sary360.stitching.NativeStitcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class CaptureActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCaptureBinding
    private lateinit var cameraExecutor: ExecutorService
    private var imageCapture: ImageCapture? = null
    private lateinit var orientationTracker: OrientationTracker

    private val shotPlan = mutableListOf<ShotPoint>()
    private var currentShotIndex = 0
    private var isSessionActive = false
    private val capturedImages = mutableListOf<String>()

    private lateinit var sessionFolder: File

    data class ShotPoint(val yaw: Float, val pitch: Float)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCaptureBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (allPermissionsGranted()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
        }

        orientationTracker = OrientationTracker(this) { yaw, pitch ->
            onOrientationChanged(yaw, pitch, 0f)
        }
        cameraExecutor = Executors.newSingleThreadExecutor()

        binding.btnStart.setOnClickListener {
            if (!isSessionActive) startSession()
        }

        setupShotPlan()
    }

    private fun setupShotPlan() {
        // Horizon ring (0 pitch)
        for (yaw in 0 until 360 step 15) shotPlan.add(ShotPoint(yaw.toFloat(), 0f))
        // Upper ring (+30 pitch)
        for (yaw in 0 until 360 step 30) shotPlan.add(ShotPoint(yaw.toFloat(), 30f))
        // Lower ring (-30 pitch)
        for (yaw in 0 until 360 step 30) shotPlan.add(ShotPoint(yaw.toFloat(), -30f))
        // Zenith & Nadir
        shotPlan.add(ShotPoint(0f, 90f))
        shotPlan.add(ShotPoint(0f, -90f))
    }

    private fun startSession() {
        isSessionActive = true
        currentShotIndex = 0
        capturedImages.clear()

        val timestamp = System.currentTimeMillis()
        sessionFolder = File(getExternalFilesDir("photospheres"), "session_$timestamp")
        sessionFolder.mkdirs()

        binding.btnStart.visibility = View.GONE
        updateUI()
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
            } catch (exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun onOrientationChanged(yaw: Float, pitch: Float, roll: Float) {
        if (!isSessionActive) return

        val target = shotPlan[currentShotIndex]
        val reached = isWithinTolerance(yaw, pitch, target.yaw, target.pitch)

        // TODO: Add captureOverlay to the layout
        // binding.captureOverlay.updateOrientation(yaw, pitch)
        // binding.captureOverlay.setTarget(target.yaw, target.pitch, reached)

        if (reached) {
            takePhoto()
        }
    }

    private fun isWithinTolerance(y1: Float, p1: Float, y2: Float, p2: Float): Boolean {
        val dy = Math.abs(normalizeAngle(y1 - y2))
        val dp = Math.abs(p1 - p2)
        return dy < 3f && dp < 3f
    }

    private fun normalizeAngle(angle: Float): Float {
        var a = angle
        while (a <= -180) a += 360
        while (a > 180) a -= 360
        return a
    }

    private fun takePhoto() {
        val imageCapture = imageCapture ?: return
        // Pause taking photos to prevent multiple captures for the same point
        isSessionActive = false

        val photoFile = File(sessionFolder, "shot_${currentShotIndex}.jpg")
        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

        imageCapture.takePicture(
            outputOptions, ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    capturedImages.add(photoFile.absolutePath)
                    currentShotIndex++

                    if (currentShotIndex < shotPlan.size) {
                        isSessionActive = true
                        updateUI()
                    } else {
                        finishCapture()
                    }
                }

                override fun onError(exc: ImageCaptureException) {
                    Log.e(TAG, "Photo capture failed: ${exc.message}", exc)
                    isSessionActive = true
                }
            })
    }

    private fun updateUI() {
        binding.txtStatus.text = "${currentShotIndex} / ${shotPlan.size}"
    }

    private fun finishCapture() {
        binding.txtStatus.text = "Stitching..."
        // TODO: Add stitchingProgress to the layout
        // binding.stitchingProgress.visibility = View.VISIBLE

        lifecycleScope.launch(Dispatchers.IO) {
            val panoPath = File(sessionFolder, "panorama.jpg").absolutePath
            val result = NativeStitcher().stitchImages(capturedImages.toTypedArray(), panoPath)

            withContext(Dispatchers.Main) {
                // binding.stitchingProgress.visibility = View.GONE
                if (result == 0) {
                    saveToDatabase(panoPath)
                    Toast.makeText(this@CaptureActivity, "Success!", Toast.LENGTH_LONG).show()
                    finish()
                } else {
                    Toast.makeText(
                        this@CaptureActivity,
                        "Stitching failed: $result",
                        Toast.LENGTH_LONG
                    ).show()
                    binding.btnStart.visibility = View.VISIBLE
                    isSessionActive = false
                }
            }
        }
    }

    private suspend fun saveToDatabase(path: String) {
        val session = SphereSession(
            panoPath = path,
            thumbPath = path, // For simplicity, use the same for now
            status = "COMPLETED"
        )
        AppDatabase.getDatabase(this).sphereDao().insert(session)
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
        private const val TAG = "CaptureActivity"
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
    }
}
