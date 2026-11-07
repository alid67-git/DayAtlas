package com.dayatlas.app.data

/**
 * Shared rules for GPS segments.
 *
 * **Distance** ([pathSegment]) stays close to the polyline geometry: only
 * drop teleports, long gaps, tiny noise, and slow courtyard cloud. Sample
 * rate must not erase real city hops — that caused 30–50 % phone-to-phone
 * gaps that corner-cutting alone cannot explain.
 *
 * **Active time / speeds** ([meaningfulSegment]) stay stricter so home
 * jitter does not inflate “driving” stats.
 */
object TrackMotion {
    /**
     * Ignore segments shorter than the same-place pin radius so a new pin
     * that barely left the courtyard bubble never inflates distance alone.
     */
    const val MIN_SEGMENT_METERS = Geo.SAME_PLACE_RADIUS_M

    /**
     * Short hops in the "fast walk / slow crawl" band are usually GPS cloud
     * bounce for *activity* stats. Distance uses a milder courtyard rule
     * ([PATH_COURTYARD_MAX_METERS] / [PATH_COURTYARD_MAX_SPEED_KMH]) so
     * frequent 10–20 s city samples are not wiped.
     */
    const val GPS_HOP_MAX_METERS = 150.0
    const val GPS_HOP_MIN_SPEED_KMH = 8.0
    const val GPS_HOP_DRIVE_SPEED_KMH = 25.0

    /** Distance-only: slow short cloud, not 20+ km/h urban crawl. */
    const val PATH_COURTYARD_MAX_METERS = 100.0
    const val PATH_COURTYARD_MAX_SPEED_KMH = 15.0

    /** Tiny hop floor for path length (below same-place collapse noise). */
    const val PATH_NOISE_FLOOR_M = 15.0

    /** Walking floor — below this, treat as stationary jitter (activity). */
    const val STATIONARY_SPEED_KMH = 3.0

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
    )

    /**
     * Geometry for daily km. No accuracy gate and no 8–25 km/h activity band —
     * those punished frequent samplers far beyond polyline densification.
     */
    fun pathSegment(prev: TrackPoint, cur: TrackPoint): Segment? {
        val dtMs = cur.timeMillis - prev.lastTimeMillis
        if (dtMs <= 0L || dtMs > MAX_SEGMENT_GAP_MS) return null
        val distM = Geo.haversineMeters(prev.lat, prev.lon, cur.lat, cur.lon)
        if (distM < PATH_NOISE_FLOOR_M) return null
        if (distM >= RouteGaps.MAX_CONTINUOUS_METERS) return null
        if (JumpFilter.isJump(prev, cur)) return null
        val speedKmh = (distM / (dtMs / 1000.0)) * 3.6
        if (speedKmh > MAX_REALISTIC_SPEED_KMH) return null
        // Courtyard cloud only (slow + short). Real crawl/drive still counts.
        if (distM < PATH_COURTYARD_MAX_METERS && speedKmh < PATH_COURTYARD_MAX_SPEED_KMH) {
            return null
        }
        return Segment(distM, speedKmh, dtMs)
    }

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
