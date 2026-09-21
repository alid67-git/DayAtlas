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
    fun coarseningDisabledKeepsUserBaseWhileStationary() {
        var state = StationaryBackoff.reset(60)
        state = StationaryBackoff.onSample(41.0, 29.0, 60, state, allowed, coarseningEnabled = false)
        repeat(12) {
            state = StationaryBackoff.onSample(41.0, 29.0, 60, state, allowed, coarseningEnabled = false)
            assertEquals(60, state.effectiveIntervalSeconds)
        }
    }

    @Test
    fun stepsStopAtOneMinuteCap() {
        var state = StationaryBackoff.reset(30)
        state = StationaryBackoff.onSample(41.0, 29.0, 30, state, allowed)
        // Enough samples to have formerly climbed to 3–5 min.
        repeat(12) {
            state = StationaryBackoff.onSample(41.00001, 29.00001, 30, state, allowed)
        }
        assertEquals(60, state.effectiveIntervalSeconds)
        val stayed = StationaryBackoff.onSample(41.00001, 29.00001, 30, state, allowed)
        assertEquals(60, stayed.effectiveIntervalSeconds)
    }

    @Test
    fun legacyCoarseEffectiveIsClampedDown() {
        // Prefs from an older build may still hold 300 s while base is 30.
        var state = StationaryBackoff.State(
            effectiveIntervalSeconds = 300,
            stationaryStreak = 0,
            lastLat = 41.0,
            lastLon = 29.0,
        )
        state = StationaryBackoff.onSample(41.0, 29.0, 30, state, allowed)
        assertEquals(60, state.effectiveIntervalSeconds)
    }

    @Test
    fun userBaseCoarserThanCapIsRespected() {
        var state = StationaryBackoff.reset(180)
        state = StationaryBackoff.onSample(41.0, 29.0, 180, state, allowed)
        repeat(6) {
            state = StationaryBackoff.onSample(41.0, 29.0, 180, state, allowed)
        }
        // Cap is max(base, 60) = 180 — no climb past the user's own base.
        assertEquals(180, state.effectiveIntervalSeconds)
        state = StationaryBackoff.onSample(41.1, 29.1, 180, state, allowed)
        assertEquals(180, state.effectiveIntervalSeconds)
        state = StationaryBackoff.onSample(41.1, 29.1, 180, state, allowed)
        assertEquals(180, state.effectiveIntervalSeconds)
    }

    @Test
    fun movementTipsToUserBaseOnFirstOffCircle() {
        var state = StationaryBackoff.reset(30)
        state = StationaryBackoff.onSample(41.0, 29.0, 30, state, allowed)
        repeat(6) {
            state = StationaryBackoff.onSample(41.0, 29.0, 30, state, allowed)
        }
        assertEquals(60, state.effectiveIntervalSeconds)
        state = StationaryBackoff.onSample(41.01, 29.0, 30, state, allowed)
        assertEquals(30, state.effectiveIntervalSeconds)
        assertEquals(1, state.movingStreak)
        state = StationaryBackoff.onSample(41.01, 29.0, 30, state, allowed)
        assertEquals(30, state.effectiveIntervalSeconds)
        assertEquals(0, state.movingStreak)
    }

    @Test
    fun singleHomeGpsSpikeDoesNotSpeedUpInterval() {
        var state = StationaryBackoff.reset(30)
        state = StationaryBackoff.onSample(41.0, 29.0, 30, state, allowed)
        repeat(6) {
            state = StationaryBackoff.onSample(41.0, 29.0, 30, state, allowed)
        }
        assertEquals(60, state.effectiveIntervalSeconds)
        state = StationaryBackoff.onSample(41.0, 29.0006, 30, state, allowed)
        assertEquals(60, state.effectiveIntervalSeconds)
        state = StationaryBackoff.onSample(41.0, 29.0008, 30, state, allowed)
        assertEquals(60, state.effectiveIntervalSeconds)
        assertEquals(0, state.movingStreak)
    }

    @Test
    fun coarseningResumesAfterFalseDepartureTip() {
        var state = StationaryBackoff.reset(30)
        state = StationaryBackoff.onSample(41.0, 29.0, 30, state, allowed)
        repeat(6) {
            state = StationaryBackoff.onSample(41.0, 29.0, 30, state, allowed)
        }
        assertEquals(60, state.effectiveIntervalSeconds)
        state = StationaryBackoff.onSample(41.01, 29.0, 30, state, allowed)
        assertEquals(30, state.effectiveIntervalSeconds)
        repeat(3) {
            state = StationaryBackoff.onSample(41.0, 29.0, 30, state, allowed)
        }
        assertEquals(60, state.effectiveIntervalSeconds)
    }

    @Test
    fun nextCoarserLadder() {
        assertEquals(60, StationaryBackoff.nextCoarser(30, allowed))
        assertEquals(180, StationaryBackoff.nextCoarser(60, allowed))
        assertEquals(300, StationaryBackoff.nextCoarser(180, allowed))
        assertNull(StationaryBackoff.nextCoarser(300, allowed))
        assertEquals(60, StationaryBackoff.coarseCapFor(30))
        assertEquals(180, StationaryBackoff.coarseCapFor(180))
    }
}
