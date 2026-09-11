package com.dayatlas.app.data

/**
 * Aggregates [DayRecord]s over a chosen date range for the Statistics tab.
 * Speed/active time reuse [SpeedStats] per day so overnight gaps between
 * days are never mistaken for one long slow segment.
 */
object RangeStats {
    data class Summary(
        val dayCount: Int,
        val totalDistanceMeters: Double,
        val totalPoints: Int,
        val maxSpeedKmh: Double,
        val avgSpeedKmh: Double,
        val activeMillis: Long,
        val bestDayIso: String?,
        val bestDayDistanceMeters: Double,
        /** Per-day distance for a simple bar list, oldest → newest. */
        val dailyDistances: List<Pair<String, Double>>,
    ) {
        companion object {
            val EMPTY = Summary(
                dayCount = 0,
                totalDistanceMeters = 0.0,
                totalPoints = 0,
                maxSpeedKmh = 0.0,
                avgSpeedKmh = 0.0,
                activeMillis = 0L,
                bestDayIso = null,
                bestDayDistanceMeters = 0.0,
                dailyDistances = emptyList(),
            )
        }
    }

    fun summarize(records: List<DayRecord>): Summary {
        if (records.isEmpty()) return Summary.EMPTY
        var totalDistance = 0.0
        var totalPoints = 0
        var maxSpeed = 0.0
        var activeMillis = 0L
        var movingDistanceM = 0.0
        var bestIso: String? = null
        var bestDistance = 0.0
        val daily = ArrayList<Pair<String, Double>>(records.size)

        for (record in records) {
            totalDistance += record.distanceMeters
            totalPoints += record.points.size
            daily.add(record.date to record.distanceMeters)
            if (record.distanceMeters >= bestDistance) {
                bestDistance = record.distanceMeters
                bestIso = record.date
            }
            val speed = SpeedStats.compute(record.points)
            if (speed.maxSpeedKmh > maxSpeed) maxSpeed = speed.maxSpeedKmh
            activeMillis += speed.activeMillis
            // Reconstruct moving distance from avg × active time so the
            // range-wide average stays consistent with per-day filtering.
            if (speed.activeMillis > 0 && speed.avgSpeedKmh > 0) {
                movingDistanceM += speed.avgSpeedKmh / 3.6 * (speed.activeMillis / 1000.0)
            }
        }

        val avgSpeed = if (activeMillis > 0) {
            (movingDistanceM / (activeMillis / 1000.0)) * 3.6
        } else {
            0.0
        }

        return Summary(
            dayCount = records.size,
            totalDistanceMeters = totalDistance,
            totalPoints = totalPoints,
            maxSpeedKmh = maxSpeed,
            avgSpeedKmh = avgSpeed,
            activeMillis = activeMillis,
            bestDayIso = bestIso,
            bestDayDistanceMeters = bestDistance,
            dailyDistances = daily,
        )
    }
}
