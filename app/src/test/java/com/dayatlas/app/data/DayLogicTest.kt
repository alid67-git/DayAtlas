package com.dayatlas.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.util.Locale

class GeoTest {
    @Test
    fun nearbyPointsHaveExpectedDistance() {
        val meters = Geo.haversineMeters(41.0, 29.0, 41.0, 29.001)
        assertTrue("expected ~85m, was $meters", meters in 70.0..110.0)
    }

    @Test
    fun pathLengthSumsSegments() {
        val points = listOf(
            TrackPoint(1L, 41.0, 29.0, null),
            TrackPoint(2L, 41.0, 29.001, null),
            TrackPoint(3L, 41.0, 29.002, null),
        )
        val len = Geo.pathLengthMeters(points)
        assertTrue(len in 140.0..220.0)
    }
}

class DayTitleTest {
    @Test
    fun turkishTitleUsesLocalCalendarDay() {
        val title = DayTitle.format(LocalDate.of(2026, 8, 28), Locale("tr", "TR"))
        assertEquals("Günlük 28 Ağu 2026", title)
    }

    @Test
    fun isoIsYearMonthDay() {
        assertEquals("2026-08-28", DayTitle.iso(LocalDate.of(2026, 8, 28)))
    }
}

class DayJsonTest {
    @Test
    fun roundTripPreservesPoints() {
        val record = DayRecord(
            date = "2026-08-28",
            title = "Günlük 28 Ağu 2026",
            points = listOf(TrackPoint(1_000L, 41.01, 29.02, 12.5f)),
            distanceMeters = 0.0,
        )
        val parsed = DayJson.fromJson(DayJson.toJson(record))
        assertEquals(record.date, parsed.date)
        assertEquals(record.title, parsed.title)
        assertEquals(1, parsed.points.size)
        assertEquals(41.01, parsed.points[0].lat, 1e-6)
        assertTrue(DayJson.toGpx(record).contains("<trkpt"))
    }
}
