package com.dayatlas.app.data

/**
 * Shared rules for which consecutive GPS fixes count as real movement
 * (distance, speed, active time). Home courtyard jitter is filtered out.
 */
object TrackMotion {
    /**
     * Ignore segments shorter than the same-place pin radius so a new pin
     * that barely left the courtyard bubble never inflates distance alone.
     */
    const val MIN_SEGMENT_METERS = Geo.SAME_PLACE_RADIUS_M

    /**
     * Short hops in the "fast walk / slow crawl" band are usually GPS cloud
     * bounce (e.g. 85 m in 30 s ≈ 10 km/h). Clear driving (≥ [GPS_HOP_DRIVE_SPEED_KMH])
     * must still count — frequent samples on a Redmi (10–20 s) only cover
     * ~100–150 m at city speed and used to be wiped by a blanket &lt;150 m rule,
     * under-counting vs a Samsung on a 30 s interval.
     */
    const val GPS_HOP_MAX_METERS = 150.0
    const val GPS_HOP_MIN_SPEED_KMH = 8.0
    const val GPS_HOP_DRIVE_SPEED_KMH = 25.0

    /** Walking floor — below this, treat as stationary jitter. */
    const val STATIONARY_SPEED_KMH = 3.0

    /** Keep in sync with [JumpFilter.MAX_SPEED_KMH] so stats match accept rules. */
    const val MAX_REALISTIC_SPEED_KMH = JumpFilter.MAX_SPEED_KMH

    /**
     * Gaps longer than this are reconnects after airplane / Doze / signal
     * loss — not driven distance. Kept in sync with [RouteGaps.GAP_MS].
     * (Previously 20 min, which still counted Phuket-scale teleports after a
     * ~8–15 min radio outage as ~100–160 km/h "driving".)
     */
    const val MAX_SEGMENT_GAP_MS = RouteGaps.GAP_MS

    data class Segment(
        val distanceMeters: Double,
        val speedKmh: Double,
        val dtMs: Long,
    )

    fun meaningfulSegment(prev: TrackPoint, cur: TrackPoint): Segment? {
        val dtMs = cur.timeMillis - prev.lastTimeMillis
        if (dtMs <= 0L || dtMs > MAX_SEGMENT_GAP_MS) return null
        val distM = Geo.haversineMeters(prev.lat, prev.lon, cur.lat, cur.lon)
        if (distM < MIN_SEGMENT_METERS) return null
        if (distM >= RouteGaps.MAX_CONTINUOUS_METERS) return null
        if (JumpFilter.isJump(prev, cur)) return null
        // Cheap phones often claim ~8–40 m accuracy while hopping indoors.
        // Cap the gate so a Redmi reporting 60–80 m accuracy cannot erase
        // every urban hop under ~160 m (that halved Phuket day distance).
        val noiseGate = maxOf(
            accuracyNoiseGateMeters(prev.accuracyMeters),
            accuracyNoiseGateMeters(cur.accuracyMeters),
        )
        if (noiseGate > 0.0 && distM <= noiseGate) return null
        val speedKmh = (distM / (dtMs / 1000.0)) * 3.6
        if (speedKmh < STATIONARY_SPEED_KMH || speedKmh > MAX_REALISTIC_SPEED_KMH) return null
        // Courtyard / ambiguous band only — not clear city driving.
        if (distM < GPS_HOP_MAX_METERS &&
            speedKmh >= GPS_HOP_MIN_SPEED_KMH &&
            speedKmh < GPS_HOP_DRIVE_SPEED_KMH
        ) {
            return null
        }
        return Segment(distM, speedKmh, dtMs)
    }

    /**
     * RideAtlas-style accuracy circle. Missing/invalid accuracy → 0 (no gate).
     * Result is capped at [ACCURACY_NOISE_CAP_M] so terrible reported accuracy
     * cannot blank out real urban segments.
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
