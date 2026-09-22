package com.dayatlas.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DwellStopsTest {
    @Test
    fun emptyOrSinglePointYieldsNothing() {
        assertTrue(DwellStops.find(emptyList()).isEmpty())
        assertTrue(
            DwellStops.find(listOf(TrackPoint(0L, 41.0, 29.0, null))).isEmpty(),
        )
    }

    @Test
    fun shortStayIsIgnored() {
        // Same place for 10 minutes — under the 15 min threshold.
        val points = (0..20).map { i ->
            TrackPoint(i * 30_000L, 41.0, 29.0, null)
        }
        assertTrue(DwellStops.find(points).isEmpty())
    }

    @Test
    fun fifteenMinuteStayIsDetected() {
        // Same place every 30 s for 16 minutes.
        val points = (0..32).map { i ->
            TrackPoint(i * 30_000L, 41.01, 29.02, null)
        }
        val stops = DwellStops.find(points)
        assertEquals(1, stops.size)
        assertEquals(0L, stops[0].startMillis)
        assertEquals(32 * 30_000L, stops[0].endMillis)
        assertTrue(stops[0].durationMillis >= DwellStops.MIN_DWELL_MILLIS)
        assertEquals(41.01, stops[0].lat, 1e-6)
        assertEquals(29.02, stops[0].lon, 1e-6)
    }

    @Test
    fun leavingRadiusStartsNewCluster() {
        val home = (0..40).map { i ->
            TrackPoint(i * 30_000L, 41.0, 29.0, null)
        }
        // ~200 m east — outside SAME_PLACE_RADIUS.
        val elsewhere = (41..80).map { i ->
            TrackPoint(i * 30_000L, 41.0, 29.002, null)
        }
        val stops = DwellStops.find(home + elsewhere)
        assertEquals(2, stops.size)
        assertTrue(stops[0].durationMillis >= DwellStops.MIN_DWELL_MILLIS)
        assertTrue(stops[1].durationMillis >= DwellStops.MIN_DWELL_MILLIS)
    }

    @Test
    fun briefVisitBetweenDwellsIsSkipped() {
        val first = (0..40).map { i ->
            TrackPoint(i * 30_000L, 41.0, 29.0, null)
        }
        // Two samples ~200 m away — too short to count as a dwell.
        val brief = listOf(
            TrackPoint(41 * 30_000L, 41.0, 29.002, null),
            TrackPoint(42 * 30_000L, 41.0, 29.002, null),
        )
        val second = (43..90).map { i ->
            TrackPoint(i * 30_000L, 41.001, 29.0, null)
        }
        val stops = DwellStops.find(first + brief + second)
        assertEquals(2, stops.size)
    }
}

class DwellNotesJsonTest {
    @Test
    fun jsonRoundTripPreservesDwellNotes() {
        val record = DayRecord(
            date = "2026-09-22",
            title = "t",
            points = listOf(TrackPoint(1_000L, 41.0, 29.0, null)),
            distanceMeters = 0.0,
            dwellNotes = mapOf(1_000L to "Ofis"),
        )
        val parsed = DayJson.fromJson(DayJson.toJson(record))
        assertEquals(mapOf(1_000L to "Ofis"), parsed.dwellNotes)
    }

    @Test
    fun jsonOmitsEmptyDwellNotes() {
        val record = DayRecord("2026-09-22", "t", emptyList(), 0.0)
        val raw = DayJson.toJson(record)
        assertTrue(!raw.contains("dwellNotes"))
    }
}
