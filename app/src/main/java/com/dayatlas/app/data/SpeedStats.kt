package com.dayatlas.app.data

object SpeedStats {
    data class Stats(
        val maxSpeedKmh: Double,
        val avgSpeedKmh: Double,
        val activeMillis: Long,
    ) {
        companion object {
            val EMPTY = Stats(0.0, 0.0, 0L)
        }
    }

    // Points already pass JumpFilter before being stored, but a moved/edited
    // file (restore, manual edit) could still contain a spurious jump - stay
    // defensive rather than let one bad segment set an absurd max speed.
    private const val MAX_REALISTIC_SPEED_KMH = 160.0

    // Below this, treat the vehicle/person as stationary - GPS jitter while
    // standing still shouldn't count toward "active" driving time/distance.
    private const val STATIONARY_SPEED_KMH = 1.0

    // A gap this long (parked, overnight, tracking paused) breaks the drive
    // into a new segment rather than being counted as one very slow stretch.
    private const val MAX_SEGMENT_GAP_MS = 20 * 60_000L

    fun compute(points: List<TrackPoint>): Stats {
        if (points.size < 2) return Stats.EMPTY
        var maxSpeed = 0.0
        var activeMillis = 0L
        var movingDistanceM = 0.0
        for (i in 1 until points.size) {
            val prev = points[i - 1]
            val cur = points[i]
            val dtMs = cur.timeMillis - prev.timeMillis
            if (dtMs <= 0 || dtMs > MAX_SEGMENT_GAP_MS) continue
            val distM = Geo.haversineMeters(prev.lat, prev.lon, cur.lat, cur.lon)
            val speedKmh = (distM / (dtMs / 1000.0)) * 3.6
            if (speedKmh > MAX_REALISTIC_SPEED_KMH) continue
            if (speedKmh > maxSpeed) maxSpeed = speedKmh
            if (speedKmh >= STATIONARY_SPEED_KMH) {
                activeMillis += dtMs
                movingDistanceM += distM
            }
        }
        val avgSpeed = if (activeMillis > 0) {
            (movingDistanceM / (activeMillis / 1000.0)) * 3.6
        } else {
            0.0
        }
        return Stats(maxSpeed, avgSpeed, activeMillis)
    }
}
