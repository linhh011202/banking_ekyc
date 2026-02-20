package com.linh.banking_ekyc.ekyc

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun FaceScanOverlay(
    centerX: Float,
    centerY: Float,
    radius: Float,
    totalTicks: Int = 72,
    completedTicks: Set<Int> = emptySet(),
    faceDetected: Boolean = false
) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val tickInnerRadius = radius + 8f
        val tickOuterRadius = radius + 28f
        val tickWidth = 4f

        for (i in 0 until totalTicks) {
            val angleDeg = (i * 360f / totalTicks) - 90f
            val angleRad = Math.toRadians(angleDeg.toDouble())

            val cosAngle = cos(angleRad).toFloat()
            val sinAngle = sin(angleRad).toFloat()

            val startX = centerX + tickInnerRadius * cosAngle
            val startY = centerY + tickInnerRadius * sinAngle
            val endX = centerX + tickOuterRadius * cosAngle
            val endY = centerY + tickOuterRadius * sinAngle

            val color = when {
                completedTicks.contains(i) -> Color(0xFF00E676)
                faceDetected -> Color(0xCCFFFFFF)
                else -> Color(0x66FFFFFF)
            }

            drawLine(
                color = color,
                start = Offset(startX, startY),
                end = Offset(endX, endY),
                strokeWidth = tickWidth,
                cap = StrokeCap.Round
            )
        }

        drawCircle(
            color = if (faceDetected) Color(0x44FFFFFF) else Color(0x22FFFFFF),
            radius = radius,
            center = Offset(centerX, centerY),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f)
        )
    }
}

