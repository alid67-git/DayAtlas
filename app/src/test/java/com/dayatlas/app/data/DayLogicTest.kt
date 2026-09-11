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
            TrackPoint(0L, 41.0, 29.0, null),
            TrackPoint(60_000L, 41.0, 29.001, null),
            TrackPoint(120_000L, 41.0, 29.002, null),
        )
        val len = Geo.pathLengthMeters(points)
        assertTrue(len in 140.0..220.0)
    }
}

class JumpFilterTest {
    @Test
    fun normalWalkIsAccepted() {
        val a = TrackPoint(0L, 41.0, 29.0, null)
        // ~85 m in 60 s ≈ 5 km/h
        val b = TrackPoint(60_000L, 41.0, 29.001, null)
        assertTrue(JumpFilter.shouldAccept(listOf(a), b))
        assertTrue(JumpFilter.findJumps(listOf(a, b)).isEmpty())
    }

    @Test
    fun teleportIsRejectedAndListed() {
        val a = TrackPoint(0L, 41.0, 29.0, null)
        // ~111 km north in 60 s
        val b = TrackPoint(60_000L, 42.0, 29.0, null)
        assertTrue(!JumpFilter.shouldAccept(listOf(a), b))
        val jumps = JumpFilter.findJumps(listOf(a, b))
        assertEquals(1, jumps.size)
        assertEquals(1, jumps[0].index)
        assertTrue(jumps[0].distanceMeters > 100_000)
        assertTrue(jumps[0].speedKmh > JumpFilter.MAX_SPEED_KMH)
    }

    @Test
    fun firstPointAlwaysAccepted() {
        val only = TrackPoint(0L, 41.0, 29.0, null)
        assertTrue(JumpFilter.shouldAccept(emptyList(), only))
    }
}

class SpeedStatsTest {
    @Test
    fun fewerThanTwoPointsIsEmpty() {
        val stats = SpeedStats.compute(listOf(TrackPoint(0L, 41.0, 29.0, null)))
        assertEquals(0.0, stats.maxSpeedKmh, 1e-9)
        assertEquals(0L, stats.activeMillis)
    }

    @Test
    fun steadyWalkGivesConsistentMaxAndAvgSpeed() {
        // ~85 m every 60 s ≈ 5.1 km/h, three legs.
        val points = listOf(
            TrackPoint(0L, 41.0, 29.000, null),
            TrackPoint(60_000L, 41.0, 29.001, null),
            TrackPoint(120_000L, 41.0, 29.002, null),
            TrackPoint(180_000L, 41.0, 29.003, null),
        )
        val stats = SpeedStats.compute(points)
        assertTrue("maxSpeed=${stats.maxSpeedKmh}", stats.maxSpeedKmh in 3.0..8.0)
        assertTrue("avgSpeed=${stats.avgSpeedKmh}", stats.avgSpeedKmh in 3.0..8.0)
        assertEquals(180_000L, stats.activeMillis)
    }

    @Test
    fun stationaryJitterDoesNotCountAsActive() {
        val points = listOf(
            TrackPoint(0L, 41.0, 29.0, null),
            // ~1 m of GPS jitter over a minute - well under the noise floor.
            TrackPoint(60_000L, 41.0, 29.00001, null),
        )
        val stats = SpeedStats.compute(points)
        assertEquals(0L, stats.activeMillis)
        assertEquals(0.0, stats.avgSpeedKmh, 1e-9)
        assertEquals(0.0, stats.maxSpeedKmh, 1e-9)
    }

    @Test
    fun homeGpsWanderDoesNotInflateActiveOrMax() {
        // ~50 m in 60 s ≈ 3 km/h — typical courtyard GPS bounce, not a walk.
        val points = listOf(
            TrackPoint(0L, 41.0, 29.0, null),
            TrackPoint(60_000L, 41.0, 29.0006, null),
            TrackPoint(120_000L, 41.0, 29.0, null),
            TrackPoint(180_000L, 41.0, 29.0006, null),
        )
        val stats = SpeedStats.compute(points)
        assertEquals(0L, stats.activeMillis)
        assertEquals(0.0, stats.maxSpeedKmh, 1e-9)
        assertTrue(Geo.pathLengthMeters(points) < 1.0)
    }

    @Test
    fun unrealisticJumpIsExcludedFromMaxSpeed() {
        val points = listOf(
            TrackPoint(0L, 41.0, 29.0, null),
            // ~111 km in 60 s - a GPS teleport, not a real max speed.
            TrackPoint(60_000L, 42.0, 29.0, null),
        )
        val stats = SpeedStats.compute(points)
        assertEquals(0.0, stats.maxSpeedKmh, 1e-9)
    }

    @Test
    fun longGapBreaksTheSegmentInsteadOfCountingAsSlowDriving() {
        val points = listOf(
            TrackPoint(0L, 41.0, 29.0, null),
            // 30 min later, a few meters away - an overnight/parked gap.
            TrackPoint(30 * 60_000L, 41.0, 29.0001, null),
        )
        val stats = SpeedStats.compute(points)
        assertEquals(0L, stats.activeMillis)
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

    @Test
    fun formatDurationSwitchesToHoursPastSixty() {
        val tr = Locale("tr", "TR")
        assertEquals("45 dk", DayTitle.formatDuration(45 * 60_000L, tr))
        assertEquals("2 sa 5 dk", DayTitle.formatDuration((2 * 60 + 5) * 60_000L, tr))
        val en = Locale.ENGLISH
        assertEquals("45 min", DayTitle.formatDuration(45 * 60_000L, en))
        assertEquals("2 h 5 min", DayTitle.formatDuration((2 * 60 + 5) * 60_000L, en))
    }

    @Test
    fun formatSpeedRoundsToWholeKmh() {
        assertEquals("42 km/sa", DayTitle.formatSpeed(42.4, Locale("tr", "TR")))
        assertEquals("42 km/h", DayTitle.formatSpeed(42.4, Locale.ENGLISH))
    }

    @Test
    fun formatEnglishAndGermanTitles() {
        val date = LocalDate.of(2026, 8, 28)
        assertEquals("Daily 28 Aug 2026", DayTitle.format(date, Locale.ENGLISH))
        assertEquals("Tag 28 Aug 2026", DayTitle.format(date, Locale.GERMAN))
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

    @Test
    fun multiDayGpxHasOneTrackPerDayWithUtcTimes() {
        val day1 = DayRecord(
            date = "2026-08-28",
            title = "Günlük 28 Ağu 2026",
            points = listOf(TrackPoint(1_720_000_000_000L, 41.01, 29.02, null)),
            distanceMeters = 0.0,
        )
        val day2 = DayRecord(
            date = "2026-08-29",
            title = "Günlük 29 Ağu 2026",
            points = listOf(
                TrackPoint(1_720_086_400_000L, 41.02, 29.03, null),
                TrackPoint(1_720_086_460_000L, 41.03, 29.04, null),
            ),
            distanceMeters = 10.0,
        )
        val empty = DayRecord.empty("2026-08-30", "empty")
        val gpx = DayJson.toGpx(listOf(day1, empty, day2), exportName = "Tatil")
        assertEquals(2, Regex("<trk>").findAll(gpx).count())
        assertTrue(gpx.contains("<name>Tatil — Günlük 28 Ağu 2026</name>"))
        assertTrue(gpx.contains("<name>Tatil — Günlük 29 Ağu 2026</name>"))
        assertEquals(3, Regex("<trkpt ").findAll(gpx).count())
        assertTrue(gpx.contains(DayJson.formatGpxTime(1_720_000_000_000L)))
        assertTrue(Regex("""<time>\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}Z</time>""").containsMatchIn(gpx))
    }

    @Test
    fun gpxTimeIsUtcWithoutMillis() {
        assertEquals("1970-01-01T00:00:01Z", DayJson.formatGpxTime(1_000L))
    }
}

class GpxExporterNameTest {
    @Test
    fun sanitizeStripsPathAndExtension() {
        assertEquals(
            "Benim rota",
            com.dayatlas.app.export.GpxExporter.sanitizeFileName("  Benim rota.gpx  "),
        )
        assertEquals(
            "a-b-c",
            com.dayatlas.app.export.GpxExporter.sanitizeFileName("a/b:c"),
        )
    }

    @Test
    fun sanitizeStripsAnyExtension() {
        assertEquals(
            "rota",
            com.dayatlas.app.export.GpxExporter.sanitizeFileName("rota.kml"),
        )
        assertEquals(
            "DayAtlas-2026-09-11.gpx",
            com.dayatlas.app.export.GpxExporter.withGpxExtension("DayAtlas-2026-09-11.txt"),
        )
    }

    @Test
    fun withGpxExtensionAlwaysAppends() {
        assertEquals(
            "rota.gpx",
            com.dayatlas.app.export.GpxExporter.withGpxExtension("rota"),
        )
        assertEquals(
            "rota.gpx",
            com.dayatlas.app.export.GpxExporter.withGpxExtension("rota.gpx"),
        )
    }

    @Test
    fun defaultNamesCoverSingleAndRange() {
        val a = LocalDate.of(2026, 9, 1)
        val b = LocalDate.of(2026, 9, 9)
        assertEquals("DayAtlas-2026-09-01", com.dayatlas.app.export.GpxExporter.defaultFileName(a, a))
        assertEquals(
            "DayAtlas-2026-09-01_2026-09-09",
            com.dayatlas.app.export.GpxExporter.defaultFileName(a, b),
        )
    }
}
