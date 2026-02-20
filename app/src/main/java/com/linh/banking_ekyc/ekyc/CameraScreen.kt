package com.linh.banking_ekyc.ekyc

import android.util.Log
import android.view.Surface
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.face.Face

/**
 * Camera preview composable using FRONT camera (selfie) with face detection and photo capture.
 */
@Composable
fun CameraPreviewView(
    onFacesDetected: (faces: List<Face>, imageWidth: Int, imageHeight: Int) -> Unit,
    onCaptureReady: (ImageCapture) -> Unit = {}
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val previewView = remember {
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
            // scaleX = -1f removed: front camera is already mirrored by the system
        }
    }

    DisposableEffect(lifecycleOwner) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)

        cameraProviderFuture.addListener({
            try {
                val cameraProvider = cameraProviderFuture.get()

                val preview = Preview.Builder()
                    .setTargetRotation(Surface.ROTATION_0)
                    .build()
                    .also { it.setSurfaceProvider(previewView.surfaceProvider) }

                val imageCapture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .setTargetRotation(Surface.ROTATION_0)
                    .build()

                val imageAnalysis = ImageAnalysis.Builder()
                    .setTargetRotation(Surface.ROTATION_0)
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also { analysis ->
                        analysis.setAnalyzer(
                            ContextCompat.getMainExecutor(context),
                            FaceDetectionAnalyzer { faces, width, height ->
                                onFacesDetected(faces, width, height)
                            }
                        )
                    }

                val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

                cameraProvider.unbindAll()

                cameraProvider.bindToLifecycle(
                    lifecycleOwner, cameraSelector,
                    preview, imageCapture, imageAnalysis
                )
                onCaptureReady(imageCapture)
                Log.d("CameraPreviewView", "Bound FRONT camera")

            } catch (e: Exception) {
                Log.e("CameraPreviewView", "Camera initialization failed", e)
            }
        }, ContextCompat.getMainExecutor(context))

        onDispose {
            try {
                val cameraProvider = ProcessCameraProvider.getInstance(context).get()
                cameraProvider.unbindAll()
            } catch (_: Exception) {}
        }
    }

    AndroidView(
        factory = { previewView },
        modifier = Modifier.fillMaxSize()
    )
}
