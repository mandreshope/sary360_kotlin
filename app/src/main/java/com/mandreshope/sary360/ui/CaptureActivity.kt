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

    private var baseYaw: Float? = null
    private var basePitch: Float? = null

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
        // Upper ring 1 (+30 pitch)
        for (yaw in 0 until 360 step 30) shotPlan.add(ShotPoint(yaw.toFloat(), 30f))
        // Upper ring 2 (+60 pitch, 3 photos)
        for (yaw in 0 until 360 step 120) shotPlan.add(ShotPoint(yaw.toFloat(), 60f))
        
        // Lower ring 1 (-30 pitch)
        for (yaw in 0 until 360 step 30) shotPlan.add(ShotPoint(yaw.toFloat(), -30f))
        // Lower ring 2 (-60 pitch, 3 photos)
        for (yaw in 0 until 360 step 120) shotPlan.add(ShotPoint(yaw.toFloat(), -60f))
        
        // Zenith & Nadir
        shotPlan.add(ShotPoint(0f, 90f))
        shotPlan.add(ShotPoint(0f, -90f))
    }

    private fun startSession() {
        isSessionActive = true
        currentShotIndex = 0
        capturedImages.clear()
        
        baseYaw = null
        basePitch = null

        val timestamp = System.currentTimeMillis()
        sessionFolder = File(getExternalFilesDir("photospheres"), "session_$timestamp")
        sessionFolder.mkdirs()

        binding.btnStart.visibility = View.GONE
        binding.captureOverlay.visibility = View.VISIBLE
        binding.captureOverlay.isSessionActive = true
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
                val camera = cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageCapture)
                camera.cameraControl.setLinearZoom(0f) // Zoom out all the way for wide-angle
            } catch (exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun onOrientationChanged(yaw: Float, pitch: Float, roll: Float) {
        if (!isSessionActive) return

        if (baseYaw == null) baseYaw = yaw

        val relativeYaw = normalizeAngle(yaw - baseYaw!!)

        val target = shotPlan[currentShotIndex]
        val reached = isWithinTolerance(relativeYaw, pitch, target.yaw, target.pitch)

        binding.captureOverlay.updateOrientation(relativeYaw, pitch)
        binding.captureOverlay.setTarget(target.yaw, target.pitch, reached)

        if (reached) {
            takePhoto()
        }
    }

    private fun isWithinTolerance(y1: Float, p1: Float, y2: Float, p2: Float): Boolean {
        val dp = Math.abs(p1 - p2)
        if (Math.abs(p2) >= 80f) {
            // Near zenith or nadir, yaw doesn't matter, just rely on pitch
            return dp < 2.5f
        }
        val dy = Math.abs(normalizeAngle(y1 - y2))
        return dy < 2.5f && dp < 2.5f
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
        binding.captureOverlay.isSessionActive = false

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
                        binding.captureOverlay.isSessionActive = true
                        updateUI()
                    } else {
                        finishCapture()
                    }
                }

                override fun onError(exc: ImageCaptureException) {
                    Log.e(TAG, "Photo capture failed: ${exc.message}", exc)
                    isSessionActive = true
                    binding.captureOverlay.isSessionActive = true
                }
            })
    }

    private fun updateUI() {
        binding.txtStatus.text = "${currentShotIndex} / ${shotPlan.size}"
    }

    private fun finishCapture() {
        binding.txtStatus.text = "Stitching..."
        binding.captureOverlay.visibility = View.GONE
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
                    binding.captureOverlay.isSessionActive = false
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
