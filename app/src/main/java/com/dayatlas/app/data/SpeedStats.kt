package com.dayatlas.app.data

/**
 * Speed / active-time cards. Uses the same [TrackMotion.pathLegs] rules as
 * daily km so sample-rate differences cannot shrink “aktif süre” while
 * distance stays high (or the reverse).
 */
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

    fun compute(points: List<TrackPoint>): Stats {
        if (points.size < 2) return Stats.EMPTY
        var maxSpeed = 0.0
        var activeMillis = 0L
        var movingDistanceM = 0.0
        for (seg in TrackMotion.pathLegs(points)) {
            if (seg.speedKmh > maxSpeed) maxSpeed = seg.speedKmh
            activeMillis += seg.dtMs
            movingDistanceM += seg.distanceMeters
        }
        val avgSpeed = if (activeMillis > 0) {
            (movingDistanceM / (activeMillis / 1000.0)) * 3.6
        } else {
            0.0
        }
        return Stats(maxSpeed, avgSpeed, activeMillis)
    }
}
