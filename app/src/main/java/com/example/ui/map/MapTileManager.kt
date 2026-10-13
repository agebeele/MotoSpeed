package com.example.ui.map

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Rect
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.LruCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit
import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.tan

/**
 * Map layers requested:
 * - GOOGLE_HYBRID: Google Híbrido (satellite + roads/labels)
 * - GOOGLE_STREETS: Google Maps Calles (standard streets/roads)
 * - ATLAS_VOXEL: Atlas Voxel (topographic relief / terrain atlas)
 * - DEEPSLATE: Deepslate (dark mineral slate high-contrast cockpit theme)
 */
enum class MapTileProvider(
    val id: String,
    val displayName: String,
    val shortName: String,
    val subtitle: String,
    val attribution: String,
    val bgColorHex: String,
    val gridColorHex: String
) {
    GOOGLE_HYBRID(
        id = "google_hybrid",
        displayName = "Google Híbrido",
        shortName = "Híbrido",
        subtitle = "Satélite + Calles HD",
        attribution = "© Google",
        bgColorHex = "#070E1A",
        gridColorHex = "#162235"
    ),
    GOOGLE_STREETS(
        id = "google_streets",
        displayName = "Google Maps Calles",
        shortName = "Calles",
        subtitle = "Calles y Carreteras",
        attribution = "© Google Maps",
        bgColorHex = "#E5E3DF",
        gridColorHex = "#D3D1CD"
    ),
    ATLAS_VOXEL(
        id = "atlas_voxel",
        displayName = "Atlas Voxel",
        shortName = "Atlas",
        subtitle = "Relieve Topográfico",
        attribution = "© OpenTopoMap / © OSM",
        bgColorHex = "#E8E2D8",
        gridColorHex = "#D5CEBF"
    ),
    DEEPSLATE(
        id = "deepslate",
        displayName = "Deepslate",
        shortName = "Deepslate",
        subtitle = "Pizarra Oscura Cockpit",
        attribution = "© Esri Dark Canvas",
        bgColorHex = "#121417",
        gridColorHex = "#1F242C"
    );

    fun getPrimaryUrl(z: Int, x: Int, y: Int): String {
        val s = (x + y).mod(4)
        return when (this) {
            GOOGLE_HYBRID -> "https://mt$s.google.com/vt/lyrs=y&x=$x&y=$y&z=$z"
            GOOGLE_STREETS -> "https://mt$s.google.com/vt/lyrs=m&x=$x&y=$y&z=$z"
            ATLAS_VOXEL -> "https://tile.opentopomap.org/$z/$x/$y.png"
            DEEPSLATE -> "https://server.arcgisonline.com/ArcGIS/rest/services/Canvas/World_Dark_Gray_Base/MapServer/tile/$z/$y/$x"
        }
    }

    fun getFallbackUrl(z: Int, x: Int, y: Int): String? {
        val s = (x + y).mod(4)
        return when (this) {
            GOOGLE_HYBRID -> "https://mt$s.google.com/vt/lyrs=s,h&x=$x&y=$y&z=$z"
            GOOGLE_STREETS -> "https://mt$s.google.com/vt/lyrs=r&x=$x&y=$y&z=$z"
            ATLAS_VOXEL -> "https://tile.openstreetmap.org/$z/$x/$y.png"
            DEEPSLATE -> "https://mt$s.google.com/vt/lyrs=m&x=$x&y=$y&z=$z"
        }
    }
}

object MapTileManager {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()

    // In-memory cache for fast Compose Canvas rendering
    private val memoryCache = object : LruCache<String, Bitmap>(240) {
        override fun sizeOf(key: String, value: Bitmap): Int = 1
    }

    // Set of active downloads to prevent duplicate network calls
    private val activeDownloads = java.util.Collections.synchronizedSet(mutableSetOf<String>())

    /**
     * Check device network connectivity to avoid hanging requests when offline.
     */
    fun isOnline(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val network = cm.activeNetwork ?: return false
        val capabilities = cm.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    /**
     * Permanent storage directory for offline map tiles in app filesDir,
     * ensuring offline tiles are NEVER wiped when system clears caches.
     */
    private fun getStorageDir(context: Context, provider: MapTileProvider, z: Int, x: Int): File {
        val dir = File(context.filesDir, "maptiles_offline/${provider.id}/$z/$x")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    private fun getCacheKey(provider: MapTileProvider, z: Int, x: Int, y: Int): String {
        return "${provider.id}_${z}_${x}_${y}"
    }

    private fun getTileFile(context: Context, provider: MapTileProvider, z: Int, x: Int, y: Int): File {
        val dir = getStorageDir(context, provider, z, x)
        return File(dir, "$y.png")
    }

    /**
     * Checks if a tile is immediately available in memory.
     */
    fun getTileFromMemory(provider: MapTileProvider, z: Int, x: Int, y: Int): Bitmap? {
        val key = getCacheKey(provider, z, x, y)
        return memoryCache.get(key)
    }

    /**
     * Multi-zoom fallback:
     * When offline and tile (z, x, y) is missing on disk,
     * check parent tile (z-1) or (z-2), crop the corresponding quadrant, and scale up!
     */
    private fun getFallbackParentTileFromDisk(
        context: Context,
        provider: MapTileProvider,
        z: Int,
        x: Int,
        y: Int
    ): Bitmap? {
        // Try parent at zoom z - 1
        if (z > 4) {
            val pz = z - 1
            val px = x / 2
            val py = y / 2
            val parentFile = getTileFile(context, provider, pz, px, py)
            if (parentFile.exists() && parentFile.length() > 0) {
                try {
                    val parentBmp = BitmapFactory.decodeFile(parentFile.absolutePath)
                    if (parentBmp != null) {
                        val subX = (x % 2) * 128
                        val subY = (y % 2) * 128
                        val cropped = Bitmap.createBitmap(parentBmp, subX, subY, 128, 128)
                        val scaled = Bitmap.createScaledBitmap(cropped, 256, 256, true)
                        return scaled
                    }
                } catch (_: Exception) {}
            }
        }

        // Try grandparent at zoom z - 2
        if (z > 5) {
            val gpz = z - 2
            val gpx = x / 4
            val gpy = y / 4
            val gpFile = getTileFile(context, provider, gpz, gpx, gpy)
            if (gpFile.exists() && gpFile.length() > 0) {
                try {
                    val gpBmp = BitmapFactory.decodeFile(gpFile.absolutePath)
                    if (gpBmp != null) {
                        val subX = (x % 4) * 64
                        val subY = (y % 4) * 64
                        val cropped = Bitmap.createBitmap(gpBmp, subX, subY, 64, 64)
                        val scaled = Bitmap.createScaledBitmap(cropped, 256, 256, true)
                        return scaled
                    }
                } catch (_: Exception) {}
            }
        }

        return null
    }

    /**
     * Tries to get tile from memory or disk, or triggers async download if missing and online.
     * If offline, immediately provides multi-zoom parent fallback tile.
     */
    fun getOrFetchTile(
        context: Context,
        scope: CoroutineScope,
        provider: MapTileProvider,
        z: Int,
        x: Int,
        y: Int,
        onTileLoaded: () -> Unit
    ): Bitmap? {
        val key = getCacheKey(provider, z, x, y)
        val memBitmap = memoryCache.get(key)
        if (memBitmap != null) {
            return memBitmap
        }

        val diskFile = getTileFile(context, provider, z, x, y)
        if (diskFile.exists() && diskFile.length() > 0) {
            try {
                val diskBitmap = BitmapFactory.decodeFile(diskFile.absolutePath)
                if (diskBitmap != null) {
                    memoryCache.put(key, diskBitmap)
                    return diskBitmap
                }
            } catch (e: Exception) {
                diskFile.delete()
            }
        }

        // Check if device is offline
        val online = isOnline(context)
        if (!online) {
            // When offline, immediately attempt parent tile scaling
            val fallbackParent = getFallbackParentTileFromDisk(context, provider, z, x, y)
            if (fallbackParent != null) {
                memoryCache.put(key, fallbackParent)
                return fallbackParent
            }
            return null
        }

        // Trigger async network fetch if online
        if (activeDownloads.add(key)) {
            scope.launch(Dispatchers.IO) {
                try {
                    val bitmap = downloadTile(context, provider, z, x, y)
                    if (bitmap != null) {
                        memoryCache.put(key, bitmap)
                        withContext(Dispatchers.Main) {
                            onTileLoaded()
                        }
                    }
                } finally {
                    activeDownloads.remove(key)
                }
            }
        }

        // While fetching, check if a parent tile is already on disk for smooth loading
        val parentPlaceholder = getFallbackParentTileFromDisk(context, provider, z, x, y)
        if (parentPlaceholder != null) {
            return parentPlaceholder
        }

        return null
    }

    /**
     * Synchronous / suspending tile getter for background batch generators (like image export).
     */
    suspend fun getTileSync(
        context: Context,
        provider: MapTileProvider,
        z: Int,
        x: Int,
        y: Int
    ): Bitmap? = withContext(Dispatchers.IO) {
        val key = getCacheKey(provider, z, x, y)
        val mem = memoryCache.get(key)
        if (mem != null) return@withContext mem

        val diskFile = getTileFile(context, provider, z, x, y)
        if (diskFile.exists() && diskFile.length() > 0) {
            try {
                val bmp = BitmapFactory.decodeFile(diskFile.absolutePath)
                if (bmp != null) {
                    memoryCache.put(key, bmp)
                    return@withContext bmp
                }
            } catch (e: Exception) {
                diskFile.delete()
            }
        }

        // Check parent fallback
        val parent = getFallbackParentTileFromDisk(context, provider, z, x, y)
        if (parent != null) {
            memoryCache.put(key, parent)
        }

        if (isOnline(context)) {
            val downloaded = downloadTile(context, provider, z, x, y)
            if (downloaded != null) {
                memoryCache.put(key, downloaded)
                return@withContext downloaded
            }
        }

        parent
    }

    private fun downloadTile(
        context: Context,
        provider: MapTileProvider,
        z: Int,
        x: Int,
        y: Int
    ): Bitmap? {
        val primaryUrl = provider.getPrimaryUrl(z, x, y)
        val primaryBmp = fetchUrlBitmap(context, provider, z, x, y, primaryUrl)
        if (primaryBmp != null) return primaryBmp

        val fallbackUrl = provider.getFallbackUrl(z, x, y)
        if (fallbackUrl != null) {
            return fetchUrlBitmap(context, provider, z, x, y, fallbackUrl)
        }

        return null
    }

    private fun fetchUrlBitmap(
        context: Context,
        provider: MapTileProvider,
        z: Int,
        x: Int,
        y: Int,
        url: String
    ): Bitmap? {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36")
            .header("Accept", "image/webp,image/png,image/jpeg,*/*")
            .build()

        return try {
            val response = httpClient.newCall(request).execute()
            if (response.isSuccessful) {
                val bytes = response.body?.bytes()
                if (bytes != null && bytes.isNotEmpty()) {
                    val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    if (bmp != null) {
                        val diskFile = getTileFile(context, provider, z, x, y)
                        try {
                            FileOutputStream(diskFile).use { fos ->
                                fos.write(bytes)
                            }
                        } catch (_: Exception) {}
                        bmp
                    } else null
                } else null
            } else null
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Pre-cache area around given latitude/longitude for offline riding.
     * Downloads tiles for zooms 13, 14, 15, and 16.
     */
    suspend fun precacheOfflineArea(
        context: Context,
        provider: MapTileProvider,
        centerLat: Double,
        centerLon: Double,
        radiusKm: Double = 8.0,
        onProgress: (downloaded: Int, total: Int) -> Unit
    ): Int = withContext(Dispatchers.IO) {
        if (!isOnline(context)) return@withContext 0

        // Approximate delta in lat/lon
        val deltaLat = radiusKm / 111.0
        val deltaLon = radiusKm / (111.0 * cos(Math.toRadians(centerLat)))

        val minLat = centerLat - deltaLat
        val maxLat = centerLat + deltaLat
        val minLon = centerLon - deltaLon
        val maxLon = centerLon + deltaLon

        val targetZooms = listOf(13, 14, 15, 16)
        val tilesToDownload = mutableListOf<Triple<Int, Int, Int>>()

        for (z in targetZooms) {
            val n = 1 shl z
            val xMin = floor((minLon + 180.0) / 360.0 * n).toInt().coerceIn(0, n - 1)
            val xMax = floor((maxLon + 180.0) / 360.0 * n).toInt().coerceIn(0, n - 1)

            val latRadMin = Math.toRadians(minLat.coerceIn(-85.0, 85.0))
            val latRadMax = Math.toRadians(maxLat.coerceIn(-85.0, 85.0))

            val yMax = floor((1.0 - ln(tan(latRadMin) + (1.0 / cos(latRadMin))) / PI) / 2.0 * n).toInt().coerceIn(0, n - 1)
            val yMin = floor((1.0 - ln(tan(latRadMax) + (1.0 / cos(latRadMax))) / PI) / 2.0 * n).toInt().coerceIn(0, n - 1)

            val actualYMin = min(yMin, yMax)
            val actualYMax = max(yMin, yMax)

            for (x in xMin..xMax) {
                for (y in actualYMin..actualYMax) {
                    val file = getTileFile(context, provider, z, x, y)
                    if (!file.exists() || file.length() == 0L) {
                        tilesToDownload.add(Triple(z, x, y))
                    }
                }
            }
        }

        val total = tilesToDownload.size
        var completed = 0

        for (tile in tilesToDownload) {
            downloadTile(context, provider, tile.first, tile.second, tile.third)
            completed++
            if (completed % 5 == 0 || completed == total) {
                withContext(Dispatchers.Main) {
                    onProgress(completed, total)
                }
            }
        }

        completed
    }

    /**
     * Get offline storage usage in megabytes.
     */
    fun getOfflineCacheSizeBytes(context: Context): Long {
        val root = File(context.filesDir, "maptiles_offline")
        if (!root.exists()) return 0L
        return root.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }

    // -------------------------------------------------------------
    // Web Mercator Slippy Map Mathematics (EPSG:3857)
    // -------------------------------------------------------------

    fun lonToWorldPixel(lon: Double, zoom: Float): Double {
        val totalTiles = 2.0.pow(zoom.toDouble())
        return ((lon + 180.0) / 360.0) * totalTiles * 256.0
    }

    fun latToWorldPixel(lat: Double, zoom: Float): Double {
        val clampedLat = lat.coerceIn(-85.05112878, 85.05112878)
        val latRad = Math.toRadians(clampedLat)
        val totalTiles = 2.0.pow(zoom.toDouble())
        val y = (1.0 - (ln(tan(latRad) + (1.0 / cos(latRad))) / PI)) / 2.0
        return y * totalTiles * 256.0
    }

    fun worldPixelToLon(px: Double, zoom: Float): Double {
        val totalTiles = 2.0.pow(zoom.toDouble())
        return (px / (totalTiles * 256.0)) * 360.0 - 180.0
    }

    fun worldPixelToLat(py: Double, zoom: Float): Double {
        val totalTiles = 2.0.pow(zoom.toDouble())
        val y = py / (totalTiles * 256.0)
        val n = PI - 2.0 * PI * y
        return Math.toDegrees(atan(kotlin.math.sinh(n)))
    }

    /**
     * Calculates the best zoom level to fit a bounding box into view dimensions.
     */
    fun calculateFitZoom(
        minLat: Double,
        maxLat: Double,
        minLon: Double,
        maxLon: Double,
        viewWidthPx: Float,
        viewHeightPx: Float,
        paddingPx: Float = 60f
    ): Float {
        val availableW = max(100f, viewWidthPx - paddingPx * 2f)
        val availableH = max(100f, viewHeightPx - paddingPx * 2f)

        for (z in 18 downTo 4) {
            val zf = z.toFloat()
            val x1 = lonToWorldPixel(minLon, zf)
            val x2 = lonToWorldPixel(maxLon, zf)
            val y1 = latToWorldPixel(maxLat, zf)
            val y2 = latToWorldPixel(minLat, zf)

            val spanW = Math.abs(x2 - x1)
            val spanH = Math.abs(y2 - y1)

            if (spanW <= availableW && spanH <= availableH) {
                return zf
            }
        }
        return 12f
    }
}
