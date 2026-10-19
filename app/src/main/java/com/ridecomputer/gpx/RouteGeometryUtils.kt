package com.ridecomputer.gpx

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

data class RouteBoundingBox(
    val north: Double,
    val south: Double,
    val east: Double,
    val west: Double,
) {
    val centerLat: Double get() = (north + south) / 2.0
    val centerLon: Double get() = (east + west) / 2.0
    val latSpan: Double get() = (north - south).coerceAtLeast(0.0001)
    val lonSpan: Double get() = (east - west).coerceAtLeast(0.0001)

    fun withPadding(factor: Double = 0.15): RouteBoundingBox {
        val latPad = latSpan * factor
        val lonPad = lonSpan * factor
        return RouteBoundingBox(
            north = min(90.0, north + latPad),
            south = max(-90.0, south - latPad),
            east = min(180.0, east + lonPad),
            west = max(-180.0, west - lonPad),
        )
    }
}

object RouteGeometryUtils {

    private const val METERS_PER_DEGREE_LAT = 110540.0
    private const val METERS_PER_DEGREE_LON = 111320.0

    /**
     * Computes the bounding box covering all points across segments.
     */
    fun calculateBoundingBox(segments: List<List<GpxPoint>>): RouteBoundingBox? {
        val allPoints = segments.flatten()
        if (allPoints.isEmpty()) return null

        var minLat = Double.MAX_VALUE
        var maxLat = -Double.MAX_VALUE
        var minLon = Double.MAX_VALUE
        var maxLon = -Double.MAX_VALUE

        for (p in allPoints) {
            if (p.latitude < minLat) minLat = p.latitude
            if (p.latitude > maxLat) maxLat = p.latitude
            if (p.longitude < minLon) minLon = p.longitude
            if (p.longitude > maxLon) maxLon = p.longitude
        }

        if (minLat > maxLat || minLon > maxLon) return null

        return RouteBoundingBox(
            north = maxLat,
            south = minLat,
            east = maxLon,
            west = minLon,
        )
    }

    /**
     * Simplifies geometry for map rendering using Ramer-Douglas-Peucker algorithm.
     * Preserves original points in storage while rendering lightweight polylines.
     */
    fun simplifySegments(
        segments: List<List<GpxPoint>>,
        toleranceMeters: Double = 3.0,
    ): List<List<GpxPoint>> {
        return segments.map { segment ->
            simplifyPoints(segment, toleranceMeters)
        }.filter { it.size >= 2 }
    }

    fun simplifyPoints(
        points: List<GpxPoint>,
        toleranceMeters: Double = 3.0,
    ): List<GpxPoint> {
        if (points.size <= 2) return points

        var maxDistance = 0.0
        var maxIndex = 0

        val first = points.first()
        val last = points.last()

        for (i in 1 until points.size - 1) {
            val dist = perpendicularDistanceMeters(points[i], first, last)
            if (dist > maxDistance) {
                maxDistance = dist
                maxIndex = i
            }
        }

        return if (maxDistance > toleranceMeters) {
            val left = simplifyPoints(points.subList(0, maxIndex + 1), toleranceMeters)
            val right = simplifyPoints(points.subList(maxIndex, points.size), toleranceMeters)
            left.dropLast(1) + right
        } else {
            listOf(first, last)
        }
    }

    /**
     * Calculates perpendicular distance from point P to line segment AB in meters.
     */
    fun perpendicularDistanceMeters(
        p: GpxPoint,
        a: GpxPoint,
        b: GpxPoint,
    ): Double {
        val midLat = Math.toRadians((a.latitude + b.latitude) / 2.0)
        val cosMid = cos(midLat)

        // Convert lat/lon deltas to approximate local Cartesian meters
        val bx = (b.longitude - a.longitude) * METERS_PER_DEGREE_LON * cosMid
        val by = (b.latitude - a.latitude) * METERS_PER_DEGREE_LAT

        val px = (p.longitude - a.longitude) * METERS_PER_DEGREE_LON * cosMid
        val py = (p.latitude - a.latitude) * METERS_PER_DEGREE_LAT

        val segmentLengthSq = (bx * bx) + (by * by)
        if (segmentLengthSq < 1e-6) {
            return sqrt((px * px) + (py * py))
        }

        // Projection factor t clamped to [0, 1]
        val t = ((px * bx + py * by) / segmentLengthSq).coerceIn(0.0, 1.0)
        val projX = t * bx
        val projY = t * by

        val dx = px - projX
        val dy = py - projY
        return sqrt((dx * dx) + (dy * dy))
    }
}
