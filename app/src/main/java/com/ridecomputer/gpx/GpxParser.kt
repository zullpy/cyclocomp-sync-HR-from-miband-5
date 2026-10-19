package com.ridecomputer.gpx

import com.ridecomputer.tracking.GpsSpeedProcessor
import org.xml.sax.Attributes
import org.xml.sax.InputSource
import org.xml.sax.helpers.DefaultHandler
import java.io.InputStream
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import javax.xml.XMLConstants
import javax.xml.parsers.SAXParserFactory

data class GpxPoint(
    val latitude: Double,
    val longitude: Double,
    val elevationM: Float? = null,
    val timestampMs: Long? = null,
    val heartRateBpm: Int? = null,
)

data class GpxSummary(
    val name: String?,
    val totalPoints: Int,
    val distanceKm: Float,
    val durationSeconds: Long,
    val startTime: Long?,
    val endTime: Long?,
    val avgSpeedKmh: Float,
    val maxSpeedKmh: Float,
    val elevationGainM: Float?,
    val avgHeartRate: Int?,
    val maxHeartRate: Int?,
    val segments: List<List<GpxPoint>> = emptyList(),
) {
    val points: List<GpxPoint>
        get() = segments.flatten()
}

sealed class GpxParseResult {
    data class Success(val summary: GpxSummary) : GpxParseResult()
    data class Error(val message: String) : GpxParseResult()
}

/**
 * High-performance, secure parser for GPX 1.0 and 1.1 files.
 * Disables external entities and DTDs to guard against XXE vulnerabilities.
 */
object GpxParser {

    private val secureSaxFactory: SAXParserFactory by lazy {
        SAXParserFactory.newInstance().apply {
            isNamespaceAware = true
            try {
                setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
            } catch (_: Exception) {}
            try {
                setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            } catch (_: Exception) {}
            try {
                setFeature("http://xml.org/sax/features/external-general-entities", false)
            } catch (_: Exception) {}
            try {
                setFeature("http://xml.org/sax/features/external-parameter-entities", false)
            } catch (_: Exception) {}
            try {
                setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
            } catch (_: Exception) {}
        }
    }

    /**
     * Parses a GPX input stream safely.
     */
    fun parse(inputStream: InputStream): GpxParseResult {
        return try {
            val parser = secureSaxFactory.newSAXParser()
            val handler = GpxSaxHandler()
            parser.parse(InputSource(inputStream), handler)
            handler.buildResult()
        } catch (e: Exception) {
            val msg = e.localizedMessage ?: e.message ?: "Unknown XML parsing error"
            GpxParseResult.Error("Failed to parse GPX: $msg")
        }
    }

    fun parseIsoTimestamp(isoString: String): Long? {
        val trimmed = isoString.trim()
        if (trimmed.isEmpty()) return null
        return try {
            OffsetDateTime.parse(trimmed, DateTimeFormatter.ISO_DATE_TIME).toInstant().toEpochMilli()
        } catch (_: Exception) {
            try {
                Instant.parse(trimmed).toEpochMilli()
            } catch (_: Exception) {
                try {
                    LocalDateTime.parse(trimmed, DateTimeFormatter.ISO_DATE_TIME)
                        .toInstant(ZoneOffset.UTC).toEpochMilli()
                } catch (_: Exception) {
                    null
                }
            }
        }
    }

    private class GpxSaxHandler : DefaultHandler() {
        private var isGpxRootFound = false
        private var routeOrTrackName: String? = null
        private var isInsideTrackName = false

        // Current point state
        private var isInsidePoint = false
        private var currentLat: Double? = null
        private var currentLon: Double? = null
        private var currentElevation: Float? = null
        private var currentTimestampMs: Long? = null
        private var currentHeartRate: Int? = null

        private val textBuffer = StringBuilder()
        private val parsedSegments = mutableListOf<MutableList<GpxPoint>>()
        private var currentSegment: MutableList<GpxPoint>? = null

        override fun startElement(
            uri: String?,
            localName: String?,
            qName: String?,
            attributes: Attributes?
        ) {
            val tag = (localName?.takeIf { it.isNotEmpty() } ?: qName ?: "").lowercase()
            textBuffer.setLength(0)

            when (tag) {
                "gpx" -> {
                    isGpxRootFound = true
                }
                "name" -> {
                    if (!isInsidePoint && routeOrTrackName == null) {
                        isInsideTrackName = true
                    }
                }
                "trkseg", "rte" -> {
                    val newSegment = mutableListOf<GpxPoint>()
                    parsedSegments.add(newSegment)
                    currentSegment = newSegment
                }
                "trkpt", "rtept" -> {
                    isInsidePoint = true
                    currentLat = attributes?.getValue("lat")?.toDoubleOrNull()
                    currentLon = attributes?.getValue("lon")?.toDoubleOrNull()
                    currentElevation = null
                    currentTimestampMs = null
                    currentHeartRate = null

                    if (currentSegment == null) {
                        val fallback = mutableListOf<GpxPoint>()
                        parsedSegments.add(fallback)
                        currentSegment = fallback
                    }
                }
            }
        }

        override fun characters(ch: CharArray, start: Int, length: Int) {
            textBuffer.append(ch, start, length)
        }

        override fun endElement(uri: String?, localName: String?, qName: String?) {
            val tag = (localName?.takeIf { it.isNotEmpty() } ?: qName ?: "").lowercase()
            val text = textBuffer.toString().trim()

            if (isInsideTrackName && tag == "name") {
                if (text.isNotEmpty()) {
                    routeOrTrackName = text
                }
                isInsideTrackName = false
                return
            }

            if (tag == "trkseg" || tag == "rte") {
                currentSegment = null
                return
            }

            if (isInsidePoint) {
                when (tag) {
                    "ele" -> {
                        currentElevation = text.toFloatOrNull()
                    }
                    "time" -> {
                        currentTimestampMs = parseIsoTimestamp(text)
                    }
                    "hr" -> {
                        // Supports <hr> and <gpxtpx:hr>
                        val hr = text.toIntOrNull()
                        if (hr != null && hr in 30..250) {
                            currentHeartRate = hr
                        }
                    }
                    "trkpt", "rtept" -> {
                        val lat = currentLat
                        val lon = currentLon
                        if (lat != null && lon != null && GpsSpeedProcessor.isValidCoordinate(lat, lon)) {
                            val point = GpxPoint(
                                latitude = lat,
                                longitude = lon,
                                elevationM = currentElevation,
                                timestampMs = currentTimestampMs,
                                heartRateBpm = currentHeartRate,
                            )
                            currentSegment?.add(point)
                        }
                        isInsidePoint = false
                    }
                }
            }
        }

        fun buildResult(): GpxParseResult {
            if (!isGpxRootFound) {
                return GpxParseResult.Error("Not a valid GPX document: missing <gpx> root element.")
            }

            val validSegments = parsedSegments
                .map { seg -> seg.filter { GpsSpeedProcessor.isValidCoordinate(it.latitude, it.longitude) } }
                .filter { it.isNotEmpty() }

            val rawPoints = validSegments.flatten()
            if (rawPoints.isEmpty()) {
                return GpxParseResult.Error("No valid GPS track points found in file.")
            }
            if (rawPoints.size < 2) {
                return GpxParseResult.Error("The track contains insufficient points (minimum 2 points required).")
            }

            // 1. Distance calculation strictly per segment (no cross-segment lines!)
            var totalDistanceM = 0f
            val segmentSpeeds = mutableListOf<Float>()

            for (segment in validSegments) {
                var previousPoint: GpxPoint? = null
                for (point in segment) {
                    previousPoint?.let { prev ->
                        val d = GpsSpeedProcessor.calculateDistanceMeters(
                            prev.latitude, prev.longitude,
                            point.latitude, point.longitude
                        )
                        totalDistanceM += d

                        // Max speed estimation between points within the same segment
                        if (prev.timestampMs != null && point.timestampMs != null) {
                            val dtSec = (point.timestampMs - prev.timestampMs) / 1000f
                            if (dtSec in 0.5f..30.0f) {
                                val speedMps = d / dtSec
                                val speedKmh = speedMps * 3.6f
                                if (speedKmh in 0.5f..120.0f) {
                                    segmentSpeeds.add(speedKmh)
                                }
                            }
                        }
                    }
                    previousPoint = point
                }
            }

            val totalDistanceKm = totalDistanceM / 1000f

            // 2. Timestamps and duration
            val pointsWithTime = rawPoints.filter { it.timestampMs != null }
            val startTime = pointsWithTime.firstOrNull()?.timestampMs
            val endTime = pointsWithTime.lastOrNull()?.timestampMs

            val durationSeconds = if (startTime != null && endTime != null && endTime > startTime) {
                (endTime - startTime) / 1000L
            } else {
                0L
            }

            // 3. Speeds
            val avgSpeedKmh = if (durationSeconds > 0 && totalDistanceKm > 0) {
                totalDistanceKm / (durationSeconds / 3600f)
            } else {
                0f
            }

            val maxSpeedKmh = if (segmentSpeeds.isNotEmpty()) {
                segmentSpeeds.maxOrNull() ?: avgSpeedKmh
            } else {
                avgSpeedKmh
            }

            // 4. Elevation gain with noise threshold calculated within segments
            var elevationGainM: Float? = null
            var totalGain = 0f
            var hasElevation = false

            for (segment in validSegments) {
                val elevationPoints = segment.mapNotNull { it.elevationM }
                if (elevationPoints.size >= 2) {
                    hasElevation = true
                    var prevEle = elevationPoints.first()
                    for (i in 1 until elevationPoints.size) {
                        val currEle = elevationPoints[i]
                        val diff = currEle - prevEle
                        if (diff > 0.5f) {
                            totalGain += diff
                            prevEle = currEle
                        } else if (diff < -0.5f) {
                            prevEle = currEle
                        }
                    }
                }
            }
            if (hasElevation) {
                elevationGainM = totalGain
            }

            // 5. Heart rate metrics
            val hrSamples = rawPoints.mapNotNull { it.heartRateBpm }
            val avgHr = if (hrSamples.isNotEmpty()) hrSamples.average().toInt() else null
            val maxHr = if (hrSamples.isNotEmpty()) hrSamples.maxOrNull() else null

            val summary = GpxSummary(
                name = routeOrTrackName,
                totalPoints = rawPoints.size,
                distanceKm = totalDistanceKm,
                durationSeconds = durationSeconds,
                startTime = startTime,
                endTime = endTime,
                avgSpeedKmh = avgSpeedKmh,
                maxSpeedKmh = maxSpeedKmh,
                elevationGainM = elevationGainM,
                avgHeartRate = avgHr,
                maxHeartRate = maxHr,
                segments = validSegments,
            )

            return GpxParseResult.Success(summary)
        }
    }
}
