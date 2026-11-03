package com.dayatlas.app.data

import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RangeStatsTest {
    private fun drivingDay(
        date: String,
        startLat: Double,
        endLat: Double,
        startMillis: Long = 1_000L,
        durationMs: Long = 600_000L,
    ): DayRecord {
        val points = listOf(
            TrackPoint(startMillis, startLat, 29.0, null),
            TrackPoint(startMillis + durationMs, endLat, 29.0, null),
        )
        return DayRecord(
            date = date,
            title = date,
            points = points,
            distanceMeters = Geo.pathLengthMeters(points),
        )
    }

    @Test
    fun emptyListYieldsEmptySummary() {
        val summary = RangeStats.summarize(emptyList())
        assertEquals(0, summary.dayCount)
        assertEquals(0.0, summary.totalDistanceMeters, 0.0)
        assertNull(summary.bestDayIso)
    }

    @Test
    fun sumsDistanceAndPicksBestDay() {
        // ~1.11 km and ~2.22 km northbound over 10 minutes (~6.7 / ~13 km/h).
        val day1 = drivingDay("2026-09-01", 41.0, 41.01)
        val day2 = drivingDay("2026-09-02", 41.0, 41.02)
        val summary = RangeStats.summarize(listOf(day1, day2))
        assertEquals(2, summary.dayCount)
        val expected = day1.distanceMeters + day2.distanceMeters
        assertEquals(expected, summary.totalDistanceMeters, 0.01)
        assertEquals(4, summary.totalPoints)
        assertEquals("2026-09-02", summary.bestDayIso)
        assertEquals(day2.distanceMeters, summary.bestDayDistanceMeters, 0.01)
        assertEquals(2, summary.dailyDistances.size)
    }

    @Test
    fun maxSpeedTakesHighestDay() {
        // Stay under RouteGaps.MAX_CONTINUOUS (2 km) so the leg still counts.
        val slow = drivingDay("2026-09-01", 41.0, 41.009, durationMs = 100_000L)
        val fast = drivingDay("2026-09-02", 41.0, 41.016, durationMs = 100_000L)
        val summary = RangeStats.summarize(listOf(slow, fast))
        assertTrue(summary.maxSpeedKmh > 50.0)
        assertTrue(summary.activeMillis > 0L)
    }

    @Test
    fun sumsCheckCountWhenGpsChecksExceedPins() {
        val day = DayRecord(
            date = "2026-09-01",
            title = "a",
            points = listOf(TrackPoint(1_000L, 41.0, 29.0, null)),
            distanceMeters = 0.0,
            gpsCheckCount = 15,
        )
        val summary = RangeStats.summarize(listOf(day))
        assertEquals(15, summary.totalPoints)
    }

    @Test
    fun byMonthGroupsOldestToNewest() {
        val aug = drivingDay("2026-08-31", 41.0, 41.01)
        val sep1 = drivingDay("2026-09-01", 41.0, 41.02)
        val sep2 = drivingDay("2026-09-15", 41.0, 41.005)
        val months = RangeStats.byMonth(listOf(sep2, aug, sep1))
        assertEquals(2, months.size)
        assertEquals(YearMonth.of(2026, 8), months[0].yearMonth)
        assertEquals(aug.distanceMeters, months[0].totalDistanceMeters, 0.01)
        assertEquals(1, months[0].dayCount)
        assertEquals(YearMonth.of(2026, 9), months[1].yearMonth)
        assertEquals(
            sep1.distanceMeters + sep2.distanceMeters,
            months[1].totalDistanceMeters,
            0.01,
        )
        assertEquals(2, months[1].dayCount)
    }

    @Test
    fun ignoresStoredDistanceInflatedByAirplaneHop() {
        // Stored field claims 77 km; points are a short real leg + a gap teleport.
        val parked = TrackPoint(0L, 7.88, 98.39, null)
        val shortMove = TrackPoint(120_000L, 7.89, 98.39, null) // ~1.1 km in 2 min
        val teleport = TrackPoint(120_000L + 600_000L, 8.05, 98.39, null) // ~19 km after 10 min gap
        val record = DayRecord(
            date = "2026-11-03",
            title = "t",
            points = listOf(parked, shortMove, teleport),
            distanceMeters = 77_400.0,
        )
        val summary = RangeStats.summarize(listOf(record))
        assertTrue(
            "expected ~1.1km real leg, got ${summary.totalDistanceMeters}",
            summary.totalDistanceMeters < 2_000.0,
        )
        assertTrue(
            "expected ~1.1km real leg, got ${summary.totalDistanceMeters}",
            summary.totalDistanceMeters > 500.0,
        )
    }
}
