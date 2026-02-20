package com.linh.banking_ekyc.ekyc

import android.util.Log
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import com.google.mlkit.vision.face.Face

@Composable
fun FaceMeshOverlay() {
    val infiniteTransition = rememberInfiniteTransition(label = "FaceMeshPulse")

    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.55f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "AlphaPulse"
    )

    Canvas(modifier = Modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height
        val centerX = width / 2
        val centerY = height / 2

        val verticalBrush = Brush.verticalGradient(
            0.0f to Color.Transparent,
            0.15f to Color.White.copy(alpha = alpha),
            0.85f to Color.White.copy(alpha = alpha),
            1.0f to Color.Transparent,
            startY = 0f,
            endY = height
        )

        drawLine(
            brush = verticalBrush,
            start = Offset(centerX, 0f),
            end = Offset(centerX, height),
            strokeWidth = 4f,
            cap = StrokeCap.Round
        )

        val arcDrop = height * 0.12f
        val path = Path().apply {
            moveTo(0f, centerY)
            quadraticBezierTo(
                centerX, centerY + arcDrop,
                width, centerY
            )
        }

        val horizontalBrush = Brush.horizontalGradient(
            0.0f to Color.Transparent,
            0.15f to Color.White.copy(alpha = alpha),
            0.85f to Color.White.copy(alpha = alpha),
            1.0f to Color.Transparent,
            startX = 0f,
            endX = width
        )

        drawPath(
            path = path,
            brush = horizontalBrush,
            style = Stroke(
                width = 4f,
                cap = StrokeCap.Round
            )
        )
    }
}

@Composable
fun FaceMeshOverlayTracking(
    face: Face?,
    imageWidth: Int,
    imageHeight: Int,
    mirrorHorizontally: Boolean = true
) {
    val infiniteTransition = rememberInfiniteTransition(label = "FaceMeshPulse")

    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "AlphaPulse"
    )

    Canvas(modifier = Modifier.fillMaxSize()) {
        if (face == null || imageWidth == 0 || imageHeight == 0) return@Canvas

        val canvasWidth = size.width
        val canvasHeight = size.height
        val boundingBox = face.boundingBox

        val imageAspectRatio = imageWidth.toFloat() / imageHeight.toFloat()
        val canvasAspectRatio = canvasWidth / canvasHeight

        val scale: Float
        val offsetX: Float
        val offsetY: Float

        if (imageAspectRatio > canvasAspectRatio) {
            scale = canvasHeight / imageHeight.toFloat()
            offsetX = (imageWidth * scale - canvasWidth) / 2f
            offsetY = 0f
        } else {
            scale = canvasWidth / imageWidth.toFloat()
            offsetX = 0f
            offsetY = (imageHeight * scale - canvasHeight) / 2f
        }

        val rawX = boundingBox.centerX() * scale
        val rawY = boundingBox.centerY() * scale

        val faceCenterX = if (mirrorHorizontally) {
            (imageWidth * scale) - rawX - offsetX
        } else {
            rawX - offsetX
        }
        val faceCenterY = rawY - offsetY

        val faceWidth = boundingBox.width() * scale
        val faceHeight = boundingBox.height() * scale

        Log.d("FaceMeshOverlay", "Canvas: ${canvasWidth}x${canvasHeight}, Image: ${imageWidth}x${imageHeight}")
        Log.d("FaceMeshOverlay", "Scale: $scale, Offset: ($offsetX, $offsetY)")
        Log.d("FaceMeshOverlay", "Face center: ($faceCenterX, $faceCenterY), size: ${faceWidth}x${faceHeight}")

        val lineTop = faceCenterY - faceHeight * 0.5f
        val lineBottom = faceCenterY + faceHeight * 0.5f

        val verticalBrush = Brush.verticalGradient(
            0.0f to Color.Transparent,
            0.1f to Color.White.copy(alpha = alpha),
            0.9f to Color.White.copy(alpha = alpha),
            1.0f to Color.Transparent,
            startY = lineTop,
            endY = lineBottom
        )

        drawLine(
            brush = verticalBrush,
            start = Offset(faceCenterX, lineTop),
            end = Offset(faceCenterX, lineBottom),
            strokeWidth = 4f,
            cap = StrokeCap.Round
        )

        val arcLeft = faceCenterX - faceWidth * 0.5f
        val arcRight = faceCenterX + faceWidth * 0.5f
        val arcDrop = faceHeight * 0.1f

        val path = Path().apply {
            moveTo(arcLeft, faceCenterY)
            quadraticBezierTo(
                faceCenterX, faceCenterY + arcDrop,
                arcRight, faceCenterY
            )
        }

        val horizontalBrush = Brush.horizontalGradient(
            0.0f to Color.Transparent,
            0.1f to Color.White.copy(alpha = alpha),
            0.9f to Color.White.copy(alpha = alpha),
            1.0f to Color.Transparent,
            startX = arcLeft,
            endX = arcRight
        )

        drawPath(
            path = path,
            brush = horizontalBrush,
            style = Stroke(
                width = 4f,
                cap = StrokeCap.Round
            )
        )
    }
}

