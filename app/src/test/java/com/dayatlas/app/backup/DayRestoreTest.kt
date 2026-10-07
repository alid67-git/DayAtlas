package com.dayatlas.app.backup

import com.dayatlas.app.data.DayRecord
import com.dayatlas.app.data.TrackPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DayRestoreTest {
    @Test
    fun dateIsoFromFileName() {
        assertEquals("2026-09-15", DayRestore.dateIsoFromFileName("2026-09-15.gpx"))
        assertEquals("2026-01-02", DayRestore.dateIsoFromFileName("path/2026-01-02.GPX"))
        assertNull(DayRestore.dateIsoFromFileName("track.gpx"))
        assertNull(DayRestore.dateIsoFromFileName(null))
    }

    @Test
    fun snapshotCountsPointsAndNote() {
        val record = DayRecord(
            date = "2026-09-01",
            title = "t",
            points = listOf(
                TrackPoint(0L, 41.0, 29.0, null),
                TrackPoint(60_000L, 41.01, 29.0, null),
            ),
            distanceMeters = 1_200.0,
            note = "hello",
            photos = listOf("a.jpg"),
        )
        val snap = DayRestore.snapshot(record)
        assertEquals(2, snap.pointCount)
        assertEquals(1_200.0, snap.distanceMeters, 0.01)
        assertEquals("hello", snap.notePreview)
        assertEquals(1, snap.photoCount)
    }

    @Test
    fun candidateFlagsLocalTrack() {
        val incoming = DayRecord(
            date = "2026-09-01",
            title = "in",
            points = listOf(TrackPoint(1L, 41.0, 29.0, null)),
            distanceMeters = 10.0,
        )
        val emptyLocal = DayRestore.Candidate(
            dateIso = "2026-09-01",
            incoming = incoming,
            current = DayRecord.empty("2026-09-01", "x"),
        )
        assertFalse(emptyLocal.hasLocalTrack)
        val withLocal = emptyLocal.copy(
            current = incoming.copy(distanceMeters = 99.0),
        )
        assertTrue(withLocal.hasLocalTrack)
    }
}
