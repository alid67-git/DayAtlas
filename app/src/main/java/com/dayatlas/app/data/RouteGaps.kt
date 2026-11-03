package com.dayatlas.app.data

/**
 * Decides where a day's track should be drawn as disconnected segments
 * instead of one continuous polyline.
 *
 * After airplane mode / Doze / null GPS, the next fix can sit many km away
 * while [JumpFilter] still accepts it (elapsed time makes the implied speed
 * look like normal driving). Connecting that hop draws a fake corridor line;
 * repeating the hop (bad fix ↔ real fix) looks like parallel overlapping tracks.
 */
object RouteGaps {
    /**
     * Any hop at least this long after a tracking gap is treated as a
     * reconnect, not a driven path.
     */
    const val GAP_MS = 5 * 60_000L

    /** Ignore tiny reconnects (still inside the same block). */
    const val MIN_BREAK_METERS = 800.0

    /**
     * A single hop this long is never drawn continuously.
     *
     * At the common 60 s sample interval, 2.0 km ≈ 120 km/h — above normal
     * Phuket / city driving and below the old 5 km hole that still drew
     * inland explosion triangles at ~100–148 km/h.
     */
    const val MAX_CONTINUOUS_METERS = 2_000.0

    /**
     * ≥ [MIN_BREAK_METERS] hops faster than this are drawn as a break even
     * when under [MAX_CONTINUOUS_METERS] (1 min × 90 km/h ≈ 1.5 km spikes).
     */
    const val BREAK_SPEED_KMH = 90.0

    fun shouldBreak(previous: TrackPoint, current: TrackPoint): Boolean {
        val distance = Geo.haversineMeters(
            previous.lat,
            previous.lon,
            current.lat,
            current.lon,
        )
        if (distance < MIN_BREAK_METERS) return false
        if (distance >= MAX_CONTINUOUS_METERS) return true
        val dt = current.timeMillis - previous.lastTimeMillis
        if (dt > GAP_MS) return true
        if (dt > 0L) {
            val speedKmh = (distance / (dt / 1_000.0)) * 3.6
            if (speedKmh > BREAK_SPEED_KMH) return true
        }
        return JumpFilter.isJump(previous, current)
    }

    /** Split indexes are the start of each contiguous run (always includes 0). */
    fun segmentStarts(points: List<TrackPoint>): List<Int> {
        if (points.isEmpty()) return emptyList()
        val starts = ArrayList<Int>(4)
        starts.add(0)
        for (i in 1 until points.size) {
            if (shouldBreak(points[i - 1], points[i])) {
                starts.add(i)
            }
        }
        return starts
    }
}
