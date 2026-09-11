package com.dayatlas.app.location

import com.dayatlas.app.data.Geo
import com.dayatlas.app.prefs.AppPrefs

/**
 * After [STREAK_TO_STEP] consecutive fixes within [TOLERANCE_METERS] of a fixed
 * anchor, step the effective sampling interval one notch coarser (never finer
 * than the user's settings base).
 *
 * Home GPS often wanders tens of meters while you sit still. Comparing each
 * fix to the previous fix (and resetting on any >25 m hop) made the interval
 * speed back up. Instead we keep the **anchor** fixed while you stay inside
 * the tolerance circle, and only treat real departure after
 * [MOVEMENT_STREAK_TO_RESET] consecutive fixes outside that circle.
 */
object StationaryBackoff {
    /** Radius around the sit-still anchor; typical indoor/yard GPS wander. */
    const val TOLERANCE_METERS = 80.0
    const val STREAK_TO_STEP = 3
    /** One spike outside the circle is jitter; two in a row is real movement. */
    const val MOVEMENT_STREAK_TO_RESET = 2

    data class State(
        val effectiveIntervalSeconds: Int,
        val stationaryStreak: Int,
        val lastLat: Double?,
        val lastLon: Double?,
        val movingStreak: Int = 0,
    )

    fun read(prefs: AppPrefs): State =
        State(
            effectiveIntervalSeconds = prefs.effectiveIntervalSeconds,
            stationaryStreak = prefs.stationaryStreak,
            lastLat = prefs.lastSampleLat,
            lastLon = prefs.lastSampleLon,
            movingStreak = prefs.movingStreak,
        )

    fun write(prefs: AppPrefs, state: State) {
        prefs.effectiveIntervalSeconds = state.effectiveIntervalSeconds
        prefs.stationaryStreak = state.stationaryStreak
        prefs.lastSampleLat = state.lastLat
        prefs.lastSampleLon = state.lastLon
        prefs.movingStreak = state.movingStreak
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
            )
        }

        val distance = Geo.haversineMeters(prevLat, prevLon, lat, lon)
        var effective = clampToAllowed(state.effectiveIntervalSeconds, allowed)
        if (effective < base) effective = base

        if (distance > TOLERANCE_METERS) {
            val moveStreak = state.movingStreak + 1
            if (moveStreak < MOVEMENT_STREAK_TO_RESET) {
                // Likely GPS spike while still sitting — keep coarse interval
                // and the original anchor.
                return State(
                    effectiveIntervalSeconds = effective,
                    stationaryStreak = 0,
                    lastLat = prevLat,
                    lastLon = prevLon,
                    movingStreak = moveStreak,
                )
            }
            // Confirmed departure — new anchor, back to user base rate.
            return State(
                effectiveIntervalSeconds = base,
                stationaryStreak = 0,
                lastLat = lat,
                lastLon = lon,
                movingStreak = 0,
            )
        }

        val streak = state.stationaryStreak + 1
        if (streak < STREAK_TO_STEP) {
            return State(
                effectiveIntervalSeconds = effective,
                stationaryStreak = streak,
                lastLat = prevLat,
                lastLon = prevLon,
                movingStreak = 0,
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
            )
        } else {
            State(
                effectiveIntervalSeconds = effective,
                stationaryStreak = 0,
                lastLat = prevLat,
                lastLon = prevLon,
                movingStreak = 0,
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
