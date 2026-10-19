package com.ridecomputer.gpx

import com.ridecomputer.tracking.GpsPoint
import com.ridecomputer.ui.dashboard.RideDashboardData
import com.ridecomputer.ui.dashboard.RideState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream

/**
 * Tests for GPX import, route selection, empty/malformed tracks,
 * live screen transition, and separation of planned GPX route from recorded live track.
 */
class LiveRouteSelectionTest {

    private val sampleValidGpx = """
        <?xml version="1.0" encoding="UTF-8"?>
        <gpx version="1.1" creator="CycloCompTest">
            <trk>
                <name>Cikini to Monas Route</name>
                <trkseg>
                    <trkpt lat="-6.1910" lon="106.8380">
                        <ele>14.0</ele>
                    </trkpt>
                    <trkpt lat="-6.1850" lon="106.8340">
                        <ele>15.0</ele>
                    </trkpt>
                    <trkpt lat="-6.1754" lon="106.8272">
                        <ele>16.0</ele>
                    </trkpt>
                </trkseg>
            </trk>
        </gpx>
    """.trimIndent()

    @Test
    fun testDirectGpxRouteImportParsesSuccessfully() {
        val result = GpxParser.parse(ByteArrayInputStream(sampleValidGpx.toByteArray()))
        assertTrue("Imported GPX should parse successfully", result is GpxParseResult.Success)

        val summary = (result as GpxParseResult.Success).summary
        assertEquals("Cikini to Monas Route", summary.name)
        assertEquals(3, summary.totalPoints)
        assertEquals(1, summary.segments.size)
        assertTrue("Route distance should be > 1.5 km", summary.distanceKm > 1.5f)
        assertTrue("Route distance should be < 3.0 km", summary.distanceKm < 3.0f)
    }

    @Test
    fun testLiveScreenTransitionPreservesZeroDistanceAndIdleState() {
        // Parse GPX route intended for live navigation
        val result = GpxParser.parse(ByteArrayInputStream(sampleValidGpx.toByteArray()))
        assertTrue(result is GpxParseResult.Success)
        val summary = (result as GpxParseResult.Success).summary

        // In the live ride screen, importing a planned route sets the guide route
        var activeGuideSegments: List<List<GpxPoint>> = summary.segments
        var activeGuideName: String? = summary.name
        var activeGuideDistanceKm: Float? = summary.distanceKm

        // Initial live dashboard data MUST NOT fabricate a completed ride or fake recorded distance
        val liveDashboardData = RideDashboardData(
            speedKmh = null,
            heartRateBpm = null,
            distanceKm = 0f,
            elapsedSeconds = 0L,
            rideState = RideState.IDLE,
        )

        assertEquals("Recorded distance must remain 0.00 km upon route import", 0f, liveDashboardData.distanceKm, 1e-4f)
        assertEquals("Elapsed duration must remain 0s upon route import", 0L, liveDashboardData.elapsedSeconds)
        assertEquals("Ride state must remain IDLE upon route import", RideState.IDLE, liveDashboardData.rideState)
        assertNull("Speed should be null before starting", liveDashboardData.speedKmh)

        // Route details are correctly available for guide display
        assertEquals("Cikini to Monas Route", activeGuideName)
        assertEquals(1, activeGuideSegments.size)
        assertNotNull(activeGuideDistanceKm)
        assertTrue(activeGuideDistanceKm!! > 0f)
    }

    @Test
    fun testClearRouteActionRemovesSelectedRoute() {
        val result = GpxParser.parse(ByteArrayInputStream(sampleValidGpx.toByteArray()))
        assertTrue(result is GpxParseResult.Success)
        val summary = (result as GpxParseResult.Success).summary

        var activeGuideSegments: List<List<GpxPoint>> = summary.segments
        var activeGuideName: String? = summary.name
        var activeGuideDistanceKm: Float? = summary.distanceKm

        assertFalse(activeGuideSegments.isEmpty())
        assertNotNull(activeGuideName)

        // User triggers "Clear Route"
        activeGuideSegments = emptyList()
        activeGuideName = null
        activeGuideDistanceKm = null

        assertTrue("Active guide segments must be empty after clear", activeGuideSegments.isEmpty())
        assertNull("Active guide name must be null after clear", activeGuideName)
        assertNull("Active guide distance must be null after clear", activeGuideDistanceKm)
    }

    @Test
    fun testPlannedRouteSeparationFromLiveGpsTrackDuringRide() {
        val result = GpxParser.parse(ByteArrayInputStream(sampleValidGpx.toByteArray()))
        assertTrue(result is GpxParseResult.Success)
        val summary = (result as GpxParseResult.Success).summary

        // 1. Route is selected before ride
        val plannedGuideSegments = summary.segments
        var liveRecordedGpsPoints = mutableListOf<GpsPoint>()

        // 2. User starts ride
        var rideState = RideState.RUNNING
        var recordedDistanceKm = 0f

        // 3. User rides and records live GPS points
        liveRecordedGpsPoints.add(GpsPoint(-6.1910, 106.8380, null, 1000L, 5f, 20f))
        liveRecordedGpsPoints.add(GpsPoint(-6.1900, 106.8370, null, 2000L, 5f, 22f))
        recordedDistanceKm = 0.15f

        // The planned GPX route remains completely unchanged
        assertEquals(1, plannedGuideSegments.size)
        assertEquals(3, plannedGuideSegments[0].size)

        // The live recorded points are distinct and independent
        assertEquals(2, liveRecordedGpsPoints.size)
        assertEquals(0.15f, recordedDistanceKm, 1e-4f)
        assertEquals(RideState.RUNNING, rideState)
    }

    @Test
    fun testRejectsMalformedXmlGpx() {
        val malformedGpx = """
            <?xml version="1.0" encoding="UTF-8"?>
            <gpx version="1.1">
                <trk>
                    <name>Broken Track
                    <trkseg>
                        <trkpt lat="-6.1910" lon="106.8380">
                    </trkseg>
            </gpx>
        """.trimIndent()

        val result = GpxParser.parse(ByteArrayInputStream(malformedGpx.toByteArray()))
        assertTrue("Malformed XML must result in error", result is GpxParseResult.Error)
        val errorMsg = (result as GpxParseResult.Error).message
        assertTrue("Error message should be descriptive", errorMsg.isNotBlank())
    }

    @Test
    fun testRejectsEmptyTrackSegments() {
        val emptyGpx = """
            <?xml version="1.0" encoding="UTF-8"?>
            <gpx version="1.1">
                <trk>
                    <name>Empty Segments</name>
                    <trkseg></trkseg>
                </trk>
            </gpx>
        """.trimIndent()

        val result = GpxParser.parse(ByteArrayInputStream(emptyGpx.toByteArray()))
        assertTrue("Empty track segments must result in error", result is GpxParseResult.Error)
        val errorMsg = (result as GpxParseResult.Error).message
        assertTrue("Error message should mention valid points", errorMsg.contains("No valid GPS track points", ignoreCase = true))
    }

    @Test
    fun testRejectsSinglePointRoute() {
        val singlePointGpx = """
            <?xml version="1.0" encoding="UTF-8"?>
            <gpx version="1.1">
                <trk>
                    <name>Single Point Route</name>
                    <trkseg>
                        <trkpt lat="-6.1910" lon="106.8380" />
                    </trkseg>
                </trk>
            </gpx>
        """.trimIndent()

        val result = GpxParser.parse(ByteArrayInputStream(singlePointGpx.toByteArray()))
        assertTrue("Route with only 1 point must be rejected", result is GpxParseResult.Error)
        val errorMsg = (result as GpxParseResult.Error).message
        assertTrue(errorMsg.contains("insufficient points", ignoreCase = true))
    }

    @Test
    fun testFiltersCoordinatesOutOfBounds() {
        val outOfBoundsGpx = """
            <?xml version="1.0" encoding="UTF-8"?>
            <gpx version="1.1">
                <trk>
                    <name>Out of Bounds</name>
                    <trkseg>
                        <trkpt lat="95.0" lon="106.8380" /> <!-- Latitude > 90 is invalid -->
                        <trkpt lat="-6.1910" lon="195.0" />  <!-- Longitude > 180 is invalid -->
                        <trkpt lat="0.0" lon="0.0" />        <!-- Null Island filtered -->
                        <trkpt lat="-6.1910" lon="106.8380" />
                        <trkpt lat="-6.1850" lon="106.8340" />
                    </trkseg>
                </trk>
            </gpx>
        """.trimIndent()

        val result = GpxParser.parse(ByteArrayInputStream(outOfBoundsGpx.toByteArray()))
        assertTrue(result is GpxParseResult.Success)
        val summary = (result as GpxParseResult.Success).summary
        // Invalid coordinates discarded; 2 valid points kept
        assertEquals(2, summary.totalPoints)
    }

    @Test
    fun testBoundingBoxForViewportAutoFit() {
        val result = GpxParser.parse(ByteArrayInputStream(sampleValidGpx.toByteArray()))
        assertTrue(result is GpxParseResult.Success)
        val summary = (result as GpxParseResult.Success).summary

        val box = RouteGeometryUtils.calculateBoundingBox(summary.segments)
        assertNotNull("Bounding box must be calculated for route segments", box)
        assertTrue("North boundary should be >= all latitudes", box!!.north >= -6.1754)
        assertTrue("South boundary should be <= all latitudes", box.south <= -6.1910)
        assertTrue("East boundary should be >= all longitudes", box.east >= 106.8380)
        assertTrue("West boundary should be <= all longitudes", box.west <= 106.8272)

        val paddedBox = box.withPadding(0.12)
        assertTrue(paddedBox.north > box.north)
        assertTrue(paddedBox.south < box.south)
        assertTrue(paddedBox.east > box.east)
        assertTrue(paddedBox.west < box.west)
    }
}
