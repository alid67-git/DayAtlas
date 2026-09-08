package com.dayatlas.app.route

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.dayatlas.app.R
import com.dayatlas.app.data.DayStore
import com.dayatlas.app.data.DayTitle
import com.dayatlas.app.data.TrackPoint
import com.dayatlas.app.databinding.ActivityRouteBinding
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import java.time.LocalDate

/**
 * A day-by-day route view drawn on an OpenStreetMap basemap (osmdroid): no
 * Google Play Services, no API key. The map is purely a foreground view
 * component - tiles are only fetched/drawn while this screen is on screen,
 * so it adds no battery cost in the background (see onResume/onPause).
 * Opens on today by default; previous/next-day arrows browse other days.
 */
class RouteActivity : AppCompatActivity() {
    private lateinit var binding: ActivityRouteBinding
    private val store by lazy { DayStore(this) }
    private lateinit var shownDate: LocalDate

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRouteBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.routeMap.setTileSource(TileSourceFactory.MAPNIK)
        binding.routeMap.setMultiTouchControls(true)

        binding.toolbar.setNavigationOnClickListener { finish() }

        val extraDate = intent.getStringExtra(EXTRA_DATE)
        shownDate = extraDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            ?: DayTitle.localToday()

        binding.previousDay.setOnClickListener {
            shownDate = shownDate.minusDays(1)
            refresh()
        }
        binding.nextDay.setOnClickListener {
            if (shownDate < DayTitle.localToday()) {
                shownDate = shownDate.plusDays(1)
                refresh()
            }
        }

        refresh()
    }

    override fun onResume() {
        super.onResume()
        binding.routeMap.onResume()
    }

    override fun onPause() {
        binding.routeMap.onPause()
        super.onPause()
    }

    private fun refresh() {
        val record = store.load(DayTitle.iso(shownDate))
        binding.dayTitle.text = DayTitle.format(shownDate)
        binding.nextDay.isEnabled = shownDate < DayTitle.localToday()

        val points = record?.points.orEmpty()
        val hasPoints = points.isNotEmpty()
        binding.emptyState.visibility = if (hasPoints) View.GONE else View.VISIBLE
        binding.distance.visibility = if (hasPoints) View.VISIBLE else View.GONE
        binding.pointCount.visibility = if (hasPoints) View.VISIBLE else View.GONE
        if (hasPoints && record != null) {
            binding.distance.text = DayTitle.formatDistance(record.distanceMeters)
            binding.pointCount.text = getString(R.string.route_point_count, points.size)
        }

        showOnMap(points)
    }

    private fun showOnMap(points: List<TrackPoint>) {
        val map = binding.routeMap
        map.overlays.clear()
        if (points.isEmpty()) {
            map.invalidate()
            return
        }

        val geoPoints = points.map { GeoPoint(it.lat, it.lon) }

        val polyline = Polyline().apply {
            setPoints(geoPoints)
            outlinePaint.color = Color.parseColor("#1F6F5B")
            outlinePaint.strokeWidth = 8f
        }
        map.overlays.add(polyline)
        map.overlays.add(marker(geoPoints.first(), R.drawable.ic_marker_start))
        if (geoPoints.size > 1) {
            map.overlays.add(marker(geoPoints.last(), R.drawable.ic_marker_end))
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

    private fun marker(point: GeoPoint, iconRes: Int): Marker =
        Marker(binding.routeMap).apply {
            position = point
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
            icon = ContextCompat.getDrawable(this@RouteActivity, iconRes)
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

    companion object {
        private const val EXTRA_DATE = "date"

        fun start(context: Context, dateIso: String? = null) {
            context.startActivity(
                Intent(context, RouteActivity::class.java).apply {
                    if (dateIso != null) putExtra(EXTRA_DATE, dateIso)
                },
            )
        }
    }
}
