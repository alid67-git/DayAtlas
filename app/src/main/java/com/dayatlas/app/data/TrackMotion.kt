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
     * Short hops that are too fast for a walk are almost always GPS cloud
     * bounce (e.g. 85 m in 30 s ≈ 10 km/h). Real walking over the same
     * distance is slower (~5 km/h over a minute).
     */
    const val GPS_HOP_MAX_METERS = 150.0
    const val GPS_HOP_MIN_SPEED_KMH = 8.0

    /** Walking floor — below this, treat as stationary jitter. */
    const val STATIONARY_SPEED_KMH = 3.0

    const val MAX_REALISTIC_SPEED_KMH = 160.0

    const val MAX_SEGMENT_GAP_MS = 20 * 60_000L

    data class Segment(
        val distanceMeters: Double,
        val speedKmh: Double,
        val dtMs: Long,
    )

    fun meaningfulSegment(prev: TrackPoint, cur: TrackPoint): Segment? {
        val dtMs = cur.timeMillis - prev.timeMillis
        if (dtMs <= 0L || dtMs > MAX_SEGMENT_GAP_MS) return null
        val distM = Geo.haversineMeters(prev.lat, prev.lon, cur.lat, cur.lon)
        if (distM < MIN_SEGMENT_METERS) return null
        if (JumpFilter.isJump(prev, cur)) return null
        val speedKmh = (distM / (dtMs / 1000.0)) * 3.6
        if (speedKmh < STATIONARY_SPEED_KMH || speedKmh > MAX_REALISTIC_SPEED_KMH) return null
        // Courtyard GPS: new pins just outside same-place, sampled tightly.
        if (distM < GPS_HOP_MAX_METERS && speedKmh >= GPS_HOP_MIN_SPEED_KMH) return null
        return Segment(distM, speedKmh, dtMs)
    }
}
