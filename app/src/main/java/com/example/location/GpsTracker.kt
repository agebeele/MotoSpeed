package com.example.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import android.os.SystemClock
import com.example.data.model.RoutePoint
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

enum class TripState {
    IDLE,
    RECORDING,
    PAUSED,
    FINISHED
}

data class GpsState(
    val currentSpeedKmh: Float = 0f,
    val maxSpeedKmh: Float = 0f,
    val avgSpeedKmh: Float = 0f,
    val distanceKm: Double = 0.0,
    val durationSeconds: Long = 0L,
    val currentLatitude: Double = 19.4326, // Default CDMX or user's GPS
    val currentLongitude: Double = -99.1332,
    val altitudeMeters: Double = 0.0,
    val headingDegrees: Float = 0f,
    val gpsAccuracyMeters: Float = 0f,
    val hasGpsFix: Boolean = false,
    val tripState: TripState = TripState.IDLE,
    val routePoints: List<RoutePoint> = emptyList(),
    val isSimulationMode: Boolean = false
)

class GpsTracker(private val context: Context) {

    private val fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)
    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager

    private val _gpsState = MutableStateFlow(GpsState())
    val gpsState: StateFlow<GpsState> = _gpsState.asStateFlow()

    private var lastKnownLocation: Location? = null
    private var accumulatedDistanceMeters: Double = 0.0
    private var startTimeMillis: Long = 0L
    private var accumulatedDurationSec: Long = 0L
    private var lastResumeTimeMillis: Long = 0L
    private var timerJob: Job? = null
    private var simulationJob: Job? = null
    private val trackerScope = CoroutineScope(Dispatchers.Default)

    private val recordedPoints = mutableListOf<RoutePoint>()
    private val speedSamples = mutableListOf<Float>()

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val location = result.lastLocation ?: return
            handleNewLocation(location)
        }
    }

    private val legacyLocationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            handleNewLocation(location)
        }
        @Deprecated("Deprecated in Java")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
        override fun onProviderEnabled(provider: String) {}
        override fun onProviderDisabled(provider: String) {}
    }

    private var isListening = false

    @SuppressLint("MissingPermission")
    fun startGpsUpdates() {
        if (isListening) return
        isListening = true

        try {
            val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 800L)
                .setMinUpdateIntervalMillis(400L)
                .setMinUpdateDistanceMeters(0.5f)
                .build()

            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper()
            )

            // Also request last known location immediately
            fusedLocationClient.lastLocation.addOnSuccessListener { loc ->
                if (loc != null && _gpsState.value.hasGpsFix.not()) {
                    handleNewLocation(loc)
                }
            }
        } catch (e: Exception) {
            // Fallback to LocationManager
            try {
                locationManager?.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    800L,
                    0.5f,
                    legacyLocationListener,
                    Looper.getMainLooper()
                )
            } catch (ignored: Exception) {}
        }
    }

    fun stopGpsUpdates() {
        if (!isListening) return
        isListening = false
        try {
            fusedLocationClient.removeLocationUpdates(locationCallback)
            locationManager?.removeUpdates(legacyLocationListener)
        } catch (ignored: Exception) {}
    }

    fun startTrip() {
        recordedPoints.clear()
        speedSamples.clear()
        accumulatedDistanceMeters = 0.0
        accumulatedDurationSec = 0L
        startTimeMillis = System.currentTimeMillis()
        lastResumeTimeMillis = SystemClock.elapsedRealtime()

        _gpsState.value = _gpsState.value.copy(
            tripState = TripState.RECORDING,
            maxSpeedKmh = 0f,
            avgSpeedKmh = 0f,
            distanceKm = 0.0,
            durationSeconds = 0L,
            routePoints = emptyList()
        )

        startTimer()
        if (_gpsState.value.isSimulationMode) {
            startSimulation()
        }
    }

    fun pauseTrip() {
        if (_gpsState.value.tripState == TripState.RECORDING) {
            accumulatedDurationSec += (SystemClock.elapsedRealtime() - lastResumeTimeMillis) / 1000L
            timerJob?.cancel()
            simulationJob?.cancel()
            _gpsState.value = _gpsState.value.copy(
                tripState = TripState.PAUSED,
                currentSpeedKmh = 0f
            )
        }
    }

    fun resumeTrip() {
        if (_gpsState.value.tripState == TripState.PAUSED) {
            lastResumeTimeMillis = SystemClock.elapsedRealtime()
            _gpsState.value = _gpsState.value.copy(tripState = TripState.RECORDING)
            startTimer()
            if (_gpsState.value.isSimulationMode) {
                startSimulation()
            }
        }
    }

    fun finishTrip() {
        if (_gpsState.value.tripState == TripState.RECORDING) {
            accumulatedDurationSec += (SystemClock.elapsedRealtime() - lastResumeTimeMillis) / 1000L
        }
        timerJob?.cancel()
        simulationJob?.cancel()
        _gpsState.value = _gpsState.value.copy(
            tripState = TripState.FINISHED,
            currentSpeedKmh = 0f,
            durationSeconds = accumulatedDurationSec
        )
    }

    fun resetTrip() {
        timerJob?.cancel()
        simulationJob?.cancel()
        recordedPoints.clear()
        speedSamples.clear()
        accumulatedDistanceMeters = 0.0
        accumulatedDurationSec = 0L
        _gpsState.value = _gpsState.value.copy(
            tripState = TripState.IDLE,
            currentSpeedKmh = 0f,
            maxSpeedKmh = 0f,
            avgSpeedKmh = 0f,
            distanceKm = 0.0,
            durationSeconds = 0L,
            routePoints = emptyList()
        )
    }

    fun setSimulationMode(enabled: Boolean) {
        _gpsState.value = _gpsState.value.copy(isSimulationMode = enabled)
        if (enabled && _gpsState.value.tripState == TripState.RECORDING) {
            startSimulation()
        } else if (!enabled) {
            simulationJob?.cancel()
        }
    }

    private fun startTimer() {
        timerJob?.cancel()
        timerJob = trackerScope.launch {
            while (isActive) {
                delay(1000L)
                if (_gpsState.value.tripState == TripState.RECORDING) {
                    val currentSec = accumulatedDurationSec + (SystemClock.elapsedRealtime() - lastResumeTimeMillis) / 1000L
                    _gpsState.value = _gpsState.value.copy(durationSeconds = currentSec)
                }
            }
        }
    }

    private fun handleNewLocation(location: Location) {
        if (_gpsState.value.isSimulationMode) return // Ignore actual GPS when simulation is running

        // Speed calculation: m/s -> km/h
        var rawSpeedKmh = if (location.hasSpeed()) {
            location.speed * 3.6f
        } else {
            0f
        }

        // Filter out stationary jitter
        if (rawSpeedKmh < 1.5f) {
            rawSpeedKmh = 0f
        }

        val accuracy = if (location.hasAccuracy()) location.accuracy else 10f
        val hasFix = accuracy < 50f
        val altitude = if (location.hasAltitude()) location.altitude else 0.0
        val bearing = if (location.hasBearing()) location.bearing else _gpsState.value.headingDegrees

        var currentDistance = accumulatedDistanceMeters
        val lastLoc = lastKnownLocation

        if (lastLoc != null && _gpsState.value.tripState == TripState.RECORDING) {
            val dist = lastLoc.distanceTo(location)
            // Filter out GPS jumps / unrealistic motorcycle teleport (> 300 km/h)
            val timeDiffSec = max(0.5f, (location.time - lastLoc.time) / 1000f)
            val calculatedSpeedKmh = (dist / timeDiffSec) * 3.6f

            if (calculatedSpeedKmh < 300f && dist > 1.0f) {
                accumulatedDistanceMeters += dist
                currentDistance = accumulatedDistanceMeters
            }
        }

        lastKnownLocation = location

        var currentMaxSpeed = _gpsState.value.maxSpeedKmh
        var currentAvgSpeed = _gpsState.value.avgSpeedKmh

        if (_gpsState.value.tripState == TripState.RECORDING) {
            if (rawSpeedKmh > currentMaxSpeed) {
                currentMaxSpeed = rawSpeedKmh
            }
            if (rawSpeedKmh > 1.0f) {
                speedSamples.add(rawSpeedKmh)
                currentAvgSpeed = speedSamples.average().toFloat()
            }

            val point = RoutePoint(
                latitude = location.latitude,
                longitude = location.longitude,
                speedKmh = rawSpeedKmh,
                timestamp = System.currentTimeMillis(),
                altitude = altitude
            )
            recordedPoints.add(point)
        }

        _gpsState.value = _gpsState.value.copy(
            currentSpeedKmh = rawSpeedKmh,
            maxSpeedKmh = currentMaxSpeed,
            avgSpeedKmh = currentAvgSpeed,
            distanceKm = currentDistance / 1000.0,
            currentLatitude = location.latitude,
            currentLongitude = location.longitude,
            altitudeMeters = altitude,
            headingDegrees = bearing,
            gpsAccuracyMeters = accuracy,
            hasGpsFix = hasFix,
            routePoints = recordedPoints.toList()
        )
    }

    private fun startSimulation() {
        simulationJob?.cancel()
        simulationJob = trackerScope.launch {
            var simSpeed = 0f
            var targetSpeed = 75f
            var lat = _gpsState.value.currentLatitude
            var lon = _gpsState.value.currentLongitude
            var angle = 0.0
            var heading = 45f

            while (isActive) {
                delay(800L)
                if (_gpsState.value.tripState != TripState.RECORDING) break

                // Dynamic acceleration / braking / curves
                if (simSpeed < targetSpeed) {
                    simSpeed += (3f + (Math.random().toFloat() * 4f))
                    if (simSpeed >= targetSpeed) {
                        targetSpeed = (40f + (Math.random().toFloat() * 85f))
                    }
                } else {
                    simSpeed -= (2f + (Math.random().toFloat() * 3f))
                    if (simSpeed <= targetSpeed) {
                        targetSpeed = (60f + (Math.random().toFloat() * 80f))
                    }
                }
                simSpeed = simSpeed.coerceIn(0f, 160f)

                // Move coordinates forward along curved motorcycle path
                angle += 0.05
                heading = ((Math.sin(angle) * 45f + 60f).toFloat() + 360f) % 360f
                val deltaMeters = (simSpeed / 3.6f) * 0.8f // distance moved in 800ms
                val deltaLat = (deltaMeters * cos(Math.toRadians(heading.toDouble()))) / 111111.0
                val deltaLon = (deltaMeters * sin(Math.toRadians(heading.toDouble()))) / (111111.0 * cos(Math.toRadians(lat)))

                lat += deltaLat
                lon += deltaLon

                accumulatedDistanceMeters += deltaMeters

                val newMax = max(_gpsState.value.maxSpeedKmh, simSpeed)
                speedSamples.add(simSpeed)
                val newAvg = speedSamples.average().toFloat()

                val point = RoutePoint(
                    latitude = lat,
                    longitude = lon,
                    speedKmh = simSpeed,
                    timestamp = System.currentTimeMillis(),
                    altitude = 2240.0 + Math.sin(angle) * 15.0
                )
                recordedPoints.add(point)

                _gpsState.value = _gpsState.value.copy(
                    currentSpeedKmh = simSpeed,
                    maxSpeedKmh = newMax,
                    avgSpeedKmh = newAvg,
                    distanceKm = accumulatedDistanceMeters / 1000.0,
                    currentLatitude = lat,
                    currentLongitude = lon,
                    altitudeMeters = point.altitude ?: 2240.0,
                    headingDegrees = heading,
                    gpsAccuracyMeters = 3.5f,
                    hasGpsFix = true,
                    routePoints = recordedPoints.toList()
                )
            }
        }
    }
}
