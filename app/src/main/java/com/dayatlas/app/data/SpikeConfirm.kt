package com.dayatlas.app.data

/**
 * Soft GPS spikes: a single fix far from home that the next fix returns from.
 * Hard teleports are still rejected by [JumpFilter] before this runs.
 *
 * Pattern A (committed) → B (pending) → A′ (back near A): drop B.
 * Pattern A → B → B′ / further away: commit B, then process the new fix.
 *
 * After a long tracking gap (airplane / null GPS), "not back home" alone is
 * not enough to commit B — two consecutive bad fixes at the same wrong
 * place used to draw a fake corridor hop. We only commit when the next fix
 * lands near the pending pin (new location confirmed).
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
            if (backHome) {
                return Outcome.DropPending(candidate)
            }
            val gapBeforePending = pending.timeMillis - lastCommitted.lastTimeMillis > RouteGaps.GAP_MS
            if (gapBeforePending) {
                val confirmRadiusM = confirmRadiusMeters(pending, candidate, samePlaceRadiusM)
                val nearPending = Geo.haversineMeters(
                    pending.lat,
                    pending.lon,
                    candidate.lat,
                    candidate.lon,
                ) <= confirmRadiusM
                return if (nearPending) {
                    Outcome.CommitPendingThen(pending, candidate)
                } else {
                    // Still wandering after a gap — keep holding the newer fix.
                    Outcome.Hold(candidate)
                }
            }
            return Outcome.CommitPendingThen(pending, candidate)
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

    /**
     * Allow real motion between the held pin and the confirming sample
     * (up to [JumpFilter.MAX_SPEED_KMH] over that interval), but never
     * shrink below the same-place radius.
     */
    internal fun confirmRadiusMeters(
        pending: TrackPoint,
        candidate: TrackPoint,
        samePlaceRadiusM: Double = Geo.SAME_PLACE_RADIUS_M,
    ): Double {
        val dtSec = ((candidate.timeMillis - pending.timeMillis).coerceAtLeast(0L)) / 1_000.0
        val motionBudgetM = (JumpFilter.MAX_SPEED_KMH / 3.6) * dtSec
        return maxOf(samePlaceRadiusM, motionBudgetM)
    }
}
