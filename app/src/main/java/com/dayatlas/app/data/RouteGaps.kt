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
    const val MIN_BREAK_METERS = 1_000.0

    /**
     * A single hop this long is never drawn continuously — even dense
     * sampling at [JumpFilter.MAX_SPEED_KMH] stays well under this at the
     * default 30–60 s interval (~1.3–2.7 km).
     */
    const val MAX_CONTINUOUS_METERS = 5_000.0

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
