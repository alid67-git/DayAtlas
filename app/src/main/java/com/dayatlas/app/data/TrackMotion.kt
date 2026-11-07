package com.dayatlas.app.data

/**
 * Shared rules for GPS segments.
 *
 * **Distance + active time + avg/max speed** use [pathLegs]:
 * 1. Thin to ~[PATH_COMPARE_INTERVAL_MS] anchors (sample-rate fair),
 * 2. Light lat/lon smooth + Douglas–Peucker (GPS cloud fair),
 * 3. Drop teleports / long gaps / near-stationary, cancel short A↔B pairs.
 *
 * Without (2), a phone that lands more fixes (Samsung fused / weak Doze)
 * still out-rolls a quieter phone by tens of km of lateral jitter even after
 * 60 s thinning — the 73 vs 66 / 84 vs 59 class of day gaps.
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

    /**
     * Douglas–Peucker tolerance. Lateral GPS error on a moving phone is
     * often 20–60 m; collapsing wiggles under this keeps day km stable
     * across OEMs without erasing real city blocks (~80 m+).
     */
    const val PATH_SIMPLIFY_EPSILON_M = 55.0

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
     * Smooth + Douglas–Peucker on continuous runs only — never across a
     * gap/jump so airplane corridors are not “simplified” into fake distance.
     */
    fun pathSimplify(
        points: List<TrackPoint>,
        epsilonMeters: Double = PATH_SIMPLIFY_EPSILON_M,
    ): List<TrackPoint> {
        if (points.size < 3) return points
        val out = ArrayList<TrackPoint>(points.size)
        var runStart = 0
        for (i in 1 until points.size) {
            val prev = points[i - 1]
            val cur = points[i]
            val dt = cur.timeMillis - prev.lastTimeMillis
            val dist = Geo.haversineMeters(prev.lat, prev.lon, cur.lat, cur.lon)
            val breakRun = dt <= 0L ||
                dt > MAX_SEGMENT_GAP_MS ||
                dist >= RouteGaps.MAX_CONTINUOUS_METERS ||
                JumpFilter.isJump(prev, cur)
            if (breakRun) {
                appendSimplifiedRun(points, runStart, i - 1, epsilonMeters, out)
                runStart = i
            }
        }
        appendSimplifiedRun(points, runStart, points.lastIndex, epsilonMeters, out)
        return out
    }

    private fun appendSimplifiedRun(
        points: List<TrackPoint>,
        from: Int,
        to: Int,
        epsilonMeters: Double,
        out: ArrayList<TrackPoint>,
    ) {
        if (from > to) return
        if (from == to) {
            out.add(points[from])
            return
        }
        var run: List<TrackPoint> = points.subList(from, to + 1)
        // Two light passes knock down ±40 m sawtooth GPS without erasing
        // ~80 m city blocks (perp distance on a right-angle stays ~60 m).
        run = smoothRun(run)
        run = smoothRun(run)
        val keep = BooleanArray(run.size)
        keep[0] = true
        keep[run.lastIndex] = true
        rdpMark(run, 0, run.lastIndex, epsilonMeters, keep)
        // Emit kept pins; if RDP left a span longer than the gap rule,
        // interpolate quiet chord bridges (not the noisy originals).
        val keptIdx = ArrayList<Int>()
        for (i in run.indices) if (keep[i]) keptIdx.add(i)
        for (k in keptIdx.indices) {
            val idx = keptIdx[k]
            if (k == 0) {
                out.add(run[idx])
                continue
            }
            val prev = out.last()
            val next = run[idx]
            appendChordBridges(prev, next, MAX_SEGMENT_GAP_MS, out)
            out.add(next)
        }
    }

    private fun smoothRun(run: List<TrackPoint>): List<TrackPoint> {
        if (run.size < 3) return run
        val out = ArrayList<TrackPoint>(run.size)
        out.add(run.first())
        for (i in 1 until run.lastIndex) {
            val a = run[i - 1]
            val b = run[i]
            val c = run[i + 1]
            out.add(
                b.copy(
                    lat = (a.lat + b.lat + c.lat) / 3.0,
                    lon = (a.lon + b.lon + c.lon) / 3.0,
                ),
            )
        }
        out.add(run.last())
        return out
    }

    /**
     * Insert linear interpolations so consecutive emitted pins stay within
     * [maxDtMs]. Bridges follow the quiet chord — reusing noisy GPS originals
     * would undo Douglas–Peucker.
     */
    private fun appendChordBridges(
        prev: TrackPoint,
        next: TrackPoint,
        maxDtMs: Long,
        out: ArrayList<TrackPoint>,
    ) {
        val totalDt = next.timeMillis - prev.lastTimeMillis
        if (totalDt <= maxDtMs) return
        val steps = ((totalDt + maxDtMs - 1) / maxDtMs).toInt()
        for (s in 1 until steps) {
            val t = prev.lastTimeMillis + s * (totalDt / steps)
            val frac = (t - prev.lastTimeMillis).toDouble() / totalDt.toDouble()
            out.add(
                TrackPoint(
                    timeMillis = t,
                    lat = prev.lat + (next.lat - prev.lat) * frac,
                    lon = prev.lon + (next.lon - prev.lon) * frac,
                    accuracyMeters = null,
                    lastTimeMillis = t,
                ),
            )
        }
    }

    private fun rdpMark(
        run: List<TrackPoint>,
        start: Int,
        end: Int,
        epsilonMeters: Double,
        keep: BooleanArray,
    ) {
        if (end <= start + 1) return
        val a = run[start]
        val b = run[end]
        var maxDist = 0.0
        var maxIdx = -1
        for (i in start + 1 until end) {
            val d = perpendicularDistanceMeters(run[i], a, b)
            if (d > maxDist) {
                maxDist = d
                maxIdx = i
            }
        }
        if (maxIdx < 0 || maxDist <= epsilonMeters) return
        keep[maxIdx] = true
        rdpMark(run, start, maxIdx, epsilonMeters, keep)
        rdpMark(run, maxIdx, end, epsilonMeters, keep)
    }

    /**
     * Cross-track distance from [p] to segment [a]→[b], in meters.
     * Degenerate segments fall back to distance-to-[a].
     */
    internal fun perpendicularDistanceMeters(
        p: TrackPoint,
        a: TrackPoint,
        b: TrackPoint,
    ): Double {
        val ab = Geo.haversineMeters(a.lat, a.lon, b.lat, b.lon)
        if (ab < 1.0) {
            return Geo.haversineMeters(a.lat, a.lon, p.lat, p.lon)
        }
        // Equirectangular local frame around [a] (good for <2 km legs).
        val cosLat = kotlin.math.cos(Math.toRadians(a.lat)).coerceAtLeast(1e-6)
        val ax = 0.0
        val ay = 0.0
        val bx = (b.lon - a.lon) * cosLat * 111_320.0
        val by = (b.lat - a.lat) * 110_540.0
        val px = (p.lon - a.lon) * cosLat * 111_320.0
        val py = (p.lat - a.lat) * 110_540.0
        val abLen2 = bx * bx + by * by
        val t = ((px * bx + py * by) / abLen2).coerceIn(0.0, 1.0)
        val qx = ax + t * bx
        val qy = ay + t * by
        val dx = px - qx
        val dy = py - qy
        return kotlin.math.sqrt(dx * dx + dy * dy)
    }

    /**
     * Path legs for daily km / active time. Anchors → simplify → segment
     * filter → cancel short reverse pairs.
     */
    fun pathLegs(points: List<TrackPoint>): List<Segment> {
        val anchors = pathSimplify(pathAnchors(points))
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
