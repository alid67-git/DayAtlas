package com.dayatlas.app.data

data class TrackPoint(
    /** First time this pin was seen (arrival). */
    val timeMillis: Long,
    val lat: Double,
    val lon: Double,
    val accuracyMeters: Float?,
    /**
     * Last same-place GPS refresh at this pin. Equals [timeMillis] until the
     * user sits still and DayStore refreshes the pin without adding a new one.
     * Used by [DwellStops] so collapsed stays still yield a duration.
     */
    val lastTimeMillis: Long = timeMillis,
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
    /**
     * How many successful GPS acquisitions were recorded today, including
     * stationary same-place refreshes that do not add a new map pin.
     * Always ≥ [points].size; older day files without this field fall back
     * to point count when loaded.
     */
    val gpsCheckCount: Int = 0,
    /**
     * Optional notes on ≥15 min dwell stops, keyed by the stop's first-point
     * epoch millis ([DwellStops.Stop.noteKey]). Empty for older day files.
     */
    val dwellNotes: Map<Long, String> = emptyMap(),
    /**
     * Optional photo file name per dwell stop (under `filesDir/photos/<date>/`),
     * keyed like [dwellNotes].
     */
    val dwellPhotos: Map<Long, String> = emptyMap(),
) {
    /** Value shown as "GPS kontrol / nokta sayısı" in the UI. */
    val checkCount: Int
        get() = maxOf(gpsCheckCount, points.size)

    companion object {
        fun empty(dateIso: String, title: String) = DayRecord(
            date = dateIso,
            title = title,
            points = emptyList(),
            distanceMeters = 0.0,
            gpsCheckCount = 0,
        )
    }
}
