package com.dayatlas.app.data

/**
 * Shared rules for GPS segments.
 *
 * **Distance + active time + avg/max speed** use [pathLegs]: polyline
 * geometry with teleport / long-gap / near-stationary cuts, plus a short
 * back-and-forth damper so courtyard GPS cloud does not sum. Keeping
 * activity on the old stricter [meaningfulSegment] recreated the same
 * phone-to-phone skew on “aktif süre” that we fixed for km.
 *
 * Absolute “short hop + under 15 km/h” courtyards are intentionally gone —
 * they erased real 15 s city samples under ~24 km/h and caused ~30 % day
 * swings between phones.
 *
 * Before summing, samples are collapsed to about [PATH_COMPARE_INTERVAL_MS]
 * so a 10–20 s phone cannot rack up 68→89 style km from extra curve detail
 * and GPS jitter versus a 60 s phone on the same drive.
 *
 * [meaningfulSegment] remains for callers that still want the strict
 * activity band (tests / future UI); SpeedStats no longer uses it.
 */
object TrackMotion {
    /**
     * Ignore segments shorter than the same-place pin radius so a new pin
     * that barely left the courtyard bubble never inflates distance alone.
     */
    const val MIN_SEGMENT_METERS = Geo.SAME_PLACE_RADIUS_M

    /**
     * Short hops in the "fast walk / slow crawl" band are usually GPS cloud
     * bounce for *activity* stats ([meaningfulSegment] only).
     */
    const val GPS_HOP_MAX_METERS = 150.0
    const val GPS_HOP_MIN_SPEED_KMH = 8.0
    const val GPS_HOP_DRIVE_SPEED_KMH = 25.0

    /** Tiny hop floor for path length (below same-place collapse noise). */
    const val PATH_NOISE_FLOOR_M = 15.0

    /**
     * Paired back-and-forth under this length (each leg) is treated as
     * courtyard oscillation, not driven distance. Larger than same-place
     * radius so ~85 m pin wander still cancels.
     */
    const val PATH_OSCILLATION_MAX_METERS = 120.0

    /** Dot product of unit step vectors below this ⇒ reverse hop. */
    const val PATH_OSCILLATION_DOT = -0.3

    /** Walking floor — below this, treat as stationary jitter (activity). */
    const val STATIONARY_SPEED_KMH = 3.0

    /**
     * Path / active-time floor. Slightly above [STATIONARY_SPEED_KMH] so
     * ~50 m courtyard bounce (~3 km/h) never becomes “aktif süre”, while a
     * real 5 km/h walk still counts.
     */
    const val PATH_STATIONARY_SPEED_KMH = 4.0

    /**
     * Target spacing for distance / active-time anchors. Finer GPS streams
     * are thinned to this so phone A at 15 s and phone B at 60 s measure
     * comparable chords (still keeps the day start/end pins).
     */
    const val PATH_COMPARE_INTERVAL_MS = 60_000L

    /** Keep in sync with [JumpFilter.MAX_SPEED_KMH] so stats match accept rules. */
    const val MAX_REALISTIC_SPEED_KMH = JumpFilter.MAX_SPEED_KMH

    /**
     * Gaps longer than this are reconnects after airplane / Doze / signal
     * loss — not driven distance. Kept in sync with [RouteGaps.GAP_MS].
     */
    const val MAX_SEGMENT_GAP_MS = RouteGaps.GAP_MS

    data class Segment(
        val distanceMeters: Double,
        val speedKmh: Double,
        val dtMs: Long,
        val dLat: Double = 0.0,
        val dLon: Double = 0.0,
    )

    /**
     * Single-leg geometry check (no oscillation context). Prefer [pathLegs]
     * for day totals so A↔B courtyard cloud cancels.
     */
    fun pathSegment(prev: TrackPoint, cur: TrackPoint): Segment? {
        val dtMs = cur.timeMillis - prev.lastTimeMillis
        if (dtMs <= 0L || dtMs > MAX_SEGMENT_GAP_MS) return null
        val distM = Geo.haversineMeters(prev.lat, prev.lon, cur.lat, cur.lon)
        if (distM < PATH_NOISE_FLOOR_M) return null
        if (distM >= RouteGaps.MAX_CONTINUOUS_METERS) return null
        if (JumpFilter.isJump(prev, cur)) return null
        val speedKmh = (distM / (dtMs / 1000.0)) * 3.6
        if (speedKmh < PATH_STATIONARY_SPEED_KMH || speedKmh > MAX_REALISTIC_SPEED_KMH) {
            return null
        }
        return Segment(
            distanceMeters = distM,
            speedKmh = speedKmh,
            dtMs = dtMs,
            dLat = cur.lat - prev.lat,
            dLon = cur.lon - prev.lon,
        )
    }

    /**
     * Thin a dense stream to ~[PATH_COMPARE_INTERVAL_MS] anchors. Sparse
     * days (already ≥ interval) pass through unchanged aside from always
     * keeping the first and last pin.
     */
    fun pathAnchors(points: List<TrackPoint>): List<TrackPoint> {
        if (points.size < 3) return points
        val out = ArrayList<TrackPoint>(points.size)
        out.add(points.first())
        val lastIndex = points.lastIndex
        for (i in 1 until lastIndex) {
            val p = points[i]
            val dt = p.timeMillis - out.last().lastTimeMillis
            if (dt >= PATH_COMPARE_INTERVAL_MS) {
                out.add(p)
            }
        }
        val end = points.last()
        if (out.last().timeMillis != end.timeMillis ||
            out.last().lat != end.lat ||
            out.last().lon != end.lon
        ) {
            out.add(end)
        }
        return out
    }

    /**
     * Path legs for daily km / active time. Anchors → segment filter →
     * cancel short reverse pairs (courtyard cloud).
     */
    fun pathLegs(points: List<TrackPoint>): List<Segment> {
        val anchors = pathAnchors(points)
        if (anchors.size < 2) return emptyList()
        val out = ArrayList<Segment>(anchors.size - 1)
        for (i in 1 until anchors.size) {
            val seg = pathSegment(anchors[i - 1], anchors[i]) ?: continue
            val last = out.lastOrNull()
            if (last != null && isCourtyardOscillation(last, seg)) {
                out.removeAt(out.lastIndex)
                continue
            }
            out.add(seg)
        }
        return out
    }

    private fun isCourtyardOscillation(a: Segment, b: Segment): Boolean {
        if (a.distanceMeters > PATH_OSCILLATION_MAX_METERS) return false
        if (b.distanceMeters > PATH_OSCILLATION_MAX_METERS) return false
        val aLen = hypot(a.dLat, a.dLon)
        val bLen = hypot(b.dLat, b.dLon)
        if (aLen < 1e-12 || bLen < 1e-12) return false
        val dot = (a.dLat * b.dLat + a.dLon * b.dLon) / (aLen * bLen)
        return dot <= PATH_OSCILLATION_DOT
    }

    private fun hypot(x: Double, y: Double): Double = kotlin.math.sqrt(x * x + y * y)

    /** Activity / speed cards — stricter than [pathSegment]. */
    fun meaningfulSegment(prev: TrackPoint, cur: TrackPoint): Segment? {
        val dtMs = cur.timeMillis - prev.lastTimeMillis
        if (dtMs <= 0L || dtMs > MAX_SEGMENT_GAP_MS) return null
        val distM = Geo.haversineMeters(prev.lat, prev.lon, cur.lat, cur.lon)
        if (distM < MIN_SEGMENT_METERS) return null
        if (distM >= RouteGaps.MAX_CONTINUOUS_METERS) return null
        if (JumpFilter.isJump(prev, cur)) return null
        val noiseGate = maxOf(
            accuracyNoiseGateMeters(prev.accuracyMeters),
            accuracyNoiseGateMeters(cur.accuracyMeters),
        )
        if (noiseGate > 0.0 && distM <= noiseGate) return null
        val speedKmh = (distM / (dtMs / 1000.0)) * 3.6
        if (speedKmh < STATIONARY_SPEED_KMH || speedKmh > MAX_REALISTIC_SPEED_KMH) return null
        if (distM < GPS_HOP_MAX_METERS &&
            speedKmh >= GPS_HOP_MIN_SPEED_KMH &&
            speedKmh < GPS_HOP_DRIVE_SPEED_KMH
        ) {
            return null
        }
        return Segment(distM, speedKmh, dtMs)
    }

    /**
     * RideAtlas-style accuracy circle for activity filters only.
     * Missing/invalid accuracy → 0 (no gate). Capped at [ACCURACY_NOISE_CAP_M].
     */
    fun accuracyNoiseGateMeters(accuracyMeters: Float?): Double {
        if (accuracyMeters == null || !accuracyMeters.isFinite() || accuracyMeters <= 0f) {
            return 0.0
        }
        return minOf(
            ACCURACY_NOISE_CAP_M,
            maxOf(ACCURACY_NOISE_FLOOR_M, 2.0 * accuracyMeters.toDouble()),
        )
    }

    const val ACCURACY_NOISE_FLOOR_M = 25.0
    /** Must stay ≥ [MIN_SEGMENT_METERS] or the gate never fires after pin collapse. */
    const val ACCURACY_NOISE_CAP_M = 100.0
}
