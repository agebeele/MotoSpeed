package com.example.ui.util

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.widget.Toast
import androidx.core.content.FileProvider
import com.example.data.model.RoutePoint
import com.example.data.model.TripEntity
import com.example.ui.map.MapTileManager
import com.example.ui.map.MapTileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

object RouteImageShareHelper {

    suspend fun shareTripImage(
        context: Context,
        trip: TripEntity,
        routePoints: List<RoutePoint>,
        provider: MapTileProvider = MapTileProvider.GOOGLE_HYBRID
    ) = withContext(Dispatchers.IO) {
        try {
            val bitmap = createRouteSummaryBitmap(context, trip, routePoints, provider)
            val shareDir = File(context.cacheDir, "shared_routes")
            if (!shareDir.exists()) {
                shareDir.mkdirs()
            }

            // Clean older shared files to prevent cache clutter
            shareDir.listFiles()?.forEach { file ->
                if (System.currentTimeMillis() - file.lastModified() > 24 * 60 * 60 * 1000) {
                    file.delete()
                }
            }

            val imageFile = File(shareDir, "motospeed_ruta_${trip.id}_${System.currentTimeMillis()}.png")
            FileOutputStream(imageFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }

            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                imageFile
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "Mi ruta en moto: ${trip.title}")
                putExtra(
                    Intent.EXTRA_TEXT,
                    "🏍️ ¡Mira mi recorrido en motocicleta con MotoSpeed!\n" +
                            "📍 Ruta: ${trip.title}\n" +
                            "🏁 Distancia: ${String.format(Locale.US, "%.2f km", trip.distanceKm)}\n" +
                            "⚡ Vel. Máxima: ${String.format(Locale.US, "%.1f km/h", trip.maxSpeedKmh)}\n" +
                            "📊 Vel. Promedio: ${String.format(Locale.US, "%.1f km/h", trip.avgSpeedKmh)}"
                )
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            val chooser = Intent.createChooser(shareIntent, "Compartir ruta en moto").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)

        } catch (e: Exception) {
            e.printStackTrace()
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Error al generar imagen de la ruta", Toast.LENGTH_SHORT).show()
            }
        }
    }

    suspend fun createRouteSummaryBitmap(
        context: Context,
        trip: TripEntity,
        routePoints: List<RoutePoint>,
        provider: MapTileProvider = MapTileProvider.GOOGLE_HYBRID
    ): Bitmap = withContext(Dispatchers.IO) {
        val width = 1080
        val height = 1440
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // 1. Background Gradient (Deep Racing Cockpit Black)
        val bgPaint = Paint().apply {
            shader = LinearGradient(
                0f, 0f, 0f, height.toFloat(),
                Color.parseColor("#0C111C"),
                Color.parseColor("#05080E"),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

        // Outer Metallic Border with Neon Accent
        val borderPaint = Paint().apply {
            color = Color.parseColor("#1B273D")
            style = Paint.Style.STROKE
            strokeWidth = 4f
            isAntiAlias = true
        }
        canvas.drawRoundRect(RectF(24f, 24f, width - 24f, height - 24f), 32f, 32f, borderPaint)

        // Subtle Neon Top Accent Line
        val neonAccentPaint = Paint().apply {
            color = Color.parseColor("#00E5FF")
            style = Paint.Style.STROKE
            strokeWidth = 6f
            strokeCap = Paint.Cap.ROUND
            isAntiAlias = true
        }
        canvas.drawLine(120f, 24f, width - 120f, 24f, neonAccentPaint)

        // 2. Header Section
        val brandPaint = Paint().apply {
            color = Color.parseColor("#00E5FF")
            textSize = 24f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
            letterSpacing = 0.15f
        }
        canvas.drawText("🏍️  MOTOSPEED  •  TELEMETRÍA DE RUTA", 64f, 90f, brandPaint)

        val titlePaint = Paint().apply {
            color = Color.WHITE
            textSize = 44f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        val cleanTitle = if (trip.title.length > 32) trip.title.take(30) + "..." else trip.title
        canvas.drawText(cleanTitle, 64f, 150f, titlePaint)

        val dateStr = SimpleDateFormat("EEEE, d 'de' MMMM yyyy  •  HH:mm 'hrs'", Locale("es", "MX"))
            .format(Date(trip.startTime))
            .replaceFirstChar { it.uppercase() }
        val datePaint = Paint().apply {
            color = Color.parseColor("#8899A6")
            textSize = 24f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            isAntiAlias = true
        }
        canvas.drawText(dateStr, 64f, 195f, datePaint)

        // 3. Map Section Box (Auto-Fit Complete Route from Start to Finish with Real Map Tiles)
        val mapLeft = 56f
        val mapTop = 230f
        val mapRight = width - 56f
        val mapBottom = 840f
        val mapRect = RectF(mapLeft, mapTop, mapRight, mapBottom)
        val mapRadius = 24f

        // Map Box Background
        val mapBgPaint = Paint().apply {
            color = Color.parseColor("#0A0F1A")
            isAntiAlias = true
        }
        canvas.drawRoundRect(mapRect, mapRadius, mapRadius, mapBgPaint)

        val mapBorderPaint = Paint().apply {
            color = Color.parseColor("#22334F")
            style = Paint.Style.STROKE
            strokeWidth = 3f
            isAntiAlias = true
        }
        canvas.drawRoundRect(mapRect, mapRadius, mapRadius, mapBorderPaint)

        // Clip subsequent drawing strictly inside the Map Box
        canvas.save()
        val clipPath = Path().apply {
            addRoundRect(mapRect, mapRadius, mapRadius, Path.Direction.CW)
        }
        canvas.clipPath(clipPath)

        // Draw Tactical Grid Lines as base fallback
        val gridPaint = Paint().apply {
            color = Color.parseColor("#152033")
            strokeWidth = 1.5f
            isAntiAlias = true
        }
        var gridX = mapLeft + 40f
        while (gridX < mapRight) {
            canvas.drawLine(gridX, mapTop, gridX, mapBottom, gridPaint)
            gridX += 60f
        }
        var gridY = mapTop + 40f
        while (gridY < mapBottom) {
            canvas.drawLine(mapLeft, gridY, mapRight, gridY, gridPaint)
            gridY += 60f
        }

        // AUTO-FIT ROUTE: Compute bounding box, load real map tiles, and draw route
        if (routePoints.size > 1) {
            val minLat = routePoints.minOf { it.latitude }
            val maxLat = routePoints.maxOf { it.latitude }
            val minLon = routePoints.minOf { it.longitude }
            val maxLon = routePoints.maxOf { it.longitude }

            val midLat = (minLat + maxLat) / 2.0
            val midLon = (minLon + maxLon) / 2.0

            val boxWidth = mapRight - mapLeft
            val boxHeight = mapBottom - mapTop

            val fitZ = MapTileManager.calculateFitZoom(
                minLat = minLat,
                maxLat = maxLat,
                minLon = minLon,
                maxLon = maxLon,
                viewWidthPx = boxWidth,
                viewHeightPx = boxHeight,
                paddingPx = 64f
            )

            val intZoom = floor(fitZ).toInt().coerceIn(4, 17)
            val subZoomFactor = 2.0.pow((fitZ - intZoom).toDouble()).toFloat()
            val tileSizeOnScreen = 256f * subZoomFactor

            val centerWorldX = MapTileManager.lonToWorldPixel(midLon, intZoom.toFloat())
            val centerWorldY = MapTileManager.latToWorldPixel(midLat, intZoom.toFloat())

            val screenCenterX = (mapLeft + mapRight) / 2f
            val screenCenterY = (mapTop + mapBottom) / 2f

            fun projectX(lon: Double): Float {
                val wx = MapTileManager.lonToWorldPixel(lon, intZoom.toFloat())
                return screenCenterX + (wx - centerWorldX).toFloat() * subZoomFactor
            }

            fun projectY(lat: Double): Float {
                val wy = MapTileManager.latToWorldPixel(lat, intZoom.toFloat())
                return screenCenterY + (wy - centerWorldY).toFloat() * subZoomFactor
            }

            // Draw Real Map Tiles
            val totalTilesAtZ = (1 shl intZoom)
            val minWorldX = centerWorldX - (screenCenterX - mapLeft) / subZoomFactor
            val maxWorldX = centerWorldX + (mapRight - screenCenterX) / subZoomFactor
            val minWorldY = centerWorldY - (screenCenterY - mapTop) / subZoomFactor
            val maxWorldY = centerWorldY + (mapBottom - screenCenterY) / subZoomFactor

            val minTileX = floor(minWorldX / 256.0).toInt().coerceIn(0, totalTilesAtZ - 1)
            val maxTileX = floor(maxWorldX / 256.0).toInt().coerceIn(0, totalTilesAtZ - 1)
            val minTileY = floor(minWorldY / 256.0).toInt().coerceIn(0, totalTilesAtZ - 1)
            val maxTileY = floor(maxWorldY / 256.0).toInt().coerceIn(0, totalTilesAtZ - 1)

            for (tx in minTileX..maxTileX) {
                for (ty in minTileY..maxTileY) {
                    val tileWorldX = tx * 256.0
                    val tileWorldY = ty * 256.0

                    val tileScreenX = screenCenterX + (tileWorldX - centerWorldX).toFloat() * subZoomFactor
                    val tileScreenY = screenCenterY + (tileWorldY - centerWorldY).toFloat() * subZoomFactor

                    val tileBmp = MapTileManager.getTileSync(
                        context = context,
                        provider = provider,
                        z = intZoom,
                        x = tx,
                        y = ty
                    )

                    if (tileBmp != null && !tileBmp.isRecycled) {
                        val srcRect = Rect(0, 0, tileBmp.width, tileBmp.height)
                        val dstRect = Rect(
                            tileScreenX.roundToInt(),
                            tileScreenY.roundToInt(),
                            (tileScreenX + tileSizeOnScreen).roundToInt(),
                            (tileScreenY + tileSizeOnScreen).roundToInt()
                        )
                        canvas.drawBitmap(tileBmp, srcRect, dstRect, null)
                    }
                }
            }

            // Glow path underneath
            val glowPaint = Paint().apply {
                color = Color.parseColor("#4400E5FF")
                style = Paint.Style.STROKE
                strokeWidth = 20f
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
                isAntiAlias = true
            }

            // High-contrast dark casing outline (like Google Maps)
            val casingPaint = Paint().apply {
                color = Color.parseColor("#E0040810")
                style = Paint.Style.STROKE
                strokeWidth = 18f
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
                isAntiAlias = true
            }

            val glowPath = Path()
            val firstPt = routePoints[0]
            glowPath.moveTo(projectX(firstPt.longitude), projectY(firstPt.latitude))
            for (i in 1 until routePoints.size) {
                glowPath.lineTo(projectX(routePoints[i].longitude), projectY(routePoints[i].latitude))
            }
            canvas.drawPath(glowPath, casingPaint)
            canvas.drawPath(glowPath, glowPaint)

            // Speed Heatmap Colored Line Segments
            for (i in 0 until routePoints.size - 1) {
                val p1 = routePoints[i]
                val p2 = routePoints[i + 1]
                val x1 = projectX(p1.longitude)
                val y1 = projectY(p1.latitude)
                val x2 = projectX(p2.longitude)
                val y2 = projectY(p2.latitude)

                val avgSegSpeed = (p1.speedKmh + p2.speedKmh) / 2f
                val segColor = when {
                    avgSegSpeed < 40f -> Color.parseColor("#00E676")   // Green
                    avgSegSpeed < 80f -> Color.parseColor("#FFB300")   // Amber
                    avgSegSpeed < 120f -> Color.parseColor("#FF6D00")  // Orange
                    else -> Color.parseColor("#FF1744")                // Red
                }

                val segPaint = Paint().apply {
                    color = segColor
                    strokeWidth = 10f
                    strokeCap = Paint.Cap.ROUND
                    strokeJoin = Paint.Join.ROUND
                    isAntiAlias = true
                }
                canvas.drawLine(x1, y1, x2, y2, segPaint)
            }

            // START Pin (INICIO)
            val startX = projectX(routePoints.first().longitude)
            val startY = projectY(routePoints.first().latitude)

            drawPin(
                canvas = canvas,
                x = startX,
                y = startY,
                text = "INICIO",
                pinColor = Color.parseColor("#00E676"),
                textColor = Color.BLACK
            )

            // FINISH Pin (FIN)
            val endX = projectX(routePoints.last().longitude)
            val endY = projectY(routePoints.last().latitude)

            drawPin(
                canvas = canvas,
                x = endX,
                y = endY,
                text = "FIN",
                pinColor = Color.parseColor("#FF1744"),
                textColor = Color.WHITE
            )

        } else {
            // Empty / Single point state
            val noRoutePaint = Paint().apply {
                color = Color.parseColor("#8899A6")
                textSize = 28f
                textAlign = Paint.Align.CENTER
                isAntiAlias = true
            }
            canvas.drawText(
                "Ruta sin suficientes puntos GPS registrados",
                (mapLeft + mapRight) / 2f,
                (mapTop + mapBottom) / 2f,
                noRoutePaint
            )
        }

        // Map Watermark & Speed Legend inside bottom of Map Box
        val legendBgPaint = Paint().apply {
            color = Color.parseColor("#E00C121D")
            isAntiAlias = true
        }
        val legendRect = RectF(mapLeft + 16f, mapBottom - 44f, mapRight - 16f, mapBottom - 12f)
        canvas.drawRoundRect(legendRect, 10f, 10f, legendBgPaint)

        val legendTextPaint = Paint().apply {
            textSize = 17f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }

        legendTextPaint.color = Color.parseColor("#8899A6")
        canvas.drawText("Velocidad: ", mapLeft + 30f, mapBottom - 23f, legendTextPaint)

        var legendX = mapLeft + 120f
        fun drawColorKey(colorHex: String, label: String) {
            val dotPaint = Paint().apply {
                color = Color.parseColor(colorHex)
                isAntiAlias = true
            }
            canvas.drawCircle(legendX, mapBottom - 27f, 6f, dotPaint)
            legendX += 12f
            legendTextPaint.color = Color.WHITE
            canvas.drawText(label, legendX, mapBottom - 22f, legendTextPaint)
            legendX += legendTextPaint.measureText(label) + 20f
        }

        drawColorKey("#00E676", "<40")
        drawColorKey("#FFB300", "40-80")
        drawColorKey("#FF6D00", "80-120")
        drawColorKey("#FF1744", "120+")

        val ptsText = "${routePoints.size} pts • OpenStreetMap"
        legendTextPaint.color = Color.parseColor("#00E5FF")
        val ptsWidth = legendTextPaint.measureText(ptsText)
        canvas.drawText(ptsText, mapRight - 30f - ptsWidth, mapBottom - 22f, legendTextPaint)

        canvas.restore() // Restore clipping

        // 4. Statistics Grid (4 High-Contrast Cockpit Cards)
        val cardMargin = 56f
        val cardGap = 20f
        val cardWidth = (width - (cardMargin * 2) - cardGap) / 2f
        val cardHeight = 175f

        val row1Top = 875f
        val row2Top = row1Top + cardHeight + cardGap

        val durationMin = trip.durationSeconds / 60
        val durationSec = trip.durationSeconds % 60
        val durationStr = if (durationMin > 60) {
            "${durationMin / 60}h ${durationMin % 60}m"
        } else {
            "${durationMin}m ${durationSec}s"
        }

        // Card 1: Distancia Total (Top-Left)
        drawTelemetryCard(
            canvas = canvas,
            rect = RectF(cardMargin, row1Top, cardMargin + cardWidth, row1Top + cardHeight),
            label = "DISTANCIA TOTAL",
            value = String.format(Locale.US, "%.2f", trip.distanceKm),
            unit = "KM",
            valueColor = Color.parseColor("#00E5FF")
        )

        // Card 2: Duración (Top-Right)
        drawTelemetryCard(
            canvas = canvas,
            rect = RectF(cardMargin + cardWidth + cardGap, row1Top, cardMargin + (cardWidth * 2) + cardGap, row1Top + cardHeight),
            label = "DURACIÓN DE RUTA",
            value = durationStr,
            unit = "TIEMPO",
            valueColor = Color.parseColor("#00E676")
        )

        // Card 3: Velocidad Máxima (Bottom-Left)
        drawTelemetryCard(
            canvas = canvas,
            rect = RectF(cardMargin, row2Top, cardMargin + cardWidth, row2Top + cardHeight),
            label = "VELOCIDAD MÁXIMA",
            value = String.format(Locale.US, "%.1f", trip.maxSpeedKmh),
            unit = "KM/H",
            valueColor = Color.parseColor("#FF1744")
        )

        // Card 4: Velocidad Promedio (Bottom-Right)
        drawTelemetryCard(
            canvas = canvas,
            rect = RectF(cardMargin + cardWidth + cardGap, row2Top, cardMargin + (cardWidth * 2) + cardGap, row2Top + cardHeight),
            label = "VELOCIDAD PROMEDIO",
            value = String.format(Locale.US, "%.1f", trip.avgSpeedKmh),
            unit = "KM/H",
            valueColor = Color.parseColor("#FFB300")
        )

        // 5. Footer Watermark
        val footerPaint = Paint().apply {
            color = Color.parseColor("#64748B")
            textSize = 21f
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            isAntiAlias = true
        }
        canvas.drawText(
            "Generado con MotoSpeed • Velocímetro Digital & GPS para Motocicleta",
            width / 2f,
            1350f,
            footerPaint
        )

        val brandingSub = Paint().apply {
            color = Color.parseColor("#00E5FF")
            textSize = 19f
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
            letterSpacing = 0.1f
        }
        canvas.drawText("MOTOSPEED GPS COCKPIT", width / 2f, 1385f, brandingSub)

        bitmap
    }

    private fun drawPin(
        canvas: Canvas,
        x: Float,
        y: Float,
        text: String,
        pinColor: Int,
        textColor: Int
    ) {
        val outerShadow = Paint().apply {
            color = Color.parseColor("#80000000")
            isAntiAlias = true
        }
        canvas.drawCircle(x, y + 4f, 16f, outerShadow)

        // Pin Head Outer Ring
        val outerPaint = Paint().apply {
            color = Color.BLACK
            isAntiAlias = true
        }
        canvas.drawCircle(x, y, 15f, outerPaint)

        // Pin Head Fill
        val pinPaint = Paint().apply {
            color = pinColor
            isAntiAlias = true
        }
        canvas.drawCircle(x, y, 12f, pinPaint)

        // Pin Center Dot
        val centerDot = Paint().apply {
            color = Color.WHITE
            isAntiAlias = true
        }
        canvas.drawCircle(x, y, 5f, centerDot)

        // Pin Pill Badge above
        val badgeTextPaint = Paint().apply {
            this.color = textColor
            textSize = 20f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
        }
        val textWidth = badgeTextPaint.measureText(text)
        val badgeHeight = 32f
        val badgeWidth = textWidth + 24f
        val badgeRect = RectF(
            x - badgeWidth / 2f,
            y - 48f,
            x + badgeWidth / 2f,
            y - 48f + badgeHeight
        )

        val badgeBgPaint = Paint().apply {
            color = pinColor
            isAntiAlias = true
        }
        canvas.drawRoundRect(badgeRect, 10f, 10f, badgeBgPaint)

        canvas.drawText(text, x, y - 25f, badgeTextPaint)
    }

    private fun drawTelemetryCard(
        canvas: Canvas,
        rect: RectF,
        label: String,
        value: String,
        unit: String,
        valueColor: Int
    ) {
        // Card Background (Frosted Cockpit Dark Surface)
        val bgPaint = Paint().apply {
            color = Color.parseColor("#101726")
            isAntiAlias = true
        }
        canvas.drawRoundRect(rect, 18f, 18f, bgPaint)

        // Card Border
        val borderPaint = Paint().apply {
            color = Color.parseColor("#202F4A")
            style = Paint.Style.STROKE
            strokeWidth = 2.5f
            isAntiAlias = true
        }
        canvas.drawRoundRect(rect, 18f, 18f, borderPaint)

        // Small Accent Dot on Top-Left of Card
        val accentDotPaint = Paint().apply {
            color = valueColor
            isAntiAlias = true
        }
        canvas.drawCircle(rect.left + 26f, rect.top + 34f, 6f, accentDotPaint)

        // Card Label
        val labelPaint = Paint().apply {
            color = Color.parseColor("#8899A6")
            textSize = 21f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            letterSpacing = 0.08f
            isAntiAlias = true
        }
        canvas.drawText(label, rect.left + 44f, rect.top + 42f, labelPaint)

        // Card Value
        val valuePaint = Paint().apply {
            color = valueColor
            textSize = 52f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            isAntiAlias = true
        }
        canvas.drawText(value, rect.left + 26f, rect.top + 115f, valuePaint)

        // Card Unit
        val unitPaint = Paint().apply {
            color = Color.parseColor("#CBD5E1")
            textSize = 22f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        val valueWidth = valuePaint.measureText(value)
        canvas.drawText(unit, rect.left + 36f + valueWidth, rect.top + 115f, unitPaint)
    }
}
