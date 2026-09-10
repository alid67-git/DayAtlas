package com.dayatlas.app.location

import com.dayatlas.app.data.Geo
import com.dayatlas.app.prefs.AppPrefs

/**
 * After [STREAK_TO_STEP] consecutive fixes within [TOLERANCE_METERS], step the
 * effective sampling interval one notch coarser (never finer than the user's
 * settings base). Movement beyond the tolerance resets to the base interval.
 *
 * Pure logic — [AppPrefs] holds the persisted state; [SampleService] applies
 * it when scheduling the next AlarmManager wake.
 */
object StationaryBackoff {
    const val TOLERANCE_METERS = 25.0
    const val STREAK_TO_STEP = 3

    data class State(
        val effectiveIntervalSeconds: Int,
        val stationaryStreak: Int,
        val lastLat: Double?,
        val lastLon: Double?,
    )

    fun read(prefs: AppPrefs): State =
        State(
            effectiveIntervalSeconds = prefs.effectiveIntervalSeconds,
            stationaryStreak = prefs.stationaryStreak,
            lastLat = prefs.lastSampleLat,
            lastLon = prefs.lastSampleLon,
        )

    fun write(prefs: AppPrefs, state: State) {
        prefs.effectiveIntervalSeconds = state.effectiveIntervalSeconds
        prefs.stationaryStreak = state.stationaryStreak
        prefs.lastSampleLat = state.lastLat
        prefs.lastSampleLon = state.lastLon
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
        )
    }

    /**
     * @return updated state after considering the new fix. Always updates the
     * last-sample position so the next comparison is consecutive.
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
            )
        }

        val distance = Geo.haversineMeters(prevLat, prevLon, lat, lon)
        if (distance > TOLERANCE_METERS) {
            return State(
                effectiveIntervalSeconds = base,
                stationaryStreak = 0,
                lastLat = lat,
                lastLon = lon,
            )
        }

        var effective = clampToAllowed(state.effectiveIntervalSeconds, allowed)
        if (effective < base) effective = base

        val streak = state.stationaryStreak + 1
        if (streak < STREAK_TO_STEP) {
            return State(effective, streak, lat, lon)
        }

        val next = nextCoarser(effective, allowed)
        return if (next != null) {
            State(
                effectiveIntervalSeconds = next.coerceAtLeast(base),
                stationaryStreak = 0,
                lastLat = lat,
                lastLon = lon,
            )
        } else {
            // Already at the coarsest allowed interval.
            State(effective, 0, lat, lon)
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

