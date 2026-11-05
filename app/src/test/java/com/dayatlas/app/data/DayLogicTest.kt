package com.dayatlas.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.util.Locale

class DayRecordCheckCountTest {
    @Test
    fun checkCountFallsBackToPointCount() {
        val record = DayRecord(
            date = "2026-09-19",
            title = "t",
            points = listOf(TrackPoint(1L, 41.0, 29.0, null)),
            distanceMeters = 0.0,
            gpsCheckCount = 0,
        )
        assertEquals(1, record.checkCount)
    }

    @Test
    fun checkCountPrefersStoredGpsChecks() {
        val record = DayRecord(
            date = "2026-09-19",
            title = "t",
            points = listOf(TrackPoint(1L, 41.0, 29.0, null)),
            distanceMeters = 0.0,
            gpsCheckCount = 12,
        )
        assertEquals(12, record.checkCount)
    }

    @Test
    fun jsonRoundTripKeepsGpsCheckCount() {
        val original = DayRecord(
            date = "2026-09-19",
            title = "t",
            points = listOf(
                TrackPoint(1_000L, 41.0, 29.0, null),
                TrackPoint(2_000L, 41.001, 29.0, null),
            ),
            distanceMeters = 100.0,
            gpsCheckCount = 9,
        )
        val restored = DayJson.fromJson(DayJson.toJson(original))
        assertEquals(9, restored.gpsCheckCount)
        assertEquals(9, restored.checkCount)
        assertEquals(2, restored.points.size)
    }

    @Test
    fun jsonWithoutFieldUsesPointCount() {
        val raw = """{"version":1,"date":"2026-09-19","title":"t","distanceMeters":0,"points":[{"t":1,"lat":41.0,"lon":29.0}]}"""
        val restored = DayJson.fromJson(raw)
        assertEquals(1, restored.gpsCheckCount)
        assertEquals(1, restored.checkCount)
    }
}

class GeoTest {
    @Test
    fun nearbyPointsHaveExpectedDistance() {
        val meters = Geo.haversineMeters(41.0, 29.0, 41.0, 29.001)
        assertTrue("expected ~85m, was $meters", meters in 70.0..110.0)
    }

    @Test
    fun pathLengthSumsSegments() {
        // ~150 m each in 60 s ≈ 9 km/h — real motion past the GPS-hop band.
        val points = listOf(
            TrackPoint(0L, 41.0, 29.0, null),
            TrackPoint(60_000L, 41.0, 29.0018, null),
            TrackPoint(120_000L, 41.0, 29.0036, null),
        )
        val len = Geo.pathLengthMeters(points)
        assertTrue("expected ~300m, was $len", len in 250.0..350.0)
    }
}

class SpikeConfirmTest {
    private val home = TrackPoint(0L, 41.0, 29.0, null)
    // ~200 m east — soft departure (not a JumpFilter teleport).
    private val spike = TrackPoint(60_000L, 41.0, 29.0024, null)
    private val homeAgain = TrackPoint(120_000L, 41.0, 29.0001, null)
    private val further = TrackPoint(120_000L, 41.0, 29.0048, null)

    @Test
    fun firstFarFixIsHeld() {
        val out = SpikeConfirm.decide(home, pending = null, candidate = spike)
        assertTrue(out is SpikeConfirm.Outcome.Hold)
    }

    @Test
    fun reboundHomeDropsPendingSpike() {
        val out = SpikeConfirm.decide(home, pending = spike, candidate = homeAgain)
        assertTrue(out is SpikeConfirm.Outcome.DropPending)
    }

    @Test
    fun continuedDepartureCommitsPending() {
        val out = SpikeConfirm.decide(home, pending = spike, candidate = further)
        assertTrue(out is SpikeConfirm.Outcome.CommitPendingThen)
    }

    @Test
    fun samePlaceProcessesWithoutHold() {
        val near = TrackPoint(30_000L, 41.0, 29.0002, null)
        val out = SpikeConfirm.decide(home, pending = null, candidate = near)
        assertTrue(out is SpikeConfirm.Outcome.Process)
    }

    @Test
    fun longGapRequiresConfirmNearPendingNotJustAwayFromHome() {
        // ~19 km east after a 10 min radio gap — airplane-mode stale cluster.
        val far = TrackPoint(10 * 60_000L, 41.0, 29.23, null)
        val elsewhere = TrackPoint(10 * 60_000L + 30_000L, 41.05, 29.23, null)
        val hold = SpikeConfirm.decide(home, pending = null, candidate = far)
        assertTrue(hold is SpikeConfirm.Outcome.Hold)
        val stillHolding = SpikeConfirm.decide(home, pending = far, candidate = elsewhere)
        assertTrue(
            "expected Hold while gap pending wanders, was $stillHolding",
            stillHolding is SpikeConfirm.Outcome.Hold,
        )
        val confirmed = TrackPoint(10 * 60_000L + 30_000L, 41.0002, 29.2301, null)
        val commit = SpikeConfirm.decide(home, pending = far, candidate = confirmed)
        assertTrue(commit is SpikeConfirm.Outcome.CommitPendingThen)
    }
}

class RouteGapsTest {
    @Test
    fun denseDrivingDoesNotBreak() {
        // ~400 m every 30 s ≈ 48 km/h
        val a = TrackPoint(0L, 7.88, 98.38, null)
        val b = TrackPoint(30_000L, 7.8836, 98.38, null)
        assertFalse(RouteGaps.shouldBreak(a, b))
    }

    @Test
    fun airplaneReconnectBreaksPath() {
        // ~19 km after 10 min — JumpFilter speed looks like driving, but the
        // map must not draw a corridor line across the gap.
        val south = TrackPoint(0L, 7.88, 98.38, null)
        val north = TrackPoint(10 * 60_000L, 8.05, 98.38, null)
        assertTrue(RouteGaps.shouldBreak(south, north))
        assertEquals(listOf(0, 1), RouteGaps.segmentStarts(listOf(south, north)))
    }

    @Test
    fun oneMinuteInlandExplosionBreaksPath() {
        // Screenshot case: ~2.5 km inland in 60 s ≈ 150 km/h — under the old
        // 5 km continuous cap, so the map drew a fake triangle corridor.
        val coast = TrackPoint(0L, 7.88, 98.38, null)
        val inland = TrackPoint(60_000L, 7.90, 98.38, null)
        assertTrue(RouteGaps.shouldBreak(coast, inland))
    }

    @Test
    fun fourPassBounceSplitsIntoDisconnectedPins() {
        val a = TrackPoint(0L, 7.88, 98.38, null)
        val b = TrackPoint(10 * 60_000L, 8.05, 98.38, null)
        val a2 = TrackPoint(20 * 60_000L, 7.88, 98.38, null)
        val b2 = TrackPoint(30 * 60_000L, 8.05, 98.38, null)
        assertEquals(listOf(0, 1, 2, 3), RouteGaps.segmentStarts(listOf(a, b, a2, b2)))
    }
}

class TrackHealTest {
    @Test
    fun fourPassAirplaneBounceDropsFarPins() {
        val a = TrackPoint(0L, 7.88, 98.38, null)
        val b = TrackPoint(10 * 60_000L, 8.05, 98.38, null)
        val a2 = TrackPoint(20 * 60_000L, 7.88, 98.38, null)
        val b2 = TrackPoint(30 * 60_000L, 8.05, 98.38, null)
        val healed = TrackHeal.withoutReconnectTeleports(listOf(a, b, a2, b2))
        assertEquals(listOf(a, a2), healed)
    }

    @Test
    fun realTripKeepsDenseClusterWhenHomeGpsBounces() {
        // Morning pin at home, long real drive north, one bad home fix, resume north.
        val home = TrackPoint(0L, 7.88, 98.38, null)
        val n1 = TrackPoint(10 * 60_000L, 8.05, 98.38, null)
        val n2 = TrackPoint(10 * 60_000L + 30_000L, 8.0536, 98.38, null)
        val n3 = TrackPoint(10 * 60_000L + 60_000L, 8.0572, 98.38, null)
        val homeSpike = TrackPoint(10 * 60_000L + 90_000L, 7.88, 98.38, null)
        val n4 = TrackPoint(10 * 60_000L + 120_000L, 8.0608, 98.38, null)
        val healed = TrackHeal.withoutReconnectTeleports(
            listOf(home, n1, n2, n3, homeSpike, n4),
        )
        assertEquals(listOf(home, n1, n2, n3, n4), healed)
    }

    @Test
    fun sustainedPostGapClusterIsKept() {
        // Radio gap, then keep recording ~19 km north — must not wipe the day.
        val south = TrackPoint(0L, 7.88, 98.38, null)
        val n1 = TrackPoint(10 * 60_000L, 8.05, 98.38, null)
        val n2 = TrackPoint(10 * 60_000L + 30_000L, 8.0536, 98.38, null)
        val n3 = TrackPoint(10 * 60_000L + 60_000L, 8.0572, 98.38, null)
        val healed = TrackHeal.withoutReconnectTeleports(listOf(south, n1, n2, n3))
        assertEquals(listOf(south, n1, n2, n3), healed)
    }

    @Test
    fun trailingReconnectAloneIsDropped() {
        val south = TrackPoint(0L, 7.88, 98.38, null)
        val north = TrackPoint(10 * 60_000L, 8.05, 98.38, null)
        val healed = TrackHeal.withoutReconnectTeleports(listOf(south, north))
        assertEquals(listOf(south), healed)
    }

    @Test
    fun hardJumpSpikeIsDroppedEvenWithoutLongGap() {
        val a = TrackPoint(0L, 41.0, 29.0, null)
        val b = TrackPoint(60_000L, 42.0, 29.0, null) // ~111 km / 60 s
        val c = TrackPoint(120_000L, 41.001, 29.0, null)
        val healed = TrackHeal.withoutReconnectTeleports(listOf(a, b, c))
        assertEquals(listOf(a, c), healed)
    }

    @Test
    fun denseDrivingUnchanged() {
        val a = TrackPoint(0L, 7.88, 98.38, null)
        val b = TrackPoint(30_000L, 7.8836, 98.38, null)
        val c = TrackPoint(60_000L, 7.8872, 98.38, null)
        assertEquals(listOf(a, b, c), TrackHeal.withoutReconnectTeleports(listOf(a, b, c)))
    }

    @Test
    fun coastalInlandExplosionTrianglesAreRemoved() {
        // Coast → inland → coast → inland → coast (1 min samples), like the
        // Phuket screenshot zigzags. Heal must keep the coastal spine.
        val c1 = TrackPoint(0L, 7.8800, 98.2800, null)
        val i1 = TrackPoint(60_000L, 7.9000, 98.2950, null)
        val c2 = TrackPoint(120_000L, 7.8835, 98.2810, null)
        val i2 = TrackPoint(180_000L, 7.9050, 98.3000, null)
        val c3 = TrackPoint(240_000L, 7.8870, 98.2820, null)
        val healed = TrackHeal.withoutReconnectTeleports(listOf(c1, i1, c2, i2, c3))
        assertEquals(listOf(c1, c2, c3), healed)
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

    @Test
    fun singleBadFixDoesNotAlsoFlagTheGoodPointAfterIt() {
        val a = TrackPoint(0L, 41.0, 29.0, null)
        // ~111 km north in 60 s - a single bad fix.
        val b = TrackPoint(60_000L, 42.0, 29.0, null)
        // Back near `a`, continuing the real path (~111 m in 120 s from a) -
        // but ~111 km / 60 s if measured from the bad point `b` instead.
        val c = TrackPoint(120_000L, 41.001, 29.0, null)
        val jumps = JumpFilter.findJumps(listOf(a, b, c))
        assertEquals(1, jumps.size)
        assertEquals(1, jumps[0].index)
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
    fun accuracyCircleHopDoesNotInflateDistance() {
        // ~90 m in 60 s with 50 m reported accuracy: above same-place (80 m)
        // but inside max(25, 2×50)=100 m noise gate — must not count.
        val a = TrackPoint(0L, 41.0, 29.0, 50f)
        val b = TrackPoint(60_000L, 41.0, 29.00081, 50f) // ~90 m
        assertEquals(100.0, TrackMotion.accuracyNoiseGateMeters(50f), 1e-9)
        assertTrue(Geo.pathLengthMeters(listOf(a, b)) < 1.0)
    }

    @Test
    fun samePlacePinWanderDoesNotInflateDistance() {
        // New pins every ~85 m (just outside same-place collapse) at 30 s —
        // courtyard GPS cloud with a tight sample interval. Must not sum.
        val points = listOf(
            TrackPoint(0L, 41.0, 29.0, null),
            TrackPoint(30_000L, 41.0, 29.001, null),
            TrackPoint(60_000L, 41.0, 29.0, null),
            TrackPoint(90_000L, 41.0, 29.001, null),
            TrackPoint(120_000L, 41.0, 29.0, null),
            TrackPoint(150_000L, 41.0, 29.001, null),
        )
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

    @Test
    fun airplaneModeCorridorTeleportDoesNotInflateDistanceOrMax() {
        // Phuket-scale: ~19 km N↔S after a 10 min radio outage. Implied speed
        // is ~114 km/h (under JumpFilter 120), which previously counted as a
        // real driven leg and drew a parallel corridor line on the map.
        val south = TrackPoint(0L, 7.88, 98.38, null)
        val north = TrackPoint(10 * 60_000L, 8.05, 98.38, null)
        val southAgain = TrackPoint(20 * 60_000L, 7.88, 98.38, null)
        assertTrue(JumpFilter.shouldAccept(listOf(south), north))
        val points = listOf(south, north, southAgain)
        val stats = SpeedStats.compute(points)
        assertEquals(0.0, stats.maxSpeedKmh, 1e-9)
        assertEquals(0L, stats.activeMillis)
        assertTrue(Geo.pathLengthMeters(points) < 1.0)
    }

    @Test
    fun dtUsesLastRefreshSoStationarySitDoesNotLookLikeAGap() {
        val parked = TrackPoint(
            timeMillis = 0L,
            lat = 41.0,
            lon = 29.0,
            accuracyMeters = null,
            lastTimeMillis = 12 * 60_000L,
        )
        // Leave ~200 m away 30 s after the last refresh.
        val leaving = TrackPoint(12 * 60_000L + 30_000L, 41.0018, 29.0, null)
        assertFalse(JumpFilter.isJump(parked, leaving))
        assertFalse(RouteGaps.shouldBreak(parked, leaving))
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

    @Test
    fun formatTitlesForNewerLanguages() {
        val date = LocalDate.of(2026, 8, 28)
        assertEquals("Diario 28 Ago 2026", DayTitle.format(date, Locale("es")))
        assertEquals("Journalier 28 Aoû 2026", DayTitle.format(date, Locale("fr")))
        assertEquals("每日 28 8月 2026", DayTitle.format(date, Locale("zh")))
        assertEquals("दैनिक 28 अग 2026", DayTitle.format(date, Locale("hi")))
        assertEquals("يومي 28 أغسطس 2026", DayTitle.format(date, Locale("ar")))
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

    @Test
    fun gpxRoundTripPreservesPointsAndTitle() {
        val record = DayRecord(
            date = "2026-08-28",
            title = "Günlük 28 Ağu 2026",
            points = listOf(
                TrackPoint(1_720_000_000_000L, 41.01, 29.02, 12.5f),
                TrackPoint(1_720_000_060_000L, 41.02, 29.03, null),
            ),
            distanceMeters = 0.0,
        )
        val parsed = DayJson.fromGpx(record.date, DayJson.toGpx(record))
        assertEquals(record.date, parsed?.date)
        assertEquals(record.title, parsed?.title)
        assertEquals(2, parsed?.points?.size)
        assertEquals(41.01, parsed?.points?.get(0)?.lat ?: 0.0, 1e-6)
        assertEquals(29.02, parsed?.points?.get(0)?.lon ?: 0.0, 1e-6)
        assertEquals(12.5f, parsed?.points?.get(0)?.accuracyMeters)
        assertEquals(1_720_000_000_000L, parsed?.points?.get(0)?.timeMillis)
        assertEquals(null, parsed?.points?.get(1)?.accuracyMeters)
    }

    @Test
    fun fromGpxRejectsGarbage() {
        assertEquals(null, DayJson.fromGpx("2026-08-28", "not xml at all"))
    }

    @Test
    fun jsonRoundTripPreservesNote() {
        val record = DayRecord(
            date = "2026-09-16",
            title = "Günlük 16 Eyl 2026",
            points = emptyList(),
            distanceMeters = 0.0,
            note = "Bugün evde dinlendim.",
        )
        val parsed = DayJson.fromJson(DayJson.toJson(record))
        assertEquals("Bugün evde dinlendim.", parsed.note)
    }

    @Test
    fun jsonRoundTripOmitsBlankNote() {
        val record = DayRecord("2026-09-16", "x", emptyList(), 0.0, note = null)
        val parsed = DayJson.fromJson(DayJson.toJson(record))
        assertEquals(null, parsed.note)
    }

    @Test
    fun gpxRoundTripPreservesNoteEvenWithoutPoints() {
        val record = DayRecord(
            date = "2026-09-16",
            title = "Günlük 16 Eyl 2026",
            points = emptyList(),
            distanceMeters = 0.0,
            note = "İzin günü.",
        )
        val parsed = DayJson.fromGpx(record.date, DayJson.toGpx(record))
        assertEquals("İzin günü.", parsed?.note)
        assertEquals(0, parsed?.points?.size)
    }

    @Test
    fun toGpxSkipsDaysWithNoPointsAndNoNote() {
        val record = DayRecord("2026-09-16", "x", emptyList(), 0.0, note = null)
        val gpx = DayJson.toGpx(listOf(record))
        assertEquals(false, gpx.contains("<trk>"))
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
