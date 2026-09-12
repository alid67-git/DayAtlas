package com.dayatlas.app.route

import android.content.Context
import android.graphics.Color
import android.view.View
import androidx.core.content.ContextCompat
import com.dayatlas.app.R
import com.dayatlas.app.data.JumpFilter
import com.dayatlas.app.data.TrackPoint
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import java.lang.ref.WeakReference
import kotlin.math.abs

/**
 * Draws a day's track on an osmdroid [MapView]. Purely a foreground view
 * helper — call from the activity that owns the map's resume/pause cycle.
 *
 * Live GPS ticks must use [updateLive] so we never clear overlays or
 * re-zoom (those flash a blank map and can ANR as the day grows).
 *
 * Camera fit is re-applied whenever the MapView's laid-out size changes:
 * a first fit on a tiny/minHeight pane followed by growth used to leave a
 * tall white hole with only a thin tile strip at the bottom.
 */
object RouteMapController {
    private const val DEFAULT_ZOOM_BORDER_PX = 160
    private const val MIN_ZOOM_INNER_PX = 48
    private const val MIN_REFIT_DELTA_PX = 24
    private const val MAX_ZOOM = 18.0
    private const val MIN_ZOOM = 3.0

    private var boundMap: WeakReference<MapView>? = null
    private var shownDateIso: String? = null
    private var trackPoly: Polyline? = null
    private var endMarker: Marker? = null

    private var pendingGeo: List<GeoPoint>? = null
    private var pendingJumps: List<JumpFilter.Jump> = emptyList()
    private var pendingAnimate = false
    private var lastFitWidth = 0
    private var lastFitHeight = 0
    private var sizeListener: View.OnLayoutChangeListener? = null
    private var refitPass: Runnable? = null
    private var refitDelayed: Runnable? = null

    fun show(
        map: MapView,
        context: Context,
        points: List<TrackPoint>,
        emptyState: View?,
        dateIso: String? = null,
        jumps: List<JumpFilter.Jump> = JumpFilter.findJumps(points),
        onJumpTap: ((JumpFilter.Jump) -> Unit)? = null,
        /** Live GPS updates should not animate zoom (causes blank flashes / ANR). */
        animateZoom: Boolean = false,
        fitCamera: Boolean = true,
    ) {
        clearLiveState(map)
        map.overlays.clear()
        val hasPoints = points.isNotEmpty()
        emptyState?.visibility = if (hasPoints) View.GONE else View.VISIBLE
        if (!hasPoints) {
            map.invalidate()
            return
        }

        val geoPoints = points.map { GeoPoint(it.lat, it.lon) }
        val poly = Polyline().apply {
            setPoints(geoPoints)
            outlinePaint.color = Color.parseColor("#1E5AA8")
            outlinePaint.strokeWidth = 8f
        }
        map.overlays.add(poly)
        map.overlays.add(marker(map, context, geoPoints.first(), R.drawable.ic_marker_start))
        val end = if (geoPoints.size > 1) {
            marker(map, context, geoPoints.last(), R.drawable.ic_marker_end).also {
                map.overlays.add(it)
            }
        } else {
            null
        }

        jumps.forEach { jump ->
            val gp = GeoPoint(jump.point.lat, jump.point.lon)
            map.overlays.add(
                Marker(map).apply {
                    position = gp
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    icon = ContextCompat.getDrawable(context, R.drawable.ic_marker_jump)
                    setInfoWindow(null)
                    if (onJumpTap != null) {
                        setOnMarkerClickListener { _, _ ->
                            onJumpTap(jump)
                            true
                        }
                    }
                },
            )
        }

        boundMap = WeakReference(map)
        shownDateIso = dateIso
        trackPoly = poly
        endMarker = end

        if (fitCamera) {
            pendingGeo = geoPoints
            pendingJumps = jumps
            pendingAnimate = animateZoom
            lastFitWidth = 0
            lastFitHeight = 0
            attachSizeListener(map)
            fitNowIfPossible(map)
            // Extra passes after the weighted LinearLayout settles — the first
            // fit often runs at minHeight, then the pane grows into a tall
            // white hole with only a thin tile strip until we re-fit.
            val pass = Runnable {
                if (boundMap?.get() === map) fitNowIfPossible(map, force = true)
            }
            val delayed = Runnable {
                if (boundMap?.get() === map) fitNowIfPossible(map, force = true)
            }
            refitPass = pass
            refitDelayed = delayed
            map.post(pass)
            map.postDelayed(delayed, 350)
        } else {
            map.invalidate()
        }
    }

    /**
     * Cheap live update: move/add the last vertex without clearing overlays
     * or changing zoom/tiles. Returns false if a full [show] is required.
     */
    fun updateLive(
        map: MapView,
        context: Context,
        dateIso: String,
        lat: Double,
        lon: Double,
        pointCount: Int,
    ): Boolean {
        if (boundMap?.get() !== map || shownDateIso != dateIso || trackPoly == null) {
            return false
        }
        val geo = GeoPoint(lat, lon)
        val poly = trackPoly!!
        val existing = ArrayList(poly.actualPoints)
        when {
            pointCount == existing.size && existing.isNotEmpty() -> {
                existing[existing.lastIndex] = geo
                poly.setPoints(existing)
            }
            pointCount == existing.size + 1 -> {
                poly.addPoint(geo)
                existing.add(geo)
            }
            else -> return false
        }
        pendingGeo = ArrayList(existing)
        val end = endMarker
        if (end != null) {
            end.position = geo
        } else if (pointCount > 1) {
            val created = marker(map, context, geo, R.drawable.ic_marker_end)
            map.overlays.add(created)
            endMarker = created
        }
        map.invalidate()
        return true
    }

    fun clearLiveState(map: MapView? = null) {
        val target = map ?: boundMap?.get()
        if (target != null) {
            detachSizeListener(target)
            refitPass?.let { target.removeCallbacks(it) }
            refitDelayed?.let { target.removeCallbacks(it) }
        }
        refitPass = null
        refitDelayed = null
        boundMap = null
        shownDateIso = null
        trackPoly = null
        endMarker = null
        pendingGeo = null
        pendingJumps = emptyList()
        lastFitWidth = 0
        lastFitHeight = 0
    }

    private fun attachSizeListener(map: MapView) {
        detachSizeListener(map)
        val listener = View.OnLayoutChangeListener { v, _, _, _, _, _, _, _, _ ->
            if (v === map && boundMap?.get() === map) {
                fitNowIfPossible(map)
            }
        }
        sizeListener = listener
        map.addOnLayoutChangeListener(listener)
    }

    private fun detachSizeListener(map: MapView) {
        val listener = sizeListener ?: return
        sizeListener = null
        map.removeOnLayoutChangeListener(listener)
    }

    private fun fitNowIfPossible(map: MapView, force: Boolean = false) {
        val geo = pendingGeo ?: return
        if (geo.isEmpty()) return
        val w = map.width
        val h = map.height
        if (w <= 0 || h <= 0) return
        if (!force &&
            abs(w - lastFitWidth) < MIN_REFIT_DELTA_PX &&
            abs(h - lastFitHeight) < MIN_REFIT_DELTA_PX
        ) {
            return
        }
        applyCamera(map, geo, pendingJumps, pendingAnimate)
        lastFitWidth = w
        lastFitHeight = h
    }

    private fun applyCamera(
        map: MapView,
        geoPoints: List<GeoPoint>,
        jumps: List<JumpFilter.Jump>,
        animateZoom: Boolean,
    ) {
        val focus = jumps.lastOrNull()?.let { GeoPoint(it.point.lat, it.point.lon) }
        val box = boundingBoxOf(geoPoints)
        val degenerate = box.latitudeSpan < 1e-6 && box.longitudeSpan < 1e-6
        if (degenerate) {
            map.controller.setZoom(16.0)
            map.controller.setCenter(geoPoints.last())
        } else if (focus != null && jumps.size == 1) {
            map.controller.setZoom(13.0)
            map.controller.setCenter(focus)
        } else {
            val border = safeZoomBorder(map.width, map.height)
            map.zoomToBoundingBox(box, animateZoom, border)
            val zoom = map.zoomLevelDouble
            if (!zoom.isFinite() || zoom < MIN_ZOOM) {
                map.controller.setZoom(12.0)
                map.controller.setCenter(box.centerWithDateLine)
            } else if (zoom > MAX_ZOOM) {
                map.controller.setZoom(MAX_ZOOM)
                map.controller.setCenter(box.centerWithDateLine)
            }
        }
        // Nudge center to rebuild projection for the current view size — without
        // this, a fit done at minHeight can leave a tall white pane with a tile strip.
        map.controller.setCenter(map.mapCenter)
        map.invalidate()
    }

    /**
     * Padding for [MapView.zoomToBoundingBox] that keeps the inner size positive.
     * Exposed for unit tests.
     */
    internal fun safeZoomBorder(
        mapWidthPx: Int,
        mapHeightPx: Int,
        preferred: Int = DEFAULT_ZOOM_BORDER_PX,
        minInner: Int = MIN_ZOOM_INNER_PX,
    ): Int {
        if (mapWidthPx <= 0 || mapHeightPx <= 0) return 0
        val maxByWidth = ((mapWidthPx - minInner) / 2).coerceAtLeast(0)
        val maxByHeight = ((mapHeightPx - minInner) / 2).coerceAtLeast(0)
        return minOf(preferred, maxByWidth, maxByHeight)
    }

    private fun marker(
        map: MapView,
        context: Context,
        point: GeoPoint,
        iconRes: Int,
    ): Marker =
        Marker(map).apply {
            position = point
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
            icon = ContextCompat.getDrawable(context, iconRes)
            setInfoWindow(null)
        }

    private fun boundingBoxOf(points: List<GeoPoint>): BoundingBox {
        var north = points[0].latitude
        var south = points[0].latitude
        var east = points[0].longitude
        var west = points[0].longitude
        points.forEach { p ->
            north = maxOf(north, p.latitude)
            south = minOf(south, p.latitude)
            east = maxOf(east, p.longitude)
            west = minOf(west, p.longitude)
        }
        return BoundingBox(north, east, south, west)
    }
}
