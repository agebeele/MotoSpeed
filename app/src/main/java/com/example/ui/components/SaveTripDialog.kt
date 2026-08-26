package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.location.GpsState
import com.example.ui.theme.CockpitCardBorder
import com.example.ui.theme.CockpitSurface
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
fun SaveTripDialog(
    gpsState: GpsState,
    onDismiss: () -> Unit,
    onSave: (title: String) -> Unit,
    onDiscard: () -> Unit
) {
    val defaultTitle = remember {
        val dateStr = SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault()).format(Date())
        "Recorrido en Moto ($dateStr)"
    }
    var titleText by remember { mutableStateOf(defaultTitle) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = CockpitSurface),
            border = androidx.compose.foundation.BorderStroke(1.dp, CockpitCardBorder),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .testTag("save_trip_dialog")
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "🏍️ ¡Ruta Completada!",
                    color = NeonCyan,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Stats summary
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(CockpitCardBorder.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Distancia:", color = TextSecondary, fontSize = 13.sp)
                        Text(
                            String.format(Locale.US, "%.2f km", gpsState.distanceKm),
                            color = TextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Vel. Máxima:", color = TextSecondary, fontSize = 13.sp)
                        Text(
                            String.format(Locale.US, "%.1f km/h", gpsState.maxSpeedKmh),
                            color = RacingRed,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Vel. Promedio:", color = TextSecondary, fontSize = 13.sp)
                        Text(
                            String.format(Locale.US, "%.1f km/h", gpsState.avgSpeedKmh),
                            color = SpeedAmber,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Puntos GPS:", color = TextSecondary, fontSize = 13.sp)
                        Text(
                            "${gpsState.routePoints.size} registrados",
                            color = RacingGreen,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = titleText,
                    onValueChange = { titleText = it },
                    label = { Text("Nombre de la Ruta") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedBorderColor = NeonCyan,
                        unfocusedBorderColor = CockpitCardBorder,
                        focusedLabelColor = NeonCyan,
                        unfocusedLabelColor = TextSecondary
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("trip_title_input")
                )

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDiscard,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = RacingRed),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("discard_trip_button")
                    ) {
                        Text("Descartar")
                    }

                    Button(
                        onClick = { onSave(titleText.ifBlank { defaultTitle }) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = NeonCyan,
                            contentColor = androidx.compose.ui.graphics.Color.Black
                        ),
                        modifier = Modifier
                            .weight(1.3f)
                            .testTag("confirm_save_trip_button")
                    ) {
                        Text("Guardar Ruta", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
