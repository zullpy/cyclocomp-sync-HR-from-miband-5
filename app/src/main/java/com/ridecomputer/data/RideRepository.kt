package com.ridecomputer.data

import android.content.Context
import android.content.SharedPreferences
import com.ridecomputer.gpx.GpxPoint
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import kotlin.math.abs

class RideRepository(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val routesDir: File = File(context.filesDir, "routes").apply {
        if (!exists()) mkdirs()
    }

    fun saveRide(record: RideRecord, segments: List<List<GpxPoint>>? = null) {
        var updatedRecord = record
        if (segments != null && segments.isNotEmpty() && segments.any { it.isNotEmpty() }) {
            saveRouteSegments(record.id, segments)
            updatedRecord = record.copy(hasRoute = true)
        }

        val currentList = getAllRides().toMutableList()
        currentList.add(0, updatedRecord) // Insert at top
        saveAll(currentList)
    }

    fun getAllRides(): List<RideRecord> {
        val jsonStr = prefs.getString(KEY_RIDES, null) ?: return emptyList()
        return try {
            val jsonArray = JSONArray(jsonStr)
            val list = mutableListOf<RideRecord>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val id = obj.getString("id")
                // Check if route file exists or flag is stored
                val hasRoute = obj.optBoolean("hasRoute", false) || hasRouteFile(id)
                list.add(
                    RideRecord(
                        id = id,
                        startTime = obj.getLong("startTime"),
                        endTime = obj.getLong("endTime"),
                        durationSeconds = obj.getLong("durationSeconds"),
                        distanceKm = obj.getDouble("distanceKm").toFloat(),
                        avgSpeedKmh = obj.getDouble("avgSpeedKmh").toFloat(),
                        maxSpeedKmh = obj.getDouble("maxSpeedKmh").toFloat(),
                        avgHeartRate = if (obj.has("avgHeartRate") && !obj.isNull("avgHeartRate")) {
                            obj.getInt("avgHeartRate")
                        } else null,
                        maxHeartRate = if (obj.has("maxHeartRate") && !obj.isNull("maxHeartRate")) {
                            obj.getInt("maxHeartRate")
                        } else null,
                        isImported = obj.optBoolean("isImported", false),
                        elevationGainM = if (obj.has("elevationGainM") && !obj.isNull("elevationGainM")) {
                            obj.getDouble("elevationGainM").toFloat()
                        } else null,
                        routeName = if (obj.has("routeName") && !obj.isNull("routeName")) {
                            obj.getString("routeName")
                        } else null,
                        hasRoute = hasRoute,
                    )
                )
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun deleteRide(id: String) {
        val filtered = getAllRides().filterNot { it.id == id }
        saveAll(filtered)
        deleteRouteFile(id)
    }

    fun clearAll() {
        prefs.edit().remove(KEY_RIDES).apply()
        routesDir.listFiles()?.forEach { it.delete() }
    }

    /**
     * Checks if a route appears to be a duplicate of an existing ride.
     */
    fun isDuplicateRoute(
        name: String?,
        distanceKm: Float,
        startTime: Long?,
        durationSeconds: Long,
    ): Boolean {
        val rides = getAllRides()
        return rides.any { r ->
            val distMatch = abs(r.distanceKm - distanceKm) < 0.05f
            val timeMatch = if (startTime != null && r.startTime > 0) {
                abs(r.startTime - startTime) < 5000L || (durationSeconds > 0 && r.durationSeconds == durationSeconds)
            } else {
                durationSeconds > 0 && r.durationSeconds == durationSeconds
            }
            val nameMatch = name != null && r.routeName != null && name.equals(r.routeName, ignoreCase = true)
            (nameMatch && distMatch) || (distMatch && timeMatch)
        }
    }

    fun saveRouteSegments(rideId: String, segments: List<List<GpxPoint>>) {
        try {
            val file = File(routesDir, "$rideId.json")
            val outerArray = JSONArray()
            for (seg in segments) {
                val segArray = JSONArray()
                for (pt in seg) {
                    val ptObj = JSONObject().apply {
                        put("lat", pt.latitude)
                        put("lon", pt.longitude)
                        if (pt.elevationM != null) put("ele", pt.elevationM.toDouble())
                        if (pt.timestampMs != null) put("time", pt.timestampMs)
                        if (pt.heartRateBpm != null) put("hr", pt.heartRateBpm)
                    }
                    segArray.put(ptObj)
                }
                outerArray.put(segArray)
            }
            file.writeText(outerArray.toString())
        } catch (_: Exception) {
            // Ignore write failures gracefully
        }
    }

    fun getRouteSegments(rideId: String): List<List<GpxPoint>>? {
        val file = File(routesDir, "$rideId.json")
        if (!file.exists()) return null
        return try {
            val jsonText = file.readText()
            val outerArray = JSONArray(jsonText)
            val segments = mutableListOf<List<GpxPoint>>()
            for (s in 0 until outerArray.length()) {
                val segArray = outerArray.getJSONArray(s)
                val points = mutableListOf<GpxPoint>()
                for (p in 0 until segArray.length()) {
                    val obj = segArray.getJSONObject(p)
                    points.add(
                        GpxPoint(
                            latitude = obj.getDouble("lat"),
                            longitude = obj.getDouble("lon"),
                            elevationM = if (obj.has("ele")) obj.getDouble("ele").toFloat() else null,
                            timestampMs = if (obj.has("time")) obj.getLong("time") else null,
                            heartRateBpm = if (obj.has("hr")) obj.getInt("hr") else null,
                        )
                    )
                }
                if (points.isNotEmpty()) {
                    segments.add(points)
                }
            }
            segments
        } catch (_: Exception) {
            null
        }
    }

    private fun hasRouteFile(rideId: String): Boolean {
        return File(routesDir, "$rideId.json").exists()
    }

    private fun deleteRouteFile(rideId: String) {
        val file = File(routesDir, "$rideId.json")
        if (file.exists()) {
            file.delete()
        }
    }

    private fun saveAll(rides: List<RideRecord>) {
        val jsonArray = JSONArray()
        for (r in rides) {
            val obj = JSONObject().apply {
                put("id", r.id)
                put("startTime", r.startTime)
                put("endTime", r.endTime)
                put("durationSeconds", r.durationSeconds)
                put("distanceKm", r.distanceKm.toDouble())
                put("avgSpeedKmh", r.avgSpeedKmh.toDouble())
                put("maxSpeedKmh", r.maxSpeedKmh.toDouble())
                if (r.avgHeartRate != null) put("avgHeartRate", r.avgHeartRate)
                if (r.maxHeartRate != null) put("maxHeartRate", r.maxHeartRate)
                put("isImported", r.isImported)
                if (r.elevationGainM != null) put("elevationGainM", r.elevationGainM.toDouble())
                if (r.routeName != null) put("routeName", r.routeName)
                put("hasRoute", r.hasRoute)
            }
            jsonArray.put(obj)
        }
        prefs.edit().putString(KEY_RIDES, jsonArray.toString()).apply()
    }

    companion object {
        private const val PREFS_NAME = "cyclocomp_rides"
        private const val KEY_RIDES = "rides_list"
    }
}
