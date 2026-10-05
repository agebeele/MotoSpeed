package com.example.ui.util

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.widget.Toast
import androidx.core.content.FileProvider
import com.example.data.model.RoutePoint
import com.example.data.model.TripEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min

object RouteImageShareHelper {

    suspend fun shareTripImage(
        context: Context,
        trip: TripEntity,
        routePoints: List<RoutePoint>
    ) = withContext(Dispatchers.IO) {
        try {
            val bitmap = createRouteSummaryBitmap(trip, routePoints)
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

    fun createRouteSummaryBitmap(
        trip: TripEntity,
        routePoints: List<RoutePoint>
    ): Bitmap {
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

        // 3. Map Section Box (Auto-Fit Complete Route from Start to Finish)
        val mapLeft = 56f
        val mapTop = 230f
        val mapRight = width - 56f
        val mapBottom = 840f
        val mapRect = RectF(mapLeft, mapTop, mapRight, mapBottom)
        val mapRadius = 24f

        // Map Box Background
        val mapBgPaint = Paint().apply {
            color = Color.parseColor("#0F1626")
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

        // Draw Tactical Grid Lines inside the Map Box
        val gridPaint = Paint().apply {
            color = Color.parseColor("#172236")
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

        // AUTO-FIT ROUTE: Compute bounding box and uniform scaling
        if (routePoints.size > 1) {
            val minLat = routePoints.minOf { it.latitude }
            val maxLat = routePoints.maxOf { it.latitude }
            val minLon = routePoints.minOf { it.longitude }
            val maxLon = routePoints.maxOf { it.longitude }

            val latSpan = (maxLat - minLat).coerceAtLeast(0.0006)
            val lonSpan = (maxLon - minLon).coerceAtLeast(0.0006)
            val midLat = (minLat + maxLat) / 2.0
            val midLon = (minLon + maxLon) / 2.0
            val cosMidLat = cos(Math.toRadians(midLat))

            // Usable drawable area with 22% inner margin so pins and badges never touch edges
            val padding = 72f
            val usableWidth = (mapRight - mapLeft) - (padding * 2)
            val usableHeight = (mapBottom - mapTop) - (padding * 2)

            val scaleX = usableWidth / (lonSpan * cosMidLat)
            val scaleY = usableHeight / latSpan
            val scale = min(scaleX, scaleY)

            val centerX = (mapLeft + mapRight) / 2f
            val centerY = (mapTop + mapBottom) / 2f

            fun projectX(lon: Double): Float {
                return (centerX + (lon - midLon) * cosMidLat * scale).toFloat()
            }

            fun projectY(lat: Double): Float {
                return (centerY - (lat - midLat) * scale).toFloat()
            }

            // Glow path underneath
            val glowPaint = Paint().apply {
                color = Color.parseColor("#3300E5FF")
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
                    strokeWidth = 9f
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
            color = Color.parseColor("#D00C121D")
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

        val ptsText = "${routePoints.size} pts GPS"
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

        return bitmap
    }

    private fun drawPin(
        canvas: Canvas,
        x: Float,
        y: Float,
        text: String,
        pinColor: Int,
        textColor: Int
    ) {
        // Outer glow
        val glowPaint = Paint().apply {
            color = pinColor
            alpha = 100
            isAntiAlias = true
        }
        canvas.drawCircle(x, y, 16f, glowPaint)

        // Black outer ring
        val ringPaint = Paint().apply {
            color = Color.BLACK
            isAntiAlias = true
        }
        canvas.drawCircle(x, y, 12f, ringPaint)

        // Pin Center Dot
        val dotPaint = Paint().apply {
            color = pinColor
            isAntiAlias = true
        }
        canvas.drawCircle(x, y, 8f, dotPaint)

        // Center White Core
        val corePaint = Paint().apply {
            color = Color.WHITE
            isAntiAlias = true
        }
        canvas.drawCircle(x, y, 3f, corePaint)

        // Label Badge Pill
        val textPaint = Paint().apply {
            color = textColor
            textSize = 18f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }
        val textWidth = textPaint.measureText(text)
        val pillPaddingH = 14f
        val pillHeight = 28f
        val pillTop = y - 36f
        val pillRect = RectF(
            x - (textWidth / 2f) - pillPaddingH,
            pillTop,
            x + (textWidth / 2f) + pillPaddingH,
            pillTop + pillHeight
        )

        val pillBgPaint = Paint().apply {
            color = pinColor
            isAntiAlias = true
        }
        canvas.drawRoundRect(pillRect, 8f, 8f, pillBgPaint)

        canvas.drawText(text, x, pillTop + 20f, textPaint)
    }

    private fun drawTelemetryCard(
        canvas: Canvas,
        rect: RectF,
        label: String,
        value: String,
        unit: String,
        valueColor: Int
    ) {
        val bgPaint = Paint().apply {
            color = Color.parseColor("#111827")
            isAntiAlias = true
        }
        canvas.drawRoundRect(rect, 20f, 20f, bgPaint)

        val borderPaint = Paint().apply {
            color = Color.parseColor("#1F2D44")
            style = Paint.Style.STROKE
            strokeWidth = 2.5f
            isAntiAlias = true
        }
        canvas.drawRoundRect(rect, 20f, 20f, borderPaint)

        // Label
        val labelPaint = Paint().apply {
            color = Color.parseColor("#8899A6")
            textSize = 18f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
            letterSpacing = 0.08f
        }
        canvas.drawText(label, rect.left + 22f, rect.top + 38f, labelPaint)

        // Big Value
        val valuePaint = Paint().apply {
            color = valueColor
            textSize = 50f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            isAntiAlias = true
        }
        canvas.drawText(value, rect.left + 22f, rect.top + 104f, valuePaint)

        // Unit
        val unitPaint = Paint().apply {
            color = Color.parseColor("#00E5FF")
            textSize = 20f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
            letterSpacing = 0.05f
        }
        canvas.drawText(unit, rect.left + 24f, rect.top + 144f, unitPaint)
    }
}
