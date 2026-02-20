package com.linh.banking_ekyc.ekyc

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import com.google.mlkit.vision.face.Face

@Composable
fun FaceTrackingOverlay(
    face: Face?,
    imageWidth: Int,
    imageHeight: Int,
    mirrorHorizontally: Boolean = true
) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        if (face == null || imageWidth == 0 || imageHeight == 0) return@Canvas

        val canvasWidth = size.width
        val canvasHeight = size.height
        val boundingBox = face.boundingBox

        val scaleX = canvasWidth / imageWidth.toFloat()
        val scaleY = canvasHeight / imageHeight.toFloat()

        val left = if (mirrorHorizontally) {
            canvasWidth - (boundingBox.right * scaleX)
        } else {
            boundingBox.left * scaleX
        }

        val right = if (mirrorHorizontally) {
            canvasWidth - (boundingBox.left * scaleX)
        } else {
            boundingBox.right * scaleX
        }

        val top = boundingBox.top * scaleY
        val bottom = boundingBox.bottom * scaleY

        val faceWidth = right - left
        val faceHeight = bottom - top

        val ovalPadding = faceWidth * 0.2f
        drawOval(
            color = Color(0xFF00E676),
            topLeft = Offset(left - ovalPadding, top - ovalPadding * 0.5f),
            size = Size(faceWidth + ovalPadding * 2, faceHeight + ovalPadding),
            style = Stroke(width = 3f)
        )
    }
}

