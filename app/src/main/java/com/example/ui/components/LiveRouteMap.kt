package com.example.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.Rect
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CompassCalibration
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.LocationSearching
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.SignalCellularOff
import androidx.compose.material.icons.filled.ZoomOutMap
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.RoutePoint
import com.example.ui.map.MapTileManager
import com.example.ui.map.MapTileProvider
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
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

@Composable
fun LiveRouteMap(
    currentLat: Double,
    currentLon: Double,
    heading: Float = 0f,
    currentSpeedKmh: Float = 0f,
    routePoints: List<RoutePoint> = emptyList(),
    sessionBreadcrumbs: List<RoutePoint> = emptyList(),
    isRecording: Boolean = false,
    showHeatmap: Boolean = true,
    isInteractive: Boolean = true,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // Active Points to draw: use official trip points if available, or session breadcrumbs if just riding around
    val activePoints = when {
        routePoints.size > 1 -> routePoints
        sessionBreadcrumbs.size > 1 -> sessionBreadcrumbs
        else -> routePoints
    }

    // 4 Map Styles requested: Google Híbrido, Google Maps Calles, Atlas Voxel, Deepslate
    var tileProvider by remember { mutableStateOf(MapTileProvider.GOOGLE_HYBRID) }
    var showLayersDialog by remember { mutableStateOf(false) }

    // Offline download state
    var isDownloadingOffline by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableStateOf(0f) }
    var downloadStatusText by remember { mutableStateOf("") }

    // Trigger state to force Canvas redraw when tiles finish downloading
    var tileRedrawTrigger by remember { mutableIntStateOf(0) }

    // Check online status periodically
    var isDeviceOnline by remember { mutableStateOf(MapTileManager.isOnline(context)) }
    LaunchedEffect(Unit) {
        while (true) {
            isDeviceOnline = MapTileManager.isOnline(context)
            kotlinx.coroutines.delay(4000L)
        }
    }

    // Initial positioning fallback (CDMX if 0.0)
    val initLat = when {
        currentLat != 0.0 -> currentLat
        activePoints.isNotEmpty() -> activePoints.last().latitude
        else -> 19.4326
    }
    val initLon = when {
        currentLon != 0.0 -> currentLon
        activePoints.isNotEmpty() -> activePoints.last().longitude
        else -> -99.1332
    }

    // Navigation and pan/zoom state
    var isFollowingUser by remember { mutableStateOf(true) }
    var zoomLevel by remember { mutableFloatStateOf(15.6f) }
    var mapCenterLat by remember { mutableStateOf(initLat) }
    var mapCenterLon by remember { mutableStateOf(initLon) }
    var panOffsetX by remember { mutableFloatStateOf(0f) }
    var panOffsetY by remember { mutableFloatStateOf(0f) }

    // Auto-fit to route if this is viewing a saved route
    LaunchedEffect(activePoints.size, isRecording) {
        if (!isRecording && activePoints.size > 1) {
            val minLat = activePoints.minOf { it.latitude }
            val maxLat = activePoints.maxOf { it.latitude }
            val minLon = activePoints.minOf { it.longitude }
            val maxLon = activePoints.maxOf { it.longitude }
            mapCenterLat = (minLat + maxLat) / 2.0
            mapCenterLon = (minLon + maxLon) / 2.0
            panOffsetX = 0f
            panOffsetY = 0f
            isFollowingUser = false

            val fitZ = MapTileManager.calculateFitZoom(
                minLat, maxLat, minLon, maxLon,
                viewWidthPx = 800f, viewHeightPx = 800f, paddingPx = 80f
            )
            zoomLevel = fitZ.coerceIn(10.5f, 17f)
        }
    }

    // Keep map centered on user when following
    LaunchedEffect(currentLat, currentLon, isFollowingUser) {
        if (isFollowingUser && (currentLat != 0.0 || currentLon != 0.0)) {
            mapCenterLat = currentLat
            mapCenterLon = currentLon
            panOffsetX = 0f
            panOffsetY = 0f
        }
    }

    fun fitWholeRoute(canvasW: Float, canvasH: Float) {
        if (activePoints.size > 1) {
            val minLat = activePoints.minOf { it.latitude }
            val maxLat = activePoints.maxOf { it.latitude }
            val minLon = activePoints.minOf { it.longitude }
            val maxLon = activePoints.maxOf { it.longitude }
            mapCenterLat = (minLat + maxLat) / 2.0
            mapCenterLon = (minLon + maxLon) / 2.0
            panOffsetX = 0f
            panOffsetY = 0f
            isFollowingUser = false

            val fitZ = MapTileManager.calculateFitZoom(
                minLat, maxLat, minLon, maxLon,
                viewWidthPx = max(300f, canvasW),
                viewHeightPx = max(300f, canvasH),
                paddingPx = 70f
            )
            zoomLevel = fitZ.coerceIn(10.5f, 17.5f)
        }
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(CockpitBackground)
            .border(1.dp, CockpitCardBorder, RoundedCornerShape(16.dp))
            .testTag("route_map_container")
    ) {
        var currentCanvasW by remember { mutableFloatStateOf(800f) }
        var currentCanvasH by remember { mutableFloatStateOf(800f) }

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(isInteractive) {
                    if (isInteractive) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            isFollowingUser = false
                            zoomLevel = (zoomLevel + (zoom - 1f) * 2.8f).coerceIn(4f, 18.5f)
                            panOffsetX += pan.x
                            panOffsetY += pan.y
                        }
                    }
                }
        ) {
            val trigger = tileRedrawTrigger
            val canvasW = size.width
            val canvasH = size.height
            if (canvasW <= 0f || canvasH <= 0f) return@Canvas
            currentCanvasW = canvasW
            currentCanvasH = canvasH

            // 1. Math Setup for Web Mercator Tiles
            val intZoom = floor(zoomLevel).toInt().coerceIn(4, 18)
            val subZoomFactor = 2.0.pow((zoomLevel - intZoom).toDouble()).toFloat()
            val tileSizeOnScreen = 256f * subZoomFactor

            val centerWorldX = MapTileManager.lonToWorldPixel(mapCenterLon, intZoom.toFloat())
            val centerWorldY = MapTileManager.latToWorldPixel(mapCenterLat, intZoom.toFloat())

            val screenCenterX = canvasW / 2f + panOffsetX
            val screenCenterY = canvasH / 2f + panOffsetY

            fun project(lat: Double, lon: Double): Offset {
                val wx = MapTileManager.lonToWorldPixel(lon, intZoom.toFloat())
                val wy = MapTileManager.latToWorldPixel(lat, intZoom.toFloat())
                val sx = screenCenterX + (wx - centerWorldX).toFloat() * subZoomFactor
                val sy = screenCenterY + (wy - centerWorldY).toFloat() * subZoomFactor
                return Offset(sx, sy)
            }

            // Tile Bounds Calculation
            val minWorldX = centerWorldX - screenCenterX / subZoomFactor
            val maxWorldX = centerWorldX + (canvasW - screenCenterX) / subZoomFactor
            val minWorldY = centerWorldY - screenCenterY / subZoomFactor
            val maxWorldY = centerWorldY + (canvasH - screenCenterY) / subZoomFactor

            val totalTilesAtZ = 1 shl intZoom
            val minTileX = floor(minWorldX / 256.0).toInt().coerceIn(0, totalTilesAtZ - 1)
            val maxTileX = floor(maxWorldX / 256.0).toInt().coerceIn(0, totalTilesAtZ - 1)
            val minTileY = floor(minWorldY / 256.0).toInt().coerceIn(0, totalTilesAtZ - 1)
            val maxTileY = floor(maxWorldY / 256.0).toInt().coerceIn(0, totalTilesAtZ - 1)

            val baseBgColor = android.graphics.Color.parseColor(tileProvider.bgColorHex)
            val gridLineColor = android.graphics.Color.parseColor(tileProvider.gridColorHex)

            val placeholderPaint = Paint().apply { color = baseBgColor }
            val gridPaint = Paint().apply {
                color = gridLineColor
                style = Paint.Style.STROKE
                strokeWidth = 1.2f
                isAntiAlias = true
            }

            val tacticalCoordPaint = Paint().apply {
                color = android.graphics.Color.parseColor("#4A5D78")
                textSize = 10f.sp.toPx()
                isAntiAlias = true
            }

            // Draw Base Background
            drawRect(color = Color(baseBgColor))

            // Draw Map Tiles (Google Hybrid, Google Streets, Atlas Voxel, Deepslate)
            for (tx in minTileX..maxTileX) {
                for (ty in minTileY..maxTileY) {
                    val tileWorldX = tx * 256.0
                    val tileWorldY = ty * 256.0

                    val tileScreenX = screenCenterX + (tileWorldX - centerWorldX).toFloat() * subZoomFactor
                    val tileScreenY = screenCenterY + (tileWorldY - centerWorldY).toFloat() * subZoomFactor
                    val tileW = tileSizeOnScreen
                    val tileH = tileSizeOnScreen

                    val bitmap = MapTileManager.getOrFetchTile(
                        context = context,
                        scope = coroutineScope,
                        provider = tileProvider,
                        z = intZoom,
                        x = tx,
                        y = ty,
                        onTileLoaded = {
                            tileRedrawTrigger++
                        }
                    )

                    if (bitmap != null && !bitmap.isRecycled) {
                        drawIntoCanvas { canvas ->
                            val srcRect = Rect(0, 0, bitmap.width, bitmap.height)
                            val dstRect = Rect(
                                tileScreenX.roundToInt(),
                                tileScreenY.roundToInt(),
                                (tileScreenX + tileW).roundToInt(),
                                (tileScreenY + tileH).roundToInt()
                            )
                            canvas.nativeCanvas.drawBitmap(bitmap, srcRect, dstRect, null)
                        }
                    } else {
                        // Tactical Offline Vector Grid for missing tiles
                        drawIntoCanvas { canvas ->
                            val dstRect = Rect(
                                tileScreenX.roundToInt(),
                                tileScreenY.roundToInt(),
                                (tileScreenX + tileW).roundToInt(),
                                (tileScreenY + tileH).roundToInt()
                            )
                            canvas.nativeCanvas.drawRect(dstRect, placeholderPaint)
                            canvas.nativeCanvas.drawRect(dstRect, gridPaint)

                            // Show tactical coordinate marks so rider has orientation even without internet
                            val tileLat = MapTileManager.worldPixelToLat(tileWorldY, intZoom.toFloat())
                            val tileLon = MapTileManager.worldPixelToLon(tileWorldX, intZoom.toFloat())
                            val coordText = String.format(Locale.US, "%.3f°, %.3f°", tileLat, tileLon)
                            canvas.nativeCanvas.drawText(
                                coordText,
                                tileScreenX + 8f,
                                tileScreenY + 22f,
                                tacticalCoordPaint
                            )
                        }
                    }
                }
            }

            // 2. Draw Google Maps-Style Route Polyline Directly on the Map
            if (activePoints.size > 1) {
                // A) Contrast outer casing border (makes route stand out crisply like Google Maps)
                val fullPath = Path()
                val firstPt = project(activePoints[0].latitude, activePoints[0].longitude)
                fullPath.moveTo(firstPt.x, firstPt.y)

                for (i in 1 until activePoints.size) {
                    val pt = project(activePoints[i].latitude, activePoints[i].longitude)
                    fullPath.lineTo(pt.x, pt.y)
                }

                // High contrast dark/halo casing (11dp)
                drawPath(
                    path = fullPath,
                    color = Color(0xFF060B12).copy(alpha = 0.85f),
                    style = Stroke(
                        width = 11.dp.toPx(),
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                )

                // High visibility neon glow under route
                drawPath(
                    path = fullPath,
                    color = NeonCyan.copy(alpha = 0.45f),
                    style = Stroke(
                        width = 8.5.dp.toPx(),
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                )

                // B) Route speed heatmap segments (Google Maps Style road core)
                var accumulatedMeters = 0.0
                var nextMilestoneKm = 1

                for (i in 0 until activePoints.size - 1) {
                    val p1 = activePoints[i]
                    val p2 = activePoints[i + 1]
                    val o1 = project(p1.latitude, p1.longitude)
                    val o2 = project(p2.latitude, p2.longitude)

                    val segmentSpeed = (p1.speedKmh + p2.speedKmh) / 2f
                    val segmentColor = if (showHeatmap) {
                        when {
                            segmentSpeed < 40f -> RacingGreen      // Green for city/chill
                            segmentSpeed < 80f -> SpeedAmber       // Amber for cruising
                            segmentSpeed < 120f -> ElectricOrange   // Orange for fast
                            else -> RacingRed                      // Red for high speed
                        }
                    } else {
                        NeonCyan
                    }

                    // Main road polyline segment
                    drawLine(
                        color = segmentColor,
                        start = o1,
                        end = o2,
                        strokeWidth = 5.5.dp.toPx(),
                        cap = StrokeCap.Round
                    )

                    // Direction Chevron Arrow every 12 segments
                    if (i % 12 == 6) {
                        val dx = o2.x - o1.x
                        val dy = o2.y - o1.y
                        val segLen = kotlin.math.sqrt(dx * dx + dy * dy)
                        if (segLen > 15f) {
                            val midX = (o1.x + o2.x) / 2f
                            val midY = (o1.y + o2.y) / 2f
                            val angleRad = atan2(dy.toDouble(), dx.toDouble())
                            val angleDeg = Math.toDegrees(angleRad).toFloat()

                            rotate(degrees = angleDeg, pivot = Offset(midX, midY)) {
                                val arrowPath = Path().apply {
                                    moveTo(midX + 5.dp.toPx(), midY)
                                    lineTo(midX - 4.dp.toPx(), midY - 4.dp.toPx())
                                    lineTo(midX - 2.dp.toPx(), midY)
                                    lineTo(midX - 4.dp.toPx(), midY + 4.dp.toPx())
                                    close()
                                }
                                drawPath(path = arrowPath, color = Color.White.copy(alpha = 0.9f))
                            }
                        }
                    }

                    // Approximate distance calculation between points
                    val dLat = Math.toRadians(p2.latitude - p1.latitude)
                    val dLon = Math.toRadians(p2.longitude - p1.longitude)
                    val a = sin(dLat / 2).pow(2.0) +
                            cos(Math.toRadians(p1.latitude)) * cos(Math.toRadians(p2.latitude)) *
                            sin(dLon / 2).pow(2.0)
                    val segDistMeters = 6371000.0 * 2.0 * atan2(kotlin.math.sqrt(a), kotlin.math.sqrt(1.0 - a))
                    accumulatedMeters += segDistMeters

                    // Distance milestone chips (1 km, 2 km, 5 km...)
                    val currentKm = (accumulatedMeters / 1000.0).toInt()
                    if (currentKm >= nextMilestoneKm) {
                        nextMilestoneKm = currentKm + 1
                        drawCircle(color = Color.Black, radius = 9.dp.toPx(), center = o2)
                        drawCircle(color = NeonCyan, radius = 7.dp.toPx(), center = o2)
                        drawCircle(color = Color.White, radius = 3.dp.toPx(), center = o2)
                    }
                }

                // INICIO (Start) Pin (Google Maps Style Green Pin with "A")
                val startPt = project(activePoints.first().latitude, activePoints.first().longitude)
                drawCircle(color = Color.Black.copy(alpha = 0.6f), radius = 18.dp.toPx(), center = startPt.copy(y = startPt.y + 3f))
                drawCircle(color = Color.Black, radius = 14.dp.toPx(), center = startPt)
                drawCircle(color = RacingGreen, radius = 11.dp.toPx(), center = startPt)
                drawCircle(color = Color.White, radius = 5.dp.toPx(), center = startPt)

                // FIN (End) Pin
                if (!isRecording) {
                    val endPt = project(activePoints.last().latitude, activePoints.last().longitude)
                    drawCircle(color = Color.Black.copy(alpha = 0.6f), radius = 18.dp.toPx(), center = endPt.copy(y = endPt.y + 3f))
                    drawCircle(color = Color.Black, radius = 14.dp.toPx(), center = endPt)
                    drawCircle(color = RacingRed, radius = 11.dp.toPx(), center = endPt)
                    drawCircle(color = Color.White, radius = 5.dp.toPx(), center = endPt)
                }
            }

            // 3. Draw Live Motorcycle Beacon Marker
            val riderLat = if (currentLat != 0.0) currentLat else mapCenterLat
            val riderLon = if (currentLon != 0.0) currentLon else mapCenterLon
            val userScreenPos = project(riderLat, riderLon)

            val inView = userScreenPos.x in -60f..(canvasW + 60f) &&
                    userScreenPos.y in -60f..(canvasH + 60f)

            if (inView) {
                // Radar sweep circle
                drawCircle(
                    color = NeonCyan.copy(alpha = 0.22f),
                    radius = 30.dp.toPx(),
                    center = userScreenPos
                )
                // Halo border
                drawCircle(
                    color = Color.Black.copy(alpha = 0.75f),
                    radius = 16.dp.toPx(),
                    center = userScreenPos,
                    style = Stroke(width = 3.dp.toPx())
                )
                drawCircle(
                    color = NeonCyan,
                    radius = 13.dp.toPx(),
                    center = userScreenPos,
                    style = Stroke(width = 2.5.dp.toPx())
                )
                // Inner core
                drawCircle(
                    color = CockpitBackground,
                    radius = 10.dp.toPx(),
                    center = userScreenPos
                )
                drawCircle(
                    color = NeonCyan,
                    radius = 6.dp.toPx(),
                    center = userScreenPos
                )

                // Directional Heading Indicator (Motorcycle cockpit arrow)
                rotate(degrees = heading, pivot = userScreenPos) {
                    val arrowPath = Path().apply {
                        moveTo(userScreenPos.x, userScreenPos.y - 22.dp.toPx())
                        lineTo(userScreenPos.x - 8.dp.toPx(), userScreenPos.y - 8.dp.toPx())
                        lineTo(userScreenPos.x + 8.dp.toPx(), userScreenPos.y - 8.dp.toPx())
                        close()
                    }
                    drawPath(path = arrowPath, color = ElectricOrange)
                }
            }

            // Tactical Scale Bar in bottom right corner
            val scaleMeters = 500.0 / (2.0.pow((zoomLevel - 15.0).toDouble()))
            val scalePx = (scaleMeters / 156543.03392 * 2.0.pow(zoomLevel.toDouble()) * cos(Math.toRadians(riderLat))).toFloat()
            val scaleBarW = scalePx.coerceIn(40.dp.toPx(), 110.dp.toPx())
            val scaleX = canvasW - scaleBarW - 16.dp.toPx()
            val scaleY = canvasH - 18.dp.toPx()

            drawLine(
                color = TextSecondary.copy(alpha = 0.8f),
                start = Offset(scaleX, scaleY),
                end = Offset(scaleX + scaleBarW, scaleY),
                strokeWidth = 2.dp.toPx()
            )
            drawLine(
                color = TextSecondary.copy(alpha = 0.8f),
                start = Offset(scaleX, scaleY - 4.dp.toPx()),
                end = Offset(scaleX, scaleY + 4.dp.toPx()),
                strokeWidth = 2.dp.toPx()
            )
            drawLine(
                color = TextSecondary.copy(alpha = 0.8f),
                start = Offset(scaleX + scaleBarW, scaleY - 4.dp.toPx()),
                end = Offset(scaleX + scaleBarW, scaleY + 4.dp.toPx()),
                strokeWidth = 2.dp.toPx()
            )
        }

        // Top Status Header: Offline / Live Satellite Pill & Active Layer Badge
        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Active Layer Badge Chip
            Surface(
                color = CockpitSurface.copy(alpha = 0.94f),
                shape = RoundedCornerShape(18.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, CockpitCardBorder),
                modifier = Modifier.clickable { showLayersDialog = true }
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Layers,
                        contentDescription = "Capa de mapa",
                        tint = ElectricOrange,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = tileProvider.displayName,
                        color = TextPrimary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // Offline Indicator Pill when internet is unavailable
            if (!isDeviceOnline) {
                Surface(
                    color = CockpitSurface.copy(alpha = 0.95f),
                    shape = RoundedCornerShape(18.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, SpeedAmber.copy(alpha = 0.6f))
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.SignalCellularOff,
                            contentDescription = "Sin conexión a internet",
                            tint = SpeedAmber,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = "Modo Offline (GPS Satelital)",
                            color = SpeedAmber,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            } else if (activePoints.isNotEmpty()) {
                Surface(
                    color = CockpitSurface.copy(alpha = 0.92f),
                    shape = RoundedCornerShape(18.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, NeonCyan.copy(alpha = 0.4f))
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(RacingGreen)
                        )
                        Text(
                            text = "${activePoints.size} pts ruta",
                            color = NeonCyan,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }

        // Floating Map Controls (Center-End)
        if (isInteractive) {
            Column(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Re-center on Motorcycle
                IconButton(
                    onClick = {
                        isFollowingUser = true
                        panOffsetX = 0f
                        panOffsetY = 0f
                        if (currentLat != 0.0 || currentLon != 0.0) {
                            mapCenterLat = currentLat
                            mapCenterLon = currentLon
                        }
                    },
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = if (isFollowingUser) NeonCyan.copy(alpha = 0.95f) else CockpitSurface.copy(alpha = 0.92f),
                        contentColor = if (isFollowingUser) CockpitBackground else TextPrimary
                    ),
                    modifier = Modifier
                        .size(38.dp)
                        .border(1.dp, CockpitCardBorder, CircleShape)
                        .testTag("map_recenter_button")
                ) {
                    Icon(
                        imageVector = if (isFollowingUser) Icons.Default.MyLocation else Icons.Default.LocationSearching,
                        contentDescription = "Centrar en mi ubicación",
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Fit Complete Route on Screen
                if (activePoints.size > 1) {
                    IconButton(
                        onClick = {
                            fitWholeRoute(currentCanvasW, currentCanvasH)
                        },
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = CockpitSurface.copy(alpha = 0.92f),
                            contentColor = NeonCyan
                        ),
                        modifier = Modifier
                            .size(38.dp)
                            .border(1.dp, NeonCyan.copy(alpha = 0.5f), CircleShape)
                            .testTag("map_fit_route_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.ZoomOutMap,
                            contentDescription = "Ver toda la ruta",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // Zoom In
                IconButton(
                    onClick = { zoomLevel = (zoomLevel + 0.8f).coerceAtMost(18.5f) },
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = CockpitSurface.copy(alpha = 0.92f),
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
                    onClick = { zoomLevel = (zoomLevel - 0.8f).coerceAtLeast(4f) },
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = CockpitSurface.copy(alpha = 0.92f),
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

                // Map Style Switcher Button
                IconButton(
                    onClick = { showLayersDialog = true },
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = CockpitSurface.copy(alpha = 0.92f),
                        contentColor = ElectricOrange
                    ),
                    modifier = Modifier
                        .size(38.dp)
                        .border(1.dp, CockpitCardBorder, CircleShape)
                        .testTag("map_layers_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Layers,
                        contentDescription = "Cambiar capa de mapa",
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        // Layer Selection Dialog / Popup with Offline Pre-caching Button
        AnimatedVisibility(
            visible = showLayersDialog,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = CockpitSurface),
                shape = RoundedCornerShape(18.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, CockpitCardBorder),
                modifier = Modifier
                    .padding(20.dp)
                    .fillMaxWidth(0.94f)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Capas de Mapa",
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        IconButton(
                            onClick = { showLayersDialog = false },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Cerrar",
                                tint = TextSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    MapTileProvider.entries.forEach { provider ->
                        val isSelected = provider == tileProvider
                        Surface(
                            color = if (isSelected) NeonCyan.copy(alpha = 0.12f) else Color.Transparent,
                            shape = RoundedCornerShape(10.dp),
                            border = if (isSelected) androidx.compose.foundation.BorderStroke(1.dp, NeonCyan.copy(alpha = 0.6f)) else null,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp)
                                .clickable {
                                    tileProvider = provider
                                    showLayersDialog = false
                                    tileRedrawTrigger++
                                }
                        ) {
                            Row(
                                modifier = Modifier
                                    .padding(horizontal = 12.dp, vertical = 9.dp)
                                    .fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = provider.displayName,
                                        color = if (isSelected) NeonCyan else TextPrimary,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        fontSize = 14.sp
                                    )
                                    Text(
                                        text = provider.subtitle,
                                        color = TextSecondary,
                                        fontSize = 11.sp
                                    )
                                }
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Seleccionado",
                                        tint = NeonCyan,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Offline Area Downloader Action
                    Surface(
                        color = CockpitCardBorder.copy(alpha = 0.35f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CloudDownload,
                                    contentDescription = "Descarga offline",
                                    tint = NeonCyan,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = "Rutas Sin Conexión",
                                    color = TextPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Pre-descarga el mapa de esta zona (10 km a la redonda) para ver calles y satélite sin internet en carretera.",
                                color = TextSecondary,
                                fontSize = 11.sp,
                                lineHeight = 15.sp
                            )
                            Spacer(modifier = Modifier.height(8.dp))

                            if (isDownloadingOffline) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    CircularProgressIndicator(
                                        progress = { downloadProgress },
                                        color = NeonCyan,
                                        modifier = Modifier.size(24.dp),
                                        strokeWidth = 2.5.dp
                                    )
                                    Text(
                                        text = downloadStatusText,
                                        color = NeonCyan,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            } else {
                                Button(
                                    onClick = {
                                        if (!MapTileManager.isOnline(context)) {
                                            Toast.makeText(context, "Conéctate a internet para pre-descargar el mapa", Toast.LENGTH_SHORT).show()
                                            return@Button
                                        }
                                        isDownloadingOffline = true
                                        downloadProgress = 0f
                                        downloadStatusText = "Iniciando descarga..."

                                        coroutineScope.launch {
                                            val cLat = if (currentLat != 0.0) currentLat else mapCenterLat
                                            val cLon = if (currentLon != 0.0) currentLon else mapCenterLon

                                            val count = MapTileManager.precacheOfflineArea(
                                                context = context,
                                                provider = tileProvider,
                                                centerLat = cLat,
                                                centerLon = cLon,
                                                radiusKm = 8.0,
                                                onProgress = { down, tot ->
                                                    downloadProgress = if (tot > 0) down.toFloat() / tot.toFloat() else 0f
                                                    downloadStatusText = "Guardando: $down / $tot cuadros"
                                                }
                                            )
                                            isDownloadingOffline = false
                                            Toast.makeText(context, "¡Listo! $count cuadros guardados para uso sin internet", Toast.LENGTH_LONG).show()
                                            tileRedrawTrigger++
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = NeonCyan.copy(alpha = 0.2f),
                                        contentColor = NeonCyan
                                    ),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = "Descargar Mapa Offline de Esta Zona",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Speed Heatmap Legend (Bottom-Left)
        if (showHeatmap && activePoints.isNotEmpty()) {
            Surface(
                color = CockpitSurface.copy(alpha = 0.90f),
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
                    Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(ElectricOrange))
                    Text(text = "80-120", color = TextPrimary, fontSize = 9.sp)
                    Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(RacingRed))
                    Text(text = ">120", color = TextPrimary, fontSize = 9.sp)
                }
            }
        }
    }
}
