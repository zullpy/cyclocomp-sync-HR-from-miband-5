package com.ridecomputer.tracking

import android.location.Location
import org.junit.Assert.assertEquals
import org.junit.Test

class LocationProbeTest {
    @Test
    fun testLocationCreation() {
        val loc = Location("gps")
        loc.latitude = -6.2088
        loc.longitude = 106.8456
        loc.speed = 5.0f
        println("Loc lat=${loc.latitude}, speed=${loc.speed}")
    }
}
