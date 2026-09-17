package com.dayatlas.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MovementConfirmationTest {
    private val anchor = MovementConfirmation.Anchor(lat = 41.0, lon = 29.0)
    private val tolerance = 80.0

    @Test
    fun withinToleranceIsStationary() {
        val result = MovementConfirmation.classify(anchor, 41.0, 29.0, tolerance)
        assertEquals(MovementConfirmation.Outcome.STATIONARY, result.outcome)
        assertNull(result.anchor.candidateLat)
    }

    @Test
    fun lonelyOutlierIsPendingNotConfirmed() {
        // ~1 km east - well outside tolerance, but nothing to compare it to yet.
        val result = MovementConfirmation.classify(anchor, 41.0, 29.01, tolerance)
        assertEquals(MovementConfirmation.Outcome.PENDING, result.outcome)
        assertEquals(29.01, result.anchor.candidateLon!!, 1e-9)
    }

    @Test
    fun twoConsistentOutliersConfirm() {
        val first = MovementConfirmation.classify(anchor, 41.0, 29.01, tolerance)
        val second = MovementConfirmation.classify(first.anchor, 41.0, 29.01, tolerance)
        assertEquals(MovementConfirmation.Outcome.CONFIRMED, second.outcome)
        assertEquals(41.0, second.anchor.lat, 1e-9)
        assertEquals(29.01, second.anchor.lon, 1e-9)
        assertNull(second.anchor.candidateLat)
    }

    @Test
    fun twoInconsistentOutliersStayPending() {
        // First outlier ~1 km east, second ~1 km west - disagree with each
        // other, so this is scatter (GPS multipath), not a settled move.
        val first = MovementConfirmation.classify(anchor, 41.0, 29.01, tolerance)
        val second = MovementConfirmation.classify(first.anchor, 41.0, 28.99, tolerance)
        assertEquals(MovementConfirmation.Outcome.PENDING, second.outcome)
        // The newest outlier replaces the stale candidate.
        assertEquals(28.99, second.anchor.candidateLon!!, 1e-9)
    }

    @Test
    fun returningWithinToleranceDropsThePendingCandidate() {
        val pending = MovementConfirmation.classify(anchor, 41.0, 29.01, tolerance)
        val backHome = MovementConfirmation.classify(pending.anchor, 41.0, 29.0, tolerance)
        assertEquals(MovementConfirmation.Outcome.STATIONARY, backHome.outcome)
        assertNull(backHome.anchor.candidateLat)
    }
}
