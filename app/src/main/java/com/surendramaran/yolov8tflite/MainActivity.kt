package com.surendramaran.yolov8tflite

import android.Manifest
import android.graphics.Canvas
import android.graphics.Color
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.View
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.surendramaran.yolov8tflite.databinding.ActivityMainBinding
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity(), Detector.DetectorListener {
    private lateinit var binding: ActivityMainBinding
    private var isRealtimeMode: Boolean = false // Flag to track current mode

    private var preview: Preview? = null
    private var imageAnalyzer: ImageAnalysis? = null
    private var camera: Camera? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private lateinit var detector: Detector

    private lateinit var cameraExecutor: ExecutorService

    private lateinit var galleryLauncher: ActivityResultLauncher<Intent>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        detector = Detector(baseContext, Constants.MODEL_PATH, Constants.LABELS_PATH, this)
        detector.setup()

        cameraExecutor = Executors.newSingleThreadExecutor()

        val selectedOption = intent.getStringExtra("mode") ?: "realtime" // Default to realtime mode

        galleryLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == Activity.RESULT_OK && result.data != null) {
                val uri = result.data!!.data
                val inputStream = contentResolver.openInputStream(uri!!)
                val bitmap = BitmapFactory.decodeStream(inputStream)
                inputStream!!.close()
                detectFromGallery(bitmap)
            }
        }

        if (selectedOption == "realtime") {
            isRealtimeMode = true
            if (allPermissionsGranted()) {
                startCamera()
            } else {
                ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
            }
        } else {
            isRealtimeMode = false
            openGallery()
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()
            bindCameraUseCases()
        }, ContextCompat.getMainExecutor(this))
    }

    private fun bindCameraUseCases() {
        val cameraProvider = cameraProvider ?: throw IllegalStateException("Camera initialization failed.")

        val rotation = binding.viewFinder.display.rotation

        val cameraSelector = CameraSelector
            .Builder()
            .requireLensFacing(CameraSelector.LENS_FACING_BACK)
            .build()

        preview = Preview.Builder()
            .setTargetAspectRatio(AspectRatio.RATIO_4_3)
            .setTargetRotation(rotation)
            .build()

        imageAnalyzer = ImageAnalysis.Builder()
            .setTargetAspectRatio(AspectRatio.RATIO_4_3)
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setTargetRotation(binding.viewFinder.display.rotation)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
            .build()

        imageAnalyzer?.setAnalyzer(cameraExecutor) { imageProxy ->
            val bitmapBuffer =
                Bitmap.createBitmap(
                    imageProxy.width,
                    imageProxy.height,
                    Bitmap.Config.ARGB_8888
                )
            imageProxy.use { bitmapBuffer.copyPixelsFromBuffer(imageProxy.planes[0].buffer) }
            imageProxy.close()

            val matrix = Matrix().apply {
                postRotate(imageProxy.imageInfo.rotationDegrees.toFloat())

                // Check if we need to mirror the image (for front camera)
                if (!isRealtimeMode) {
                    postScale(-1f, 1f, imageProxy.width.toFloat() / 2, imageProxy.height.toFloat() / 2)
                }
            }

            val rotatedBitmap = Bitmap.createBitmap(
                bitmapBuffer, 0, 0, bitmapBuffer.width, bitmapBuffer.height,
                matrix, true
            )

            if (isRealtimeMode) {
                detector.detect(rotatedBitmap)
            } else {
                // If not in realtime mode, do nothing with the camera frames
            }
        }

        cameraProvider.unbindAll()

        try {
            camera = cameraProvider.bindToLifecycle(
                this,
                cameraSelector,
                preview,
                imageAnalyzer
            )

            preview?.setSurfaceProvider(binding.viewFinder.surfaceProvider)
        } catch (exc: Exception) {
            Log.e(TAG, "Use case binding failed", exc)
        }
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    override fun onDestroy() {
        super.onDestroy()
        detector.clear()
        cameraExecutor.shutdown()
    }

    override fun onResume() {
        super.onResume()
        if (isRealtimeMode && allPermissionsGranted()) {
            startCamera()
        } else {
            // In gallery mode, we don't need to resume camera
        }
    }

    override fun onEmptyDetect() {
        runOnUiThread {
            binding.overlay.visibility = View.GONE
        }
    }

    override fun onDetect(boundingBoxes: List<BoundingBox>, inferenceTime: Long) {
        runOnUiThread {
            if (isRealtimeMode) {
                binding.overlay.apply {
                    setResults(boundingBoxes)
                    visibility = View.VISIBLE
                    invalidate()
                }
            } else {
                // In gallery mode, show detection results
                binding.overlay.apply {
                    setResults(boundingBoxes)
                    visibility = View.VISIBLE // Show overlay when showing selected image
                    invalidate()
                }
            }
        }
    }

    private fun openGallery() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        galleryLauncher.launch(intent)
    }

    private fun detectFromGallery(bitmap: Bitmap) {
        // Calculate the target width and height with 3:4 aspect ratio
        val targetWidth = detector.tensorWidth
        val targetHeight = detector.tensorHeight

        // Calculate the aspect ratio of the original image
        val originalWidth = bitmap.width
        val originalHeight = bitmap.height
        val aspectRatio = originalWidth.toFloat() / originalHeight.toFloat()

        // Calculate new dimensions maintaining 3:4 aspect ratio
        val newWidth: Int
        val newHeight: Int
        if (aspectRatio > 3.0 / 4.0) {
            // Image is wider, calculate new dimensions based on width
            newWidth = targetWidth
            newHeight = (targetWidth / aspectRatio).toInt()
        } else {
            // Image is taller or matches aspect ratio, calculate new dimensions based on height
            newHeight = targetHeight
            newWidth = (targetHeight * aspectRatio).toInt()
        }

        // Resize the bitmap while maintaining aspect ratio
        val resizedBitmap = Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)

        // Create a new bitmap of target dimensions with neutral padding color (white)
        val paddedBitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(paddedBitmap)
        canvas.drawColor(Color.WHITE) // or any other neutral color

        // Calculate the position to draw the resized image centered in the padded bitmap
        val left = (targetWidth - newWidth) / 2
        val top = (targetHeight - newHeight) / 2
        canvas.drawBitmap(resizedBitmap, left.toFloat(), top.toFloat(), null)

        // Run detection on the padded bitmap
        detector.detect(paddedBitmap)

        // Display the selected image on the UI
        runOnUiThread {
            binding.selectedImage.setImageBitmap(paddedBitmap)
            binding.selectedImage.visibility = View.VISIBLE
        }
    }



    companion object {
        private const val TAG = "Camera"
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = mutableListOf(
            Manifest.permission.CAMERA
        ).toTypedArray()
    }
}
