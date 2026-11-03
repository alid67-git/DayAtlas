package com.dayatlas.app.data

/**
 * Strips reconnect teleports and hard GPS jumps from an already-recorded day.
 *
 * After airplane mode / null GPS, [JumpFilter] may still have accepted a
 * multi-km hop (elapsed time made the speed look normal). Those pins stay in
 * the JSON and draw as fake corridor passes until removed.
 *
 * Soft gap-reconnects (≥ [RouteGaps.MIN_BREAK_METERS] after > [RouteGaps.GAP_MS])
 * are dropped only when the next pin bounces back toward the last kept point
 * (or when the hop is trailing with nothing to confirm a new cluster). A
 * sustained new cluster after a radio gap is kept — [RouteGaps] still breaks
 * the drawn path so no fake corridor appears.
 */
object TrackHeal {
    /**
     * Keeps a point only when it continues smoothly from the last kept pin,
     * or starts a sustained cluster after a radio gap.
     */
    fun withoutReconnectTeleports(points: List<TrackPoint>): List<TrackPoint> {
        if (points.size < 2) return points
        val kept = ArrayList<TrackPoint>(points.size)
        kept.add(points[0])
        var i = 1
        while (i < points.size) {
            val prev = kept.last()
            val cur = points[i]
            if (!shouldDrop(prev, cur)) {
                kept.add(cur)
                i++
                continue
            }
            // Potential teleport / reconnect. Look ahead before discarding —
            // a real post-gap relocation must not wipe the rest of the day.
            if (i + 1 >= points.size) {
                // Trailing hop with no confirmation → drop (orphan reconnect).
                i++
                continue
            }
            val next = points[i + 1]
            val nextToPrev = Geo.haversineMeters(prev.lat, prev.lon, next.lat, next.lon)
            val nextToCur = Geo.haversineMeters(cur.lat, cur.lon, next.lat, next.lon)
            if (nextToPrev < nextToCur) {
                // Bounce back toward the last kept pin → cur is a spike.
                i++
                continue
            }
            // New cluster around cur — keep it as the start of the post-gap run.
            kept.add(cur)
            i++
        }
        return kept
    }

    fun shouldDrop(previous: TrackPoint, candidate: TrackPoint): Boolean {
        if (JumpFilter.isJump(previous, candidate)) return true
        val distance = Geo.haversineMeters(
            previous.lat,
            previous.lon,
            candidate.lat,
            candidate.lon,
        )
        if (distance < RouteGaps.MIN_BREAK_METERS) return false
        val dt = candidate.timeMillis - previous.lastTimeMillis
        return dt > RouteGaps.GAP_MS
    }
}
