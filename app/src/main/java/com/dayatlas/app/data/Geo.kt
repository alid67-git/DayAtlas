package com.dayatlas.app.data

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

object Geo {
    private const val EARTH_RADIUS_M = 6_371_000.0

    /**
     * Same-place radius used to collapse stacked GPS fixes while sitting still
     * and as the stationary-backoff anchor (see [com.dayatlas.app.location.StationaryBackoff]).
     */
    const val SAME_PLACE_RADIUS_M = 80.0

    /** @deprecated Prefer [SAME_PLACE_RADIUS_M]; kept as an alias for call sites. */
    const val PATH_NOISE_FLOOR_M = SAME_PLACE_RADIUS_M

    fun haversineMeters(
        lat1: Double,
        lon1: Double,
        lat2: Double,
        lon2: Double,
    ): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
            sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return EARTH_RADIUS_M * c
    }

    /**
     * Sum of [TrackMotion.pathLegs] lengths — same geometry rules as
     * [com.dayatlas.app.data.SpeedStats] active time (sample-rate stable).
     */
    fun pathLengthMeters(points: List<TrackPoint>): Double {
        if (points.size < 2) return 0.0
        var sum = 0.0
        for (seg in TrackMotion.pathLegs(points)) {
            sum += seg.distanceMeters
        }
        return sum
    }
}
