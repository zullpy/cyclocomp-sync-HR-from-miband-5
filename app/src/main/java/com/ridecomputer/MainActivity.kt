package com.ridecomputer

import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.ridecomputer.bluetooth.HeartRateClient
import com.ridecomputer.bluetooth.HrEvent
import com.ridecomputer.data.RideRecord
import com.ridecomputer.data.RideRepository
import com.ridecomputer.gpx.GpxParseResult
import com.ridecomputer.gpx.GpxParser
import com.ridecomputer.gpx.GpxPoint
import com.ridecomputer.gpx.GpxSummary
import com.ridecomputer.tracking.GpsPoint
import com.ridecomputer.tracking.GpsSpeedProcessor
import com.ridecomputer.tracking.GpsTracker
import com.ridecomputer.tracking.toGpsPoint
import com.ridecomputer.ui.dashboard.RideDashboard
import com.ridecomputer.ui.dashboard.RideDashboardData
import com.ridecomputer.ui.dashboard.RideState
import com.ridecomputer.ui.history.GpxImportPreviewDialog
import com.ridecomputer.ui.history.RideHistoryScreen
import com.ridecomputer.ui.map.RouteMapScreen
import com.ridecomputer.ui.splash.LoadingScreen
import com.ridecomputer.ui.theme.AccentGreen
import com.ridecomputer.ui.theme.CyclocompTheme
import com.ridecomputer.ui.theme.DashboardSurface
import com.ridecomputer.ui.theme.TextPrimary
import com.ridecomputer.ui.theme.TextSecondary
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

enum class ActiveScreen { SPLASH, DASHBOARD, HISTORY, ROUTE_MAP }

class MainActivity : ComponentActivity() {

    private val dashboardData = MutableStateFlow(RideDashboardData())
    private val currentScreen = MutableStateFlow(ActiveScreen.SPLASH)
    private val historyRides = MutableStateFlow<List<RideRecord>>(emptyList())

    // Map and Route states
    private val liveRecordedGpsPoints = MutableStateFlow<List<GpsPoint>>(emptyList())
    private val latestGpsLocation = MutableStateFlow<GpsPoint?>(null)
    private val activeGuideSegments = MutableStateFlow<List<List<GpxPoint>>>(emptyList())
    private val activeGuideName = MutableStateFlow<String?>(null)
    private val activeGuideDistanceKm = MutableStateFlow<Float?>(null)
    private val selectedRideForMap = MutableStateFlow<RideRecord?>(null)
    private val selectedRouteSegments = MutableStateFlow<List<List<GpxPoint>>>(emptyList())
    private val isGpxDuplicate = MutableStateFlow(false)

    enum class GpxImportOrigin { DASHBOARD, HISTORY }
    private var gpxImportOrigin = GpxImportOrigin.DASHBOARD

    private lateinit var gpsTracker: GpsTracker
    private lateinit var heartRateClient: HeartRateClient
    private lateinit var rideRepository: RideRepository
    private val gpsSpeedProcessor = GpsSpeedProcessor()

    private var gpsJob: Job? = null
    private var hrJob: Job? = null
    private var timerJob: Job? = null
    private var accumulatedDistanceM: Float = 0f
    private var pendingStartRide: Boolean = false

    // Session stats tracking for History
    private var rideStartTime: Long = 0L
    private val speedSamples = mutableListOf<Float>()
    private val hrSamples = mutableListOf<Int>()
    private var maxSpeedKmh: Float = 0f
    private var maxHrBpm: Int = 0

    // GPX import states
    private val gpxPreviewSummary = MutableStateFlow<GpxSummary?>(null)
    private val gpxErrorMessage = MutableStateFlow<String?>(null)
    private val isParsingGpx = MutableStateFlow(false)

    private val requiredPermissions: Array<String>
        get() = buildList {
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                add(Manifest.permission.BLUETOOTH_SCAN)
                add(Manifest.permission.BLUETOOTH_CONNECT)
            }
        }.toTypedArray()

    companion object {
        private const val TAG = "Cyclocomp"
    }

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
            Log.d(TAG, "Permissions result: $grants")
            if (hasBluetoothPermissions()) {
                startHeartRateTracking()
            }
            if (pendingStartRide && grants[Manifest.permission.ACCESS_FINE_LOCATION] == true) {
                pendingStartRide = false
                startRide()
            }
        }

    private val gpxPickerLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            if (uri != null) {
                parseGpxUri(uri)
            }
        }

    private fun parseGpxUri(uri: Uri) {
        lifecycleScope.launch {
            isParsingGpx.value = true
            val result = withContext(Dispatchers.IO) {
                try {
                    contentResolver.openInputStream(uri)?.use { stream ->
                        GpxParser.parse(stream)
                    } ?: GpxParseResult.Error("Unable to open selected file.")
                } catch (e: Exception) {
                    GpxParseResult.Error("Failed to read file: ${e.localizedMessage ?: e.message}")
                }
            }
            isParsingGpx.value = false
            when (result) {
                is GpxParseResult.Success -> {
                    if (gpxImportOrigin == GpxImportOrigin.DASHBOARD) {
                        // Direct import to live ride screen without fabricating ride or distance in history
                        activeGuideSegments.value = result.summary.segments
                        activeGuideName.value = result.summary.name ?: "Imported Route"
                        activeGuideDistanceKm.value = result.summary.distanceKm
                        currentScreen.value = ActiveScreen.DASHBOARD
                        gpxPreviewSummary.value = null
                        gpxErrorMessage.value = null
                        isGpxDuplicate.value = false
                        Log.d(TAG, "Imported GPX set as active route on live ride screen: ${result.summary.name}")
                    } else {
                        isGpxDuplicate.value = rideRepository.isDuplicateRoute(
                            result.summary.name,
                            result.summary.distanceKm,
                            result.summary.startTime,
                            result.summary.durationSeconds,
                        )
                        gpxPreviewSummary.value = result.summary
                        gpxErrorMessage.value = null
                    }
                }
                is GpxParseResult.Error -> {
                    gpxErrorMessage.value = result.message
                    gpxPreviewSummary.value = null
                    isGpxDuplicate.value = false
                }
            }
        }
    }

    private fun confirmImportGpx(summary: GpxSummary) {
        val now = System.currentTimeMillis()
        val startTime = summary.startTime ?: (now - (summary.durationSeconds * 1000L))
        val endTime = summary.endTime ?: now
        val record = RideRecord(
            id = UUID.randomUUID().toString(),
            startTime = startTime,
            endTime = endTime,
            durationSeconds = summary.durationSeconds,
            distanceKm = summary.distanceKm,
            avgSpeedKmh = summary.avgSpeedKmh,
            maxSpeedKmh = summary.maxSpeedKmh,
            avgHeartRate = summary.avgHeartRate,
            maxHeartRate = summary.maxHeartRate,
            isImported = true,
            elevationGainM = summary.elevationGainM,
            routeName = summary.name ?: "Imported GPX",
            hasRoute = summary.segments.isNotEmpty(),
        )
        rideRepository.saveRide(record, summary.segments)
        historyRides.value = rideRepository.getAllRides()
        gpxPreviewSummary.value = null
        isGpxDuplicate.value = false
        Log.d(TAG, "Imported GPX saved to history: $record")
    }

    private fun openRouteMap(ride: RideRecord) {
        lifecycleScope.launch {
            val segments = withContext(Dispatchers.IO) {
                rideRepository.getRouteSegments(ride.id)
            } ?: emptyList()
            selectedRideForMap.value = ride
            selectedRouteSegments.value = segments
            currentScreen.value = ActiveScreen.ROUTE_MAP
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "MainActivity onCreate")
        try {
            gpsTracker = GpsTracker(this)
            heartRateClient = HeartRateClient(this)
            rideRepository = RideRepository(this)
            historyRides.value = rideRepository.getAllRides()
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing components", e)
        }

        // Start scanning for Heart Rate sensor immediately on launch so it displays before Start
        if (hasBluetoothPermissions()) {
            startHeartRateTracking()
        } else {
            permissionLauncher.launch(requiredPermissions)
        }

        setContent {
            CyclocompTheme {
                val screen by currentScreen.collectAsState()
                val rides by historyRides.collectAsState()
                val previewSummary by gpxPreviewSummary.collectAsState()
                val errorMsg by gpxErrorMessage.collectAsState()
                val isDuplicate by isGpxDuplicate.collectAsState()

                val livePoints by liveRecordedGpsPoints.collectAsState()
                val currentGps by latestGpsLocation.collectAsState()
                val guideSegs by activeGuideSegments.collectAsState()
                val guideName by activeGuideName.collectAsState()
                val guideDistanceKm by activeGuideDistanceKm.collectAsState()
                val selectedRide by selectedRideForMap.collectAsState()
                val selectedSegs by selectedRouteSegments.collectAsState()

                when (screen) {
                    ActiveScreen.SPLASH -> {
                        LoadingScreen(
                            onFinished = {
                                currentScreen.value = ActiveScreen.DASHBOARD
                            }
                        )
                    }
                    ActiveScreen.DASHBOARD -> {
                        RideDashboard(
                            dataFlow = dashboardData,
                            liveTrackPoints = livePoints,
                            currentGpsLocation = currentGps,
                            guideRouteSegments = guideSegs,
                            guideRouteName = guideName,
                            guideRouteDistanceKm = guideDistanceKm,
                            onStart = ::onStartPressed,
                            onPause = ::onPausePressed,
                            onResume = ::onResumePressed,
                            onFinish = ::onFinishPressed,
                            onOpenHistory = { currentScreen.value = ActiveScreen.HISTORY },
                            onImportGpxRoute = {
                                gpxImportOrigin = GpxImportOrigin.DASHBOARD
                                gpxPickerLauncher.launch(
                                    arrayOf(
                                        "application/gpx+xml",
                                        "application/xml",
                                        "text/xml",
                                        "*/*"
                                    )
                                )
                            },
                            onClearGuideRoute = {
                                activeGuideSegments.value = emptyList()
                                activeGuideName.value = null
                                activeGuideDistanceKm.value = null
                            },
                        )
                    }
                    ActiveScreen.HISTORY -> {
                        RideHistoryScreen(
                            rides = rides,
                            onBack = { currentScreen.value = ActiveScreen.DASHBOARD },
                            onDeleteRide = { id ->
                                rideRepository.deleteRide(id)
                                historyRides.value = rideRepository.getAllRides()
                            },
                            onClearAll = {
                                rideRepository.clearAll()
                                historyRides.value = emptyList()
                            },
                            onImportGpx = {
                                gpxImportOrigin = GpxImportOrigin.HISTORY
                                gpxPickerLauncher.launch(
                                    arrayOf(
                                        "application/gpx+xml",
                                        "application/xml",
                                        "text/xml",
                                        "*/*"
                                    )
                                )
                            },
                            onSelectRideForMap = ::openRouteMap,
                        )
                    }
                    ActiveScreen.ROUTE_MAP -> {
                        selectedRide?.let { ride ->
                            RouteMapScreen(
                                ride = ride,
                                segments = selectedSegs,
                                onBack = { currentScreen.value = ActiveScreen.HISTORY },
                                onStartWithRoute = { r ->
                                    activeGuideSegments.value = selectedSegs
                                    activeGuideName.value = r.routeName
                                    activeGuideDistanceKm.value = r.distanceKm
                                    currentScreen.value = ActiveScreen.DASHBOARD
                                },
                            )
                        }
                    }
                }

                // GPX Preview Dialog
                previewSummary?.let { summary ->
                    GpxImportPreviewDialog(
                        summary = summary,
                        isDuplicate = isDuplicate,
                        onConfirm = { confirmImportGpx(summary) },
                        onDismiss = { gpxPreviewSummary.value = null },
                    )
                }

                // GPX Parsing Error Dialog
                errorMsg?.let { msg ->
                    AlertDialog(
                        onDismissRequest = { gpxErrorMessage.value = null },
                        title = {
                            Text(
                                text = "Gagal Mengimpor GPX",
                                color = TextPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                            )
                        },
                        text = {
                            Text(
                                text = msg,
                                color = TextSecondary,
                                fontSize = 14.sp,
                            )
                        },
                        confirmButton = {
                            TextButton(onClick = { gpxErrorMessage.value = null }) {
                                Text(
                                    text = "OK",
                                    color = AccentGreen,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        },
                        containerColor = DashboardSurface,
                        shape = RoundedCornerShape(16.dp),
                    )
                }
            }
        }
    }

    private fun onStartPressed() {
        Log.d(TAG, "onStartPressed")
        if (hasPermissions()) {
            startRide()
        } else {
            pendingStartRide = true
            permissionLauncher.launch(requiredPermissions)
        }
    }

    private fun onPausePressed() {
        dashboardData.update { it.copy(rideState = RideState.PAUSED, speedKmh = null) }
        timerJob?.cancel()
        gpsJob?.cancel()
    }

    private fun onResumePressed() {
        dashboardData.update { it.copy(rideState = RideState.RUNNING) }
        gpsSpeedProcessor.reset()
        startGpsTracking()
        startTimer()
    }

    private fun onFinishPressed() {
        gpsJob?.cancel()
        timerJob?.cancel()

        val currentData = dashboardData.value
        val durationSeconds = currentData.elapsedSeconds
        val distanceKm = currentData.distanceKm

        // Calculate average speed
        val avgSpeedKmh = if (durationSeconds > 0 && distanceKm > 0) {
            distanceKm / (durationSeconds / 3600f)
        } else if (speedSamples.isNotEmpty()) {
            speedSamples.average().toFloat()
        } else {
            0f
        }

        val avgHr = if (hrSamples.isNotEmpty()) hrSamples.average().toInt() else null
        val maxHr = if (maxHrBpm > 0) maxHrBpm else null
        val finishTime = System.currentTimeMillis()

        // Convert live recorded points to route segments if available
        val livePoints = liveRecordedGpsPoints.value
        val segments = if (livePoints.size >= 2) {
            listOf(livePoints.map { GpxPoint(it.latitude, it.longitude, null, it.timestampMs, null) })
        } else null

        // Save completed ride to repository
        val record = RideRecord(
            id = UUID.randomUUID().toString(),
            startTime = if (rideStartTime > 0) rideStartTime else finishTime,
            endTime = finishTime,
            durationSeconds = durationSeconds,
            distanceKm = distanceKm,
            avgSpeedKmh = avgSpeedKmh,
            maxSpeedKmh = maxSpeedKmh,
            avgHeartRate = avgHr,
            maxHeartRate = maxHr,
            routeName = activeGuideName.value ?: "Recorded Ride",
            hasRoute = segments != null,
        )
        rideRepository.saveRide(record, segments)
        historyRides.value = rideRepository.getAllRides()
        Log.d(TAG, "Ride saved to history: $record")

        dashboardData.update {
            it.copy(
                rideState = RideState.FINISHED,
                speedKmh = null,
                endTimeMillis = finishTime,
            )
        }

        // Navigate directly to History screen after finish
        currentScreen.value = ActiveScreen.HISTORY
    }

    private fun startRide() {
        gpsSpeedProcessor.reset()
        accumulatedDistanceM = 0f
        rideStartTime = System.currentTimeMillis()
        speedSamples.clear()
        hrSamples.clear()
        maxSpeedKmh = 0f
        maxHrBpm = 0
        liveRecordedGpsPoints.value = emptyList()

        dashboardData.update {
            it.copy(
                rideState = RideState.RUNNING,
                distanceKm = 0f,
                elapsedSeconds = 0L,
                speedKmh = 0f,
                startTimeMillis = rideStartTime,
                endTimeMillis = null,
            )
        }
        startGpsTracking()
        if (hrJob?.isActive != true) {
            startHeartRateTracking()
        }
        startTimer()
    }

    private fun startGpsTracking() {
        gpsJob?.cancel()
        gpsJob = lifecycleScope.launch {
            gpsTracker.locationUpdates().collect { location ->
                val state = dashboardData.value.rideState
                val point = location.toGpsPoint()
                latestGpsLocation.value = point

                if (state != RideState.RUNNING) return@collect

                val now = System.currentTimeMillis()
                val result = gpsSpeedProcessor.processFix(point, now)

                if (result.isFixAccepted) {
                    accumulatedDistanceM += result.deltaDistanceM
                    val speedKmh = result.speedKmh

                    if (speedKmh != null && speedKmh > 0f) {
                        speedSamples.add(speedKmh)
                        if (speedKmh > maxSpeedKmh) {
                            maxSpeedKmh = speedKmh
                        }
                    }

                    liveRecordedGpsPoints.update { it + point }

                    dashboardData.update {
                        it.copy(
                            speedKmh = speedKmh,
                            distanceKm = accumulatedDistanceM / 1000f,
                        )
                    }
                }
            }
        }
    }

    private fun startHeartRateTracking() {
        if (hrJob?.isActive == true) return
        if (!hasBluetoothPermissions()) {
            Log.w(TAG, "Bluetooth permissions not granted! Cannot start HR tracking.")
            dashboardData.update { it.copy(hrStatus = "No Permission") }
            return
        }
        hrJob = lifecycleScope.launch {
            try {
                heartRateClient.scanAndConnect().collect { event ->
                    when (event) {
                        is HrEvent.Measurement -> {
                            if (dashboardData.value.rideState == RideState.RUNNING) {
                                hrSamples.add(event.bpm)
                                if (event.bpm > maxHrBpm) {
                                    maxHrBpm = event.bpm
                                }
                            }
                            dashboardData.update {
                                it.copy(
                                    heartRateBpm = event.bpm,
                                    hrStatus = "Connected"
                                )
                            }
                        }
                        is HrEvent.StatusChanged -> {
                            dashboardData.update {
                                it.copy(hrStatus = event.status)
                            }
                        }
                    }
                }
            } catch (e: CancellationException) {
                Log.d(TAG, "HR tracking coroutine cancelled normally")
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "BLE scan error: ${e.message}", e)
                dashboardData.update { it.copy(hrStatus = "Error") }
            }
        }
    }

    private fun startTimer() {
        timerJob?.cancel()
        timerJob = lifecycleScope.launch {
            while (true) {
                delay(1000)
                if (dashboardData.value.rideState == RideState.RUNNING) {
                    val now = System.currentTimeMillis()
                    val signalLost = gpsSpeedProcessor.isSignalLost(now)
                    dashboardData.update { current ->
                        current.copy(
                            elapsedSeconds = current.elapsedSeconds + 1,
                            speedKmh = if (signalLost) null else current.speedKmh,
                        )
                    }
                }
            }
        }
    }

    private fun hasPermissions(): Boolean =
        requiredPermissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }

    private fun hasBluetoothPermissions(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

    override fun onDestroy() {
        super.onDestroy()
        gpsJob?.cancel()
        hrJob?.cancel()
        timerJob?.cancel()
    }
}
