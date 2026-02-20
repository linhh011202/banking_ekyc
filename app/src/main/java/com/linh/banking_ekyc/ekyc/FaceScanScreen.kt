package com.linh.banking_ekyc.ekyc

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
import kotlin.coroutines.resume

private const val TOTAL_TICKS = 72
private const val PHOTOS_PER_PHASE = 3
private const val CAPTURE_INTERVAL_MS = 600L
private const val FACE_SIZE_THRESHOLD = 0.35f
private const val EULER_Y_THRESHOLD = 20f

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
        val result = when (scanPhase) {
            ScanPhase.FACE_CENTER -> {
                val faceRatio = face.boundingBox.width().toFloat() / imageWidth.coerceAtLeast(1)
                Log.d("FaceScan", "FACE_CENTER check: faceRatio=$faceRatio, threshold=$FACE_SIZE_THRESHOLD, imageWidth=$imageWidth")
                faceRatio > FACE_SIZE_THRESHOLD
            }
            ScanPhase.TURN_LEFT -> {
                Log.d("FaceScan", "TURN_LEFT check: eulerY=${face.headEulerAngleY}, threshold=$EULER_Y_THRESHOLD")
                face.headEulerAngleY > EULER_Y_THRESHOLD
            }
            ScanPhase.TURN_RIGHT -> {
                Log.d("FaceScan", "TURN_RIGHT check: eulerY=${face.headEulerAngleY}, threshold=-$EULER_Y_THRESHOLD")
                face.headEulerAngleY < -EULER_Y_THRESHOLD
            }
            ScanPhase.COMPLETED -> false
        }
        Log.d("FaceScan", "Phase ${scanPhase.name} condition met: $result")
        return result
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

            val instructionText = when (scanPhase) {
                ScanPhase.FACE_CENTER -> "Move your face closer"
                ScanPhase.TURN_LEFT -> "Turn left"
                ScanPhase.TURN_RIGHT -> "Turn right"
                ScanPhase.COMPLETED -> "Done!"
            }

            Text(
                text = instructionText,
                color = Color.White,
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
                    val hintText = when (scanPhase) {
                        ScanPhase.FACE_CENTER -> "Move your face closer"
                        ScanPhase.TURN_LEFT -> "Turn your head further left"
                        ScanPhase.TURN_RIGHT -> "Turn your head further right"
                        else -> ""
                    }
                    if (hintText.isNotEmpty()) {
                        Text(text = hintText, color = Color(0xAAFFFFFF), fontSize = 13.sp)
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
 * Captures a single photo and returns the JPEG bytes directly in memory (no file saved).
 */
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
                    val bytes = ByteArray(buffer.remaining())
                    buffer.get(bytes)
                    image.close()
                    Log.d("FaceScan", "✓ Captured in-memory (${bytes.size} bytes)")
                    cont.resume(bytes)
                }

                override fun onError(exception: ImageCaptureException) {
                    Log.e("FaceScan", "Capture failed: ${exception.message}", exception)
                    cont.resume(null)
                }
            }
        )
    }
}
