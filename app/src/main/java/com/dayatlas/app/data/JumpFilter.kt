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

    /**
     * Walks the day comparing each point to the last **accepted** (non-jump)
     * point, not to its raw predecessor — the same anchor [shouldAccept]
     * uses live. Comparing to the raw predecessor instead (the old approach)
     * meant one bad fix corrupted two segments: it flagged the bad point
     * itself, then also flagged the very next *good* point, since that
     * point's distance from the bad one still looked like a teleport. That
     * produced two flagged rows for one actual mistake, with no way to tell
     * from the list which one was real — deleting the wrong row left the
     * other stuck showing a jump forever. Anchoring on the last accepted
     * point instead means each real gap gets exactly one row, and once it's
     * deleted the rest of the day is already consistent.
     */
    fun findJumps(points: List<TrackPoint>): List<Jump> {
        if (points.size < 2) return emptyList()
        val out = ArrayList<Jump>()
        var anchorIndex = 0
        for (i in 1 until points.size) {
            val anchor = points[anchorIndex]
            val cur = points[i]
            if (!isJump(anchor, cur)) {
                anchorIndex = i
                continue
            }
            val distance = Geo.haversineMeters(anchor.lat, anchor.lon, cur.lat, cur.lon)
            val dt = (cur.timeMillis - anchor.timeMillis).coerceAtLeast(1L)
            val speedKmh = (distance / (dt / 1_000.0)) * 3.6
            out.add(
                Jump(
                    index = i,
                    point = cur,
                    distanceMeters = distance,
                    speedKmh = speedKmh,
                    deltaMillis = cur.timeMillis - anchor.timeMillis,
                ),
            )
            // anchorIndex stays put — cur was rejected, so the next point is
            // still judged against the last point we trust, not against cur.
        }
        return out
    }
}
