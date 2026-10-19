package com.ridecomputer.tracking

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class GpsSpeedProcessorTest {

    private lateinit var processor: GpsSpeedProcessor

    @Before
    fun setUp() {
        // Default smoothing factor 1.0 in some tests or test with smoothing directly
        processor = GpsSpeedProcessor(
            maxAccuracyThresholdM = 25f,
            signalLossTimeoutMs = 3500L,
            smoothingFactor = 1.0f, // 1.0f to test raw calculation output directly where needed
        )
    }

    @Test
    fun testSpeedConversionMpsToKmh() {
        // Direct formula verification: speedKmh = speedMps * 3.6f
        assertEquals(0.0f, GpsSpeedProcessor.convertMpsToKmh(0.0f), 0.001f)
        assertEquals(18.0f, GpsSpeedProcessor.convertMpsToKmh(5.0f), 0.001f)
        assertEquals(36.0f, GpsSpeedProcessor.convertMpsToKmh(10.0f), 0.001f)
        assertEquals(45.0f, GpsSpeedProcessor.convertMpsToKmh(12.5f), 0.001f)
        assertEquals(72.0f, GpsSpeedProcessor.convertMpsToKmh(20.0f), 0.001f)

        // Pipeline verification with Doppler speed
        val startPoint = GpsPoint(
            latitude = -6.2088,
            longitude = 106.8456,
            speedMps = 0f,
            accuracyM = 5f,
            timestampMs = 1000L,
        )
        processor.processFix(startPoint)

        // Standstill to 5.0 m/s (~18.0 km/h)
        val fix18Kmh = GpsPoint(
            latitude = -6.20875,
            longitude = 106.8456,
            speedMps = 5.0f,
            accuracyM = 4f,
            timestampMs = 2000L,
        )
        val res18 = processor.processFix(fix18Kmh)
        assertNotNull(res18.speedKmh)
        assertEquals(18.0f, res18.speedKmh!!, 0.05f)

        // Accelerate smoothly to 8.5 m/s (~30.6 km/h)
        val fix30Kmh = GpsPoint(
            latitude = -6.20865,
            longitude = 106.8456,
            speedMps = 8.5f,
            accuracyM = 4f,
            timestampMs = 3000L,
        )
        val res30 = processor.processFix(fix30Kmh)
        assertEquals(30.6f, res30.speedKmh!!, 0.05f)

        // Accelerate smoothly to 12.0 m/s (~43.2 km/h)
        val fix43Kmh = GpsPoint(
            latitude = -6.20850,
            longitude = 106.8456,
            speedMps = 12.0f,
            accuracyM = 3f,
            timestampMs = 4000L,
        )
        val res43 = processor.processFix(fix43Kmh)
        assertEquals(43.2f, res43.speedKmh!!, 0.05f)
    }

    @Test
    fun testRejectsInvalidCoordinates() {
        val nullIsland = GpsPoint(latitude = 0.0, longitude = 0.0, speedMps = 5f, accuracyM = 5f, timestampMs = 1000L)
        val res1 = processor.processFix(nullIsland)
        assertFalse(res1.isFixAccepted)
        assertEquals(0f, res1.deltaDistanceM, 0.01f)

        val outOfBoundsLat = GpsPoint(latitude = 95.0, longitude = 100.0, speedMps = 5f, accuracyM = 5f, timestampMs = 2000L)
        val res2 = processor.processFix(outOfBoundsLat)
        assertFalse(res2.isFixAccepted)

        val outOfBoundsLon = GpsPoint(latitude = 10.0, longitude = 190.0, speedMps = 5f, accuracyM = 5f, timestampMs = 3000L)
        val res3 = processor.processFix(outOfBoundsLon)
        assertFalse(res3.isFixAccepted)

        val nanCoord = GpsPoint(latitude = Double.NaN, longitude = 100.0, speedMps = 5f, accuracyM = 5f, timestampMs = 4000L)
        val res4 = processor.processFix(nanCoord)
        assertFalse(res4.isFixAccepted)
    }

    @Test
    fun testRejectsPoorAccuracyFixes() {
        val goodFix = GpsPoint(latitude = -6.2088, longitude = 106.8456, speedMps = 5f, accuracyM = 8f, timestampMs = 1000L)
        assertTrue(processor.processFix(goodFix).isFixAccepted)

        // Poor accuracy (> 25m)
        val poorAccuracyFix = GpsPoint(latitude = -6.2080, longitude = 106.8450, speedMps = 15f, accuracyM = 45f, timestampMs = 2000L)
        val resPoor = processor.processFix(poorAccuracyFix)
        assertFalse(resPoor.isFixAccepted)
        assertEquals(0f, resPoor.deltaDistanceM, 0.01f)

        // Null accuracy
        val nullAccuracyFix = GpsPoint(latitude = -6.2081, longitude = 106.8451, speedMps = 5f, accuracyM = null, timestampMs = 3000L)
        assertFalse(processor.processFix(nullAccuracyFix).isFixAccepted)
    }

    @Test
    fun testRejectsStaleAndDuplicateTimestamps() {
        val fix1 = GpsPoint(latitude = -6.2088, longitude = 106.8456, speedMps = 5f, accuracyM = 5f, timestampMs = 5000L)
        assertTrue(processor.processFix(fix1).isFixAccepted)

        // Duplicate timestamp
        val fixDuplicate = GpsPoint(latitude = -6.2089, longitude = 106.8457, speedMps = 6f, accuracyM = 5f, timestampMs = 5000L)
        val resDup = processor.processFix(fixDuplicate)
        assertFalse(resDup.isFixAccepted)

        // Out of order / older timestamp
        val fixOlder = GpsPoint(latitude = -6.2089, longitude = 106.8457, speedMps = 6f, accuracyM = 5f, timestampMs = 4000L)
        val resOlder = processor.processFix(fixOlder)
        assertFalse(resOlder.isFixAccepted)
    }

    @Test
    fun testStationaryDriftFiltering() {
        // Cyclist stopped at red light. GPS antenna jitters ±3-5 meters.
        val baseLat = -6.208800
        val baseLon = 106.845600

        var currentTime = 10000L
        val initFix = GpsPoint(
            latitude = baseLat,
            longitude = baseLon,
            speedMps = 0f,
            accuracyM = 6f,
            timestampMs = currentTime,
        )
        processor.processFix(initFix)

        var totalAccumulatedDistance = 0f

        // 10 jitter updates while stationary (Doppler speed is 0 or < 0.3 m/s)
        val jitterOffsets = listOf(
            0.000030 to 0.000020, // ~3.5m jitter
            -0.000020 to 0.000030,
            0.000010 to -0.000025,
            -0.000015 to -0.000010,
            0.000025 to 0.000015,
        )

        for ((dLat, dLon) in jitterOffsets) {
            currentTime += 1000L
            val jitterFix = GpsPoint(
                latitude = baseLat + dLat,
                longitude = baseLon + dLon,
                speedMps = 0.15f, // Doppler speed below stationary threshold (0.3 m/s)
                accuracyM = 6f,
                timestampMs = currentTime,
            )
            val result = processor.processFix(jitterFix)
            assertTrue(result.isFixAccepted)
            // Speed must be 0 km/h
            assertEquals(0f, result.speedKmh ?: -1f, 0.01f)
            totalAccumulatedDistance += result.deltaDistanceM
        }

        // Distance should NOT accumulate from stationary jitter
        assertEquals(0f, totalAccumulatedDistance, 0.01f)
    }

    @Test
    fun testDistanceDerivedSpeedFallbackWhenDopplerMissing() {
        // Location missing Doppler speed (speedMps = null)
        val startPoint = GpsPoint(
            latitude = -6.208800,
            longitude = 106.845600,
            speedMps = null,
            accuracyM = 4f,
            timestampMs = 10000L,
        )
        processor.processFix(startPoint)

        // Cyclist moves ~7 meters in 1 second (~7 m/s = 25.2 km/h)
        // 0.000063 deg lat is approximately 7.0 meters
        val movePoint = GpsPoint(
            latitude = -6.208800 + 0.000063,
            longitude = 106.845600,
            speedMps = null,
            accuracyM = 4f,
            timestampMs = 11000L, // dt = 1.0s
        )
        val res = processor.processFix(movePoint)
        assertTrue(res.isFixAccepted)
        assertNotNull(res.speedKmh)
        // Distance ~7m / 1s = 7 m/s = ~25.2 km/h
        assertEquals(25.2f, res.speedKmh!!, 1.5f)
        assertTrue(res.deltaDistanceM in 6.5f..7.5f)
    }

    @Test
    fun testDistanceDerivedSpeedRejectsSmallDisplacementsWithinNoiseRadius() {
        // Two fixes with missing Doppler speed, but displacement is only 0.8 meters (GPS noise)
        val p1 = GpsPoint(
            latitude = -6.208800,
            longitude = 106.845600,
            speedMps = null,
            accuracyM = 8f,
            timestampMs = 10000L,
        )
        processor.processFix(p1)

        // 0.8 meter drift
        val p2 = GpsPoint(
            latitude = -6.208800 + 0.000007,
            longitude = 106.845600,
            speedMps = null,
            accuracyM = 8f,
            timestampMs = 11000L,
        )
        val res = processor.processFix(p2)
        // Since displacement is within noise radius, derived speed should be 0
        assertEquals(0f, res.speedKmh ?: -1f, 0.01f)
        assertEquals(0f, res.deltaDistanceM, 0.01f)
    }

    @Test
    fun testDopplerSpeedHasPriorityOverPositionDifferencing() {
        // Start fix
        val p1 = GpsPoint(
            latitude = -6.208800,
            longitude = 106.845600,
            speedMps = 8.0f, // 28.8 km/h
            accuracyM = 4f,
            timestampMs = 10000L,
        )
        processor.processFix(p1)

        // Next fix has exact Doppler speed = 8.0 m/s (28.8 km/h),
        // even if coordinate shifted slightly more or less due to normal GPS multipath
        val p2 = GpsPoint(
            latitude = -6.208800 + 0.000085, // ~9.4m displacement
            longitude = 106.845600,
            speedMps = 8.0f,
            accuracyM = 4f,
            timestampMs = 11000L,
        )
        val res = processor.processFix(p2)
        // Must use Doppler speed (8.0 * 3.6 = 28.8 km/h), not 9.4 * 3.6 = 33.8 km/h
        assertEquals(28.8f, res.speedKmh!!, 0.1f)
    }

    @Test
    fun testKinematicJumpConstraintPreventsUnrealisticAccelerationSpike() {
        // Cyclist riding at 20 km/h (~5.56 m/s)
        val p1 = GpsPoint(
            latitude = -6.208800,
            longitude = 106.845600,
            speedMps = 5.56f,
            accuracyM = 3f,
            timestampMs = 10000L,
        )
        processor.processFix(p1)

        // In 1 second, a GPS multipath glitch reports 80 km/h (22.2 m/s)
        // Acceleration = 16.6 m/s^2, which is impossible on a bicycle (max ~4.0 m/s^2)
        val spikePoint = GpsPoint(
            latitude = -6.208900,
            longitude = 106.845600,
            speedMps = 22.2f,
            accuracyM = 5f,
            timestampMs = 11000L, // dt = 1.0s
        )
        val res = processor.processFix(spikePoint)

        // Speed should be constrained by max acceleration (5.56 + 4.0 = 9.56 m/s -> ~34.4 km/h)
        // NOT 80 km/h!
        val speedKmh = res.speedKmh ?: 0f
        assertTrue("Speed $speedKmh should be constrained below 40 km/h", speedKmh < 40f)
    }

    @Test
    fun testNoArbitrarySpeedCapOnFastCyclingDescent() {
        // A cyclist descending a steep hill can realistically reach 65-75 km/h
        // The algorithm should NOT artificially cap valid high speeds
        var time = 10000L
        var lat = -6.208800
        var speedMps = 10.0f // Start at 36 km/h

        processor.processFix(
            GpsPoint(latitude = lat, longitude = 106.8456, speedMps = speedMps, accuracyM = 3f, timestampMs = time)
        )

        // Accelerate smoothly downhill: +2.5 m/s every second (realistic downhill acceleration)
        // 10 -> 12.5 -> 15.0 -> 17.5 -> 20.0 m/s (72.0 km/h)
        for (i in 1..4) {
            time += 1000L
            speedMps += 2.5f
            lat += 0.00015
            val fix = GpsPoint(
                latitude = lat,
                longitude = 106.8456,
                speedMps = speedMps,
                accuracyM = 3f,
                timestampMs = time,
            )
            val res = processor.processFix(fix)
            assertNotNull(res.speedKmh)
        }

        // Final speed is 20.0 m/s * 3.6 = 72.0 km/h. Must NOT be capped at 50 km/h!
        val finalFix = GpsPoint(
            latitude = lat + 0.00018,
            longitude = 106.8456,
            speedMps = 20.0f,
            accuracyM = 3f,
            timestampMs = time + 1000L,
        )
        val finalRes = processor.processFix(finalFix)
        assertEquals(72.0f, finalRes.speedKmh!!, 0.5f)
    }

    @Test
    fun testSignalLossDetection() {
        val fixTime = 20000L
        val fix = GpsPoint(
            latitude = -6.2088,
            longitude = 106.8456,
            speedMps = 6.0f,
            accuracyM = 4f,
            timestampMs = fixTime,
        )
        val res = processor.processFix(fix, currentTimeMs = fixTime)
        assertTrue(res.isFixAccepted)
        assertFalse(res.isSignalLost)

        // After 2.0 seconds: signal still alive (< 3500ms timeout)
        assertFalse(processor.isSignalLost(currentTimeMs = fixTime + 2000L))

        // After 4.0 seconds (> 3500ms timeout): signal lost!
        assertTrue(processor.isSignalLost(currentTimeMs = fixTime + 4000L))

        // If fix is rejected during signal loss, speed is returned as null
        val rejectedFix = GpsPoint(
            latitude = 0.0,
            longitude = 0.0,
            speedMps = 0f,
            accuracyM = 5f,
            timestampMs = fixTime + 4000L,
        )
        val resRejected = processor.processFix(rejectedFix, currentTimeMs = fixTime + 4000L)
        assertTrue(resRejected.isSignalLost)
        assertNull(resRejected.speedKmh)
    }

    @Test
    fun testSignalRecoveryAfterLoss() {
        val t0 = 10000L
        processor.processFix(GpsPoint(-6.2088, 106.8456, 5f, 4f, t0), currentTimeMs = t0)

        // Signal lost after 5s
        assertTrue(processor.isSignalLost(t0 + 5000L))

        // Signal recovers with a valid fix
        val tRecover = t0 + 6000L
        val recoverFix = GpsPoint(-6.2080, 106.8456, 6f, 4f, tRecover)
        val resRecover = processor.processFix(recoverFix, currentTimeMs = tRecover)

        assertTrue(resRecover.isFixAccepted)
        assertFalse(resRecover.isSignalLost)
        assertFalse(processor.isSignalLost(tRecover))
        // 6 m/s * 3.6 = 21.6 km/h
        assertEquals(21.6f, resRecover.speedKmh!!, 0.2f)
    }

    @Test
    fun testInstantZeroSpeedWhenStopping() {
        // Verify rider coming to a dead stop gets 0.0 km/h immediately without sluggish decay
        val defaultSmoothedProcessor = GpsSpeedProcessor(smoothingFactor = 0.65f)

        // Riding at 25 km/h (~6.94 m/s)
        val p1 = GpsPoint(-6.2088, 106.8456, 6.94f, 4f, 10000L)
        val p2 = GpsPoint(-6.2087, 106.8456, 6.94f, 4f, 11000L)
        defaultSmoothedProcessor.processFix(p1)
        defaultSmoothedProcessor.processFix(p2)

        // Full stop at red light (Doppler speed = 0.0 m/s)
        val stopPoint = GpsPoint(-6.2086, 106.8456, 0.0f, 4f, 12000L)
        val stopRes = defaultSmoothedProcessor.processFix(stopPoint)

        assertEquals(0f, stopRes.speedKmh ?: -1f, 0.001f)
    }
}
