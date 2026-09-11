package com.dayatlas.app.data

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

object Geo {
    private const val EARTH_RADIUS_M = 6_371_000.0

    /** In-place refresh threshold when appending a near-duplicate fix. */
    const val PATH_NOISE_FLOOR_M = 25.0

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
     * Sum of [TrackMotion]-qualified segment lengths so home GPS jitter does
     * not inflate daily distance.
     */
    fun pathLengthMeters(points: List<TrackPoint>): Double {
        if (points.size < 2) return 0.0
        var sum = 0.0
        for (i in 1 until points.size) {
            val seg = TrackMotion.meaningfulSegment(points[i - 1], points[i]) ?: continue
            sum += seg.distanceMeters
        }
        return sum
    }
}
