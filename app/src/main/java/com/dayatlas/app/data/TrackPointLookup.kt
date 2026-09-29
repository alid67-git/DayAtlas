package com.dayatlas.app.data

/**
 * Finds the nearest [TrackPoint] to a map tap. Pure logic — unit-tested.
 */
object TrackPointLookup {
    /** Default hit radius — roughly same-place GPS tolerance. */
    const val DEFAULT_MAX_METERS = 80.0

    data class Hit(
        val index: Int,
        val point: TrackPoint,
        val distanceMeters: Double,
    )

    fun nearest(
        points: List<TrackPoint>,
        lat: Double,
        lon: Double,
        maxMeters: Double = DEFAULT_MAX_METERS,
    ): Hit? {
        if (points.isEmpty()) return null
        var bestIndex = -1
        var bestDist = Double.POSITIVE_INFINITY
        for (i in points.indices) {
            val p = points[i]
            val d = Geo.haversineMeters(lat, lon, p.lat, p.lon)
            if (d < bestDist) {
                bestDist = d
                bestIndex = i
            }
        }
        if (bestIndex < 0 || bestDist > maxMeters) return null
        return Hit(index = bestIndex, point = points[bestIndex], distanceMeters = bestDist)
    }

    /** If [index] falls inside a dwell cluster, return that stop (so notes share one key). */
    fun dwellCovering(
        dwells: List<DwellStops.Stop>,
        index: Int,
    ): DwellStops.Stop? =
        dwells.firstOrNull { index in it.startIndex..it.endIndex }
}
