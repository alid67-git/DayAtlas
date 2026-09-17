package com.dayatlas.app.location

import com.dayatlas.app.prefs.AppPrefs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StationaryBackoffTest {
    private val allowed = AppPrefs.ALLOWED_INTERVAL_SECONDS

    @Test
    fun firstSampleStaysAtUserBase() {
        val base = 30
        val next = StationaryBackoff.onSample(
            lat = 41.0,
            lon = 29.0,
            userBaseSeconds = base,
            state = StationaryBackoff.reset(base),
            allowed = allowed,
        )
        assertEquals(base, next.effectiveIntervalSeconds)
        assertEquals(0, next.stationaryStreak)
    }

    @Test
    fun threeStationaryStepsFrom30To60() {
        var state = StationaryBackoff.reset(30)
        // Seed last position.
        state = StationaryBackoff.onSample(41.0, 29.0, 30, state, allowed)
        repeat(2) {
            state = StationaryBackoff.onSample(41.0, 29.0, 30, state, allowed)
            assertEquals(30, state.effectiveIntervalSeconds)
        }
        // 3rd consecutive stationary after seed → streak hits 3 → step to 60.
        state = StationaryBackoff.onSample(41.0, 29.0, 30, state, allowed)
        assertEquals(60, state.effectiveIntervalSeconds)
        assertEquals(0, state.stationaryStreak)
    }

    @Test
    fun stepsContinueTowardMaxThenStay() {
        var state = StationaryBackoff.reset(30)
        state = StationaryBackoff.onSample(41.0, 29.0, 30, state, allowed)
        // Drive enough stationary samples to climb 30→60→180→300.
        repeat(12) {
            state = StationaryBackoff.onSample(41.00001, 29.00001, 30, state, allowed)
        }
        assertEquals(300, state.effectiveIntervalSeconds)
        val stayed = StationaryBackoff.onSample(41.00001, 29.00001, 30, state, allowed)
        assertEquals(300, stayed.effectiveIntervalSeconds)
    }

    @Test
    fun neverFinerThanUserBase() {
        var state = StationaryBackoff.reset(180)
        state = StationaryBackoff.onSample(41.0, 29.0, 180, state, allowed)
        repeat(6) {
            state = StationaryBackoff.onSample(41.0, 29.0, 180, state, allowed)
        }
        assertEquals(300, state.effectiveIntervalSeconds)
        // Two consecutive far fixes confirm movement → back to user base 180.
        state = StationaryBackoff.onSample(41.1, 29.1, 180, state, allowed)
        assertEquals(300, state.effectiveIntervalSeconds)
        state = StationaryBackoff.onSample(41.1, 29.1, 180, state, allowed)
        assertEquals(180, state.effectiveIntervalSeconds)
    }

    @Test
    fun movementResetsToUserBaseAfterConfirmedStreak() {
        var state = StationaryBackoff.reset(30)
        state = StationaryBackoff.onSample(41.0, 29.0, 30, state, allowed)
        repeat(3) {
            state = StationaryBackoff.onSample(41.0, 29.0, 30, state, allowed)
        }
        assertEquals(60, state.effectiveIntervalSeconds)
        // ~1 km away — first spike alone must not speed the interval back up.
        state = StationaryBackoff.onSample(41.01, 29.0, 30, state, allowed)
        assertEquals(60, state.effectiveIntervalSeconds)
        state = StationaryBackoff.onSample(41.01, 29.0, 30, state, allowed)
        assertEquals(30, state.effectiveIntervalSeconds)
        assertEquals(0, state.stationaryStreak)
    }

    @Test
    fun singleHomeGpsSpikeDoesNotSpeedUpInterval() {
        var state = StationaryBackoff.reset(30)
        state = StationaryBackoff.onSample(41.0, 29.0, 30, state, allowed)
        repeat(9) {
            state = StationaryBackoff.onSample(41.0, 29.0, 30, state, allowed)
        }
        assertEquals(300, state.effectiveIntervalSeconds)
        // ~50 m courtyard bounce (was enough to reset with the old 25 m rule).
        state = StationaryBackoff.onSample(41.0, 29.0006, 30, state, allowed)
        assertEquals(300, state.effectiveIntervalSeconds)
        // Still inside 80 m of the fixed anchor — keep coarsening, not speeding up.
        state = StationaryBackoff.onSample(41.0, 29.0008, 30, state, allowed)
        assertEquals(300, state.effectiveIntervalSeconds)
        assertEquals(0, state.movingStreak)
    }

    @Test
    fun twoInconsistentOutliersDoNotConfirmMovement() {
        // Regression test: indoor/urban GPS multipath can easily put two
        // *different* far-looking fixes on either side of the anchor while
        // the device never moved - the old rule ("2 fixes outside the
        // circle, in a row") treated that as confirmed departure. Only a
        // second fix that agrees with the *first* away fix should confirm.
        var state = StationaryBackoff.reset(30)
        state = StationaryBackoff.onSample(41.0, 29.0, 30, state, allowed)
        repeat(9) {
            state = StationaryBackoff.onSample(41.0, 29.0, 30, state, allowed)
        }
        assertEquals(300, state.effectiveIntervalSeconds)
        // ~1 km away to the east.
        state = StationaryBackoff.onSample(41.0, 29.01, 30, state, allowed)
        assertEquals(300, state.effectiveIntervalSeconds)
        // ~1 km away to the *west* instead - disagrees with the first
        // outlier, so this is scatter, not a settled new location.
        state = StationaryBackoff.onSample(41.0, 28.99, 30, state, allowed)
        assertEquals(300, state.effectiveIntervalSeconds)
        assertEquals(0, state.stationaryStreak)
        // A third fix that agrees with the *second* outlier now confirms it.
        state = StationaryBackoff.onSample(41.0, 28.99, 30, state, allowed)
        assertEquals(30, state.effectiveIntervalSeconds)
    }

    @Test
    fun nextCoarserLadder() {
        assertEquals(60, StationaryBackoff.nextCoarser(30, allowed))
        assertEquals(180, StationaryBackoff.nextCoarser(60, allowed))
        assertEquals(300, StationaryBackoff.nextCoarser(180, allowed))
        assertNull(StationaryBackoff.nextCoarser(300, allowed))
    }
}
