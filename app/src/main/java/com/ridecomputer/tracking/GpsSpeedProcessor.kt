package com.ridecomputer.tracking

import android.location.Location
import android.os.Build
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Platform-independent GPS data point to facilitate deterministic unit testing
 * and clean separation of concerns.
 */
data class GpsPoint(
    val latitude: Double,
    val longitude: Double,
    val speedMps: Float? = null,
    val accuracyM: Float? = null,
    val timestampMs: Long = 0L,
    val speedAccuracyMps: Float? = null,
)

/**
 * Result of processing a GPS fix.
 */
data class SpeedProcessResult(
    val speedKmh: Float?,
    val speedMps: Float?,
    val deltaDistanceM: Float,
    val isSignalLost: Boolean,
    val isFixAccepted: Boolean,
    val rejectionReason: String? = null,
)

/**
 * Converts an Android [Location] to a [GpsPoint].
 */
fun Location.toGpsPoint(): GpsPoint {
    val speedVal = if (hasSpeed()) speed else null
    val accVal = if (hasAccuracy()) accuracy else null
    val speedAccVal = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && hasSpeedAccuracy()) {
        speedAccuracyMetersPerSecond
    } else {
        null
    }
    val ts = if (elapsedRealtimeNanos > 0) {
        // Prefer monotonic clock if available to prevent wall-clock shifts
        elapsedRealtimeNanos / 1_000_000L
    } else {
        time
    }
    return GpsPoint(
        latitude = latitude,
        longitude = longitude,
        speedMps = speedVal,
        accuracyM = accVal,
        timestampMs = ts,
        speedAccuracyMps = speedAccVal,
    )
}

/**
 * Robust GPS Speed and Distance processor for cycling.
 *
 * Implements:
 * 1. Android Location.speed in m/s converted to km/h (speedMps * 3.6f).
 * 2. GPS fix validity & accuracy checks (reject fixes with poor accuracy or out-of-bounds coordinates).
 * 3. Stale fix detection (reject old or duplicate timestamps).
 * 4. Stationary drift filtering (deadband to prevent distance accumulation while stopped).
 * 5. Safe distance-derived fallback speed with strict time (dt) and distance thresholds.
 * 6. Kinematic acceleration / deceleration sanity checks to prevent unrealistic jumps.
 * 7. Exponential Moving Average (EMA) smoothing for responsive yet steady display.
 * 8. GPS signal loss detection to display null (--.-) when signal drops.
 */
class GpsSpeedProcessor(
    private val maxAccuracyThresholdM: Float = DEFAULT_MAX_ACCURACY_M,
    private val signalLossTimeoutMs: Long = DEFAULT_SIGNAL_LOSS_TIMEOUT_MS,
    private val smoothingFactor: Float = DEFAULT_SMOOTHING_FACTOR,
) {
    private var lastAcceptedPoint: GpsPoint? = null
    private var lastValidSpeedMps: Float = 0f
    private var smoothedSpeedKmh: Float = 0f
    private var lastFixAcceptedTimestampMs: Long = 0L

    fun reset() {
        lastAcceptedPoint = null
        lastValidSpeedMps = 0f
        smoothedSpeedKmh = 0f
        lastFixAcceptedTimestampMs = 0L
    }

    /**
     * Checks if GPS signal has been lost based on elapsed time since last accepted fix.
     */
    fun isSignalLost(currentTimeMs: Long): Boolean {
        if (lastFixAcceptedTimestampMs == 0L) return false
        return (currentTimeMs - lastFixAcceptedTimestampMs) > signalLossTimeoutMs
    }

    /**
     * Process a GPS fix and compute the accurate speed and delta distance.
     *
     * @param point The incoming GPS fix
     * @param currentTimeMs Optional system timestamp to evaluate freshness and signal loss
     */
    fun processFix(point: GpsPoint, currentTimeMs: Long = point.timestampMs): SpeedProcessResult {
        // 1. Basic coordinate sanity
        if (!isValidCoordinate(point.latitude, point.longitude)) {
            return reject(point, "Invalid coordinates (${point.latitude}, ${point.longitude})", currentTimeMs)
        }

        // 2. Accuracy check
        if (point.accuracyM == null || point.accuracyM > maxAccuracyThresholdM || point.accuracyM <= 0f) {
            return reject(point, "Poor accuracy: ${point.accuracyM}m (max: ${maxAccuracyThresholdM}m)", currentTimeMs)
        }

        // 3. Stale fix detection
        val prev = lastAcceptedPoint
        if (prev != null) {
            if (point.timestampMs <= prev.timestampMs) {
                return reject(point, "Stale/duplicate timestamp: ${point.timestampMs} <= ${prev.timestampMs}", currentTimeMs)
            }
            val dtSec = (point.timestampMs - prev.timestampMs) / 1000f
            if (dtSec > MAX_ACCEPTABLE_GAP_SEC) {
                // Large gap in reception: reset previous position to avoid derived speed jump
                lastAcceptedPoint = point
                lastFixAcceptedTimestampMs = currentTimeMs
                val speedKmh = determineInitialSpeedKmh(point)
                val speedMps = (speedKmh ?: 0f) / 3.6f
                lastValidSpeedMps = speedMps
                smoothedSpeedKmh = speedKmh ?: 0f
                return SpeedProcessResult(
                    speedKmh = speedKmh,
                    speedMps = speedMps,
                    deltaDistanceM = 0f,
                    isSignalLost = false,
                    isFixAccepted = true,
                )
            }
        }

        // First fix initialization
        if (prev == null) {
            lastAcceptedPoint = point
            lastFixAcceptedTimestampMs = currentTimeMs
            val initialSpeed = determineInitialSpeedKmh(point)
            val initialSpeedMps = (initialSpeed ?: 0f) / 3.6f
            lastValidSpeedMps = initialSpeedMps
            smoothedSpeedKmh = initialSpeed ?: 0f
            return SpeedProcessResult(
                speedKmh = initialSpeed,
                speedMps = initialSpeedMps,
                deltaDistanceM = 0f,
                isSignalLost = false,
                isFixAccepted = true,
            )
        }

        val dtSec = (point.timestampMs - prev.timestampMs) / 1000f
        val distanceM = calculateDistanceMeters(prev.latitude, prev.longitude, point.latitude, point.longitude)

        // 4. Speed calculation: Doppler vs Distance-derived
        var rawSpeedMps: Float? = null

        // Validate Doppler speed if available
        if (point.speedMps != null && point.speedMps >= 0f) {
            // Speed accuracy check if provided by device
            val isDopplerReliable = point.speedAccuracyMps == null || point.speedAccuracyMps < MAX_SPEED_ACCURACY_MPS
            if (isDopplerReliable) {
                rawSpeedMps = if (point.speedMps < STATIONARY_SPEED_THRESHOLD_MPS) {
                    0f
                } else {
                    point.speedMps
                }
            }
        }

        // Fallback: Distance-derived speed if Doppler speed was unavailable or unreliable
        if (rawSpeedMps == null) {
            if (dtSec in MIN_DERIVED_DT_SEC..MAX_DERIVED_DT_SEC) {
                // Must exceed accuracy noise threshold to avoid false movement
                val combinedAccuracy = (point.accuracyM + (prev.accuracyM ?: point.accuracyM)) * 0.4f
                val minMoveDistance = maxOf(MIN_DERIVED_DISTANCE_M, combinedAccuracy)
                rawSpeedMps = if (distanceM < minMoveDistance) {
                    0f
                } else {
                    distanceM / dtSec
                }
            } else {
                // Cannot reliably derive speed over abnormal dt
                rawSpeedMps = lastValidSpeedMps
            }
        }

        // 5. Kinematic jump validation (realistic acceleration & braking)
        val validatedSpeedMps = applyKinematicConstraints(rawSpeedMps, lastValidSpeedMps, dtSec)

        // 6. Stationary drift & distance accumulation filtering
        val deltaDistanceM = if (validatedSpeedMps < STATIONARY_SPEED_THRESHOLD_MPS) {
            // Cyclist is stopped: ignore positional noise completely
            0f
        } else {
            // Check if displacement is consistent with movement
            val maxReasonableDisplacement = (validatedSpeedMps * dtSec * 1.8f) + point.accuracyM
            if (distanceM > maxReasonableDisplacement) {
                // Position jump detected (multipath or sudden GPS lock shift)
                // Use kinematic distance to avoid corrupting odometer
                validatedSpeedMps * dtSec
            } else {
                distanceM
            }
        }

        // 7. Speed smoothing (EMA)
        // Convert m/s to km/h using formula: speedKmh = speedMps * 3.6f
        val targetSpeedKmh = validatedSpeedMps * 3.6f
        val displaySpeedKmh = if (validatedSpeedMps == 0f) {
            // Instant drop to zero when stopped
            smoothedSpeedKmh = 0f
            0f
        } else {
            if (smoothedSpeedKmh == 0f) {
                smoothedSpeedKmh = targetSpeedKmh
            } else {
                smoothedSpeedKmh = (smoothingFactor * targetSpeedKmh) + ((1f - smoothingFactor) * smoothedSpeedKmh)
            }
            smoothedSpeedKmh
        }

        lastAcceptedPoint = point
        lastValidSpeedMps = validatedSpeedMps
        lastFixAcceptedTimestampMs = currentTimeMs

        return SpeedProcessResult(
            speedKmh = displaySpeedKmh,
            speedMps = validatedSpeedMps,
            deltaDistanceM = deltaDistanceM,
            isSignalLost = false,
            isFixAccepted = true,
        )
    }

    private fun determineInitialSpeedKmh(point: GpsPoint): Float? {
        val speedMps = point.speedMps
        return if (speedMps != null && speedMps >= STATIONARY_SPEED_THRESHOLD_MPS) {
            speedMps * 3.6f
        } else {
            0f
        }
    }

    private fun applyKinematicConstraints(rawSpeedMps: Float, previousSpeedMps: Float, dtSec: Float): Float {
        if (dtSec <= 0f) return previousSpeedMps

        val deltaSpeed = rawSpeedMps - previousSpeedMps
        return if (deltaSpeed > 0f) {
            // Acceleration limit: allow initial pedal push-off when accelerating from stop
            val baseStep = if (previousSpeedMps < STATIONARY_SPEED_THRESHOLD_MPS) {
                INITIAL_PUSHOFF_SPEED_MPS
            } else {
                MIN_STEP_CHANGE_MPS
            }
            val maxAllowedDelta = maxOf(baseStep, MAX_ACCELERATION_MPS2 * dtSec)
            if (deltaSpeed > maxAllowedDelta) {
                previousSpeedMps + maxAllowedDelta
            } else {
                rawSpeedMps
            }
        } else {
            // Braking / deceleration limit
            val maxAllowedDrop = maxOf(MIN_STEP_CHANGE_MPS, MAX_DECELERATION_MPS2 * dtSec)
            if (-deltaSpeed > maxAllowedDrop) {
                maxOf(0f, previousSpeedMps - maxAllowedDrop)
            } else {
                rawSpeedMps
            }
        }
    }

    private fun reject(point: GpsPoint, reason: String, currentTimeMs: Long): SpeedProcessResult {
        val signalLost = isSignalLost(currentTimeMs)
        return SpeedProcessResult(
            speedKmh = if (signalLost) null else smoothedSpeedKmh,
            speedMps = if (signalLost) null else lastValidSpeedMps,
            deltaDistanceM = 0f,
            isSignalLost = signalLost,
            isFixAccepted = false,
            rejectionReason = reason,
        )
    }

    companion object {
        const val DEFAULT_MAX_ACCURACY_M = 25f
        const val DEFAULT_SIGNAL_LOSS_TIMEOUT_MS = 3500L
        const val DEFAULT_SMOOTHING_FACTOR = 0.65f

        // Stationary threshold: ~1.08 km/h (0.3 m/s)
        const val STATIONARY_SPEED_THRESHOLD_MPS = 0.3f

        // Physics constraints for bicycles
        const val MAX_ACCELERATION_MPS2 = 4.0f // ~14.4 km/h per sec max realistic acceleration
        const val MAX_DECELERATION_MPS2 = 7.0f // ~25.2 km/h per sec emergency braking
        const val INITIAL_PUSHOFF_SPEED_MPS = 7.5f // ~27 km/h sprint launch from standstill
        const val MIN_STEP_CHANGE_MPS = 3.0f   // Step change allowed when already moving

        const val MIN_DERIVED_DT_SEC = 0.5f
        const val MAX_DERIVED_DT_SEC = 3.0f
        const val MIN_DERIVED_DISTANCE_M = 2.0f
        const val MAX_ACCEPTABLE_GAP_SEC = 10.0f
        const val MAX_SPEED_ACCURACY_MPS = 2.5f

        /**
         * Converts meters per second to kilometers per hour.
         * Formula: speedKmh = speedMps * 3.6f
         */
        fun convertMpsToKmh(speedMps: Float): Float = speedMps * 3.6f

        fun isValidCoordinate(latitude: Double, longitude: Double): Boolean {
            if (latitude.isNaN() || longitude.isNaN()) return false
            if (latitude == 0.0 && longitude == 0.0) return false
            return latitude in -90.0..90.0 && longitude in -180.0..180.0
        }

        /**
         * Standard Haversine distance in meters.
         */
        fun calculateDistanceMeters(
            lat1: Double,
            lon1: Double,
            lat2: Double,
            lon2: Double,
        ): Float {
            val earthRadiusM = 6371000.0
            val dLat = Math.toRadians(lat2 - lat1)
            val dLon = Math.toRadians(lon2 - lon1)
            val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2) * sin(dLon / 2)
            val c = 2 * atan2(sqrt(a), sqrt(1 - a))
            return (earthRadiusM * c).toFloat()
        }
    }
}
