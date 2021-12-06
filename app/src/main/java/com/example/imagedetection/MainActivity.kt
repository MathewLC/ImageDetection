package com.example.imagedetection

import android.Manifest
import android.content.pm.PackageManager
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.material.snackbar.Snackbar
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {
    private lateinit var cameraExecutor: ExecutorService

    private val requestPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted: Boolean ->
            val view = findViewById<ConstraintLayout>(R.id.main_activity_parent)
            if (isGranted) {
                startCamera()
            } else {
                val snackbar = Snackbar.make(
                    view,
                    "NÃO É POSSÍVEL USAR O APP POR FALTA DE PERMISSÃO",
                    Snackbar.LENGTH_LONG
                )
                snackbar.setAction("OK") {
                }.show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        cameraExecutor = Executors.newSingleThreadExecutor()
        verityPermissionAndStartCameraAccordingly()
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }

    private fun startCamera() {
        val viewFinder = findViewById<PreviewView>(R.id.viewFinder)
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            // Used to bind the lifecycle of cameras to the lifecycle owner
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            // Preview
            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(viewFinder.surfaceProvider)
                }

            // Select back camera as a default
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                // Unbind use cases before rebinding
                cameraProvider.unbindAll()
                // Bind use cases to camera
                cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview
                )

            } catch (exc: Exception) {
                //log error here
            }

        }, ContextCompat.getMainExecutor(this))
    }

    private fun verityPermissionAndStartCameraAccordingly() {
        val view = findViewById<ConstraintLayout>(R.id.main_activity_parent)
        when {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED -> {
                val snackbar = Snackbar.make(view, "PERMISSION GRANTED", Snackbar.LENGTH_LONG)
                snackbar.setAction("OK") {
                }.show()
                startCamera()
            }

            ActivityCompat.shouldShowRequestPermissionRationale(
                this,
                Manifest.permission.CAMERA
            ) -> {
                val snackbar = Snackbar.make(
                    view,
                    "SEM PERMISSÃO NÃo É POSSÍVEL USAR O APP",
                    Snackbar.LENGTH_LONG
                )
                snackbar.setAction("OK") {
                }.show()

                requestPermissionLauncher.launch(
                    Manifest.permission.CAMERA
                )
            }

            else -> {
                requestPermissionLauncher.launch(
                    Manifest.permission.CAMERA
                )
            }
        }
    }

}