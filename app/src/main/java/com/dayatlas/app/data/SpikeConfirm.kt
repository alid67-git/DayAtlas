package com.dayatlas.app.data

/**
 * Soft GPS spikes: a single fix far from home that the next fix returns from.
 * Hard teleports are still rejected by [JumpFilter] before this runs.
 *
 * Pattern A (committed) → B (pending) → A′ (back near A): drop B.
 * Pattern A → B → B′ / further away: commit B, then process the new fix.
 */
object SpikeConfirm {
    sealed class Outcome {
        /** Soft departure — wait one more sample before drawing B. */
        data class Hold(val pending: TrackPoint) : Outcome()

        /** Rebound to the last committed pin — discard pending spike. */
        data class DropPending(val candidate: TrackPoint) : Outcome()

        /** Movement confirmed — commit pending, then handle candidate. */
        data class CommitPendingThen(val pending: TrackPoint, val candidate: TrackPoint) : Outcome()

        /** No pending gate — handle candidate against committed points. */
        data class Process(val candidate: TrackPoint) : Outcome()
    }

    fun decide(
        lastCommitted: TrackPoint?,
        pending: TrackPoint?,
        candidate: TrackPoint,
        samePlaceRadiusM: Double = Geo.SAME_PLACE_RADIUS_M,
    ): Outcome {
        if (lastCommitted == null) {
            return Outcome.Process(candidate)
        }
        if (pending != null) {
            val backHome = Geo.haversineMeters(
                lastCommitted.lat,
                lastCommitted.lon,
                candidate.lat,
                candidate.lon,
            ) < samePlaceRadiusM
            return if (backHome) {
                Outcome.DropPending(candidate)
            } else {
                Outcome.CommitPendingThen(pending, candidate)
            }
        }
        val drift = Geo.haversineMeters(
            lastCommitted.lat,
            lastCommitted.lon,
            candidate.lat,
            candidate.lon,
        )
        if (drift < samePlaceRadiusM) {
            return Outcome.Process(candidate)
        }
        // Soft departure (hard jumps never reach here). Hold one sample.
        return Outcome.Hold(candidate)
    }
}
