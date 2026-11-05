package com.dayatlas.app.route

import android.content.Context
import android.graphics.Color
import android.view.View
import androidx.core.content.ContextCompat
import com.dayatlas.app.R
import com.dayatlas.app.data.DwellStops
import com.dayatlas.app.data.Geo
import com.dayatlas.app.data.JumpFilter
import com.dayatlas.app.data.RouteGaps
import com.dayatlas.app.data.TrackPoint
import com.dayatlas.app.data.TrackPointLookup
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
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
    /**
     * Extra padding around a day’s track so start/end markers stay clear of
     * the floating day-nav strip at the bottom of the map pane.
     */
    private const val DEFAULT_ZOOM_BORDER_PX = 200
    private const val MIN_ZOOM_INNER_PX = 48
    private const val MIN_REFIT_DELTA_PX = 24
    private const val MAX_ZOOM = 18.0
    private const val MIN_ZOOM = 3.0
    /** Pull back one-and-a-half levels after fit so edge pins aren’t clipped. */
    private const val FIT_ZOOM_OUT_LEVELS = 1.5

    private var boundMap: WeakReference<MapView>? = null
    private var shownDateIso: String? = null
    private var trackPoly: Polyline? = null
    /** Total geometry pins last drawn — not the last segment's vertex count. */
    private var shownPointCount: Int = 0
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
        dwellStops: List<DwellStops.Stop> = emptyList(),
        /** Stops the user has annotated (note/photo) — drawn in the active color. */
        activeDwellKeys: Set<Long> = emptySet(),
        onDwellTap: ((DwellStops.Stop) -> Unit)? = null,
        /** Tap near any track point (route or map) to add note/photo. */
        onTrackPointTap: ((TrackPoint) -> Unit)? = null,
        /**
         * Tap on empty map (no track/marker hit). Used by the daily pane to
         * enter fullscreen examine mode; ignored when null.
         */
        onMapTap: (() -> Unit)? = null,
        /** Live GPS updates should not animate zoom (causes blank flashes / ANR). */
        animateZoom: Boolean = false,
        fitCamera: Boolean = true,
    ) {
        clearLiveState(map)
        map.overlays.clear()
        val hasPoints = points.isNotEmpty()
        emptyState?.visibility = if (hasPoints) View.GONE else View.VISIBLE
        if (!hasPoints) {
            addMapTapOverlay(map, points = emptyList(), onTrackPointTap = null, onMapTap = onMapTap)
            map.invalidate()
            return
        }

        val geoPoints = points.map { GeoPoint(it.lat, it.lon) }
        // One Polyline per contiguous run — gap hops after airplane / null GPS
        // must not draw fake corridor lines (those look like parallel tracks).
        val segmentStarts = RouteGaps.segmentStarts(points)
        var poly: Polyline? = null
        for (s in segmentStarts.indices) {
            val from = segmentStarts[s]
            val toExclusive = segmentStarts.getOrNull(s + 1) ?: points.size
            if (toExclusive - from < 1) continue
            val segmentGeo = geoPoints.subList(from, toExclusive)
            // A lone reconnect pin still needs a vertex so live updates can extend it.
            val segmentPoly = Polyline().apply {
                setPoints(ArrayList(segmentGeo))
                outlinePaint.color = Color.parseColor("#0F766E")
                outlinePaint.strokeWidth = 8f
                if (onTrackPointTap != null) {
                    setOnClickListener { _, _, eventPos ->
                        val hit = TrackPointLookup.nearest(
                            points,
                            eventPos.latitude,
                            eventPos.longitude,
                        ) ?: return@setOnClickListener false
                        onTrackPointTap(hit.point)
                        true
                    }
                }
            }
            map.overlays.add(segmentPoly)
            poly = segmentPoly
        }
        map.overlays.add(marker(map, context, geoPoints.first(), R.drawable.ic_marker_start))
        val end = if (geoPoints.size > 1) {
            marker(map, context, geoPoints.last(), R.drawable.ic_marker_end).also {
                map.overlays.add(it)
            }
        } else {
            null
        }

        addMapTapOverlay(map, points, onTrackPointTap, onMapTap)

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

        val dwellKeys = dwellStops.map { it.noteKey }.toSet()
        dwellStops.forEach { stop ->
            val active = stop.noteKey in activeDwellKeys
            val gp = GeoPoint(stop.lat, stop.lon)
            map.overlays.add(
                Marker(map).apply {
                    position = gp
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                    icon = ContextCompat.getDrawable(
                        context,
                        if (active) R.drawable.ic_marker_dwell else R.drawable.ic_marker_dwell_passive,
                    )
                    setInfoWindow(null)
                    if (onDwellTap != null) {
                        setOnMarkerClickListener { _, _ ->
                            onDwellTap(stop)
                            true
                        }
                    }
                },
            )
        }

        // Annotated points that are not already covered by a dwell marker.
        if (activeDwellKeys.isNotEmpty()) {
            val byArrival = points.associateBy { it.timeMillis }
            activeDwellKeys.forEach { key ->
                if (key in dwellKeys) return@forEach
                val point = byArrival[key] ?: return@forEach
                val gp = GeoPoint(point.lat, point.lon)
                map.overlays.add(
                    Marker(map).apply {
                        position = gp
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                        icon = ContextCompat.getDrawable(context, R.drawable.ic_marker_note)
                        setInfoWindow(null)
                        if (onTrackPointTap != null) {
                            setOnMarkerClickListener { _, _ ->
                                onTrackPointTap(point)
                                true
                            }
                        }
                    },
                )
            }
        }

        boundMap = WeakReference(map)
        shownDateIso = dateIso
        trackPoly = poly // last contiguous segment — live ticks extend this only
        shownPointCount = points.size
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
        // [pointCount] is geometry pin count (points.size), not gpsCheckCount.
        // [trackPoly] may be only the last contiguous segment after a gap break.
        val existing = ArrayList(poly.actualPoints)
        val last = existing.lastOrNull()
        if (last != null) {
            val hopM = Geo.haversineMeters(last.latitude, last.longitude, lat, lon)
            // Possible airplane/null-GPS reconnect: force a full [show] so the
            // hop is drawn as a new segment instead of extending this polyline.
            // Threshold is MAX_CONTINUOUS (not MIN_BREAK) so normal highway
            // hops at 30–60 s (~1–3 km) still use the cheap live path.
            if (hopM >= RouteGaps.MAX_CONTINUOUS_METERS) {
                return false
            }
        }
        when {
            pointCount == shownPointCount && existing.isNotEmpty() -> {
                existing[existing.lastIndex] = geo
                poly.setPoints(existing)
                val fit = ArrayList(pendingGeo.orEmpty())
                if (fit.isNotEmpty()) {
                    fit[fit.lastIndex] = geo
                    pendingGeo = fit
                }
            }
            pointCount == shownPointCount + 1 -> {
                poly.addPoint(geo)
                existing.add(geo)
                shownPointCount = pointCount
                val fit = ArrayList(pendingGeo.orEmpty())
                fit.add(geo)
                pendingGeo = fit
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
            detachSizeListener(target)
            refitPass?.let { target.removeCallbacks(it) }
            refitDelayed?.let { target.removeCallbacks(it) }
        }
        refitPass = null
        refitDelayed = null
        boundMap = null
        shownDateIso = null
        trackPoly = null
        shownPointCount = 0
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
            // Single-point day: neighborhood, not street-level.
            map.controller.setZoom(16.0)
            map.controller.setCenter(geoPoints.last())
        } else if (focus != null && jumps.size == 1) {
            map.controller.setZoom(12.0)
            map.controller.setCenter(focus)
        } else {
            val border = safeZoomBorder(map.width, map.height)
            map.zoomToBoundingBox(box, animateZoom, border)
            val zoom = map.zoomLevelDouble
            if (!zoom.isFinite() || zoom < MIN_ZOOM) {
                map.controller.setZoom(11.0)
                map.controller.setCenter(box.centerWithDateLine)
            } else {
                // Pull back from a bare bounding-box fit — long day tracks
                // were opening too tight, and bottom markers sat under the
                // floating day-nav strip.
                val wider = (zoom - FIT_ZOOM_OUT_LEVELS).coerceIn(MIN_ZOOM, MAX_ZOOM)
                map.controller.setZoom(wider)
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

    private fun addMapTapOverlay(
        map: MapView,
        points: List<TrackPoint>,
        onTrackPointTap: ((TrackPoint) -> Unit)?,
        onMapTap: (() -> Unit)?,
    ) {
        if (onTrackPointTap == null && onMapTap == null) return
        map.overlays.add(
            MapEventsOverlay(
                object : MapEventsReceiver {
                    override fun singleTapConfirmedHelper(p: GeoPoint): Boolean {
                        if (onTrackPointTap != null && points.isNotEmpty()) {
                            val hit = TrackPointLookup.nearest(points, p.latitude, p.longitude)
                            if (hit != null) {
                                onTrackPointTap(hit.point)
                                return true
                            }
                        }
                        if (onMapTap != null) {
                            onMapTap()
                            return true
                        }
                        return false
                    }

                    override fun longPressHelper(p: GeoPoint): Boolean = false
                },
            ),
        )
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
