package com.dayatlas.app.route

import android.content.Context
import android.graphics.Color
import android.view.View
import android.view.ViewTreeObserver
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

/**
 * Draws a day's track on an osmdroid [MapView]. Purely a foreground view
 * helper — call from the activity that owns the map's resume/pause cycle.
 *
 * Live GPS ticks must use [updateLive] so we never clear overlays or
 * re-zoom (those flash a blank map and can ANR as the day grows).
 */
object RouteMapController {
    private var boundMap: WeakReference<MapView>? = null
    private var shownDateIso: String? = null
    private var trackPoly: Polyline? = null
    private var endMarker: Marker? = null
    private var layoutListener: ViewTreeObserver.OnGlobalLayoutListener? = null

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
            fitWhenLaidOut(map, geoPoints, jumps, animateZoom)
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
        val existing = poly.actualPoints
        when {
            pointCount == existing.size && existing.isNotEmpty() -> {
                // Same-place time refresh should not call this; if it does,
                // only nudge the last vertex (no zoom).
                val copy = ArrayList(existing)
                copy[copy.lastIndex] = geo
                poly.setPoints(copy)
            }
            pointCount == existing.size + 1 -> {
                poly.addPoint(geo)
            }
            else -> return false
        }
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
            removeLayoutListener(target)
        }
        boundMap = null
        shownDateIso = null
        trackPoly = null
        endMarker = null
    }

    private fun fitWhenLaidOut(
        map: MapView,
        geoPoints: List<GeoPoint>,
        jumps: List<JumpFilter.Jump>,
        animateZoom: Boolean,
    ) {
        removeLayoutListener(map)
        val apply = {
            if (map.width > 0 && map.height > 0) {
                applyCamera(map, geoPoints, jumps, animateZoom)
                true
            } else {
                false
            }
        }
        if (apply()) return
        val listener = object : ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                if (apply()) {
                    removeLayoutListener(map)
                }
            }
        }
        layoutListener = listener
        map.viewTreeObserver.addOnGlobalLayoutListener(listener)
        // Fallback center so the map is not left empty while waiting for layout.
        map.controller.setZoom(15.0)
        map.controller.setCenter(geoPoints.last())
        map.invalidate()
    }

    private fun removeLayoutListener(map: MapView) {
        val listener = layoutListener ?: return
        layoutListener = null
        val observer = map.viewTreeObserver
        if (observer.isAlive) {
            observer.removeOnGlobalLayoutListener(listener)
        }
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
            map.controller.setZoom(17.0)
            map.controller.setCenter(geoPoints.last())
        } else if (focus != null && jumps.size == 1) {
            map.controller.setZoom(14.0)
            map.controller.setCenter(focus)
        } else {
            map.zoomToBoundingBox(box, animateZoom, 96)
        }
        map.invalidate()
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
