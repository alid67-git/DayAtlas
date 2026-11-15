package com.dayatlas.app.location

import com.dayatlas.app.data.Geo
import com.dayatlas.app.prefs.AppPrefs

/**
 * After [STREAK_TO_STEP] consecutive fixes within [TOLERANCE_METERS] of a fixed
 * anchor, step the effective sampling interval one notch coarser (never
 * coarser than [MAX_BACKOFF_INTERVAL_SECONDS] unless the user base itself is
 * already coarser).
 *
 * While moving, the effective interval drops to [MOTION_INTERVAL_SECONDS]
 * (10 s) regardless of the Settings base — so a user who picked 3 min for
 * battery still gets dense samples on the road (mountain curves vs continuous
 * trackers). Sitting still still climbs the ladder from that fine rate back
 * toward the user base / 5 min cap.
 *
 * Home GPS often wanders tens of meters while you sit still. Comparing each
 * fix to the previous fix (and resetting on any >25 m hop) made the interval
 * speed back up. Instead we keep the **anchor** fixed while you stay inside
 * the tolerance circle, and only treat real departure after
 * [MOVEMENT_STREAK_TO_RESET] consecutive fixes outside that circle. The first
 * off-circle fix still tips to the motion rate so the next alarm is fine-
 * grained even before confirmation.
 */
object StationaryBackoff {
    /** Radius around the sit-still anchor; typical indoor/yard GPS wander. */
    const val TOLERANCE_METERS = Geo.SAME_PLACE_RADIUS_M
    const val STREAK_TO_STEP = 3
    /** One spike outside the circle is jitter; two in a row is real movement. */
    const val MOVEMENT_STREAK_TO_RESET = 2

    /**
     * While driving / walking off the sit-still circle, sample this often
     * even if Settings base is 1–5 min. Must stay in
     * [AppPrefs.ALLOWED_INTERVAL_SECONDS].
     */
    const val MOTION_INTERVAL_SECONDS = 10

    /**
     * Ceiling for adaptive coarsening when the user base is finer than this
     * (typical ladder: 10 s → 20 → 30 → 1 → 3 → 5 min). [MotionWakeTrigger]
     * tips back to the motion rate on significant motion so a long sit does
     * not delay the start of a real trip by a full 5 minutes.
     */
    const val MAX_BACKOFF_INTERVAL_SECONDS = 300

    /**
     * When true (default), [recordSample] steps the effective interval
     * coarser while you sit still. Kept as a flag so a future diagnostic
     * build can temporarily freeze the interval without deleting the math.
     */
    const val COARSENING_ENABLED = true

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
            coarseningEnabled = COARSENING_ENABLED,
        )
        write(prefs, next)
    }

    /**
     * Significant-motion hint: drop to [MOTION_INTERVAL_SECONDS] without
     * clearing the sit-still anchor. The next GPS fix still decides lasting
     * movement; a false alarm only costs a short fine-grained burst.
     */
    fun speedUpForSuspectedMotion(prefs: AppPrefs) {
        val motion = motionIntervalSeconds(prefs.intervalSeconds)
        val state = read(prefs)
        if (state.effectiveIntervalSeconds <= motion) return
        write(
            prefs,
            state.copy(
                effectiveIntervalSeconds = motion,
                stationaryStreak = 0,
            ),
        )
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

    /** Interval used while off the sit-still circle (always ≤ user base). */
    fun motionIntervalSeconds(
        userBaseSeconds: Int,
        allowed: IntArray = AppPrefs.ALLOWED_INTERVAL_SECONDS,
    ): Int {
        val base = clampToAllowed(userBaseSeconds, allowed)
        // Prefer the dedicated motion rate; if the user already chose 10 s,
        // keep it. Never go coarser than the motion floor while moving.
        return minOf(base, MOTION_INTERVAL_SECONDS).let { candidate ->
            if (candidate in allowed) candidate else MOTION_INTERVAL_SECONDS
        }
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
        coarseningEnabled: Boolean = true,
    ): State {
        val base = clampToAllowed(userBaseSeconds, allowed)
        val motion = motionIntervalSeconds(userBaseSeconds, allowed)
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
        val coarseCap = coarseCapFor(base)
        if (effective > coarseCap) effective = coarseCap

        if (distance > TOLERANCE_METERS) {
            val moveStreak = state.movingStreak + 1
            if (moveStreak < MOVEMENT_STREAK_TO_RESET) {
                // Tentative departure: tip to motion rate immediately so the
                // *next* alarm is not stuck at 3–5 min, but keep the anchor
                // until a second off-circle fix confirms real movement.
                return State(
                    effectiveIntervalSeconds = motion,
                    stationaryStreak = 0,
                    lastLat = prevLat,
                    lastLon = prevLon,
                    movingStreak = moveStreak,
                )
            }
            // Confirmed departure — new anchor, stay on the motion rate.
            return State(
                effectiveIntervalSeconds = motion,
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

        val nextRaw = if (coarseningEnabled) nextCoarser(effective, allowed) else null
        val next = nextRaw?.coerceAtMost(coarseCap)
        return if (next != null && next > effective) {
            // Floor at the user Settings base once coarsening past the
            // motion burst — don't linger at 10/20 s while parked if the
            // user asked for 1–5 min at rest.
            State(
                effectiveIntervalSeconds = next.coerceAtLeast(base),
                stationaryStreak = 0,
                lastLat = prevLat,
                lastLon = prevLon,
                movingStreak = 0,
            )
        } else {
            State(
                effectiveIntervalSeconds = effective.coerceAtLeast(base),
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

    /** Adaptive ceiling: never coarser than 5 min unless user base already is. */
    fun coarseCapFor(userBaseSeconds: Int): Int =
        userBaseSeconds.coerceAtLeast(MAX_BACKOFF_INTERVAL_SECONDS)

    private fun clampToAllowed(
        seconds: Int,
        allowed: IntArray = AppPrefs.ALLOWED_INTERVAL_SECONDS,
    ): Int {
        if (seconds in allowed) return seconds
        return AppPrefs.DEFAULT_INTERVAL_SECONDS
    }
}
