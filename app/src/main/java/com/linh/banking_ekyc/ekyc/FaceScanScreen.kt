package com.linh.banking_ekyc.ekyc

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.face.Face
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.ByteArrayOutputStream
import kotlin.coroutines.resume

private const val TOTAL_TICKS = 72
private const val PHOTOS_PER_PHASE = 3
private const val CAPTURE_INTERVAL_MS = 600L

enum class ScanPhase {
    FACE_CENTER,
    TURN_LEFT,
    TURN_RIGHT,
    COMPLETED
}

@Composable
fun FaceScanScreen(
    onCancel: () -> Unit = {},
    onCompleted: (List<ByteArray>) -> Unit = {}
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val screenWidthDp = configuration.screenWidthDp.dp
    val circleSizeDp = screenWidthDp * 0.7f
    val density = LocalDensity.current
    val circleSizePx = with(density) { circleSizeDp.toPx() }
    val circleRadius = circleSizePx / 2f

    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var detectedFaces by remember { mutableStateOf<List<Face>>(emptyList()) }
    var imageWidth by remember { mutableIntStateOf(0) }
    var imageHeight by remember { mutableIntStateOf(0) }

    var scanPhase by remember { mutableStateOf(ScanPhase.FACE_CENTER) }
    var capturedPhotos by remember { mutableStateOf<List<ByteArray>>(emptyList()) }
    var photosInCurrentPhase by remember { mutableIntStateOf(0) }
    var isCapturing by remember { mutableStateOf(false) }
    var captureStatus by remember { mutableStateOf("") }
    var validationResult by remember { mutableStateOf(FaceValidationResult.VALID) }

    val captureScope = rememberCoroutineScope()
    val faceDetected = detectedFaces.isNotEmpty()

    val ticksPerPhase = TOTAL_TICKS / 3
    val completedTicks = remember(scanPhase, photosInCurrentPhase) {
        val phaseIndex = when (scanPhase) {
            ScanPhase.FACE_CENTER -> 0
            ScanPhase.TURN_LEFT -> 1
            ScanPhase.TURN_RIGHT -> 2
            ScanPhase.COMPLETED -> 3
        }
        val fullPhases = phaseIndex.coerceAtMost(3)
        val partialTicks = if (scanPhase != ScanPhase.COMPLETED) {
            (photosInCurrentPhase.toFloat() / PHOTOS_PER_PHASE * ticksPerPhase).toInt()
        } else 0
        (0 until (fullPhases * ticksPerPhase + partialTicks).coerceAtMost(TOTAL_TICKS)).toSet()
    }

    fun isPhaseConditionMet(face: Face): Boolean {
        val result = FaceValidator.validate(face, imageWidth, scanPhase)
        validationResult = result
        Log.d("FaceScan", "Phase ${scanPhase.name} validation: valid=${result.isValid}, msg=${result.warningMessage}")
        return result.isValid
    }

    // Continuously validate face even when not trying to capture
    LaunchedEffect(detectedFaces, scanPhase) {
        if (detectedFaces.isNotEmpty() && !isCapturing && scanPhase != ScanPhase.COMPLETED) {
            val face = detectedFaces.first()
            validationResult = FaceValidator.validate(face, imageWidth, scanPhase)
        } else if (detectedFaces.isEmpty()) {
            validationResult = FaceValidationResult(
                isValid = false,
                warningMessage = "No face detected"
            )
        }
    }

    LaunchedEffect(scanPhase) {
        if (scanPhase == ScanPhase.COMPLETED || isCapturing) return@LaunchedEffect
        Log.d("FaceScan", "Waiting for phase ${scanPhase.name} condition...")

        while (scanPhase != ScanPhase.COMPLETED && !isCapturing) {
            if (detectedFaces.isNotEmpty()) {
                val face = detectedFaces.first()
                if (isPhaseConditionMet(face)) {
                    Log.d("FaceScan", "Condition met! Starting capture for phase ${scanPhase.name}")

                    captureScope.launch {
                        isCapturing = true
                        captureStatus = "Starting capture..."

                        for (i in 0 until PHOTOS_PER_PHASE) {
                            captureStatus = "Capturing photo ${i + 1}/${PHOTOS_PER_PHASE}..."
                            Log.d("FaceScan", "Taking photo ${i + 1}...")

                            val bytes = capturePhotoBytes(imageCapture, ContextCompat.getMainExecutor(context))
                            if (bytes != null) {
                                capturedPhotos = capturedPhotos + bytes
                                photosInCurrentPhase++
                                Log.d("FaceScan", "Captured photo ${capturedPhotos.size} (${bytes.size} bytes)")
                                captureStatus = "✓ Captured ${photosInCurrentPhase}/${PHOTOS_PER_PHASE}"
                            } else {
                                Log.e("FaceScan", "Failed to capture photo ${i + 1}")
                                captureStatus = "✗ Capture error"
                            }
                            delay(CAPTURE_INTERVAL_MS)
                        }

                        captureStatus = ""
                        photosInCurrentPhase = 0
                        scanPhase = when (scanPhase) {
                            ScanPhase.FACE_CENTER -> ScanPhase.TURN_LEFT
                            ScanPhase.TURN_LEFT -> ScanPhase.TURN_RIGHT
                            ScanPhase.TURN_RIGHT -> {
                                onCompleted(capturedPhotos)
                                ScanPhase.COMPLETED
                            }
                            ScanPhase.COMPLETED -> ScanPhase.COMPLETED
                        }
                        isCapturing = false
                    }
                    break
                }
            }
            delay(100)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        TextButton(
            onClick = onCancel,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(top = 48.dp, start = 16.dp)
        ) {
            Text("Cancel", color = Color(0xFF448AFF), fontSize = 17.sp)
        }

        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier.size(circleSizeDp + 64.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(circleSizeDp)
                        .clip(CircleShape)
                ) {
                    CameraPreviewView(
                        onFacesDetected = { faces, w, h ->
                            detectedFaces = faces
                            imageWidth = w
                            imageHeight = h
                        },
                        onCaptureReady = { capture ->
                            imageCapture = capture
                        }
                    )
                    if (scanPhase != ScanPhase.COMPLETED) {
                        FaceMeshOverlay()
                    }
                }

                FaceScanOverlay(
                    centerX = (circleSizePx + with(density) { 64.dp.toPx() }) / 2f,
                    centerY = (circleSizePx + with(density) { 64.dp.toPx() }) / 2f,
                    radius = circleRadius,
                    totalTicks = TOTAL_TICKS,
                    completedTicks = completedTicks,
                    faceDetected = faceDetected
                )
            }

            Spacer(modifier = Modifier.height(40.dp))

            val instructionText = when {
                scanPhase == ScanPhase.COMPLETED -> "Done!"
                !faceDetected -> "Place your face in the frame"
                !validationResult.isValid -> validationResult.warningMessage
                else -> when (scanPhase) {
                    ScanPhase.FACE_CENTER -> "Look straight at the camera"
                    ScanPhase.TURN_LEFT -> "Turn your head left"
                    ScanPhase.TURN_RIGHT -> "Turn your head right"
                    ScanPhase.COMPLETED -> "Done!"
                }
            }

            val instructionColor = when {
                scanPhase == ScanPhase.COMPLETED -> Color.White
                !faceDetected -> Color.White
                !validationResult.isValid && (validationResult.hasMask || validationResult.hasSunglasses || validationResult.isFaceObstructed) -> Color(0xFFFF5252)
                !validationResult.isValid -> Color(0xFFFFAB40)
                else -> Color.White
            }

            Text(
                text = instructionText,
                color = instructionColor,
                fontSize = 22.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 48.dp)
            )

            if (scanPhase != ScanPhase.COMPLETED) {
                Spacer(modifier = Modifier.height(12.dp))

                val phaseNumber = when (scanPhase) {
                    ScanPhase.FACE_CENTER -> 1
                    ScanPhase.TURN_LEFT -> 2
                    ScanPhase.TURN_RIGHT -> 3
                    ScanPhase.COMPLETED -> 3
                }

                Text(
                    text = "Step $phaseNumber/3 — Photo $photosInCurrentPhase/$PHOTOS_PER_PHASE",
                    color = Color(0xFF00E676),
                    fontSize = 14.sp
                )

                if (faceDetected && !isCapturing) {
                    Spacer(modifier = Modifier.height(8.dp))
                    val hintText = when {
                        validationResult.hasMask -> "⚠ Mask detected"
                        validationResult.hasSunglasses -> "⚠ Sunglasses detected"
                        validationResult.isFaceObstructed -> "⚠ Face is obstructed"
                        !validationResult.eyesOpen -> "⚠ Please open your eyes"
                        !validationResult.isValid -> validationResult.warningMessage
                        else -> when (scanPhase) {
                            ScanPhase.FACE_CENTER -> "Hold still, preparing to capture..."
                            ScanPhase.TURN_LEFT -> "Turn your head further left"
                            ScanPhase.TURN_RIGHT -> "Turn your head further right"
                            else -> ""
                        }
                    }
                    val hintColor = if (validationResult.hasMask || validationResult.hasSunglasses || validationResult.isFaceObstructed || !validationResult.eyesOpen) {
                        Color(0xFFFF5252)
                    } else {
                        Color(0xAAFFFFFF)
                    }
                    if (hintText.isNotEmpty()) {
                        Text(text = hintText, color = hintColor, fontSize = 13.sp)
                    }
                }

                if (isCapturing) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (captureStatus.isNotEmpty()) captureStatus else "Capturing... hold still",
                        color = Color(0xFF00E676),
                        fontSize = 13.sp
                    )
                }
            }

            AnimatedVisibility(
                visible = scanPhase == ScanPhase.COMPLETED,
                enter = scaleIn() + fadeIn()
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("✓", color = Color(0xFF00E676), fontSize = 48.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Captured ${capturedPhotos.size} photos",
                        color = Color(0xAAFFFFFF),
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
}

/**
 * Captures a single photo, downscales to [maxDimension] px and compresses
 * to JPEG at [quality]%. Returns the compressed bytes directly in memory.
 */
private const val CAPTURE_MAX_DIMENSION = 640
private const val CAPTURE_JPEG_QUALITY = 80

internal suspend fun capturePhotoBytes(
    imageCapture: ImageCapture?,
    executor: java.util.concurrent.Executor
): ByteArray? {
    if (imageCapture == null) {
        Log.e("FaceScan", "ImageCapture is not ready!")
        return null
    }
    return suspendCancellableCoroutine { cont ->
        imageCapture.takePicture(
            executor,
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    val buffer = image.planes[0].buffer
                    val rawBytes = ByteArray(buffer.remaining())
                    buffer.get(rawBytes)
                    image.close()

                    // Downscale + compress
                    val compressed = compressPhoto(rawBytes)
                    Log.d("FaceScan", "✓ Captured: raw=${rawBytes.size / 1024}KB → compressed=${compressed.size / 1024}KB")
                    cont.resume(compressed)
                }

                override fun onError(exception: ImageCaptureException) {
                    Log.e("FaceScan", "Capture failed: ${exception.message}", exception)
                    cont.resume(null)
                }
            }
        )
    }
}

/**
 * Downscales a JPEG byte array so that the longest side is at most
 * [CAPTURE_MAX_DIMENSION] px, then re-encodes at [CAPTURE_JPEG_QUALITY]%.
 */
private fun compressPhoto(rawBytes: ByteArray): ByteArray {
    // Decode bounds only
    val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size, opts)
    val origW = opts.outWidth
    val origH = opts.outHeight

    // Calculate inSampleSize (power of 2 downscale for speed)
    var sampleSize = 1
    val longestSide = maxOf(origW, origH)
    while (longestSide / sampleSize > CAPTURE_MAX_DIMENSION * 2) {
        sampleSize *= 2
    }

    // Decode with sample size
    val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
    val sampled = BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size, decodeOpts)
        ?: return rawBytes // fallback

    // Fine-scale to exact max dimension
    val scale = CAPTURE_MAX_DIMENSION.toFloat() / maxOf(sampled.width, sampled.height).coerceAtLeast(1)
    val bitmap = if (scale < 1f) {
        val newW = (sampled.width * scale).toInt().coerceAtLeast(1)
        val newH = (sampled.height * scale).toInt().coerceAtLeast(1)
        Bitmap.createScaledBitmap(sampled, newW, newH, true).also {
            if (it !== sampled) sampled.recycle()
        }
    } else {
        sampled
    }

    // Compress to JPEG
    val out = ByteArrayOutputStream()
    bitmap.compress(Bitmap.CompressFormat.JPEG, CAPTURE_JPEG_QUALITY, out)
    bitmap.recycle()
    return out.toByteArray()
}

