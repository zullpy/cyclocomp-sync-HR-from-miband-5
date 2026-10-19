package com.ridecomputer.ui.map

import android.content.Context
import android.graphics.Paint
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.ridecomputer.gpx.GpxPoint
import com.ridecomputer.gpx.RouteBoundingBox
import com.ridecomputer.gpx.RouteGeometryUtils
import com.ridecomputer.tracking.GpsPoint
import com.ridecomputer.ui.theme.DashboardBackground
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.CopyrightOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import java.io.File

@Composable
fun OsmMapView(
    modifier: Modifier = Modifier,
    routeSegments: List<List<GpxPoint>> = emptyList(),
    liveTrackPoints: List<GpsPoint> = emptyList(),
    userLocation: GpsPoint? = null,
    showStartFinishMarkers: Boolean = true,
    autoFitRoute: Boolean = true,
    routeColor: Int = 0xFF0070F3.toInt(), // High-contrast AccentBlue for imported/reference route
    liveTrackColor: Int = 0xFF00E676.toInt(), // AccentGreen for live recorded track
    onMapReady: ((MapView) -> Unit)? = null,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Initialize osmdroid configuration safely once
    remember {
        try {
            Configuration.getInstance().userAgentValue = context.packageName
            val osmdroidBase = File(context.cacheDir, "osmdroid")
            val osmdroidTiles = File(osmdroidBase, "tiles")
            if (!osmdroidTiles.exists()) osmdroidTiles.mkdirs()
            Configuration.getInstance().osmdroidBasePath = osmdroidBase
            Configuration.getInstance().osmdroidTileCache = osmdroidTiles
            Configuration.getInstance().tileFileSystemCacheMaxBytes = 50L * 1024 * 1024 // 50MB
            Configuration.getInstance().tileFileSystemCacheTrimBytes = 40L * 1024 * 1024
        } catch (_: Exception) {}
        true
    }

    val mapView = remember {
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
            minZoomLevel = 3.0
            maxZoomLevel = 20.0
            isTilesScaledToDpi = true
            // Ensure standard, natural, readable OpenStreetMap tiles without any tint/inversion
            overlayManager.tilesOverlay.setColorFilter(null)
        }
    }

    // Lifecycle observer for MapView
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_DESTROY -> mapView.onDetach()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView.onDetach()
        }
    }

    // Redraw overlays when routeSegments, liveTrackPoints, or userLocation changes
    LaunchedEffect(routeSegments, liveTrackPoints, userLocation) {
        mapView.overlays.clear()

        // 0. Required OpenStreetMap attribution
        try {
            val copyrightOverlay = CopyrightOverlay(context).apply {
                setAlignRight(true)
                setTextSize(10)
            }
            mapView.overlays.add(copyrightOverlay)
        } catch (_: Exception) {}

        // 1. Draw static / imported route segments (each segment independently!)
        if (routeSegments.isNotEmpty()) {
            for (segment in routeSegments) {
                if (segment.size < 2) continue
                val polyline = Polyline(mapView).apply {
                    outlinePaint.apply {
                        color = routeColor
                        strokeWidth = 9f
                        isAntiAlias = true
                        strokeCap = Paint.Cap.ROUND
                        strokeJoin = Paint.Join.ROUND
                    }
                    val geoPoints = segment.map { GeoPoint(it.latitude, it.longitude) }
                    setPoints(geoPoints)
                }
                mapView.overlays.add(polyline)
            }

            // Start & Finish Markers
            if (showStartFinishMarkers) {
                val firstSeg = routeSegments.firstOrNull { it.isNotEmpty() }
                val lastSeg = routeSegments.lastOrNull { it.isNotEmpty() }
                val startPoint = firstSeg?.firstOrNull()
                val finishPoint = lastSeg?.lastOrNull()

                if (startPoint != null) {
                    val startMarker = Marker(mapView).apply {
                        position = GeoPoint(startPoint.latitude, startPoint.longitude)
                        icon = MarkerUtils.createRouteBadgeDrawable(
                            context = context,
                            label = "START",
                            backgroundColor = 0xFF00E676.toInt(), // AccentGreen
                        )
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                        title = "Start Location"
                    }
                    mapView.overlays.add(startMarker)
                }

                if (finishPoint != null && finishPoint != startPoint) {
                    val finishMarker = Marker(mapView).apply {
                        position = GeoPoint(finishPoint.latitude, finishPoint.longitude)
                        icon = MarkerUtils.createRouteBadgeDrawable(
                            context = context,
                            label = "FINISH",
                            backgroundColor = 0xFFFF5252.toInt(), // AccentRed
                        )
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                        title = "Finish Location"
                    }
                    mapView.overlays.add(finishMarker)
                }
            }
        }

        // 2. Draw live recorded track points
        if (liveTrackPoints.size >= 2) {
            val livePolyline = Polyline(mapView).apply {
                outlinePaint.apply {
                    color = liveTrackColor
                    strokeWidth = 11f
                    isAntiAlias = true
                    strokeCap = Paint.Cap.ROUND
                    strokeJoin = Paint.Join.ROUND
                }
                val geoPoints = liveTrackPoints.map { GeoPoint(it.latitude, it.longitude) }
                setPoints(geoPoints)
            }
            mapView.overlays.add(livePolyline)
        }

        // 3. Draw user current location marker
        if (userLocation != null) {
            val locMarker = Marker(mapView).apply {
                position = GeoPoint(userLocation.latitude, userLocation.longitude)
                icon = MarkerUtils.createUserLocationDrawable(context)
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                title = "Your Location"
            }
            mapView.overlays.add(locMarker)
        }

        mapView.invalidate()
    }

    // Viewport auto-fit when route is loaded
    LaunchedEffect(routeSegments, autoFitRoute) {
        if (autoFitRoute && routeSegments.isNotEmpty()) {
            val bounds = RouteGeometryUtils.calculateBoundingBox(routeSegments)?.withPadding(0.12)
            if (bounds != null) {
                mapView.post {
                    try {
                        val osmBox = BoundingBox(bounds.north, bounds.east, bounds.south, bounds.west)
                        if (mapView.width > 0 && mapView.height > 0) {
                            mapView.zoomToBoundingBox(osmBox, true, 80)
                        } else {
                            mapView.addOnFirstLayoutListener { _, _, _, _, _ ->
                                mapView.zoomToBoundingBox(osmBox, true, 80)
                            }
                        }
                    } catch (_: Exception) {}
                }
            }
        } else if (userLocation != null && routeSegments.isEmpty()) {
            mapView.controller.animateTo(GeoPoint(userLocation.latitude, userLocation.longitude))
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(DashboardBackground),
    ) {
        AndroidView(
            factory = {
                onMapReady?.invoke(mapView)
                mapView
            },
            modifier = Modifier.fillMaxSize(),
        )
    }
}
