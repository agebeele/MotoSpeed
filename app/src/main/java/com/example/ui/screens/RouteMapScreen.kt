package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ElectricMeter
import androidx.compose.material.icons.filled.Landscape
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.RoutePoint
import com.example.location.GpsState
import com.example.location.TripState
import com.example.ui.AppSettings
import com.example.ui.components.LiveRouteMap
import com.example.ui.components.SaveTripDialog
import com.example.ui.components.TripControlsBar
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
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RouteMapScreen(
    gpsState: GpsState,
    settings: AppSettings,
    onStartTrip: () -> Unit,
    onPauseTrip: () -> Unit,
    onResumeTrip: () -> Unit,
    onFinishAndSaveTrip: (title: String) -> Unit,
    onResetTrip: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showSaveDialog by remember { mutableStateOf(false) }

    if (showSaveDialog) {
        SaveTripDialog(
            gpsState = gpsState,
            onDismiss = { showSaveDialog = false },
            onSave = { title ->
                onFinishAndSaveTrip(title)
                showSaveDialog = false
            },
            onDiscard = {
                onResetTrip()
                showSaveDialog = false
            }
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(CockpitBackground)
            .testTag("route_map_screen")
    ) {
        // Fullscreen Live Route Map
        LiveRouteMap(
            currentLat = gpsState.currentLatitude,
            currentLon = gpsState.currentLongitude,
            heading = gpsState.headingDegrees,
            currentSpeedKmh = gpsState.currentSpeedKmh,
            routePoints = gpsState.routePoints,
            isRecording = gpsState.tripState == TripState.RECORDING,
            showHeatmap = settings.showSpeedHeatmap,
            isInteractive = true,
            modifier = Modifier.fillMaxSize()
        )

        // Top Overlay Header with Speed & Distance
        Surface(
            color = CockpitSurface.copy(alpha = 0.92f),
            shape = RoundedCornerShape(16.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, CockpitCardBorder),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Live Speed Readout
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = String.format(Locale.US, "%.0f", gpsState.currentSpeedKmh),
                        color = if (gpsState.currentSpeedKmh > settings.speedLimitAlertKmh) RacingRed else TextPrimary,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (settings.useMetricUnits) "km/h" else "mph",
                        color = NeonCyan,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }

                // Distance & Altitude
                Column(horizontalAlignment = Alignment.End) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ElectricMeter,
                            contentDescription = "Distancia",
                            tint = NeonCyan,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = String.format(Locale.US, "%.2f km", gpsState.distanceKm),
                            color = TextPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    Text(
                        text = "Altitud: ${String.format(Locale.US, "%.0f m", gpsState.altitudeMeters)}",
                        color = TextSecondary,
                        fontSize = 11.sp
                    )
                }
            }
        }

        // Bottom Overlay Panel: Speed Profile Graph + Trip Controls
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Speed Profile Sparkline Card
            if (gpsState.routePoints.size > 2) {
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = CockpitSurface.copy(alpha = 0.92f)),
                    border = androidx.compose.foundation.BorderStroke(1.dp, CockpitCardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "PERFIL DE VELOCIDAD",
                                color = TextSecondary,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                            Text(
                                text = "Max: ${String.format(Locale.US, "%.0f km/h", gpsState.maxSpeedKmh)}",
                                color = RacingRed,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        SpeedSparkline(
                            routePoints = gpsState.routePoints,
                            maxSpeed = gpsState.maxSpeedKmh.coerceAtLeast(60f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(38.dp)
                        )
                    }
                }
            }

            // Controls
            TripControlsBar(
                tripState = gpsState.tripState,
                onStart = onStartTrip,
                onPause = onPauseTrip,
                onResume = onResumeTrip,
                onStop = { showSaveDialog = true }
            )
        }
    }
}

@Composable
fun SpeedSparkline(
    routePoints: List<RoutePoint>,
    maxSpeed: Float,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        if (routePoints.size < 2) return@Canvas

        val w = size.width
        val h = size.height
        val stepX = w / (routePoints.size - 1).toFloat()

        val path = Path()
        val fillPath = Path()

        val firstY = h - ((routePoints[0].speedKmh / maxSpeed).coerceIn(0f, 1f) * h)
        path.moveTo(0f, firstY)
        fillPath.moveTo(0f, h)
        fillPath.lineTo(0f, firstY)

        for (i in 1 until routePoints.size) {
            val x = i * stepX
            val y = h - ((routePoints[i].speedKmh / maxSpeed).coerceIn(0f, 1f) * (h - 4.dp.toPx()))
            path.lineTo(x, y)
            fillPath.lineTo(x, y)
        }

        fillPath.lineTo(w, h)
        fillPath.close()

        drawPath(
            path = fillPath,
            brush = Brush.verticalGradient(
                colors = listOf(NeonCyan.copy(alpha = 0.35f), Color.Transparent),
                startY = 0f,
                endY = h
            )
        )

        drawPath(
            path = path,
            color = NeonCyan,
            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
        )
    }
}
