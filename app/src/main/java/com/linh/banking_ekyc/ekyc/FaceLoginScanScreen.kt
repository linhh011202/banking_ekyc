package com.linh.banking_ekyc.ekyc

import android.util.Log
import androidx.camera.core.ImageCapture
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

private const val LOGIN_PHOTOS_COUNT = 3
private const val LOGIN_CAPTURE_INTERVAL_MS = 600L
private const val LOGIN_FACE_SIZE_THRESHOLD = 0.55f
private const val LOGIN_TOTAL_TICKS = 72

/**
 * Simplified face scan screen for eKYC Login.
 * Captures 3 frontal face photos as ByteArrays in memory (no file saved).
 */
@Composable
fun FaceLoginScanScreen(
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

    var capturedPhotos by remember { mutableStateOf<List<ByteArray>>(emptyList()) }
    var isCapturing by remember { mutableStateOf(false) }
    var isDone by remember { mutableStateOf(false) }
    var captureStatus by remember { mutableStateOf("") }
    var validationResult by remember { mutableStateOf(FaceValidationResult.VALID) }

    val captureScope = rememberCoroutineScope()
    val faceDetected = detectedFaces.isNotEmpty()

    val completedTicks = remember(capturedPhotos.size, isDone) {
        val filled = if (isDone) LOGIN_TOTAL_TICKS
        else (capturedPhotos.size.toFloat() / LOGIN_PHOTOS_COUNT * LOGIN_TOTAL_TICKS).toInt()
        (0 until filled).toSet()
    }

    // Continuously validate face
    LaunchedEffect(detectedFaces) {
        if (detectedFaces.isNotEmpty() && !isCapturing && !isDone) {
            val face = detectedFaces.first()
            validationResult = FaceValidator.validate(face, imageWidth, scanPhase = null)
        } else if (detectedFaces.isEmpty()) {
            validationResult = FaceValidationResult(
                isValid = false,
                warningMessage = "No face detected"
            )
        }
    }

    LaunchedEffect(Unit) {
        while (!isDone && !isCapturing) {
            if (detectedFaces.isNotEmpty()) {
                val face = detectedFaces.first()
                val result = FaceValidator.validate(face, imageWidth, scanPhase = null)
                validationResult = result
                val faceRatio = face.boundingBox.width().toFloat() / imageWidth.coerceAtLeast(1)
                if (result.isValid && faceRatio > LOGIN_FACE_SIZE_THRESHOLD) {
                    Log.d("FaceLoginScan", "Face detected! Starting capture.")
                    captureScope.launch {
                        isCapturing = true
                        captureStatus = "Starting capture..."

                        for (i in 0 until LOGIN_PHOTOS_COUNT) {
                            captureStatus = "Capturing photo ${i + 1}/$LOGIN_PHOTOS_COUNT..."
                            val bytes = capturePhotoBytes(imageCapture, ContextCompat.getMainExecutor(context))
                            if (bytes != null) {
                                capturedPhotos = capturedPhotos + bytes
                                Log.d("FaceLoginScan", "Captured ${capturedPhotos.size} (${bytes.size} bytes)")
                                captureStatus = "✓ Captured ${capturedPhotos.size}/$LOGIN_PHOTOS_COUNT"
                            } else {
                                Log.e("FaceLoginScan", "Failed photo ${i + 1}")
                                captureStatus = "✗ Capture error"
                            }
                            delay(LOGIN_CAPTURE_INTERVAL_MS)
                        }

                        isDone = true
                        isCapturing = false
                        captureStatus = ""
                        onCompleted(capturedPhotos)
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
                        onFacesDetected = { faces, w, _ ->
                            detectedFaces = faces
                            imageWidth = w
                        },
                        onCaptureReady = { capture ->
                            imageCapture = capture
                        }
                    )
                    if (!isDone) {
                        FaceMeshOverlay()
                    }
                }

                FaceScanOverlay(
                    centerX = (circleSizePx + with(density) { 64.dp.toPx() }) / 2f,
                    centerY = (circleSizePx + with(density) { 64.dp.toPx() }) / 2f,
                    radius = circleRadius,
                    totalTicks = LOGIN_TOTAL_TICKS,
                    completedTicks = completedTicks,
                    faceDetected = faceDetected
                )
            }

            Spacer(modifier = Modifier.height(40.dp))

            val instructionText = when {
                isDone -> "Done!"
                !faceDetected -> "Place your face in the frame"
                !validationResult.isValid -> validationResult.warningMessage
                else -> "Look straight at the camera"
            }

            val instructionColor = when {
                isDone -> Color.White
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

            if (!isDone) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Photo ${capturedPhotos.size}/$LOGIN_PHOTOS_COUNT",
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
                        else -> "Move your face closer"
                    }
                    val hintColor = if (validationResult.hasMask || validationResult.hasSunglasses || validationResult.isFaceObstructed || !validationResult.eyesOpen) {
                        Color(0xFFFF5252)
                    } else {
                        Color(0xAAFFFFFF)
                    }
                    Text(text = hintText, color = hintColor, fontSize = 13.sp)
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

            AnimatedVisibility(visible = isDone, enter = scaleIn() + fadeIn()) {
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
