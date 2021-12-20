package com.example.imagedetection

import android.Manifest
import android.content.pm.PackageManager
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.widget.RelativeLayout
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.material.snackbar.Snackbar
import com.google.mlkit.common.model.LocalModel
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.objects.ObjectDetection
import com.google.mlkit.vision.objects.ObjectDetector
import com.google.mlkit.vision.objects.custom.CustomObjectDetectorOptions
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions
import java.nio.ByteBuffer
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {
    private lateinit var cameraExecutor: ExecutorService
    private var imageCapture: ImageCapture? = null
    private lateinit var view: ConstraintLayout
    private lateinit var viewFinder: PreviewView
    private lateinit var layout: RelativeLayout

//    val customObjectDetectorOptions =
//        CustomObjectDetectorOptions.Builder(localModel)            .setDetectorMode(CustomObjectDetectorOptions.STREAM_MODE)
//            .enableClassification()
//            .setClassificationConfidenceThreshold(0.5f)
//            .setMaxPerObjectLabelCount(3)
//            .build()

    private lateinit var processImageListener: (InputImage,ImageProxy) -> Unit

    private lateinit var objectDetector: ObjectDetector

    private val requestPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted: Boolean ->

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
        setupDetector()

        view = findViewById(R.id.main_activity_parent)
        viewFinder = findViewById(R.id.viewFinder)
        layout = findViewById(R.id.layout)
    }

    private fun setupDetector(){
        val localModel = LocalModel.Builder()
            .setAssetFilePath("lite-model_on_device_vision_classifier_popular_us_products_V1_1.tflite")
            .build()
        val customObjectDetectorOptions =
            CustomObjectDetectorOptions.Builder(localModel)
                .setDetectorMode(CustomObjectDetectorOptions.STREAM_MODE)
                .enableClassification()
                .setClassificationConfidenceThreshold(0.5f)
                .enableMultipleObjects()
                .setMaxPerObjectLabelCount(3)
                .build()

        //TODO("Update options build once I hace a custom trained model")
        //TODO("Move this to use case")
        val detectorOptions = ObjectDetectorOptions.Builder()
            .setDetectorMode(ObjectDetectorOptions.STREAM_MODE)
            .enableMultipleObjects()
            .enableClassification()  // Optional
            .build()

        cameraExecutor = Executors.newSingleThreadExecutor()
        verityPermissionAndStartCameraAccordingly()
        //objectDetector = ObjectDetection.getClient(detectorOptions)
        objectDetector = ObjectDetection.getClient(customObjectDetectorOptions)
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }

    private fun startCamera() {

        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        processImageListener = { image, imageProxy ->
            objectDetector.process(image)
                .addOnSuccessListener { detectedObjects ->
                    for( it in detectedObjects) {
                        if(layout.childCount > 1)  layout.removeViewAt(1)
                        val element = Draw(this, it.boundingBox, it.labels.firstOrNull()?.text ?: "Undefined")
                        layout.addView(element,1)
                    }
                    Log.e("ObjectDetector",detectedObjects.flatMap { it.labels.map { it.text } }.joinToString { it })
                }
                .addOnFailureListener { e ->
                    Log.e("ObjectDetectorError",e.stackTraceToString() ?: "ERROR")
                }
                .continueWith {
                    imageProxy.close()
                }
        }

        cameraProviderFuture.addListener({
            // Used to bind the lifecycle of cameras to the lifecycle owner
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            // Preview
            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(viewFinder.surfaceProvider)
                }

            imageCapture = ImageCapture.Builder()
                .build()

            val imageAnalyzer = ImageAnalysis.Builder()
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor, LuminosityAnalyzer(processImageListener))
                }

            // Select back camera as a default
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                // Unbind use cases before rebinding
                cameraProvider.unbindAll()

                // Bind use cases to camera
                cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, imageCapture, imageAnalyzer
                )

            } catch (exc: Exception) {
                //Log this exception
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


    //private class LuminosityAnalyzer(private val listener: LumaListener) : ImageAnalysis.Analyzer {
    private class LuminosityAnalyzer(private val unit: (InputImage,ImageProxy) -> Unit) :
        ImageAnalysis.Analyzer {
        private fun ByteBuffer.toByteArray(): ByteArray {
            rewind()    // Rewind the buffer to zero
            val data = ByteArray(remaining())
            get(data)   // Copy the buffer into a byte array
            return data // Return the byte array
        }

        @androidx.camera.core.ExperimentalGetImage
        override fun analyze(image: ImageProxy) {

            val buffer = image.planes[0].buffer
            val data = buffer.toByteArray()
            val pixels = data.map { it.toInt() and 0xFF }
            val luma = pixels.average()
//            //listener(luma)

            val mediaImage = image.image
            if (mediaImage != null) {
                val inputImage = InputImage.fromMediaImage(mediaImage, image.imageInfo.rotationDegrees)
                unit(inputImage, image)
            }
        }
    }
}