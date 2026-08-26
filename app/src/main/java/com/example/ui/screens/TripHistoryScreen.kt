package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AvTimer
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.ElectricMeter
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.TwoWheeler
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.model.RoutePoint
import com.example.data.model.TripEntity
import com.example.ui.components.LiveRouteMap
import com.example.ui.theme.CockpitBackground
import com.example.ui.theme.CockpitCardBorder
import com.example.ui.theme.CockpitSurface
import com.example.ui.theme.CockpitSurfaceVariant
import com.example.ui.theme.ElectricOrange
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.RacingGreen
import com.example.ui.theme.RacingRed
import com.example.ui.theme.SpeedAmber
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun TripHistoryScreen(
    trips: List<TripEntity>,
    totalOdometerKm: Double,
    overallMaxSpeed: Float,
    selectedTrip: TripEntity?,
    onSelectTrip: (TripEntity?) -> Unit,
    onDeleteTrip: (Long) -> Unit,
    onParsePoints: (TripEntity) -> List<RoutePoint>,
    onStartNewRide: () -> Unit,
    modifier: Modifier = Modifier
) {
    var tripToDelete by remember { mutableStateOf<TripEntity?>(null) }

    if (tripToDelete != null) {
        AlertDialog(
            onDismissRequest = { tripToDelete = null },
            title = { Text("Eliminar Ruta", color = TextPrimary) },
            text = { Text("¿Deseas eliminar '${tripToDelete?.title}' de tu historial de rutas?", color = TextSecondary) },
            confirmButton = {
                Button(
                    onClick = {
                        tripToDelete?.id?.let { onDeleteTrip(it) }
                        tripToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = RacingRed)
                ) {
                    Text("Eliminar", color = Color.White)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { tripToDelete = null }) {
                    Text("Cancelar", color = TextPrimary)
                }
            },
            containerColor = CockpitSurface
        )
    }

    if (selectedTrip != null) {
        TripDetailDialog(
            trip = selectedTrip,
            routePoints = onParsePoints(selectedTrip),
            onDismiss = { onSelectTrip(null) },
            onDelete = {
                onDeleteTrip(selectedTrip.id)
                onSelectTrip(null)
            }
        )
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(CockpitBackground)
            .padding(horizontal = 16.dp)
            .testTag("trip_history_screen"),
        contentPadding = PaddingValues(vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Hero Summary Banner (Total Odometer, Total Rides, Record Max Speed)
        item {
            TotalStatsBanner(
                totalOdometerKm = totalOdometerKm,
                totalRides = trips.size,
                overallMaxSpeed = overallMaxSpeed
            )
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "RUTAS REGISTRADAS (${trips.size})",
                    color = TextSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
            }
        }

        if (trips.isEmpty()) {
            item {
                EmptyTripsState(onStartNewRide = onStartNewRide)
            }
        } else {
            items(trips, key = { it.id }) { trip ->
                TripHistoryItemCard(
                    trip = trip,
                    onClick = { onSelectTrip(trip) },
                    onDelete = { tripToDelete = trip }
                )
            }
        }
    }
}

@Composable
fun TotalStatsBanner(
    totalOdometerKm: Double,
    totalRides: Int,
    overallMaxSpeed: Float
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = CockpitSurface),
        border = androidx.compose.foundation.BorderStroke(1.dp, CockpitCardBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.TwoWheeler,
                    contentDescription = "Odómetro total",
                    tint = NeonCyan,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = "ODÓMETRO TOTAL EN MOTO",
                    color = TextSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = String.format(Locale.US, "%.1f", totalOdometerKm),
                    color = TextPrimary,
                    fontSize = 38.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = "KM RECORRIDOS",
                    color = NeonCyan,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    color = CockpitCardBorder.copy(alpha = 0.35f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text("Total Rutas", color = TextSecondary, fontSize = 10.sp)
                        Text(
                            text = "$totalRides viajes",
                            color = RacingGreen,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Surface(
                    color = CockpitCardBorder.copy(alpha = 0.35f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text("Récord Vel. Max", color = TextSecondary, fontSize = 10.sp)
                        Text(
                            text = String.format(Locale.US, "%.1f km/h", overallMaxSpeed),
                            color = RacingRed,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun TripHistoryItemCard(
    trip: TripEntity,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val dateStr = SimpleDateFormat("EEEE, d MMMM yyyy - HH:mm", Locale("es", "MX"))
        .format(Date(trip.startTime))
        .replaceFirstChar { it.uppercase() }

    val durationMin = trip.durationSeconds / 60
    val durationSec = trip.durationSeconds % 60
    val timeFormatted = if (durationMin > 60) {
        val hrs = durationMin / 60
        "${hrs}h ${durationMin % 60}m"
    } else {
        "${durationMin}m ${durationSec}s"
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CockpitSurface),
        border = androidx.compose.foundation.BorderStroke(1.dp, CockpitCardBorder),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .testTag("trip_card_${trip.id}")
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = trip.title,
                        color = TextPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = dateStr,
                        color = TextSecondary,
                        fontSize = 11.sp
                    )
                }

                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = "Eliminar ruta",
                        tint = TextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 4 Stats in row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Distancia", color = TextSecondary, fontSize = 10.sp)
                    Text(
                        text = String.format(Locale.US, "%.2f km", trip.distanceKm),
                        color = NeonCyan,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Column {
                    Text("Tiempo", color = TextSecondary, fontSize = 10.sp)
                    Text(
                        text = timeFormatted,
                        color = RacingGreen,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Column {
                    Text("Vel. Máx", color = TextSecondary, fontSize = 10.sp)
                    Text(
                        text = String.format(Locale.US, "%.0f km/h", trip.maxSpeedKmh),
                        color = RacingRed,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Column {
                    Text("Vel. Prom", color = TextSecondary, fontSize = 10.sp)
                    Text(
                        text = String.format(Locale.US, "%.0f km/h", trip.avgSpeedKmh),
                        color = SpeedAmber,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}

@Composable
fun EmptyTripsState(onStartNewRide: () -> Unit) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = CockpitSurface),
        border = androidx.compose.foundation.BorderStroke(1.dp, CockpitCardBorder),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)
    ) {
        Column(
            modifier = Modifier.padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(NeonCyan.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Route,
                    contentDescription = "Sin rutas",
                    tint = NeonCyan,
                    modifier = Modifier.size(32.dp)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "Aún no tienes rutas guardadas",
                color = TextPrimary,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Presiona 'Iniciar Ruta' en el velocímetro para registrar tu velocidad, mapa GPS y tiempos.",
                color = TextSecondary,
                fontSize = 13.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = onStartNewRide,
                colors = ButtonDefaults.buttonColors(containerColor = RacingGreen, contentColor = Color.Black),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Ir al Velocímetro", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun TripDetailDialog(
    trip: TripEntity,
    routePoints: List<RoutePoint>,
    onDismiss: () -> Unit,
    onDelete: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = CockpitSurface),
            border = androidx.compose.foundation.BorderStroke(1.dp, CockpitCardBorder),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp)
                .testTag("trip_detail_dialog")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = trip.title,
                            color = NeonCyan,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = SimpleDateFormat("dd MMMM yyyy - HH:mm", Locale("es", "MX")).format(Date(trip.startTime)),
                            color = TextSecondary,
                            fontSize = 11.sp
                        )
                    }

                    IconButton(onClick = onDelete) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Eliminar",
                            tint = RacingRed,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Route Map Visualization
                val centerLat = if (routePoints.isNotEmpty()) routePoints[routePoints.size / 2].latitude else 19.4326
                val centerLon = if (routePoints.isNotEmpty()) routePoints[routePoints.size / 2].longitude else -99.1332

                LiveRouteMap(
                    currentLat = centerLat,
                    currentLon = centerLon,
                    heading = 0f,
                    currentSpeedKmh = 0f,
                    routePoints = routePoints,
                    isRecording = false,
                    showHeatmap = true,
                    isInteractive = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Stats Grid
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        color = CockpitCardBorder.copy(alpha = 0.35f),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Text("Distancia", color = TextSecondary, fontSize = 10.sp)
                            Text(
                                String.format(Locale.US, "%.2f km", trip.distanceKm),
                                color = NeonCyan,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Surface(
                        color = CockpitCardBorder.copy(alpha = 0.35f),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Text("Duración", color = TextSecondary, fontSize = 10.sp)
                            val min = trip.durationSeconds / 60
                            val sec = trip.durationSeconds % 60
                            Text(
                                "${min}m ${sec}s",
                                color = RacingGreen,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        color = CockpitCardBorder.copy(alpha = 0.35f),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Text("Velocidad Máx", color = TextSecondary, fontSize = 10.sp)
                            Text(
                                String.format(Locale.US, "%.1f km/h", trip.maxSpeedKmh),
                                color = RacingRed,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Surface(
                        color = CockpitCardBorder.copy(alpha = 0.35f),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Text("Velocidad Prom", color = TextSecondary, fontSize = 10.sp)
                            Text(
                                String.format(Locale.US, "%.1f km/h", trip.avgSpeedKmh),
                                color = SpeedAmber,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = Color.Black),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Cerrar Detalle", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
