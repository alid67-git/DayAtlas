package com.dayatlas.app.data

/**
 * Finds places where the track stayed put for at least [MIN_DWELL_MILLIS].
 *
 * Clustering uses the same ~80 m radius as stationary GPS collapse
 * ([Geo.SAME_PLACE_RADIUS_M]). A single collapsed pin still counts when its
 * [TrackPoint.lastTimeMillis] − [TrackPoint.timeMillis] span is long enough
 * (DayStore keeps arrival time while refreshing the same place).
 */
object DwellStops {
    /** 15 minutes — short enough for a coffee stop, long enough to ignore traffic lights. */
    const val MIN_DWELL_MILLIS = 15L * 60_000L

    data class Stop(
        /** Index of the first point of this dwell in the day's track. */
        val startIndex: Int,
        /** Index of the last point of this dwell in the day's track. */
        val endIndex: Int,
        val startMillis: Long,
        val endMillis: Long,
        /** Mean lat/lon of the cluster (marker position). */
        val lat: Double,
        val lon: Double,
    ) {
        val durationMillis: Long
            get() = (endMillis - startMillis).coerceAtLeast(0L)

        /** Stable key for optional per-stop notes/photos in [DayRecord]. */
        val noteKey: Long
            get() = startMillis

        fun isActive(record: DayRecord?): Boolean {
            if (record == null) return false
            return !record.dwellNotes[noteKey].isNullOrEmpty() ||
                !record.dwellPhotos[noteKey].isNullOrEmpty()
        }
    }

    fun find(
        points: List<TrackPoint>,
        minDwellMillis: Long = MIN_DWELL_MILLIS,
        radiusMeters: Double = Geo.SAME_PLACE_RADIUS_M,
    ): List<Stop> {
        if (points.isEmpty()) return emptyList()
        val out = ArrayList<Stop>()
        var clusterStart = 0
        var sumLat = points[0].lat
        var sumLon = points[0].lon
        var count = 1

        fun flush(endExclusive: Int) {
            if (endExclusive <= clusterStart) return
            val first = points[clusterStart]
            val last = points[endExclusive - 1]
            val start = first.timeMillis
            val end = last.lastTimeMillis.coerceAtLeast(last.timeMillis)
            val duration = end - start
            if (duration < minDwellMillis) return
            out.add(
                Stop(
                    startIndex = clusterStart,
                    endIndex = endExclusive - 1,
                    startMillis = start,
                    endMillis = end,
                    lat = sumLat / count,
                    lon = sumLon / count,
                ),
            )
        }

        for (i in 1 until points.size) {
            val anchor = points[clusterStart]
            val cur = points[i]
            val dist = Geo.haversineMeters(anchor.lat, anchor.lon, cur.lat, cur.lon)
            if (dist <= radiusMeters) {
                sumLat += cur.lat
                sumLon += cur.lon
                count++
                continue
            }
            flush(i)
            clusterStart = i
            sumLat = cur.lat
            sumLon = cur.lon
            count = 1
        }
        flush(points.size)
        return out
    }
}
