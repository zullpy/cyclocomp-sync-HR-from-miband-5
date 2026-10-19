package com.ridecomputer.gpx

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteGeometryUtilsTest {

    @Test
    fun testBoundingBoxCalculation() {
        val seg1 = listOf(
            GpxPoint(latitude = -6.2000, longitude = 106.8000),
            GpxPoint(latitude = -6.2100, longitude = 106.8100),
        )
        val seg2 = listOf(
            GpxPoint(latitude = -6.2200, longitude = 106.8200),
            GpxPoint(latitude = -6.1900, longitude = 106.8300),
        )

        val box = RouteGeometryUtils.calculateBoundingBox(listOf(seg1, seg2))
        assertNotNull(box)
        assertEquals(-6.1900, box!!.north, 1e-5)
        assertEquals(-6.2200, box.south, 1e-5)
        assertEquals(106.8300, box.east, 1e-5)
        assertEquals(106.8000, box.west, 1e-5)

        assertEquals((-6.1900 + -6.2200) / 2.0, box.centerLat, 1e-5)
        assertEquals((106.8300 + 106.8000) / 2.0, box.centerLon, 1e-5)
    }

    @Test
    fun testBoundingBoxEmptyReturnsNull() {
        val emptyBox = RouteGeometryUtils.calculateBoundingBox(emptyList())
        assertNull(emptyBox)

        val emptySegs = RouteGeometryUtils.calculateBoundingBox(listOf(emptyList(), emptyList()))
        assertNull(emptySegs)
    }

    @Test
    fun testBoundingBoxPadding() {
        val box = RouteBoundingBox(
            north = 10.0,
            south = 0.0,
            east = 20.0,
            west = 10.0,
        )
        val padded = box.withPadding(0.1)
        assertTrue(padded.north > box.north)
        assertTrue(padded.south < box.south)
        assertTrue(padded.east > box.east)
        assertTrue(padded.west < box.west)
    }

    @Test
    fun testDouglasPeuckerCollinearPointsSimplification() {
        // 10 collinear points along latitude line from -6.2000 to -6.2090
        val points = (0..9).map { i ->
            GpxPoint(latitude = -6.2000 - (i * 0.001), longitude = 106.8000)
        }

        val simplified = RouteGeometryUtils.simplifyPoints(points, toleranceMeters = 3.0)
        // Collinear line should simplify down to just 2 endpoints
        assertEquals(2, simplified.size)
        assertEquals(points.first().latitude, simplified.first().latitude, 1e-6)
        assertEquals(points.last().latitude, simplified.last().latitude, 1e-6)
    }

    @Test
    fun testDouglasPeuckerPreservesProminentCurve() {
        // Point A, detour point B (~100 meters away), Point C
        val p1 = GpxPoint(latitude = -6.2000, longitude = 106.8000)
        val p2 = GpxPoint(latitude = -6.2000, longitude = 106.8010) // ~111m east detour
        val p3 = GpxPoint(latitude = -6.2020, longitude = 106.8000)

        val points = listOf(p1, p2, p3)
        val simplified = RouteGeometryUtils.simplifyPoints(points, toleranceMeters = 5.0)

        // Detour exceeds tolerance: must keep all 3 points
        assertEquals(3, simplified.size)
    }

    @Test
    fun testSimplifyMultiSegmentsMaintainsSegmentSeparation() {
        val seg1 = (0..5).map { i -> GpxPoint(-6.2000 + i * 0.0001, 106.8000) }
        val seg2 = (0..5).map { i -> GpxPoint(-6.3000 + i * 0.0001, 106.9000) }

        val simplified = RouteGeometryUtils.simplifySegments(listOf(seg1, seg2), toleranceMeters = 3.0)
        assertEquals(2, simplified.size)
        assertEquals(2, simplified[0].size)
        assertEquals(2, simplified[1].size)
    }

    @Test
    fun testPerpendicularDistanceCalculation() {
        val a = GpxPoint(-6.2000, 106.8000)
        val b = GpxPoint(-6.2000, 106.8100) // horizontal line
        val pOnLine = GpxPoint(-6.2000, 106.8050)
        val pOffLine = GpxPoint(-6.2010, 106.8050) // ~110.5 meters south

        val dOn = RouteGeometryUtils.perpendicularDistanceMeters(pOnLine, a, b)
        assertTrue("Distance on line should be near 0", dOn < 0.1)

        val dOff = RouteGeometryUtils.perpendicularDistanceMeters(pOffLine, a, b)
        // 0.001 deg lat is ~110.5 meters
        assertEquals(110.5, dOff, 2.0)
    }
}
