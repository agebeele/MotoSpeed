package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.database.AppDatabase
import com.example.data.model.RoutePoint
import com.example.data.model.TripEntity
import com.example.data.repository.TripRepository
import com.example.location.GpsState
import com.example.location.GpsTracker
import com.example.location.MotoSensors
import com.example.location.SensorData
import com.example.location.TripState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class AppTab {
    SPEEDOMETER,
    MAP,
    HISTORY,
    SETTINGS
}

data class AppSettings(
    val isHudMode: Boolean = false,
    val speedLimitAlertKmh: Float = 110f,
    val isSpeedAlertEnabled: Boolean = true,
    val useMetricUnits: Boolean = true, // km/h & km
    val showSpeedHeatmap: Boolean = true,
    val keepScreenOn: Boolean = true,
    val highContrastCockpit: Boolean = true
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AppDatabase.getDatabase(application)
    private val repository = TripRepository(database.tripDao())

    private val gpsTracker = GpsTracker(application)
    private val motoSensors = MotoSensors(application)

    val gpsState: StateFlow<GpsState> = gpsTracker.gpsState
    val sensorData: StateFlow<SensorData> = motoSensors.sensorData

    val allTrips: StateFlow<List<TripEntity>> = repository.allTrips
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val totalOdometerKm: StateFlow<Double?> = repository.totalDistanceKm
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    val overallMaxSpeedKmh: StateFlow<Float?> = repository.overallMaxSpeed
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0f)

    private val _currentTab = MutableStateFlow(AppTab.SPEEDOMETER)
    val currentTab: StateFlow<AppTab> = _currentTab.asStateFlow()

    private val _settings = MutableStateFlow(AppSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    private val _selectedTripDetail = MutableStateFlow<TripEntity?>(null)
    val selectedTripDetail: StateFlow<TripEntity?> = _selectedTripDetail.asStateFlow()

    private val _savedTripToast = MutableStateFlow<String?>(null)
    val savedTripToast: StateFlow<String?> = _savedTripToast.asStateFlow()

    init {
        gpsTracker.startGpsUpdates()
        motoSensors.start()
    }

    override fun onCleared() {
        super.onCleared()
        gpsTracker.stopGpsUpdates()
        motoSensors.stop()
    }

    fun setTab(tab: AppTab) {
        _currentTab.value = tab
    }

    fun startTrip() {
        gpsTracker.startTrip()
        motoSensors.resetPeaks()
    }

    fun pauseTrip() {
        gpsTracker.pauseTrip()
    }

    fun resumeTrip() {
        gpsTracker.resumeTrip()
    }

    fun finishAndSaveTrip(customTitle: String? = null) {
        val currentState = gpsTracker.gpsState.value
        gpsTracker.finishTrip()

        viewModelScope.launch {
            val title = if (!customTitle.isNullOrBlank()) {
                customTitle
            } else {
                val timeStr = SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault()).format(Date())
                "Ruta en Moto ($timeStr)"
            }

            val startTime = System.currentTimeMillis() - (currentState.durationSeconds * 1000L)
            val endTime = System.currentTimeMillis()

            repository.saveTrip(
                title = title,
                startTime = startTime,
                endTime = endTime,
                durationSeconds = currentState.durationSeconds,
                distanceKm = currentState.distanceKm,
                maxSpeedKmh = currentState.maxSpeedKmh,
                avgSpeedKmh = currentState.avgSpeedKmh,
                routePoints = currentState.routePoints
            )

            _savedTripToast.value = "¡Ruta guardada con éxito! ${String.format(Locale.US, "%.2f km", currentState.distanceKm)}"
        }
    }

    fun resetTrip() {
        gpsTracker.resetTrip()
        motoSensors.resetPeaks()
    }

    fun deleteTrip(id: Long) {
        viewModelScope.launch {
            repository.deleteTripById(id)
            if (_selectedTripDetail.value?.id == id) {
                _selectedTripDetail.value = null
            }
        }
    }

    fun selectTripDetail(trip: TripEntity?) {
        _selectedTripDetail.value = trip
    }

    fun parsePoints(trip: TripEntity): List<RoutePoint> {
        return repository.parseRoutePoints(trip.routePointsJson)
    }

    fun toggleHudMode() {
        _settings.value = _settings.value.copy(isHudMode = !_settings.value.isHudMode)
    }

    fun toggleSimulationMode() {
        val next = !gpsTracker.gpsState.value.isSimulationMode
        gpsTracker.setSimulationMode(next)
    }

    fun setSpeedLimit(limit: Float) {
        _settings.value = _settings.value.copy(speedLimitAlertKmh = limit)
    }

    fun toggleSpeedAlert(enabled: Boolean) {
        _settings.value = _settings.value.copy(isSpeedAlertEnabled = enabled)
    }

    fun toggleMetricUnits(metric: Boolean) {
        _settings.value = _settings.value.copy(useMetricUnits = metric)
    }

    fun toggleHeatmap(show: Boolean) {
        _settings.value = _settings.value.copy(showSpeedHeatmap = show)
    }

    fun clearToast() {
        _savedTripToast.value = null
    }
}
