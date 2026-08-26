package com.example.data.repository

import com.example.data.database.TripDao
import com.example.data.model.RoutePoint
import com.example.data.model.TripEntity
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class TripRepository(private val tripDao: TripDao) {

    private val moshi: Moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private val listType = Types.newParameterizedType(List::class.java, RoutePoint::class.java)
    private val adapter = moshi.adapter<List<RoutePoint>>(listType)

    val allTrips: Flow<List<TripEntity>> = tripDao.getAllTrips()
    val totalDistanceKm: Flow<Double?> = tripDao.getTotalDistanceKm()
    val overallMaxSpeed: Flow<Float?> = tripDao.getOverallMaxSpeed()
    val tripCount: Flow<Int> = tripDao.getTripCount()

    fun getTripById(id: Long): Flow<TripEntity?> = tripDao.getTripById(id)

    suspend fun saveTrip(
        title: String,
        startTime: Long,
        endTime: Long,
        durationSeconds: Long,
        distanceKm: Double,
        maxSpeedKmh: Float,
        avgSpeedKmh: Float,
        routePoints: List<RoutePoint>
    ): Long = withContext(Dispatchers.IO) {
        val json = try {
            adapter.toJson(routePoints)
        } catch (e: Exception) {
            "[]"
        }
        val entity = TripEntity(
            title = title,
            startTime = startTime,
            endTime = endTime,
            durationSeconds = durationSeconds,
            distanceKm = distanceKm,
            maxSpeedKmh = maxSpeedKmh,
            avgSpeedKmh = avgSpeedKmh,
            routePointsJson = json
        )
        tripDao.insertTrip(entity)
    }

    suspend fun deleteTrip(trip: TripEntity) = withContext(Dispatchers.IO) {
        tripDao.deleteTrip(trip)
    }

    suspend fun deleteTripById(id: Long) = withContext(Dispatchers.IO) {
        tripDao.deleteTripById(id)
    }

    fun parseRoutePoints(json: String): List<RoutePoint> {
        return try {
            adapter.fromJson(json) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }
}
