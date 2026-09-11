package com.dayatlas.app.data

/**
 * Shared rules for which consecutive GPS fixes count as real movement
 * (distance, speed, active time). Home courtyard jitter is filtered out.
 */
object TrackMotion {
    /** Same noise floor as [com.dayatlas.app.location.StationaryBackoff.TOLERANCE_METERS], raised for stats. */
    const val MIN_SEGMENT_METERS = 40.0

    /** Walking floor — below this, treat as stationary jitter. */
    const val STATIONARY_SPEED_KMH = 5.0

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
        return Segment(distM, speedKmh, dtMs)
    }
}
