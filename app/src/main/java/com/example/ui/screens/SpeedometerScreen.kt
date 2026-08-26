package com.example.ui.screens

import android.content.res.Configuration
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Flip
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.GpsNotFixed
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.location.GpsState
import com.example.location.SensorData
import com.example.location.TripState
import com.example.ui.AppSettings
import com.example.ui.components.LiveRouteMap
import com.example.ui.components.MotoLeanMeter
import com.example.ui.components.RideStatsGrid
import com.example.ui.components.SaveTripDialog
import com.example.ui.components.SpeedometerGauge
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

@Composable
fun SpeedometerScreen(
    gpsState: GpsState,
    sensorData: SensorData,
    settings: AppSettings,
    onStartTrip: () -> Unit,
    onPauseTrip: () -> Unit,
    onResumeTrip: () -> Unit,
    onFinishAndSaveTrip: (title: String) -> Unit,
    onResetTrip: () -> Unit,
    onToggleHud: () -> Unit,
    onToggleSimulation: () -> Unit,
    onNavigateToMap: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showSaveDialog by remember { mutableStateOf(false) }
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

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

    // Apply HUD mode (mirror horizontally for windshield reflection)
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(CockpitBackground)
            .graphicsLayer(
                scaleX = if (settings.isHudMode) -1f else 1f
            )
            .testTag("speedometer_screen")
    ) {
        if (isLandscape) {
            // Landscape Cockpit Layout (TFT Motorcycle Dashboard)
            LandscapeCockpitView(
                gpsState = gpsState,
                sensorData = sensorData,
                settings = settings,
                onStartTrip = onStartTrip,
                onPauseTrip = onPauseTrip,
                onResumeTrip = onResumeTrip,
                onStopTrip = { showSaveDialog = true },
                onToggleHud = onToggleHud,
                onToggleSimulation = onToggleSimulation,
                onNavigateToMap = onNavigateToMap
            )
        } else {
            // Portrait Cockpit Layout
            PortraitCockpitView(
                gpsState = gpsState,
                sensorData = sensorData,
                settings = settings,
                onStartTrip = onStartTrip,
                onPauseTrip = onPauseTrip,
                onResumeTrip = onResumeTrip,
                onStopTrip = { showSaveDialog = true },
                onToggleHud = onToggleHud,
                onToggleSimulation = onToggleSimulation,
                onNavigateToMap = onNavigateToMap
            )
        }
    }
}

@Composable
private fun PortraitCockpitView(
    gpsState: GpsState,
    sensorData: SensorData,
    settings: AppSettings,
    onStartTrip: () -> Unit,
    onPauseTrip: () -> Unit,
    onResumeTrip: () -> Unit,
    onStopTrip: () -> Unit,
    onToggleHud: () -> Unit,
    onToggleSimulation: () -> Unit,
    onNavigateToMap: () -> Unit
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Status & Quick Action Bar
        TopStatusHeader(
            gpsState = gpsState,
            isHudMode = settings.isHudMode,
            onToggleHud = onToggleHud,
            onToggleSimulation = onToggleSimulation
        )

        // Main Speedometer Gauge
        SpeedometerGauge(
            currentSpeed = gpsState.currentSpeedKmh,
            maxGaugeSpeed = 220f,
            speedLimitAlert = settings.speedLimitAlertKmh,
            isSpeedAlertEnabled = settings.isSpeedAlertEnabled,
            unitText = if (settings.useMetricUnits) "KM/H" else "MPH",
            leanAngle = sensorData.leanAngleDegrees,
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .padding(vertical = 4.dp)
        )

        // Ride Controls
        TripControlsBar(
            tripState = gpsState.tripState,
            onStart = onStartTrip,
            onPause = onPauseTrip,
            onResume = onResumeTrip,
            onStop = onStopTrip
        )

        // Lean Angle & G-Force Bar
        MotoLeanMeter(sensorData = sensorData)

        // 4 Key Statistics Grid (Vel Max, Vel Prom, Distancia en KM, Tiempo)
        RideStatsGrid(
            maxSpeedKmh = gpsState.maxSpeedKmh,
            avgSpeedKmh = gpsState.avgSpeedKmh,
            distanceKm = gpsState.distanceKm,
            durationSeconds = gpsState.durationSeconds,
            useMetric = settings.useMetricUnits
        )

        // Mini Map Preview Card with Live GPS Trail
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = CockpitSurface),
            border = androidx.compose.foundation.BorderStroke(1.dp, CockpitCardBorder),
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onNavigateToMap() }
                .testTag("mini_map_card")
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
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
                            imageVector = Icons.Default.Map,
                            contentDescription = "Mapa de Recorrido",
                            tint = NeonCyan,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "MAPA DEL RECORRIDO",
                            color = TextSecondary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "Pantalla completa",
                            color = NeonCyan,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Icon(
                            imageVector = Icons.Default.OpenInFull,
                            contentDescription = "Abrir mapa",
                            tint = NeonCyan,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                LiveRouteMap(
                    currentLat = gpsState.currentLatitude,
                    currentLon = gpsState.currentLongitude,
                    heading = gpsState.headingDegrees,
                    currentSpeedKmh = gpsState.currentSpeedKmh,
                    routePoints = gpsState.routePoints,
                    isRecording = gpsState.tripState == TripState.RECORDING,
                    showHeatmap = settings.showSpeedHeatmap,
                    isInteractive = false,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
    }
}

@Composable
private fun LandscapeCockpitView(
    gpsState: GpsState,
    sensorData: SensorData,
    settings: AppSettings,
    onStartTrip: () -> Unit,
    onPauseTrip: () -> Unit,
    onResumeTrip: () -> Unit,
    onStopTrip: () -> Unit,
    onToggleHud: () -> Unit,
    onToggleSimulation: () -> Unit,
    onNavigateToMap: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left Column: Massive Speedometer Gauge
        Column(
            modifier = Modifier
                .weight(1.1f)
                .fillMaxHeight(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            SpeedometerGauge(
                currentSpeed = gpsState.currentSpeedKmh,
                maxGaugeSpeed = 220f,
                speedLimitAlert = settings.speedLimitAlertKmh,
                isSpeedAlertEnabled = settings.isSpeedAlertEnabled,
                unitText = if (settings.useMetricUnits) "KM/H" else "MPH",
                leanAngle = sensorData.leanAngleDegrees,
                modifier = Modifier.fillMaxSize(0.95f)
            )
        }

        // Right Column: Dashboard Stats, Live Map & Controls
        Column(
            modifier = Modifier
                .weight(1.3f)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Header
            TopStatusHeader(
                gpsState = gpsState,
                isHudMode = settings.isHudMode,
                onToggleHud = onToggleHud,
                onToggleSimulation = onToggleSimulation
            )

            // Horizontal Stats & Lean Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    RideStatsGrid(
                        maxSpeedKmh = gpsState.maxSpeedKmh,
                        avgSpeedKmh = gpsState.avgSpeedKmh,
                        distanceKm = gpsState.distanceKm,
                        durationSeconds = gpsState.durationSeconds,
                        useMetric = settings.useMetricUnits
                    )
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(0.65f)
                ) {
                    LiveRouteMap(
                        currentLat = gpsState.currentLatitude,
                        currentLon = gpsState.currentLongitude,
                        heading = gpsState.headingDegrees,
                        currentSpeedKmh = gpsState.currentSpeedKmh,
                        routePoints = gpsState.routePoints,
                        isRecording = gpsState.tripState == TripState.RECORDING,
                        showHeatmap = settings.showSpeedHeatmap,
                        isInteractive = false,
                        modifier = Modifier
                            .fillMaxSize()
                            .clickable { onNavigateToMap() }
                    )
                }
            }

            // Controls
            TripControlsBar(
                tripState = gpsState.tripState,
                onStart = onStartTrip,
                onPause = onPauseTrip,
                onResume = onResumeTrip,
                onStop = onStopTrip
            )
        }
    }
}

@Composable
fun TopStatusHeader(
    gpsState: GpsState,
    isHudMode: Boolean,
    onToggleHud: () -> Unit,
    onToggleSimulation: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // GPS Lock Status Pill
        Surface(
            color = CockpitSurface,
            shape = RoundedCornerShape(12.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, CockpitCardBorder)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Icon(
                    imageVector = if (gpsState.hasGpsFix) Icons.Default.GpsFixed else Icons.Default.GpsNotFixed,
                    contentDescription = "Estado del GPS",
                    tint = if (gpsState.hasGpsFix) RacingGreen else ElectricOrange,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (gpsState.isSimulationMode) "MODO SIMULACIÓN" else if (gpsState.hasGpsFix) "GPS ACTIVO (${String.format("%.0fm", gpsState.gpsAccuracyMeters)})" else "BUSCANDO GPS...",
                    color = if (gpsState.isSimulationMode) NeonCyan else if (gpsState.hasGpsFix) RacingGreen else ElectricOrange,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            // Simulation Demo Mode Toggle
            IconButton(
                onClick = onToggleSimulation,
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = if (gpsState.isSimulationMode) NeonCyan.copy(alpha = 0.2f) else CockpitSurface,
                    contentColor = if (gpsState.isSimulationMode) NeonCyan else TextSecondary
                ),
                modifier = Modifier
                    .size(36.dp)
                    .border(
                        1.dp,
                        if (gpsState.isSimulationMode) NeonCyan else CockpitCardBorder,
                        CircleShape
                    )
                    .testTag("simulation_toggle_button")
            ) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = "Modo Simulación Demo",
                    modifier = Modifier.size(16.dp)
                )
            }

            // HUD Mirror Mode Toggle
            IconButton(
                onClick = onToggleHud,
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = if (isHudMode) SpeedAmber.copy(alpha = 0.2f) else CockpitSurface,
                    contentColor = if (isHudMode) SpeedAmber else TextSecondary
                ),
                modifier = Modifier
                    .size(36.dp)
                    .border(
                        1.dp,
                        if (isHudMode) SpeedAmber else CockpitCardBorder,
                        CircleShape
                    )
                    .testTag("hud_mirror_toggle_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Flip,
                    contentDescription = "Modo HUD Espejo",
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}
