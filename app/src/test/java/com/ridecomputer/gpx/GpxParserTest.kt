package com.ridecomputer.gpx

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream

class GpxParserTest {

    @Test
    fun testParseValidGpx11WithTrackElevationAndHeartRate() {
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <gpx version="1.1" creator="CycloCompTest" xmlns="http://www.topografix.com/GPX/1/1"
                 xmlns:gpxtpx="http://www.garmin.com/xmlschemas/TrackPointExtension/v1">
                <trk>
                    <name>Morning Mountain Ride</name>
                    <trkseg>
                        <trkpt lat="-6.2088" lon="106.8456">
                            <ele>150.0</ele>
                            <time>2023-06-01T06:00:00Z</time>
                            <extensions>
                                <gpxtpx:TrackPointExtension>
                                    <gpxtpx:hr>135</gpxtpx:hr>
                                </gpxtpx:TrackPointExtension>
                            </extensions>
                        </trkpt>
                        <trkpt lat="-6.2090" lon="106.8458">
                            <ele>155.0</ele>
                            <time>2023-06-01T06:00:10Z</time>
                            <extensions>
                                <gpxtpx:TrackPointExtension>
                                    <gpxtpx:hr>145</gpxtpx:hr>
                                </gpxtpx:TrackPointExtension>
                            </extensions>
                        </trkpt>
                        <trkpt lat="-6.2095" lon="106.8462">
                            <ele>162.0</ele>
                            <time>2023-06-01T06:00:25Z</time>
                            <extensions>
                                <gpxtpx:TrackPointExtension>
                                    <gpxtpx:hr>155</gpxtpx:hr>
                                </gpxtpx:TrackPointExtension>
                            </extensions>
                        </trkpt>
                    </trkseg>
                </trk>
            </gpx>
        """.trimIndent()

        val result = GpxParser.parse(ByteArrayInputStream(xml.toByteArray()))
        assertTrue(result is GpxParseResult.Success)
        val summary = (result as GpxParseResult.Success).summary

        assertEquals("Morning Mountain Ride", summary.name)
        assertEquals(3, summary.totalPoints)
        assertTrue("Distance should be greater than 0", summary.distanceKm > 0.05f)
        assertEquals(25L, summary.durationSeconds)
        assertTrue(summary.avgSpeedKmh > 0f)
        assertTrue(summary.maxSpeedKmh >= summary.avgSpeedKmh)

        // Elevation gain: 150 -> 155 (+5) -> 162 (+7) = +12.0m
        assertNotNull(summary.elevationGainM)
        assertEquals(12.0f, summary.elevationGainM!!, 0.5f)

        // Heart rate: avg (135 + 145 + 155)/3 = 145, max = 155
        assertEquals(145, summary.avgHeartRate)
        assertEquals(155, summary.maxHeartRate)
    }

    @Test
    fun testParseGpx10FormatWithDirectHrExtension() {
        val xml = """
            <?xml version="1.0" encoding="ISO-8859-1"?>
            <gpx version="1.0" creator="OldGpsDevice">
                <trk>
                    <name>Classic GPX 1.0</name>
                    <trkseg>
                        <trkpt lat="-7.2504" lon="112.7688">
                            <time>2023-07-10T08:00:00Z</time>
                            <hr>120</hr>
                        </trkpt>
                        <trkpt lat="-7.2510" lon="112.7695">
                            <time>2023-07-10T08:00:20Z</time>
                            <hr>130</hr>
                        </trkpt>
                    </trkseg>
                </trk>
            </gpx>
        """.trimIndent()

        val result = GpxParser.parse(ByteArrayInputStream(xml.toByteArray()))
        assertTrue(result is GpxParseResult.Success)
        val summary = (result as GpxParseResult.Success).summary

        assertEquals("Classic GPX 1.0", summary.name)
        assertEquals(2, summary.totalPoints)
        assertEquals(20L, summary.durationSeconds)
        assertEquals(125, summary.avgHeartRate)
        assertEquals(130, summary.maxHeartRate)
        // No elevation in this file
        assertNull(summary.elevationGainM)
    }

    @Test
    fun testParseRouteElementsFallback() {
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <gpx version="1.1">
                <rte>
                    <name>Planned Road Route</name>
                    <rtept lat="-6.2000" lon="106.8000" />
                    <rtept lat="-6.2100" lon="106.8100" />
                    <rtept lat="-6.2200" lon="106.8200" />
                </rte>
            </gpx>
        """.trimIndent()

        val result = GpxParser.parse(ByteArrayInputStream(xml.toByteArray()))
        assertTrue(result is GpxParseResult.Success)
        val summary = (result as GpxParseResult.Success).summary

        assertEquals("Planned Road Route", summary.name)
        assertEquals(3, summary.totalPoints)
        assertTrue(summary.distanceKm > 2.0f)
        // No timestamps
        assertEquals(0L, summary.durationSeconds)
        assertEquals(0f, summary.avgSpeedKmh, 0.01f)
    }

    @Test
    fun testDistanceCalculationKnownCoordinates() {
        // Monas (-6.1754, 106.8272) to Bundaran HI (-6.1950, 106.8230) ~ 2.22 km
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <gpx version="1.1">
                <trk>
                    <name>Monas to HI</name>
                    <trkseg>
                        <trkpt lat="-6.1754" lon="106.8272">
                            <time>2023-05-01T07:00:00Z</time>
                        </trkpt>
                        <trkpt lat="-6.1950" lon="106.8230">
                            <time>2023-05-01T07:05:00Z</time>
                        </trkpt>
                    </trkseg>
                </trk>
            </gpx>
        """.trimIndent()

        val result = GpxParser.parse(ByteArrayInputStream(xml.toByteArray()))
        assertTrue(result is GpxParseResult.Success)
        val summary = (result as GpxParseResult.Success).summary

        // Distance should be ~2.22 km (±0.1 km)
        assertEquals(2.22f, summary.distanceKm, 0.15f)
        assertEquals(300L, summary.durationSeconds) // 5 minutes
        // Speed ~ 2.22km / (300/3600 h) = ~26.6 km/h
        assertEquals(26.6f, summary.avgSpeedKmh, 1.5f)
    }

    @Test
    fun testRejectsMalformedXml() {
        val brokenXml = "<gpx version='1.1'><trk><trkseg><trkpt lat='10.0' lon='20.0'><ele>broken"
        val result = GpxParser.parse(ByteArrayInputStream(brokenXml.toByteArray()))
        assertTrue("Malformed XML should fail", result is GpxParseResult.Error)
    }

    @Test
    fun testRejectsNonGpxDocument() {
        val nonGpxXml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <kml xmlns="http://www.opengis.net/kml/2.2">
                <Document><name>Not a GPX</name></Document>
            </kml>
        """.trimIndent()

        val result = GpxParser.parse(ByteArrayInputStream(nonGpxXml.toByteArray()))
        assertTrue(result is GpxParseResult.Error)
        val msg = (result as GpxParseResult.Error).message
        assertTrue(msg.contains("missing <gpx>", ignoreCase = true))
    }

    @Test
    fun testRejectsEmptyTrack() {
        val emptyGpx = """
            <?xml version="1.0" encoding="UTF-8"?>
            <gpx version="1.1">
                <trk><name>Empty</name><trkseg></trkseg></trk>
            </gpx>
        """.trimIndent()

        val result = GpxParser.parse(ByteArrayInputStream(emptyGpx.toByteArray()))
        assertTrue(result is GpxParseResult.Error)
        val msg = (result as GpxParseResult.Error).message
        assertTrue(msg.contains("No valid GPS track points", ignoreCase = true))
    }

    @Test
    fun testRejectsSinglePointTrack() {
        val singlePoint = """
            <?xml version="1.0" encoding="UTF-8"?>
            <gpx version="1.1">
                <trk>
                    <trkseg>
                        <trkpt lat="-6.2088" lon="106.8456" />
                    </trkseg>
                </trk>
            </gpx>
        """.trimIndent()

        val result = GpxParser.parse(ByteArrayInputStream(singlePoint.toByteArray()))
        assertTrue(result is GpxParseResult.Error)
        val msg = (result as GpxParseResult.Error).message
        assertTrue(msg.contains("insufficient points", ignoreCase = true))
    }

    @Test
    fun testFiltersInvalidCoordinates() {
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <gpx version="1.1">
                <trk>
                    <trkseg>
                        <trkpt lat="95.0" lon="100.0" /> <!-- Invalid Lat -->
                        <trkpt lat="0.0" lon="0.0" />     <!-- Null Island -->
                        <trkpt lat="-6.2000" lon="106.8000" />
                        <trkpt lat="-6.2050" lon="106.8050" />
                    </trkseg>
                </trk>
            </gpx>
        """.trimIndent()

        val result = GpxParser.parse(ByteArrayInputStream(xml.toByteArray()))
        assertTrue(result is GpxParseResult.Success)
        val summary = (result as GpxParseResult.Success).summary
        // Only 2 valid points kept
        assertEquals(2, summary.totalPoints)
    }

    @Test
    fun testTimestampParserFormats() {
        // UTC format
        val t1 = GpxParser.parseIsoTimestamp("2023-08-15T10:30:00Z")
        assertNotNull(t1)

        // Milliseconds format
        val t2 = GpxParser.parseIsoTimestamp("2023-08-15T10:30:00.500Z")
        assertNotNull(t2)
        assertEquals(500L, t2!! - t1!!)

        // Timezone offset format
        val t3 = GpxParser.parseIsoTimestamp("2023-08-15T12:30:00+02:00")
        assertNotNull(t3)
        assertEquals(t1, t3) // 12:30+02:00 is same UTC instant as 10:30Z

        // Invalid format
        val invalid = GpxParser.parseIsoTimestamp("not-a-timestamp")
        assertNull(invalid)
    }

    @Test
    fun testXxeSecurityDefense() {
        // Attempt XML External Entity injection
        val xxePayload = """
            <?xml version="1.0"?>
            <!DOCTYPE gpx [
                <!ENTITY xxe SYSTEM "file:///etc/passwd">
            ]>
            <gpx version="1.1">
                <trk>
                    <name>&xxe;</name>
                    <trkseg>
                        <trkpt lat="-6.2000" lon="106.8000" />
                        <trkpt lat="-6.2050" lon="106.8050" />
                    </trkseg>
                </trk>
            </gpx>
        """.trimIndent()

        val result = GpxParser.parse(ByteArrayInputStream(xxePayload.toByteArray()))
        // Should either be rejected by disallow-doctype-decl or entity not resolved
        if (result is GpxParseResult.Success) {
            val name = result.summary.name ?: ""
            assertTrue("Entity expansion must not expose system content", !name.contains("root:"))
        } else {
            assertTrue("DOCTYPE disallow correctly blocked XXE attempt", result is GpxParseResult.Error)
        }
    }

    @Test
    fun testMultiSegmentTrackSeparationWithoutConnectingLines() {
        val multiSegmentGpx = """
            <?xml version="1.0" encoding="UTF-8"?>
            <gpx version="1.1" creator="CycloCompTest">
                <trk>
                    <name>Two Disjoint Stages</name>
                    <!-- Stage 1 in Jakarta (~2.22 km) -->
                    <trkseg>
                        <trkpt lat="-6.1754" lon="106.8272">
                            <time>2023-05-01T07:00:00Z</time>
                        </trkpt>
                        <trkpt lat="-6.1950" lon="106.8230">
                            <time>2023-05-01T07:05:00Z</time>
                        </trkpt>
                    </trkseg>
                    <!-- Stage 2 in Bandung (~2.49 km), ~120 km away from Jakarta -->
                    <trkseg>
                        <trkpt lat="-6.9025" lon="107.6186">
                            <time>2023-05-01T10:00:00Z</time>
                        </trkpt>
                        <trkpt lat="-6.9219" lon="107.6071">
                            <time>2023-05-01T10:06:00Z</time>
                        </trkpt>
                    </trkseg>
                </trk>
            </gpx>
        """.trimIndent()

        val result = GpxParser.parse(ByteArrayInputStream(multiSegmentGpx.toByteArray()))
        assertTrue(result is GpxParseResult.Success)
        val summary = (result as GpxParseResult.Success).summary

        assertEquals("Two Disjoint Stages", summary.name)
        assertEquals(2, summary.segments.size)
        assertEquals(4, summary.totalPoints)
        assertEquals(2, summary.segments[0].size)
        assertEquals(2, summary.segments[1].size)

        // Total distance must be stage 1 (~2.22km) + stage 2 (~2.49km) = ~4.71 km
        // Must NEVER include the ~120 km gap between Jakarta and Bandung!
        assertTrue("Distance must not include the 120km gap between segments", summary.distanceKm < 10.0f)
        assertEquals(4.71f, summary.distanceKm, 0.4f)
    }
}
