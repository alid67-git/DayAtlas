package com.dayatlas.app.location

import com.dayatlas.app.data.Geo
import com.dayatlas.app.data.MovementConfirmation
import com.dayatlas.app.prefs.AppPrefs

/**
 * After [STREAK_TO_STEP] consecutive fixes within [TOLERANCE_METERS] of a fixed
 * anchor, step the effective sampling interval one notch coarser (never finer
 * than the user's settings base).
 *
 * Home GPS often wanders tens of meters while you sit still. Comparing each
 * fix to the previous fix (and resetting on any >25 m hop) made the interval
 * speed back up. Instead we keep the **anchor** fixed while you stay inside
 * the tolerance circle, and only treat real departure once
 * [MovementConfirmation] confirms it — two consecutive fixes outside that
 * circle that also agree with *each other*, not just one noisy outlier (see
 * [MovementConfirmation]'s own doc for why a lone outlier isn't enough).
 */
object StationaryBackoff {
    /** Radius around the sit-still anchor; typical indoor/yard GPS wander. */
    const val TOLERANCE_METERS = Geo.SAME_PLACE_RADIUS_M
    const val STREAK_TO_STEP = 3
    /** One spike outside the circle is jitter; two *consistent* ones is real movement. */
    const val MOVEMENT_STREAK_TO_RESET = 2

    data class State(
        val effectiveIntervalSeconds: Int,
        val stationaryStreak: Int,
        val lastLat: Double?,
        val lastLon: Double?,
        /** 1 when there's an unconfirmed "away" candidate pending, else 0. */
        val movingStreak: Int = 0,
        val movingLat: Double? = null,
        val movingLon: Double? = null,
    )

    fun read(prefs: AppPrefs): State =
        State(
            effectiveIntervalSeconds = prefs.effectiveIntervalSeconds,
            stationaryStreak = prefs.stationaryStreak,
            lastLat = prefs.lastSampleLat,
            lastLon = prefs.lastSampleLon,
            movingStreak = prefs.movingStreak,
            movingLat = prefs.movingCandidateLat,
            movingLon = prefs.movingCandidateLon,
        )

    fun write(prefs: AppPrefs, state: State) {
        prefs.effectiveIntervalSeconds = state.effectiveIntervalSeconds
        prefs.stationaryStreak = state.stationaryStreak
        prefs.lastSampleLat = state.lastLat
        prefs.lastSampleLon = state.lastLon
        prefs.movingStreak = state.movingStreak
        prefs.movingCandidateLat = state.movingLat
        prefs.movingCandidateLon = state.movingLon
    }

    /** Apply a successful fix and persist the updated adaptive interval. */
    fun recordSample(prefs: AppPrefs, lat: Double, lon: Double) {
        val next = onSample(
            lat = lat,
            lon = lon,
            userBaseSeconds = prefs.intervalSeconds,
            state = read(prefs),
        )
        write(prefs, next)
    }

    fun reset(userBaseSeconds: Int): State {
        val base = clampToAllowed(userBaseSeconds)
        return State(
            effectiveIntervalSeconds = base,
            stationaryStreak = 0,
            lastLat = null,
            lastLon = null,
            movingStreak = 0,
            movingLat = null,
            movingLon = null,
        )
    }

    /**
     * @return updated state after considering the new fix. While stationary,
     * the anchor ([State.lastLat]/[State.lastLon]) stays put so jitter does
     * not walk the reference point.
     */
    fun onSample(
        lat: Double,
        lon: Double,
        userBaseSeconds: Int,
        state: State,
        allowed: IntArray = AppPrefs.ALLOWED_INTERVAL_SECONDS,
    ): State {
        val base = clampToAllowed(userBaseSeconds, allowed)
        val prevLat = state.lastLat
        val prevLon = state.lastLon
        if (prevLat == null || prevLon == null) {
            return State(
                effectiveIntervalSeconds = base,
                stationaryStreak = 0,
                lastLat = lat,
                lastLon = lon,
                movingStreak = 0,
                movingLat = null,
                movingLon = null,
            )
        }

        var effective = clampToAllowed(state.effectiveIntervalSeconds, allowed)
        if (effective < base) effective = base

        val anchor = MovementConfirmation.Anchor(prevLat, prevLon, state.movingLat, state.movingLon)
        val result = MovementConfirmation.classify(anchor, lat, lon, TOLERANCE_METERS)

        when (result.outcome) {
            MovementConfirmation.Outcome.PENDING -> {
                // A lone (or inconsistent) outlier - likely a GPS spike while
                // still sitting. Keep the coarse interval and the original
                // anchor; just remember this fix as the new candidate.
                return State(
                    effectiveIntervalSeconds = effective,
                    stationaryStreak = 0,
                    lastLat = prevLat,
                    lastLon = prevLon,
                    movingStreak = 1,
                    movingLat = result.anchor.candidateLat,
                    movingLon = result.anchor.candidateLon,
                )
            }
            MovementConfirmation.Outcome.CONFIRMED -> {
                // Two consistent readings away from the anchor — confirmed
                // departure, new anchor, back to user base rate.
                return State(
                    effectiveIntervalSeconds = base,
                    stationaryStreak = 0,
                    lastLat = lat,
                    lastLon = lon,
                    movingStreak = 0,
                    movingLat = null,
                    movingLon = null,
                )
            }
            MovementConfirmation.Outcome.STATIONARY -> {
                // Falls through to the stationary-streak handling below.
            }
        }

        val streak = state.stationaryStreak + 1
        if (streak < STREAK_TO_STEP) {
            return State(
                effectiveIntervalSeconds = effective,
                stationaryStreak = streak,
                lastLat = prevLat,
                lastLon = prevLon,
                movingStreak = 0,
                movingLat = null,
                movingLon = null,
            )
        }

        val next = nextCoarser(effective, allowed)
        return if (next != null) {
            State(
                effectiveIntervalSeconds = next.coerceAtLeast(base),
                stationaryStreak = 0,
                lastLat = prevLat,
                lastLon = prevLon,
                movingStreak = 0,
                movingLat = null,
                movingLon = null,
            )
        } else {
            State(
                effectiveIntervalSeconds = effective,
                stationaryStreak = 0,
                lastLat = prevLat,
                lastLon = prevLon,
                movingStreak = 0,
                movingLat = null,
                movingLon = null,
            )
        }
    }

    fun nextCoarser(currentSeconds: Int, allowed: IntArray = AppPrefs.ALLOWED_INTERVAL_SECONDS): Int? {
        val idx = allowed.indexOf(currentSeconds)
        if (idx >= 0) return allowed.getOrNull(idx + 1)
        return allowed.firstOrNull { it > currentSeconds }
    }

    private fun clampToAllowed(
        seconds: Int,
        allowed: IntArray = AppPrefs.ALLOWED_INTERVAL_SECONDS,
    ): Int {
        if (seconds in allowed) return seconds
        return AppPrefs.DEFAULT_INTERVAL_SECONDS
    }
}
