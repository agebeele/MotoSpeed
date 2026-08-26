package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.CockpitBackground
import com.example.ui.theme.CockpitCardBorder
import com.example.ui.theme.CockpitSurface
import com.example.ui.theme.ElectricOrange
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.RacingGreen
import com.example.ui.theme.RacingRed
import com.example.ui.theme.SpeedAmber
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun SpeedometerGauge(
    currentSpeed: Float,
    maxGaugeSpeed: Float = 220f,
    speedLimitAlert: Float = 110f,
    isSpeedAlertEnabled: Boolean = true,
    unitText: String = "KM/H",
    leanAngle: Float = 0f,
    modifier: Modifier = Modifier
) {
    val animatedSpeed by animateFloatAsState(
        targetValue = currentSpeed.coerceIn(0f, maxGaugeSpeed),
        animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing),
        label = "SpeedAnimation"
    )

    val isOverSpeed = isSpeedAlertEnabled && currentSpeed > speedLimitAlert

    val infiniteTransition = rememberInfiniteTransition(label = "OverSpeedFlash")
    val alertAlpha by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(400),
            repeatMode = RepeatMode.Reverse
        ),
        label = "AlertAlpha"
    )

    BoxWithConstraints(
        modifier = modifier
            .aspectRatio(1f)
            .padding(8.dp)
            .testTag("speedometer_gauge"),
        contentAlignment = Alignment.Center
    ) {
        val sizeDp = maxWidth

        // Background Outer Ring with Glow
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val radius = (size.minDimension / 2f) - 16.dp.toPx()

            // Outer dark bezel
            drawCircle(
                color = CockpitSurface,
                radius = radius + 10.dp.toPx(),
                center = center
            )
            drawCircle(
                color = CockpitCardBorder,
                radius = radius + 10.dp.toPx(),
                center = center,
                style = Stroke(width = 2.dp.toPx())
            )

            // Outer metallic track arc
            val startAngle = 135f
            val totalSweep = 270f

            drawArc(
                color = Color(0xFF1E2638),
                startAngle = startAngle,
                sweepAngle = totalSweep,
                useCenter = false,
                topLeft = Offset(center.x - radius, center.y - radius),
                size = Size(radius * 2, radius * 2),
                style = Stroke(width = 14.dp.toPx(), cap = StrokeCap.Round)
            )

            // Speed gradient track
            val speedFraction = (animatedSpeed / maxGaugeSpeed).coerceIn(0f, 1f)
            val currentSweep = totalSweep * speedFraction

            val speedColor = when {
                animatedSpeed < 60f -> RacingGreen
                animatedSpeed < 110f -> SpeedAmber
                animatedSpeed < 160f -> ElectricOrange
                else -> RacingRed
            }

            if (currentSweep > 0f) {
                drawArc(
                    brush = Brush.sweepGradient(
                        0.0f to RacingGreen,
                        0.4f to SpeedAmber,
                        0.7f to ElectricOrange,
                        1.0f to RacingRed,
                        center = center
                    ),
                    startAngle = startAngle,
                    sweepAngle = currentSweep,
                    useCenter = false,
                    topLeft = Offset(center.x - radius, center.y - radius),
                    size = Size(radius * 2, radius * 2),
                    style = Stroke(width = 14.dp.toPx(), cap = StrokeCap.Round)
                )
            }

            // Draw Speed Ticks & Numbers
            val tickStep = 20
            val totalTicks = (maxGaugeSpeed / tickStep).toInt()
            val paint = android.graphics.Paint().apply {
                color = TextSecondary.toArgb()
                textSize = 10.sp.toPx()
                textAlign = android.graphics.Paint.Align.CENTER
                isAntiAlias = true
                isFakeBoldText = true
            }

            for (i in 0..totalTicks) {
                val tickSpeed = i * tickStep
                val tickFrac = tickSpeed / maxGaugeSpeed
                val angleDeg = startAngle + (totalSweep * tickFrac)
                val angleRad = Math.toRadians(angleDeg.toDouble())

                val isMajor = tickSpeed % 40 == 0
                val tickLength = if (isMajor) 12.dp.toPx() else 6.dp.toPx()
                val tickWidth = if (isMajor) 3.dp.toPx() else 1.5.dp.toPx()

                val innerR = radius - 12.dp.toPx()
                val outerR = innerR - tickLength

                val startX = center.x + (innerR * cos(angleRad)).toFloat()
                val startY = center.y + (innerR * sin(angleRad)).toFloat()
                val endX = center.x + (outerR * cos(angleRad)).toFloat()
                val endY = center.y + (outerR * sin(angleRad)).toFloat()

                val tickColor = if (tickSpeed <= animatedSpeed) speedColor else TextSecondary.copy(alpha = 0.4f)
                drawLine(
                    color = tickColor,
                    start = Offset(startX, startY),
                    end = Offset(endX, endY),
                    strokeWidth = tickWidth,
                    cap = StrokeCap.Round
                )

                if (isMajor) {
                    val textR = innerR - 20.dp.toPx()
                    val textX = center.x + (textR * cos(angleRad)).toFloat()
                    val textY = center.y + (textR * sin(angleRad)).toFloat() + (paint.textSize / 3f)
                    drawContext.canvas.nativeCanvas.drawText(
                        tickSpeed.toString(),
                        textX,
                        textY,
                        paint
                    )
                }
            }

            // Speed Limit Warning Mark
            if (isSpeedAlertEnabled && speedLimitAlert <= maxGaugeSpeed) {
                val limitFrac = speedLimitAlert / maxGaugeSpeed
                val limitAngle = startAngle + (totalSweep * limitFrac)
                val limitRad = Math.toRadians(limitAngle.toDouble())

                val markR1 = radius + 6.dp.toPx()
                val markR2 = radius + 15.dp.toPx()
                val mx1 = center.x + (markR1 * cos(limitRad)).toFloat()
                val my1 = center.y + (markR1 * sin(limitRad)).toFloat()
                val mx2 = center.x + (markR2 * cos(limitRad)).toFloat()
                val my2 = center.y + (markR2 * sin(limitRad)).toFloat()

                drawLine(
                    color = RacingRed,
                    start = Offset(mx1, my1),
                    end = Offset(mx2, my2),
                    strokeWidth = 4.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }

            // Center Dark Cluster
            val clusterRadius = radius * 0.62f
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFF161E2D), CockpitBackground),
                    center = center,
                    radius = clusterRadius
                ),
                radius = clusterRadius,
                center = center
            )
            drawCircle(
                color = if (isOverSpeed) RacingRed.copy(alpha = alertAlpha) else NeonCyan.copy(alpha = 0.3f),
                radius = clusterRadius,
                center = center,
                style = Stroke(width = 2.dp.toPx())
            )

            // Animated Gauge Needle
            val needleAngle = startAngle + (totalSweep * speedFraction)
            val needleLength = radius - 8.dp.toPx()
            val needleWidth = 5.dp.toPx()

            rotate(degrees = needleAngle - 90f, pivot = center) {
                val needlePath = Path().apply {
                    moveTo(center.x - needleWidth, center.y)
                    lineTo(center.x, center.y + needleLength)
                    lineTo(center.x + needleWidth, center.y)
                    close()
                }
                drawPath(
                    path = needlePath,
                    brush = Brush.verticalGradient(
                        colors = listOf(Color.White, speedColor),
                        startY = center.y,
                        endY = center.y + needleLength
                    )
                )
            }

            // Center Pivot Hub
            drawCircle(
                color = Color.Black,
                radius = 16.dp.toPx(),
                center = center
            )
            drawCircle(
                color = speedColor,
                radius = 10.dp.toPx(),
                center = center
            )
            drawCircle(
                color = Color.White,
                radius = 4.dp.toPx(),
                center = center
            )
        }

        // Center Digital Readout & Status
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(top = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            val speedInt = animatedSpeed.toInt()

            // Dynamic Zone Badge (ECO / SPORT / TRACK / TURBO)
            val zoneLabel = when {
                speedInt == 0 -> "STOP"
                speedInt < 50 -> "ECO"
                speedInt < 90 -> "SPORT"
                speedInt < 140 -> "TRACK"
                else -> "TURBO"
            }
            val zoneColor = when {
                speedInt == 0 -> TextSecondary
                speedInt < 50 -> RacingGreen
                speedInt < 90 -> SpeedAmber
                speedInt < 140 -> ElectricOrange
                else -> RacingRed
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(zoneColor.copy(alpha = 0.2f))
                    .border(1.dp, zoneColor.copy(alpha = 0.6f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Text(
                    text = zoneLabel,
                    color = zoneColor,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.5.sp
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Main Digital Speed Number
            Text(
                text = String.format("%02d", speedInt),
                color = if (isOverSpeed) RacingRed else TextPrimary,
                fontSize = 56.sp,
                fontWeight = FontWeight.ExtraBold,
                fontFamily = FontFamily.Monospace,
                lineHeight = 56.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.testTag("digital_speed_text")
            )

            // Unit (KM/H)
            Text(
                text = unitText,
                color = NeonCyan,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp
            )

            if (isOverSpeed) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Alerta límite de velocidad",
                        tint = RacingRed,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = "¡LÍMITE!",
                        color = RacingRed,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black
                    )
                }
            } else if (kotlin.math.abs(leanAngle) > 2f) {
                Text(
                    text = "Inclinación: ${String.format("%.0f°", kotlin.math.abs(leanAngle))}",
                    color = TextSecondary,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}
