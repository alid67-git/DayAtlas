package com.dayatlas.app.route

import android.content.Context
import android.graphics.Color
import android.view.View
import androidx.core.content.ContextCompat
import com.dayatlas.app.R
import com.dayatlas.app.data.TrackPoint
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline

/**
 * Draws a day's track on an osmdroid [MapView]. Purely a foreground view
 * helper — call from the activity that owns the map's resume/pause cycle.
 */
object RouteMapController {
    fun show(
        map: MapView,
        context: Context,
        points: List<TrackPoint>,
        emptyState: View?,
    ) {
        map.overlays.clear()
        val hasPoints = points.isNotEmpty()
        emptyState?.visibility = if (hasPoints) View.GONE else View.VISIBLE
        if (!hasPoints) {
            map.invalidate()
            return
        }

        val geoPoints = points.map { GeoPoint(it.lat, it.lon) }
        map.overlays.add(
            Polyline().apply {
                setPoints(geoPoints)
                outlinePaint.color = Color.parseColor("#1F6F5B")
                outlinePaint.strokeWidth = 8f
            },
        )
        map.overlays.add(marker(map, context, geoPoints.first(), R.drawable.ic_marker_start))
        if (geoPoints.size > 1) {
            map.overlays.add(marker(map, context, geoPoints.last(), R.drawable.ic_marker_end))
        }

        map.post {
            val box = boundingBoxOf(geoPoints)
            val degenerate = box.latitudeSpan < 1e-6 && box.longitudeSpan < 1e-6
            if (degenerate) {
                map.controller.setZoom(17.0)
                map.controller.setCenter(geoPoints.last())
            } else {
                map.zoomToBoundingBox(box, true, 96)
            }
            map.invalidate()
        }
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
