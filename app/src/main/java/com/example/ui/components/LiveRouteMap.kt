package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTransformGestures
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.LocationSearching
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Remove
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
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.RoutePoint
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
import kotlin.math.max
import kotlin.math.min

enum class MapTheme {
    COCKPIT_DARK,
    STREET_GRID,
    SATELLITE_EMERALD
}

@Composable
fun LiveRouteMap(
    currentLat: Double,
    currentLon: Double,
    heading: Float = 0f,
    currentSpeedKmh: Float = 0f,
    routePoints: List<RoutePoint> = emptyList(),
    isRecording: Boolean = false,
    showHeatmap: Boolean = true,
    isInteractive: Boolean = true,
    modifier: Modifier = Modifier
) {
    var zoomScale by remember { mutableFloatStateOf(1f) }
    var panOffsetX by remember { mutableFloatStateOf(0f) }
    var panOffsetY by remember { mutableFloatStateOf(0f) }
    var mapTheme by remember { mutableStateOf(MapTheme.COCKPIT_DARK) }

    // Map background styling
    val mapBgColor = when (mapTheme) {
        MapTheme.COCKPIT_DARK -> Color(0xFF0D121B)
        MapTheme.STREET_GRID -> Color(0xFF141D2B)
        MapTheme.SATELLITE_EMERALD -> Color(0xFF091416)
    }

    val gridColor = when (mapTheme) {
        MapTheme.COCKPIT_DARK -> Color(0xFF1B2436)
        MapTheme.STREET_GRID -> Color(0xFF23324A)
        MapTheme.SATELLITE_EMERALD -> Color(0xFF132B2A)
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(mapBgColor)
            .border(1.dp, CockpitCardBorder, RoundedCornerShape(16.dp))
            .testTag("route_map_container")
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(isInteractive) {
                    if (isInteractive) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            zoomScale = (zoomScale * zoom).coerceIn(0.5f, 6.0f)
                            panOffsetX += pan.x
                            panOffsetY += pan.y
                        }
                    }
                }
        ) {
            val canvasWidth = size.width
            val canvasHeight = size.height
            val center = Offset(canvasWidth / 2f + panOffsetX, canvasHeight / 2f + panOffsetY)

            // Draw GPS Road Grid / Tile lines
            val gridSize = 40.dp.toPx() * zoomScale
            var x = (center.x % gridSize)
            while (x < canvasWidth) {
                drawLine(
                    color = gridColor,
                    start = Offset(x, 0f),
                    end = Offset(x, canvasHeight),
                    strokeWidth = 1.dp.toPx()
                )
                x += gridSize
            }
            var y = (center.y % gridSize)
            while (y < canvasHeight) {
                drawLine(
                    color = gridColor,
                    start = Offset(0f, y),
                    end = Offset(canvasWidth, y),
                    strokeWidth = 1.dp.toPx()
                )
                y += gridSize
            }

            // Geographic Coordinate Projection (Mercator-like local flat projection)
            // 1 degree lat approx 111,000 meters.
            // Scale factor to map pixels
            val basePixelsPerDegree = 80000.0 * zoomScale

            fun project(lat: Double, lon: Double): Offset {
                val dx = (lon - currentLon) * cos(Math.toRadians(currentLat)) * basePixelsPerDegree
                val dy = -(lat - currentLat) * basePixelsPerDegree
                return Offset(
                    x = (center.x + dx).toFloat(),
                    y = (center.y + dy).toFloat()
                )
            }

            // Draw Recorded Route Path
            if (routePoints.size > 1) {
                // Route glow shadow
                val fullPath = Path()
                val firstPt = project(routePoints[0].latitude, routePoints[0].longitude)
                fullPath.moveTo(firstPt.x, firstPt.y)

                for (i in 1 until routePoints.size) {
                    val pt = project(routePoints[i].latitude, routePoints[i].longitude)
                    fullPath.lineTo(pt.x, pt.y)
                }

                drawPath(
                    path = fullPath,
                    color = NeonCyan.copy(alpha = 0.2f),
                    style = Stroke(
                        width = 12.dp.toPx(),
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                )

                // Segmented Speed Heatmap Lines
                for (i in 0 until routePoints.size - 1) {
                    val p1 = routePoints[i]
                    val p2 = routePoints[i + 1]
                    val o1 = project(p1.latitude, p1.longitude)
                    val o2 = project(p2.latitude, p2.longitude)

                    val segmentSpeed = (p1.speedKmh + p2.speedKmh) / 2f
                    val segmentColor = if (showHeatmap) {
                        when {
                            segmentSpeed < 40f -> RacingGreen
                            segmentSpeed < 80f -> SpeedAmber
                            segmentSpeed < 120f -> ElectricOrange
                            else -> RacingRed
                        }
                    } else {
                        NeonCyan
                    }

                    drawLine(
                        color = segmentColor,
                        start = o1,
                        end = o2,
                        strokeWidth = 5.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }

                // Draw Start Flag Pin
                val startScreenPos = project(routePoints.first().latitude, routePoints.first().longitude)
                drawCircle(
                    color = Color.Black,
                    radius = 9.dp.toPx(),
                    center = startScreenPos
                )
                drawCircle(
                    color = RacingGreen,
                    radius = 7.dp.toPx(),
                    center = startScreenPos
                )
                drawCircle(
                    color = Color.White,
                    radius = 3.dp.toPx(),
                    center = startScreenPos
                )

                val paint = android.graphics.Paint().apply {
                    color = TextPrimary.toArgb()
                    textSize = 10.sp.toPx()
                    textAlign = android.graphics.Paint.Align.CENTER
                    isFakeBoldText = true
                }
                drawContext.canvas.nativeCanvas.drawText(
                    "INICIO",
                    startScreenPos.x,
                    startScreenPos.y - 12.dp.toPx(),
                    paint
                )
            }

            // Draw Current Motorcycle Location & Orientation Pin
            val currentScreenPos = project(currentLat, currentLon)

            // Radar pulse ring when recording
            if (isRecording) {
                drawCircle(
                    color = NeonCyan.copy(alpha = 0.25f),
                    radius = 24.dp.toPx() * zoomScale.coerceIn(0.8f, 1.5f),
                    center = currentScreenPos
                )
            }

            // Outer ring
            drawCircle(
                color = CockpitSurface,
                radius = 14.dp.toPx(),
                center = currentScreenPos
            )
            drawCircle(
                color = if (isRecording) NeonCyan else SpeedAmber,
                radius = 12.dp.toPx(),
                center = currentScreenPos
            )

            // Direction Heading Arrow
            rotate(degrees = heading, pivot = currentScreenPos) {
                val arrowPath = Path().apply {
                    moveTo(currentScreenPos.x, currentScreenPos.y - 10.dp.toPx())
                    lineTo(currentScreenPos.x + 6.dp.toPx(), currentScreenPos.y + 6.dp.toPx())
                    lineTo(currentScreenPos.x, currentScreenPos.y + 3.dp.toPx())
                    lineTo(currentScreenPos.x - 6.dp.toPx(), currentScreenPos.y + 6.dp.toPx())
                    close()
                }
                drawPath(
                    path = arrowPath,
                    color = Color.Black
                )
            }
        }

        // Floating Info Badge on Top-Left
        Surface(
            color = CockpitSurface.copy(alpha = 0.88f),
            shape = RoundedCornerShape(10.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, CockpitCardBorder),
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(if (isRecording) RacingGreen else SpeedAmber)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (isRecording) "Ruta GPS en Vivo" else "Mapa GPS",
                    color = TextPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                if (routePoints.isNotEmpty()) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "${routePoints.size} pts",
                        color = TextSecondary,
                        fontSize = 11.sp
                    )
                }
            }
        }

        // Floating Map Controls (Zoom +, Zoom -, Center, Style)
        if (isInteractive) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Re-center on Motorcycle
                IconButton(
                    onClick = {
                        panOffsetX = 0f
                        panOffsetY = 0f
                        zoomScale = 1.2f
                    },
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = CockpitSurface.copy(alpha = 0.9f),
                        contentColor = NeonCyan
                    ),
                    modifier = Modifier
                        .size(38.dp)
                        .border(1.dp, CockpitCardBorder, CircleShape)
                        .testTag("map_recenter_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.MyLocation,
                        contentDescription = "Centrar en moto",
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Zoom In
                IconButton(
                    onClick = { zoomScale = (zoomScale * 1.3f).coerceAtMost(6.0f) },
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = CockpitSurface.copy(alpha = 0.9f),
                        contentColor = TextPrimary
                    ),
                    modifier = Modifier
                        .size(38.dp)
                        .border(1.dp, CockpitCardBorder, CircleShape)
                        .testTag("map_zoom_in_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Acercar mapa",
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Zoom Out
                IconButton(
                    onClick = { zoomScale = (zoomScale / 1.3f).coerceAtLeast(0.5f) },
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = CockpitSurface.copy(alpha = 0.9f),
                        contentColor = TextPrimary
                    ),
                    modifier = Modifier
                        .size(38.dp)
                        .border(1.dp, CockpitCardBorder, CircleShape)
                        .testTag("map_zoom_out_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Remove,
                        contentDescription = "Alejar mapa",
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Map Style Switcher
                IconButton(
                    onClick = {
                        mapTheme = when (mapTheme) {
                            MapTheme.COCKPIT_DARK -> MapTheme.STREET_GRID
                            MapTheme.STREET_GRID -> MapTheme.SATELLITE_EMERALD
                            MapTheme.SATELLITE_EMERALD -> MapTheme.COCKPIT_DARK
                        }
                    },
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = CockpitSurface.copy(alpha = 0.9f),
                        contentColor = ElectricOrange
                    ),
                    modifier = Modifier
                        .size(38.dp)
                        .border(1.dp, CockpitCardBorder, CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Layers,
                        contentDescription = "Cambiar estilo de mapa",
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        // Speed Heatmap Legend (Bottom-Left)
        if (showHeatmap && routePoints.isNotEmpty()) {
            Surface(
                color = CockpitSurface.copy(alpha = 0.85f),
                shape = RoundedCornerShape(8.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, CockpitCardBorder),
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(text = "Velocidad:", color = TextSecondary, fontSize = 10.sp)
                    Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(RacingGreen))
                    Text(text = "<40", color = TextPrimary, fontSize = 9.sp)
                    Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(SpeedAmber))
                    Text(text = "40-80", color = TextPrimary, fontSize = 9.sp)
                    Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(RacingRed))
                    Text(text = "100+", color = TextPrimary, fontSize = 9.sp)
                }
            }
        }
    }
}
