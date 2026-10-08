package com.dayatlas.app.data

import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SampleDayBucketTest {
    private val zone = ZoneId.of("America/New_York")

    @Test
    fun sameLocalDayKeepsGpsTime() {
        val wall = ZonedDateTime.of(2026, 10, 7, 23, 30, 0, 0, zone).toInstant().toEpochMilli()
        val gps = wall + 15_000L
        val r = SampleDayBucket.resolve(gps, wall, zone)
        assertEquals(LocalDate.of(2026, 10, 7), r.localDate)
        assertEquals(gps, r.pointTimeMillis)
        assertFalse(r.usedWallClockForPoint)
    }

    @Test
    fun gpsAheadPastMidnightUsesWallDay() {
        // Phone still 23:30; GPS fix already stamped 00:05 next day.
        val wall = ZonedDateTime.of(2026, 10, 7, 23, 30, 0, 0, zone).toInstant().toEpochMilli()
        val gps = ZonedDateTime.of(2026, 10, 8, 0, 5, 0, 0, zone).toInstant().toEpochMilli()
        val r = SampleDayBucket.resolve(gps, wall, zone)
        assertEquals(LocalDate.of(2026, 10, 7), r.localDate)
        assertEquals(wall, r.pointTimeMillis)
        assertTrue(r.usedWallClockForPoint)
    }

    @Test
    fun missingGpsTimeFallsBackToWall() {
        val wall = ZonedDateTime.of(2026, 10, 7, 12, 0, 0, 0, zone).toInstant().toEpochMilli()
        val r = SampleDayBucket.resolve(0L, wall, zone)
        assertEquals(LocalDate.of(2026, 10, 7), r.localDate)
        assertEquals(wall, r.pointTimeMillis)
    }
}
