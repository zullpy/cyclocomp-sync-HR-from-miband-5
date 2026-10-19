package com.ridecomputer.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ridecomputer.ui.theme.AccentGreen
import com.ridecomputer.ui.theme.DashboardSurfaceVariant
import com.ridecomputer.ui.theme.TextPrimary
import kotlin.math.cos
import kotlin.math.sin

/**
 * Modern geometric cyclocomputer brand logo.
 * Features an athletic velocity dial arc with an integrated forward chevron monogram.
 */
@Composable
fun CycloCompLogo(
    modifier: Modifier = Modifier,
    size: Dp = 96.dp,
    sweepProgress: Float = 1.0f,
    accentColor: Color = AccentGreen,
    trackColor: Color = DashboardSurfaceVariant,
    monogramColor: Color = TextPrimary,
) {
    Canvas(
        modifier = modifier.size(size)
    ) {
        val w = this.size.width
        val h = this.size.height
        val s = minOf(w, h) / 100f
        val center = Offset(w / 2f, h / 2f)
        val dialRadius = 38f * s
        val strokeWidth = 6.5f * s

        // 1. Inactive background gauge track
        drawArc(
            color = trackColor,
            startAngle = 135f,
            sweepAngle = 270f,
            useCenter = false,
            topLeft = Offset(center.x - dialRadius, center.y - dialRadius),
            size = Size(dialRadius * 2f, dialRadius * 2f),
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
        )

        // 2. Active velocity sweep arc
        val clampedProgress = sweepProgress.coerceIn(0f, 1f)
        if (clampedProgress > 0f) {
            val sweepAngle = 270f * clampedProgress
            drawArc(
                color = accentColor,
                startAngle = 135f,
                sweepAngle = sweepAngle,
                useCenter = false,
                topLeft = Offset(center.x - dialRadius, center.y - dialRadius),
                size = Size(dialRadius * 2f, dialRadius * 2f),
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
            )

            // Dynamic tip indicator
            val currentAngleRad = Math.toRadians((135f + sweepAngle).toDouble())
            val tipX = center.x + dialRadius * cos(currentAngleRad).toFloat()
            val tipY = center.y + dialRadius * sin(currentAngleRad).toFloat()
            drawCircle(
                color = Color.White,
                radius = strokeWidth * 0.45f,
                center = Offset(tipX, tipY),
            )
        }

        // 3. Central "C" geometric bracket
        val cPath = Path().apply {
            moveTo(center.x + 8f * s, center.y - 14f * s)
            lineTo(center.x - 6f * s, center.y - 14f * s)
            cubicTo(
                center.x - 18f * s, center.y - 14f * s,
                center.x - 18f * s, center.y + 14f * s,
                center.x - 6f * s, center.y + 14f * s,
            )
            lineTo(center.x + 8f * s, center.y + 14f * s)
        }
        drawPath(
            path = cPath,
            color = monogramColor,
            style = Stroke(width = 4.5f * s, cap = StrokeCap.Round),
        )

        // 4. Forward velocity chevron (representing momentum & cadence)
        val chevronPath = Path().apply {
            moveTo(center.x + 3f * s, center.y - 9f * s)
            lineTo(center.x + 13f * s, center.y)
            lineTo(center.x + 3f * s, center.y + 9f * s)
        }
        drawPath(
            path = chevronPath,
            color = accentColor,
            style = Stroke(
                width = 4.5f * s,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round,
            ),
        )
    }
}
