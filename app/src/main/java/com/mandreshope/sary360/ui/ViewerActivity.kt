package com.mandreshope.sary360.ui

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.MotionEvent
import androidx.appcompat.app.AppCompatActivity
import com.mandreshope.sary360.databinding.ActivityViewerBinding
import com.mandreshope.sary360.gl.SphereRenderer

class ViewerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityViewerBinding
    private lateinit var renderer: SphereRenderer

    private var previousX: Float = 0f
    private var previousY: Float = 0f

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityViewerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val panoPath = intent.getStringExtra("panoPath") ?: ""
        renderer = SphereRenderer(this, panoPath)

        binding.glSurfaceView.setEGLContextClientVersion(2)
        binding.glSurfaceView.setRenderer(renderer)
        
        binding.glSurfaceView.setOnTouchListener { _, event ->
            val x = event.x
            val y = event.y

            when (event.action) {
                MotionEvent.ACTION_MOVE -> {
                    val dx = x - previousX
                    val dy = y - previousY

                    renderer.yaw += dx * 0.1f
                    renderer.pitch += dy * 0.1f
                    
                    // Clamp pitch to avoid flipping
                    if (renderer.pitch > 90f) renderer.pitch = 90f
                    if (renderer.pitch < -90f) renderer.pitch = -90f
                }
            }
            previousX = x
            previousY = y
            true
        }

        binding.backButton.setOnClickListener { finish() }
    }

    override fun onResume() {
        super.onResume()
        binding.glSurfaceView.onResume()
    }

    override fun onPause() {
        super.onPause()
        binding.glSurfaceView.onPause()
    }
}
