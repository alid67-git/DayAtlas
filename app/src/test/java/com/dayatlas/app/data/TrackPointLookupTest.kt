package com.dayatlas.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackPointLookupTest {
    @Test
    fun nearestWithinRadius() {
        val points = listOf(
            TrackPoint(0L, 41.0, 29.0, null),
            TrackPoint(1_000L, 41.01, 29.01, null),
        )
        val hit = TrackPointLookup.nearest(points, 41.0001, 29.0001)
        assertEquals(0, hit?.index)
        assertTrue(hit!!.distanceMeters < 80.0)
    }

    @Test
    fun nearestOutsideRadiusIsNull() {
        val points = listOf(TrackPoint(0L, 41.0, 29.0, null))
        // ~1 km east
        assertNull(TrackPointLookup.nearest(points, 41.0, 29.012, maxMeters = 80.0))
    }

    @Test
    fun dwellCoveringMatchesIndexRange() {
        val dwell = DwellStops.Stop(
            startIndex = 2,
            endIndex = 5,
            startMillis = 100L,
            endMillis = 200L,
            lat = 41.0,
            lon = 29.0,
        )
        assertEquals(dwell, TrackPointLookup.dwellCovering(listOf(dwell), 3))
        assertNull(TrackPointLookup.dwellCovering(listOf(dwell), 1))
    }
}
