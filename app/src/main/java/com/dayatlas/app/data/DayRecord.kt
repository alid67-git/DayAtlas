package com.dayatlas.app.data

data class TrackPoint(
    val timeMillis: Long,
    val lat: Double,
    val lon: Double,
    val accuracyMeters: Float?,
)

data class DayRecord(
    val date: String,
    val title: String,
    val points: List<TrackPoint>,
    val distanceMeters: Double,
) {
    companion object {
        fun empty(dateIso: String, title: String) = DayRecord(
            date = dateIso,
            title = title,
            points = emptyList(),
            distanceMeters = 0.0,
        )
    }
}
