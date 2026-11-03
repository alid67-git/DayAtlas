package com.dayatlas.app.data

/**
 * Rebuilds a day's track into a coherent path by dropping explosion / spike
 * pins (airplane reconnect zigzags, hard GPS teleports) while keeping real
 * travel.
 *
 * Display-only by default — callers should not persist the result unless the
 * user explicitly asks to clean the file. Auto-writing stripped pins once
 * preferred the first cluster and could erase a real post-gap trip.
 */
object TrackHeal {
    /**
     * Iteratively removes the strongest geometric spike until the path is
     * stable, then drops a trailing reconnect orphan. Remaining points stay
     * in time order so the map can draw one continuous shape (or gap-split
     * only where a sustained relocation remains).
     */
    fun withoutReconnectTeleports(points: List<TrackPoint>): List<TrackPoint> {
        if (points.size < 2) return points
        if (points.size == 2) {
            return if (shouldDrop(points[0], points[1])) listOf(points[0]) else points
        }
        val working = points.toMutableList()
        while (working.size >= 3) {
            var bestIdx = -1
            var bestScore = 0.0
            for (i in 1 until working.lastIndex) {
                val score = spikeScore(working[i - 1], working[i], working[i + 1])
                if (score > bestScore) {
                    bestScore = score
                    bestIdx = i
                }
            }
            if (bestIdx < 0 || bestScore < MIN_SPIKE_SCORE_M) break
            working.removeAt(bestIdx)
        }
        while (working.size >= 2 && shouldDrop(working[working.lastIndex - 1], working.last())) {
            working.removeAt(working.lastIndex)
        }
        return working
    }

    /**
     * How much of a detour [candidate] is versus going straight prev→next.
     * Zero means keep; larger means a clearer explosion pin.
     */
    fun spikeScore(previous: TrackPoint, candidate: TrackPoint, next: TrackPoint): Double {
        val dPrev = Geo.haversineMeters(previous.lat, previous.lon, candidate.lat, candidate.lon)
        val dNext = Geo.haversineMeters(candidate.lat, candidate.lon, next.lat, next.lon)
        val dSkip = Geo.haversineMeters(previous.lat, previous.lon, next.lat, next.lon)
        // Not a detour — candidate lies on the prev→next path.
        if (dPrev + dNext <= dSkip + 50.0) return 0.0

        // Classic spike: both legs long, direct skip stays local (A→B→A′).
        if (dPrev >= RouteGaps.MIN_BREAK_METERS &&
            dNext >= RouteGaps.MIN_BREAK_METERS &&
            dSkip < RouteGaps.MIN_BREAK_METERS
        ) {
            return dPrev + dNext - dSkip
        }

        // Soft zigzag: via-candidate is a large multiple of the skip.
        if (dSkip < RouteGaps.MAX_CONTINUOUS_METERS &&
            dPrev + dNext > dSkip * 2.5 &&
            (dPrev >= RouteGaps.MIN_BREAK_METERS || dNext >= RouteGaps.MIN_BREAK_METERS)
        ) {
            return dPrev + dNext - dSkip
        }

        // Hard teleport out and back (or out then continue near start).
        if (JumpFilter.isJump(previous, candidate) &&
            dSkip < JumpFilter.MAX_SEGMENT_METERS &&
            !JumpFilter.isJump(previous, next)
        ) {
            return dPrev + dNext
        }
        return 0.0
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

    /** Minimum detour (meters) before a mid pin counts as a removable spike. */
    const val MIN_SPIKE_SCORE_M = 1_000.0
}
