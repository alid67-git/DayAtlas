package com.dayatlas.app.data

import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RangeStatsTest {
    @Test
    fun emptyListYieldsEmptySummary() {
        val summary = RangeStats.summarize(emptyList())
        assertEquals(0, summary.dayCount)
        assertEquals(0.0, summary.totalDistanceMeters, 0.0)
        assertNull(summary.bestDayIso)
    }

    @Test
    fun sumsDistanceAndPicksBestDay() {
        val day1 = DayRecord(
            date = "2026-09-01",
            title = "a",
            points = listOf(
                TrackPoint(1_000L, 41.0, 29.0, null),
                TrackPoint(1_000L + 60_000L, 41.01, 29.0, null),
            ),
            distanceMeters = 1_000.0,
        )
        val day2 = DayRecord(
            date = "2026-09-02",
            title = "b",
            points = listOf(
                TrackPoint(2_000L, 41.0, 29.0, null),
                TrackPoint(2_000L + 60_000L, 41.02, 29.0, null),
            ),
            distanceMeters = 2_500.0,
        )
        val summary = RangeStats.summarize(listOf(day1, day2))
        assertEquals(2, summary.dayCount)
        assertEquals(3_500.0, summary.totalDistanceMeters, 0.01)
        assertEquals(4, summary.totalPoints)
        assertEquals("2026-09-02", summary.bestDayIso)
        assertEquals(2_500.0, summary.bestDayDistanceMeters, 0.01)
        assertEquals(2, summary.dailyDistances.size)
    }

    @Test
    fun maxSpeedTakesHighestDay() {
        // ~36 km/h for 1 km in 100s
        val slow = DayRecord(
            date = "2026-09-01",
            title = "slow",
            points = listOf(
                TrackPoint(0L, 41.0, 29.0, null),
                TrackPoint(100_000L, 41.009, 29.0, null),
            ),
            distanceMeters = 1_000.0,
        )
        // ~72 km/h-ish for ~2 km in 100s
        val fast = DayRecord(
            date = "2026-09-02",
            title = "fast",
            points = listOf(
                TrackPoint(0L, 41.0, 29.0, null),
                TrackPoint(100_000L, 41.018, 29.0, null),
            ),
            distanceMeters = 2_000.0,
        )
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
        val aug = DayRecord(
            date = "2026-08-31",
            title = "a",
            points = listOf(TrackPoint(1L, 41.0, 29.0, null)),
            distanceMeters = 1_000.0,
        )
        val sep1 = DayRecord(
            date = "2026-09-01",
            title = "b",
            points = listOf(TrackPoint(2L, 41.0, 29.0, null)),
            distanceMeters = 2_000.0,
        )
        val sep2 = DayRecord(
            date = "2026-09-15",
            title = "c",
            points = listOf(TrackPoint(3L, 41.0, 29.0, null)),
            distanceMeters = 500.0,
        )
        val months = RangeStats.byMonth(listOf(sep2, aug, sep1))
        assertEquals(2, months.size)
        assertEquals(YearMonth.of(2026, 8), months[0].yearMonth)
        assertEquals(1_000.0, months[0].totalDistanceMeters, 0.01)
        assertEquals(1, months[0].dayCount)
        assertEquals(YearMonth.of(2026, 9), months[1].yearMonth)
        assertEquals(2_500.0, months[1].totalDistanceMeters, 0.01)
        assertEquals(2, months[1].dayCount)
    }
}
