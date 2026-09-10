package com.dayatlas.app.data

/**
 * Detects GPS teleports / spikes between consecutive track points.
 *
 * DayAtlas samples sparsely (0.5–5 min). A real vehicle rarely exceeds ~160 km/h
 * sustained between samples; a single segment longer than [MAX_SEGMENT_METERS]
 * is almost always a bad fix. Pure logic — unit-tested; used both to reject
 * new samples and to list existing outliers for manual deletion.
 */
object JumpFilter {
    /** ~160 km/h — above typical road use for this daily log. */
    const val MAX_SPEED_KMH = 160.0

    /** Hard cap for one sample interval (~30 km ≈ 360 km/h over 5 min). */
    const val MAX_SEGMENT_METERS = 30_000.0

    data class Jump(
        /** Index of the suspect point in the day's [TrackPoint] list. */
        val index: Int,
        val point: TrackPoint,
        val distanceMeters: Double,
        val speedKmh: Double,
        val deltaMillis: Long,
    )

    fun isJump(previous: TrackPoint, candidate: TrackPoint): Boolean {
        val distance = Geo.haversineMeters(
            previous.lat,
            previous.lon,
            candidate.lat,
            candidate.lon,
        )
        if (distance >= MAX_SEGMENT_METERS) return true
        val dt = candidate.timeMillis - previous.timeMillis
        if (dt <= 0L) return distance > 50.0
        val speedKmh = (distance / (dt / 1_000.0)) * 3.6
        return speedKmh > MAX_SPEED_KMH
    }

    fun shouldAccept(existing: List<TrackPoint>, candidate: TrackPoint): Boolean {
        val previous = existing.lastOrNull() ?: return true
        return !isJump(previous, candidate)
    }

    fun findJumps(points: List<TrackPoint>): List<Jump> {
        if (points.size < 2) return emptyList()
        val out = ArrayList<Jump>()
        for (i in 1 until points.size) {
            val prev = points[i - 1]
            val cur = points[i]
            if (!isJump(prev, cur)) continue
            val distance = Geo.haversineMeters(prev.lat, prev.lon, cur.lat, cur.lon)
            val dt = (cur.timeMillis - prev.timeMillis).coerceAtLeast(1L)
            val speedKmh = (distance / (dt / 1_000.0)) * 3.6
            out.add(
                Jump(
                    index = i,
                    point = cur,
                    distanceMeters = distance,
                    speedKmh = speedKmh,
                    deltaMillis = cur.timeMillis - prev.timeMillis,
                ),
            )
        }
        return out
    }
}
