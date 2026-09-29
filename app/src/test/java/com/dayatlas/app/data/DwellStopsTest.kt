package com.dayatlas.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DwellStopsTest {
    @Test
    fun emptyOrSingleFreshPointYieldsNothing() {
        assertTrue(DwellStops.find(emptyList()).isEmpty())
        assertTrue(
            DwellStops.find(listOf(TrackPoint(0L, 41.0, 29.0, null))).isEmpty(),
        )
    }

    @Test
    fun shortStayIsIgnored() {
        val points = (0..20).map { i ->
            TrackPoint(i * 30_000L, 41.0, 29.0, null)
        }
        assertTrue(DwellStops.find(points).isEmpty())
    }

    @Test
    fun fifteenMinuteStayIsDetected() {
        val points = (0..32).map { i ->
            TrackPoint(i * 30_000L, 41.01, 29.02, null)
        }
        val stops = DwellStops.find(points)
        assertEquals(1, stops.size)
        assertEquals(0L, stops[0].startMillis)
        assertEquals(32 * 30_000L, stops[0].endMillis)
        assertTrue(stops[0].durationMillis >= DwellStops.MIN_DWELL_MILLIS)
    }

    @Test
    fun collapsedSamePlacePinWithLastTimeIsDetected() {
        // DayStore collapses same-place refreshes into one pin, keeping arrival
        // in timeMillis and advancing lastTimeMillis — that must still count.
        val pin = TrackPoint(
            timeMillis = 0L,
            lat = 41.0,
            lon = 29.0,
            accuracyMeters = null,
            lastTimeMillis = 20L * 60_000L,
        )
        val stops = DwellStops.find(listOf(pin))
        assertEquals(1, stops.size)
        assertEquals(20L * 60_000L, stops[0].durationMillis)
    }

    @Test
    fun leavingRadiusStartsNewCluster() {
        val home = (0..40).map { i ->
            TrackPoint(i * 30_000L, 41.0, 29.0, null)
        }
        val elsewhere = (41..80).map { i ->
            TrackPoint(i * 30_000L, 41.0, 29.002, null)
        }
        val stops = DwellStops.find(home + elsewhere)
        assertEquals(2, stops.size)
    }

    @Test
    fun briefVisitBetweenDwellsIsSkipped() {
        val first = (0..40).map { i ->
            TrackPoint(i * 30_000L, 41.0, 29.0, null)
        }
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
    fun jsonRoundTripPreservesDwellNotesAndPhotosAndLastTime() {
        val record = DayRecord(
            date = "2026-09-22",
            title = "t",
            points = listOf(
                TrackPoint(1_000L, 41.0, 29.0, null, lastTimeMillis = 900_000L),
            ),
            distanceMeters = 0.0,
            dwellNotes = mapOf(1_000L to "Ofis"),
            dwellPhotos = mapOf(1_000L to "123.jpg"),
        )
        val parsed = DayJson.fromJson(DayJson.toJson(record))
        assertEquals(mapOf(1_000L to "Ofis"), parsed.dwellNotes)
        assertEquals(mapOf(1_000L to "123.jpg"), parsed.dwellPhotos)
        assertEquals(1_000L, parsed.points[0].timeMillis)
        assertEquals(900_000L, parsed.points[0].lastTimeMillis)
    }

    @Test
    fun jsonOmitsEmptyDwellNotes() {
        val record = DayRecord("2026-09-22", "t", emptyList(), 0.0)
        val raw = DayJson.toJson(record)
        assertTrue(!raw.contains("dwellNotes"))
        assertTrue(!raw.contains("dwellPhotos"))
    }
}
