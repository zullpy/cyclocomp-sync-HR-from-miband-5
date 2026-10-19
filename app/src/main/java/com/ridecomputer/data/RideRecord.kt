package com.ridecomputer.data

data class RideRecord(
    val id: String,
    val startTime: Long,
    val endTime: Long,
    val durationSeconds: Long,
    val distanceKm: Float,
    val avgSpeedKmh: Float,
    val maxSpeedKmh: Float,
    val avgHeartRate: Int? = null,
    val maxHeartRate: Int? = null,
    val isImported: Boolean = false,
    val elevationGainM: Float? = null,
    val routeName: String? = null,
    val hasRoute: Boolean = false,
)
