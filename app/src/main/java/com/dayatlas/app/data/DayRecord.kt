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
    /** Free-form user note for this day, capped at 500 chars by the editor UI. */
    val note: String? = null,
    /**
     * File names (not paths) of this day's photos, under
     * `filesDir/photos/<date>/` - see [PhotoStore]. At most
     * [PhotoStore.MAX_PHOTOS_PER_DAY]; order is add order.
     */
    val photos: List<String> = emptyList(),
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
