package com.ridecomputer.ui.map

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ridecomputer.data.RideRecord
import com.ridecomputer.gpx.GpxPoint
import com.ridecomputer.gpx.RouteGeometryUtils
import com.ridecomputer.ui.theme.AccentAmber
import com.ridecomputer.ui.theme.AccentBlue
import com.ridecomputer.ui.theme.AccentGreen
import com.ridecomputer.ui.theme.DashboardBackground
import com.ridecomputer.ui.theme.DashboardSurface
import com.ridecomputer.ui.theme.DashboardSurfaceVariant
import com.ridecomputer.ui.theme.TextDim
import com.ridecomputer.ui.theme.TextPrimary
import com.ridecomputer.ui.theme.TextSecondary
import org.osmdroid.util.BoundingBox
import org.osmdroid.views.MapView

@Composable
fun RouteMapScreen(
    ride: RideRecord,
    segments: List<List<GpxPoint>>,
    onBack: () -> Unit,
    onStartWithRoute: ((RideRecord) -> Unit)? = null,
) {
    var mapViewRef by remember { mutableStateOf<MapView?>(null) }

    // Simplify points off-thread/remember for smooth rendering
    val simplifiedSegments = remember(segments) {
        RouteGeometryUtils.simplifySegments(segments, toleranceMeters = 2.5)
    }

    val totalPoints = remember(segments) { segments.sumOf { it.size } }

    Box(modifier = Modifier.fillMaxSize().background(DashboardBackground)) {
        // Fullscreen OsmMapView
        OsmMapView(
            modifier = Modifier.fillMaxSize(),
            routeSegments = simplifiedSegments,
            showStartFinishMarkers = true,
            autoFitRoute = true,
            routeColor = 0xFF00B0FF.toInt(), // AccentBlue
            onMapReady = { mapViewRef = it },
        )

        // Top Navigation Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp, start = 12.dp, end = 12.dp)
                .background(DashboardSurface.copy(alpha = 0.92f), RoundedCornerShape(14.dp))
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onBack) {
                Text(
                    text = "← BACK",
                    color = AccentGreen,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                )
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                Text(
                    text = ride.routeName ?: "Route Map",
                    color = TextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                )
                if (ride.isImported) {
                    Text(
                        text = "IMPORTED GPX",
                        color = AccentAmber,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            }

            // Fit route button
            TextButton(
                onClick = {
                    val box = RouteGeometryUtils.calculateBoundingBox(simplifiedSegments)?.withPadding(0.12)
                    if (box != null && mapViewRef != null) {
                        val osmBox = BoundingBox(box.north, box.east, box.south, box.west)
                        mapViewRef?.zoomToBoundingBox(osmBox, true, 80)
                    }
                }
            ) {
                Text(
                    text = "FIT",
                    color = AccentBlue,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                )
            }
        }

        // Floating Map Controls (Zoom in / Zoom out)
        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            FloatingActionButton(
                onClick = { mapViewRef?.controller?.zoomIn() },
                modifier = Modifier.size(42.dp),
                shape = CircleShape,
                containerColor = DashboardSurface.copy(alpha = 0.9f),
                contentColor = TextPrimary,
                elevation = FloatingActionButtonDefaults.elevation(4.dp),
            ) {
                Text(text = "+", fontSize = 22.sp, fontWeight = FontWeight.Bold)
            }

            FloatingActionButton(
                onClick = { mapViewRef?.controller?.zoomOut() },
                modifier = Modifier.size(42.dp),
                shape = CircleShape,
                containerColor = DashboardSurface.copy(alpha = 0.9f),
                contentColor = TextPrimary,
                elevation = FloatingActionButtonDefaults.elevation(4.dp),
            ) {
                Text(text = "−", fontSize = 22.sp, fontWeight = FontWeight.Bold)
            }
        }

        // Bottom Route Summary Card
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(16.dp)
                .background(DashboardSurface.copy(alpha = 0.95f), RoundedCornerShape(16.dp))
                .padding(16.dp),
        ) {
            // Distance and Duration Primary Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom,
            ) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = "%.2f".format(ride.distanceKm),
                        color = TextPrimary,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                    )
                    Text(
                        text = " km",
                        color = TextSecondary,
                        fontSize = 15.sp,
                        modifier = Modifier.padding(bottom = 3.dp),
                    )
                }

                if (ride.durationSeconds > 0) {
                    Text(
                        text = formatDuration(ride.durationSeconds),
                        color = AccentGreen,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(bottom = 3.dp),
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            // Secondary Metrics
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DashboardSurfaceVariant.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                MetricItem(label = "AVG SPEED", value = if (ride.avgSpeedKmh > 0) "%.1f km/h".format(ride.avgSpeedKmh) else "--")
                MetricItem(label = "MAX SPEED", value = if (ride.maxSpeedKmh > 0) "%.1f km/h".format(ride.maxSpeedKmh) else "--")
                MetricItem(label = "ELEVATION", value = ride.elevationGainM?.let { "+%.0f m".format(it) } ?: "--")
                MetricItem(label = "POINTS", value = "%,d".format(totalPoints))
            }

            if (onStartWithRoute != null) {
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = { onStartWithRoute(ride) },
                    modifier = Modifier.fillMaxWidth().height(46.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AccentGreen),
                    shape = RoundedCornerShape(10.dp),
                ) {
                    Text(
                        text = "RIDE THIS ROUTE",
                        color = DashboardBackground,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        letterSpacing = 1.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun MetricItem(label: String, value: String) {
    Column {
        Text(
            text = label,
            color = TextDim,
            fontSize = 9.sp,
            fontWeight = FontWeight.Medium,
        )
        Text(
            text = value,
            color = TextPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = FontFamily.Monospace,
        )
    }
}

private fun formatDuration(totalSeconds: Long): String {
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}
