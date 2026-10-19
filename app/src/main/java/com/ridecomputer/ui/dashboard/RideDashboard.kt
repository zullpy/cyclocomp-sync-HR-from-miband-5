package com.ridecomputer.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import com.ridecomputer.gpx.GpxPoint
import com.ridecomputer.gpx.RouteGeometryUtils
import com.ridecomputer.tracking.GpsPoint
import com.ridecomputer.ui.components.CycloCompLogo
import com.ridecomputer.ui.components.MetricCard
import com.ridecomputer.ui.map.OsmMapView
import com.ridecomputer.ui.theme.AccentAmber
import com.ridecomputer.ui.theme.AccentBlue
import com.ridecomputer.ui.theme.AccentGreen
import com.ridecomputer.ui.theme.AccentRed
import com.ridecomputer.ui.theme.DashboardBackground
import com.ridecomputer.ui.theme.DashboardSurface
import com.ridecomputer.ui.theme.DashboardSurfaceVariant
import com.ridecomputer.ui.theme.TextDim
import com.ridecomputer.ui.theme.TextPrimary
import com.ridecomputer.ui.theme.TextSecondary
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class RideState { IDLE, RUNNING, PAUSED, FINISHED }
enum class DashboardViewMode { MAP, METRICS }

data class RideDashboardData(
    val speedKmh: Float? = null,
    val heartRateBpm: Int? = null,
    val distanceKm: Float = 0f,
    val slopePercent: Float? = null,
    val elapsedSeconds: Long = 0L,
    val rideState: RideState = RideState.IDLE,
    val hrStatus: String? = null,
    val startTimeMillis: Long? = null,
    val endTimeMillis: Long? = null,
)

@Composable
fun RideDashboard(
    dataFlow: StateFlow<RideDashboardData>,
    liveTrackPoints: List<GpsPoint> = emptyList(),
    currentGpsLocation: GpsPoint? = null,
    guideRouteSegments: List<List<GpxPoint>> = emptyList(),
    guideRouteName: String? = null,
    guideRouteDistanceKm: Float? = null,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onFinish: () -> Unit,
    onOpenHistory: () -> Unit,
    onImportGpxRoute: () -> Unit,
    onClearGuideRoute: () -> Unit = {},
) {
    val data by dataFlow.collectAsState()
    var viewMode by remember { mutableStateOf(DashboardViewMode.MAP) }
    var liveMapViewRef by remember { mutableStateOf<MapView?>(null) }

    // Live clock ticking every second
    var currentTime by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            currentTime = System.currentTimeMillis()
            delay(1000)
        }
    }
    val timeFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
    val clockStr = timeFormat.format(Date(currentTime))

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DashboardBackground)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Top Header: CYCLOCOMP | [METRICS / MAP] Toggle | HISTORY →
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CycloCompLogo(size = 20.dp, sweepProgress = 1f)
                Spacer(Modifier.width(6.dp))
                Text(
                    text = clockStr,
                    color = TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                )
            }

            // View Mode Toggle
            Row(
                modifier = Modifier
                    .background(DashboardSurfaceVariant, RoundedCornerShape(8.dp))
                    .padding(2.dp),
            ) {
                Text(
                    text = "METRICS",
                    color = if (viewMode == DashboardViewMode.METRICS) DashboardBackground else TextSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .background(
                            if (viewMode == DashboardViewMode.METRICS) AccentGreen else Color.Transparent,
                            RoundedCornerShape(6.dp)
                        )
                        .clickable { viewMode = DashboardViewMode.METRICS }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                )
                Text(
                    text = "MAP",
                    color = if (viewMode == DashboardViewMode.MAP) DashboardBackground else TextSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .background(
                            if (viewMode == DashboardViewMode.MAP) AccentGreen else Color.Transparent,
                            RoundedCornerShape(6.dp)
                        )
                        .clickable { viewMode = DashboardViewMode.MAP }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }

            TextButton(onClick = onOpenHistory) {
                Text(
                    text = "HISTORY →",
                    color = AccentGreen,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                )
            }
        }

        // Active Planned GPX Route Banner
        if (guideRouteSegments.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(AccentBlue.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "ROUTE: ${guideRouteName ?: "Imported Route"}${guideRouteDistanceKm?.let { " • %.1f km".format(it) } ?: ""}",
                        color = AccentBlue,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                    )
                }
                Text(
                    text = "✕ CLEAR ROUTE",
                    color = AccentRed,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clickable { onClearGuideRoute() }
                        .padding(start = 8.dp),
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        if (viewMode == DashboardViewMode.MAP) {
            // === MAIN CONTENT: INTERACTIVE MAP VIEW ===
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(DashboardSurface, RoundedCornerShape(14.dp)),
            ) {
                OsmMapView(
                    modifier = Modifier.fillMaxSize(),
                    routeSegments = guideRouteSegments,
                    liveTrackPoints = liveTrackPoints,
                    userLocation = currentGpsLocation,
                    routeColor = 0xFF0070F3.toInt(), // AccentBlue for planned guide route
                    liveTrackColor = 0xFF00E676.toInt(), // AccentGreen for live recorded track
                    showStartFinishMarkers = guideRouteSegments.isNotEmpty(),
                    autoFitRoute = guideRouteSegments.isNotEmpty(),
                    onMapReady = { liveMapViewRef = it },
                )

                // Floating Map Controls: Recenter, Fit Route, Zoom In, Zoom Out
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    // Zoom In
                    FloatingActionButton(
                        onClick = { liveMapViewRef?.controller?.zoomIn() },
                        modifier = Modifier.size(38.dp),
                        shape = CircleShape,
                        containerColor = DashboardSurface.copy(alpha = 0.90f),
                        contentColor = TextPrimary,
                        elevation = FloatingActionButtonDefaults.elevation(3.dp),
                    ) {
                        Text(text = "+", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    }

                    // Zoom Out
                    FloatingActionButton(
                        onClick = { liveMapViewRef?.controller?.zoomOut() },
                        modifier = Modifier.size(38.dp),
                        shape = CircleShape,
                        containerColor = DashboardSurface.copy(alpha = 0.90f),
                        contentColor = TextPrimary,
                        elevation = FloatingActionButtonDefaults.elevation(3.dp),
                    ) {
                        Text(text = "−", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    }

                    // Fit Route (if route is loaded)
                    if (guideRouteSegments.isNotEmpty()) {
                        FloatingActionButton(
                            onClick = {
                                val bounds = RouteGeometryUtils.calculateBoundingBox(guideRouteSegments)?.withPadding(0.12)
                                if (bounds != null) {
                                    liveMapViewRef?.zoomToBoundingBox(
                                        org.osmdroid.util.BoundingBox(bounds.north, bounds.east, bounds.south, bounds.west),
                                        true,
                                        80
                                    )
                                }
                            },
                            modifier = Modifier.size(38.dp),
                            shape = CircleShape,
                            containerColor = DashboardSurface.copy(alpha = 0.90f),
                            contentColor = AccentBlue,
                            elevation = FloatingActionButtonDefaults.elevation(3.dp),
                        ) {
                            Text(text = "⛶", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    // Recenter GPS
                    if (currentGpsLocation != null) {
                        FloatingActionButton(
                            onClick = {
                                liveMapViewRef?.controller?.animateTo(
                                    GeoPoint(currentGpsLocation.latitude, currentGpsLocation.longitude)
                                )
                                liveMapViewRef?.controller?.setZoom(17.0)
                            },
                            modifier = Modifier.size(42.dp),
                            shape = CircleShape,
                            containerColor = DashboardSurface.copy(alpha = 0.92f),
                            contentColor = AccentGreen,
                            elevation = FloatingActionButtonDefaults.elevation(4.dp),
                        ) {
                            Text(text = "◎", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // Preserved Metric Cards below Map
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                MetricCard(
                    label = "Speed",
                    value = data.speedKmh?.let { "%.1f".format(it) } ?: "--.-",
                    unit = "km/h",
                    available = data.speedKmh != null,
                    valueFontSize = 22.sp,
                    modifier = Modifier.weight(1f),
                )
                MetricCard(
                    label = "Distance",
                    value = "%.2f".format(data.distanceKm),
                    unit = "km",
                    available = data.rideState != RideState.IDLE,
                    valueFontSize = 22.sp,
                    modifier = Modifier.weight(1f),
                )
                MetricCard(
                    label = "Duration",
                    value = formatDuration(data.elapsedSeconds),
                    unit = "",
                    available = data.rideState != RideState.IDLE,
                    valueFontSize = 22.sp,
                    modifier = Modifier.weight(1f),
                )
            }

            Spacer(Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                MetricCard(
                    label = "Heart Rate",
                    value = data.heartRateBpm?.toString() ?: "--",
                    unit = "bpm",
                    available = data.heartRateBpm != null,
                    statusText = if (data.heartRateBpm != null) null else (data.hrStatus ?: "Standby"),
                    valueFontSize = 22.sp,
                    modifier = Modifier.weight(1f),
                )
                MetricCard(
                    label = "Slope",
                    value = data.slopePercent?.let { "%+.1f".format(it) } ?: "--",
                    unit = "%",
                    available = data.slopePercent != null,
                    valueFontSize = 22.sp,
                    modifier = Modifier.weight(1f),
                )
            }

            // Session start to finish timing indicator
            val sessionStart = data.startTimeMillis
            val sessionEnd = data.endTimeMillis
            if (sessionStart != null) {
                Spacer(Modifier.height(6.dp))
                val startStr = timeFormat.format(Date(sessionStart))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(DashboardSurfaceVariant, RoundedCornerShape(8.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "START ",
                            color = AccentGreen,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = startStr,
                            color = TextPrimary,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Medium,
                        )
                    }

                    if (data.rideState == RideState.FINISHED && sessionEnd != null) {
                        val finishStr = timeFormat.format(Date(sessionEnd))
                        Text(
                            text = "→",
                            color = TextDim,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "FINISH ",
                                color = AccentAmber,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                text = finishStr,
                                color = TextPrimary,
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    } else {
                        Text(
                            text = if (data.rideState == RideState.PAUSED) "PAUSED" else "ACTIVE",
                            color = if (data.rideState == RideState.PAUSED) AccentAmber else AccentGreen,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp,
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
        } else {
            // === FULL METRICS VIEW ===
            SpeedDisplay(speedKmh = data.speedKmh)

            Spacer(Modifier.height(18.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                MetricCard(
                    label = "Heart Rate",
                    value = data.heartRateBpm?.toString() ?: "--",
                    unit = "bpm",
                    available = data.heartRateBpm != null,
                    statusText = if (data.heartRateBpm != null) null else (data.hrStatus ?: "Standby"),
                    modifier = Modifier.weight(1f),
                )
                MetricCard(
                    label = "Distance",
                    value = "%.2f".format(data.distanceKm),
                    unit = "km",
                    available = data.rideState != RideState.IDLE,
                    modifier = Modifier.weight(1f),
                )
            }

            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                MetricCard(
                    label = "Slope",
                    value = data.slopePercent?.let { "%+.1f".format(it) } ?: "--",
                    unit = "%",
                    available = data.slopePercent != null,
                    modifier = Modifier.weight(1f),
                )
                MetricCard(
                    label = "Duration",
                    value = formatDuration(data.elapsedSeconds),
                    unit = "",
                    available = data.rideState != RideState.IDLE,
                    modifier = Modifier.weight(1f),
                )
            }

            // Session start to finish time indicator
            val sessionStart = data.startTimeMillis
            val sessionEnd = data.endTimeMillis
            if (sessionStart != null) {
                Spacer(Modifier.height(14.dp))
                val startStr = timeFormat.format(Date(sessionStart))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(DashboardSurfaceVariant, RoundedCornerShape(8.dp))
                        .padding(horizontal = 14.dp, vertical = 7.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "START ",
                            color = AccentGreen,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = startStr,
                            color = TextPrimary,
                            fontSize = 13.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Medium,
                        )
                    }

                    if (data.rideState == RideState.FINISHED && sessionEnd != null) {
                        val finishStr = timeFormat.format(Date(sessionEnd))
                        Text(
                            text = "→",
                            color = TextDim,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "FINISH ",
                                color = AccentAmber,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                text = finishStr,
                                color = TextPrimary,
                                fontSize = 13.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    } else {
                        Text(
                            text = if (data.rideState == RideState.PAUSED) "PAUSED" else "ACTIVE",
                            color = if (data.rideState == RideState.PAUSED) AccentAmber else AccentGreen,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp,
                        )
                    }
                }
            }

            Spacer(Modifier.weight(1f))
        }

        RideControls(
            state = data.rideState,
            onStart = onStart,
            onPause = onPause,
            onResume = onResume,
            onFinish = onFinish,
            onOpenHistory = onOpenHistory,
            onImportGpxRoute = onImportGpxRoute,
        )

        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun SpeedDisplay(speedKmh: Float?) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = "SPEED",
            color = TextSecondary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 2.sp,
        )
        Text(
            text = speedKmh?.let { "%.1f".format(it) } ?: "--.-",
            color = if (speedKmh != null) TextPrimary else TextDim,
            fontSize = 96.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            lineHeight = 96.sp,
        )
        Text(
            text = "km/h",
            color = TextSecondary,
            fontSize = 16.sp,
        )
    }
}

@Composable
private fun RideControls(
    state: RideState,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onFinish: () -> Unit,
    onOpenHistory: () -> Unit,
    onImportGpxRoute: () -> Unit,
) {
    when (state) {
        RideState.IDLE -> {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Button(
                    onClick = onImportGpxRoute,
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = DashboardSurfaceVariant),
                ) {
                    Text(
                        text = "IMPORT GPX",
                        color = AccentBlue,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                    )
                }
                ActionButton(
                    text = "Start",
                    color = AccentGreen,
                    onClick = onStart,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        RideState.RUNNING -> {
            ActionButton(text = "Pause", color = AccentRed, onClick = onPause)
        }
        RideState.PAUSED -> {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ActionButton(
                    text = "Resume",
                    color = AccentGreen,
                    onClick = onResume,
                    modifier = Modifier.weight(1f),
                )
                ActionButton(
                    text = "Finish",
                    color = AccentRed,
                    onClick = onFinish,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        RideState.FINISHED -> {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ActionButton(
                    text = "New Ride",
                    color = AccentGreen,
                    onClick = onStart,
                    modifier = Modifier.weight(1f),
                )
                ActionButton(
                    text = "History",
                    color = DashboardSurface,
                    textColor = TextPrimary,
                    onClick = onOpenHistory,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun ActionButton(
    text: String,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    textColor: Color = DashboardBackground,
) {
    Button(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(containerColor = color),
    ) {
        Text(
            text = text.uppercase(),
            color = textColor,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 2.sp,
        )
    }
}

private fun formatDuration(totalSeconds: Long): String {
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}
