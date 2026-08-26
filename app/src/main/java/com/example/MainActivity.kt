package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.TwoWheeler
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.AppTab
import com.example.ui.MainViewModel
import com.example.ui.screens.RouteMapScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.SpeedometerScreen
import com.example.ui.screens.TripHistoryScreen
import com.example.ui.theme.CockpitBackground
import com.example.ui.theme.CockpitCardBorder
import com.example.ui.theme.CockpitSurface
import com.example.ui.theme.ElectricOrange
import com.example.ui.theme.MotoSpeedTheme
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.RacingGreen
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Keep screen on while motorcycle app is active
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setContent {
            MotoSpeedTheme {
                MotoSpeedApp(viewModel = viewModel)
            }
        }
    }
}

@Composable
fun MotoSpeedApp(viewModel: MainViewModel) {
    val context = LocalContext.current
    val currentTab by viewModel.currentTab.collectAsStateWithLifecycle()
    val gpsState by viewModel.gpsState.collectAsStateWithLifecycle()
    val sensorData by viewModel.sensorData.collectAsStateWithLifecycle()
    val allTrips by viewModel.allTrips.collectAsStateWithLifecycle()
    val totalOdometerKm by viewModel.totalOdometerKm.collectAsStateWithLifecycle()
    val overallMaxSpeed by viewModel.overallMaxSpeedKmh.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val selectedTrip by viewModel.selectedTripDetail.collectAsStateWithLifecycle()
    val toastMessage by viewModel.savedTripToast.collectAsStateWithLifecycle()

    var hasLocationPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasLocationPermission = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
    }

    LaunchedEffect(Unit) {
        if (!hasLocationPermission) {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    LaunchedEffect(toastMessage) {
        toastMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            viewModel.clearToast()
        }
    }

    if (!hasLocationPermission && !gpsState.isSimulationMode) {
        LocationPermissionScreen(
            onRequestPermission = {
                permissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    )
                )
            },
            onUseSimulation = {
                viewModel.toggleSimulationMode()
            }
        )
    } else {
        Scaffold(
            modifier = Modifier
                .fillMaxSize()
                .background(CockpitBackground),
            bottomBar = {
                // Bottom Navigation Bar
                NavigationBar(
                    containerColor = CockpitSurface,
                    contentColor = TextPrimary,
                    tonalElevation = 8.dp,
                    modifier = Modifier
                        .navigationBarsPadding()
                        .border(
                            width = 1.dp,
                            color = CockpitCardBorder,
                            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
                        )
                        .testTag("main_navigation_bar")
                ) {
                    NavigationBarItem(
                        selected = currentTab == AppTab.SPEEDOMETER,
                        onClick = { viewModel.setTab(AppTab.SPEEDOMETER) },
                        icon = {
                            Icon(
                                imageVector = if (currentTab == AppTab.SPEEDOMETER) Icons.Filled.Speed else Icons.Outlined.Speed,
                                contentDescription = "Velocímetro"
                            )
                        },
                        label = { Text("Velocímetro", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color.Black,
                            selectedTextColor = NeonCyan,
                            indicatorColor = NeonCyan,
                            unselectedIconColor = TextSecondary,
                            unselectedTextColor = TextSecondary
                        ),
                        modifier = Modifier.testTag("nav_speedometer")
                    )

                    NavigationBarItem(
                        selected = currentTab == AppTab.MAP,
                        onClick = { viewModel.setTab(AppTab.MAP) },
                        icon = {
                            Icon(
                                imageVector = if (currentTab == AppTab.MAP) Icons.Filled.Map else Icons.Outlined.Map,
                                contentDescription = "Mapa de Ruta"
                            )
                        },
                        label = { Text("Mapa Ruta", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color.Black,
                            selectedTextColor = NeonCyan,
                            indicatorColor = NeonCyan,
                            unselectedIconColor = TextSecondary,
                            unselectedTextColor = TextSecondary
                        ),
                        modifier = Modifier.testTag("nav_map")
                    )

                    NavigationBarItem(
                        selected = currentTab == AppTab.HISTORY,
                        onClick = { viewModel.setTab(AppTab.HISTORY) },
                        icon = {
                            Icon(
                                imageVector = if (currentTab == AppTab.HISTORY) Icons.Filled.History else Icons.Outlined.History,
                                contentDescription = "Historial"
                            )
                        },
                        label = { Text("Historial", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color.Black,
                            selectedTextColor = NeonCyan,
                            indicatorColor = NeonCyan,
                            unselectedIconColor = TextSecondary,
                            unselectedTextColor = TextSecondary
                        ),
                        modifier = Modifier.testTag("nav_history")
                    )

                    NavigationBarItem(
                        selected = currentTab == AppTab.SETTINGS,
                        onClick = { viewModel.setTab(AppTab.SETTINGS) },
                        icon = {
                            Icon(
                                imageVector = if (currentTab == AppTab.SETTINGS) Icons.Filled.Settings else Icons.Outlined.Settings,
                                contentDescription = "Ajustes"
                            )
                        },
                        label = { Text("Ajustes", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color.Black,
                            selectedTextColor = NeonCyan,
                            indicatorColor = NeonCyan,
                            unselectedIconColor = TextSecondary,
                            unselectedTextColor = TextSecondary
                        ),
                        modifier = Modifier.testTag("nav_settings")
                    )
                }
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .statusBarsPadding()
            ) {
                when (currentTab) {
                    AppTab.SPEEDOMETER -> SpeedometerScreen(
                        gpsState = gpsState,
                        sensorData = sensorData,
                        settings = settings,
                        onStartTrip = { viewModel.startTrip() },
                        onPauseTrip = { viewModel.pauseTrip() },
                        onResumeTrip = { viewModel.resumeTrip() },
                        onFinishAndSaveTrip = { title -> viewModel.finishAndSaveTrip(title) },
                        onResetTrip = { viewModel.resetTrip() },
                        onToggleHud = { viewModel.toggleHudMode() },
                        onToggleSimulation = { viewModel.toggleSimulationMode() },
                        onNavigateToMap = { viewModel.setTab(AppTab.MAP) }
                    )

                    AppTab.MAP -> RouteMapScreen(
                        gpsState = gpsState,
                        settings = settings,
                        onStartTrip = { viewModel.startTrip() },
                        onPauseTrip = { viewModel.pauseTrip() },
                        onResumeTrip = { viewModel.resumeTrip() },
                        onFinishAndSaveTrip = { title -> viewModel.finishAndSaveTrip(title) },
                        onResetTrip = { viewModel.resetTrip() },
                        onBack = { viewModel.setTab(AppTab.SPEEDOMETER) }
                    )

                    AppTab.HISTORY -> TripHistoryScreen(
                        trips = allTrips,
                        totalOdometerKm = totalOdometerKm ?: 0.0,
                        overallMaxSpeed = overallMaxSpeed ?: 0.0f,
                        selectedTrip = selectedTrip,
                        onSelectTrip = { viewModel.selectTripDetail(it) },
                        onDeleteTrip = { viewModel.deleteTrip(it) },
                        onParsePoints = { viewModel.parsePoints(it) },
                        onStartNewRide = { viewModel.setTab(AppTab.SPEEDOMETER) }
                    )

                    AppTab.SETTINGS -> SettingsScreen(
                        settings = settings,
                        isSimulationActive = gpsState.isSimulationMode,
                        onSetSpeedLimit = { viewModel.setSpeedLimit(it) },
                        onToggleSpeedAlert = { viewModel.toggleSpeedAlert(it) },
                        onToggleUnits = { viewModel.toggleMetricUnits(it) },
                        onToggleHeatmap = { viewModel.toggleHeatmap(it) },
                        onToggleHud = { viewModel.toggleHudMode() },
                        onToggleSimulation = { viewModel.toggleSimulationMode() }
                    )
                }
            }
        }
    }
}

@Composable
fun LocationPermissionScreen(
    onRequestPermission: () -> Unit,
    onUseSimulation: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(CockpitBackground)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = CockpitSurface),
            border = androidx.compose.foundation.BorderStroke(1.dp, CockpitCardBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(NeonCyan.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = "Ubicación GPS",
                        tint = NeonCyan,
                        modifier = Modifier.size(38.dp)
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))

                Text(
                    text = "Acceso GPS para tu Moto",
                    color = TextPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Para calcular la velocidad exacta en km/h, registrar tu mapa de recorrido, distancia recorrida y tiempos en tu motocicleta, MotoSpeed necesita permiso de ubicación precisa.",
                    color = TextSecondary,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = 18.sp
                )

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = onRequestPermission,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = RacingGreen,
                        contentColor = Color.Black
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("grant_location_permission_button")
                ) {
                    Text(
                        text = "CONCEDER PERMISO GPS",
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Button(
                    onClick = onUseSimulation,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CockpitCardBorder.copy(alpha = 0.5f),
                        contentColor = NeonCyan
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp)
                ) {
                    Text(
                        text = "Probar con Modo Simulación",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
            }
        }
    }
}
