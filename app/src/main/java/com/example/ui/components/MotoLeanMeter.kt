package com.example.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.TwoWheeler
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.location.SensorData
import com.example.ui.theme.CockpitCardBorder
import com.example.ui.theme.CockpitSurface
import com.example.ui.theme.ElectricOrange
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.RacingGreen
import com.example.ui.theme.RacingRed
import com.example.ui.theme.SpeedAmber
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlin.math.abs

@Composable
fun MotoLeanMeter(
    sensorData: SensorData,
    modifier: Modifier = Modifier
) {
    val animatedLean by animateFloatAsState(
        targetValue = sensorData.leanAngleDegrees.coerceIn(-55f, 55f),
        animationSpec = tween(150),
        label = "LeanAngle"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(CockpitSurface)
            .border(1.dp, CockpitCardBorder, RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .testTag("moto_lean_meter")
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.TwoWheeler,
                        contentDescription = "Inclinación de moto",
                        tint = NeonCyan,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "INCLINACIÓN & G-FORCE",
                        color = TextSecondary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }

                Text(
                    text = "${String.format("%.1f", sensorData.accelerationG)} G (Max: ${String.format("%.1f", sensorData.maxGForce)}G)",
                    color = SpeedAmber,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Horizon Lean Bar
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(26.dp)
            ) {
                val midX = size.width / 2f
                val midY = size.height / 2f

                // Base track
                drawLine(
                    color = Color(0xFF1E2638),
                    start = Offset(20f, midY),
                    end = Offset(size.width - 20f, midY),
                    strokeWidth = 4.dp.toPx(),
                    cap = StrokeCap.Round
                )

                // Center zero marker
                drawLine(
                    color = TextSecondary,
                    start = Offset(midX, midY - 6.dp.toPx()),
                    end = Offset(midX, midY + 6.dp.toPx()),
                    strokeWidth = 2.dp.toPx()
                )

                // Dynamic Lean indicator bar
                val leanRatio = (animatedLean / 50f).coerceIn(-1f, 1f)
                val targetX = midX + (leanRatio * (midX - 30f))

                val leanColor = when {
                    abs(animatedLean) < 15f -> RacingGreen
                    abs(animatedLean) < 32f -> SpeedAmber
                    abs(animatedLean) < 45f -> ElectricOrange
                    else -> RacingRed
                }

                drawLine(
                    color = leanColor,
                    start = Offset(midX, midY),
                    end = Offset(targetX, midY),
                    strokeWidth = 6.dp.toPx(),
                    cap = StrokeCap.Round
                )

                // Motorcycle tilt indicator dot
                drawCircle(
                    color = Color.White,
                    radius = 5.dp.toPx(),
                    center = Offset(targetX, midY)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Izq Max: ${String.format("%.0f°", sensorData.maxLeftLean)}",
                    color = if (sensorData.maxLeftLean > 35f) ElectricOrange else TextSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "Actual: ${String.format("%.0f°", abs(animatedLean))} ${if (animatedLean < -1) "←" else if (animatedLean > 1) "→" else "•"}",
                    color = TextPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = "Der Max: ${String.format("%.0f°", sensorData.maxRightLean)}",
                    color = if (sensorData.maxRightLean > 35f) ElectricOrange else TextSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}
