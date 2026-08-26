package com.example.data.model

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class RoutePoint(
    val latitude: Double,
    val longitude: Double,
    val speedKmh: Float,
    val timestamp: Long,
    val altitude: Double? = null
)
