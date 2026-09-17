package com.dayatlas.app.data

/**
 * Shared "is this really movement, or GPS noise while sitting still"
 * classifier. Used both by [com.dayatlas.app.location.StationaryBackoff]
 * (decides the adaptive sampling interval) and by [DayStore.append] (decides
 * whether a new fix becomes a recorded, drawn track point).
 *
 * A single fix landing outside [toleranceMeters] of the anchor is not proof
 * of movement — indoor/urban GPS multipath routinely produces one
 * far-looking fix while the device hasn't moved at all (documented in
 * practice as "courtyard/home jitter" elsewhere in this codebase). Treating
 * every such fix as real movement is what produced two visible bugs: the
 * adaptive interval snapping back to the fast base rate on its own (no real
 * departure needed, one noisy reading was enough), and a scattered
 * "starburst" of crisscrossing lines drawn on the map for a day the device
 * never actually left home.
 *
 * The fix: a fix outside tolerance only becomes [Outcome.CONFIRMED] once a
 * *second* fix lands both outside the anchor's tolerance AND close to the
 * *first* such fix — i.e. the device settled into one new place, rather
 * than scattering to a different spot each time. A lone outlier, or two
 * outliers that disagree with each other, stay [Outcome.PENDING] — noted,
 * but not acted on until (or unless) confirmed.
 */
object MovementConfirmation {
    enum class Outcome { STATIONARY, PENDING, CONFIRMED }

    data class Anchor(
        val lat: Double,
        val lon: Double,
        /** The most recent not-yet-confirmed "away" fix, if any. */
        val candidateLat: Double? = null,
        val candidateLon: Double? = null,
    )

    data class Result(val outcome: Outcome, val anchor: Anchor)

    fun classify(anchor: Anchor, lat: Double, lon: Double, toleranceMeters: Double): Result {
        val distance = Geo.haversineMeters(anchor.lat, anchor.lon, lat, lon)
        if (distance <= toleranceMeters) {
            // Back within range of the anchor - any pending candidate was a
            // dead end (or this fix itself is the noise), drop it.
            return Result(Outcome.STATIONARY, anchor.copy(candidateLat = null, candidateLon = null))
        }
        val candidateLat = anchor.candidateLat
        val candidateLon = anchor.candidateLon
        val consistent = candidateLat != null && candidateLon != null &&
            Geo.haversineMeters(candidateLat, candidateLon, lat, lon) <= toleranceMeters
        if (!consistent) {
            // First "away" reading, or one that doesn't agree with the
            // previous away reading (scattered noise) - remember this fix
            // as the new candidate but confirm nothing yet.
            return Result(Outcome.PENDING, anchor.copy(candidateLat = lat, candidateLon = lon))
        }
        // Two consistent readings away from the anchor - confirmed.
        return Result(Outcome.CONFIRMED, Anchor(lat = lat, lon = lon))
    }
}
