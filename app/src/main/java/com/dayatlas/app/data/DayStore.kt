package com.dayatlas.app.data

import android.content.Context
import android.location.Location
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

data class AppendResult(
    val record: DayRecord,
    /** True when a new pin was added (not same-place time refresh / reject). */
    val geometryChanged: Boolean,
    /** False when nothing was written: a teleport jump was rejected, or the
     *  fix is an unconfirmed movement candidate (see [MovementConfirmation]). */
    val wrote: Boolean,
)

class DayStore(context: Context) {
    private val appContext = context.applicationContext

    fun daysDir(): File = File(appContext.filesDir, "days").also { it.mkdirs() }

    fun jsonFile(dateIso: String): File = File(daysDir(), "$dateIso.json")

    fun gpxFile(dateIso: String): File = File(daysDir(), "$dateIso.gpx")

    fun load(dateIso: String): DayRecord? = lock.withLock {
        memoryToday?.takeIf { it.date == dateIso }?.let { return@withLock it }
        val loaded = loadUnlocked(dateIso) ?: return@withLock null
        if (dateIso == DayTitle.iso(DayTitle.localToday())) {
            memoryToday = loaded
        }
        loaded
    }

    fun loadToday(zoneId: ZoneId = ZoneId.systemDefault()): DayRecord {
        val date = DayTitle.localToday(zoneId)
        val iso = DayTitle.iso(date)
        return load(iso) ?: DayRecord.empty(iso, DayTitle.format(date))
    }

    /** Dates that have a JSON day file, ascending. */
    fun listDates(): List<LocalDate> = lock.withLock {
        daysDir().listFiles()
            ?.asSequence()
            ?.filter { it.isFile && it.name.endsWith(".json") }
            ?.mapNotNull { runCatching { LocalDate.parse(it.name.removeSuffix(".json")) }.getOrNull() }
            ?.sorted()
            ?.toList()
            .orEmpty()
    }

    fun loadRange(from: LocalDate, to: LocalDate): List<DayRecord> {
        if (to.isBefore(from)) return emptyList()
        val out = ArrayList<DayRecord>()
        var day = from
        while (!day.isAfter(to)) {
            val record = load(DayTitle.iso(day))
            if (record != null && record.points.isNotEmpty()) {
                out.add(record)
            }
            day = day.plusDays(1)
        }
        return out
    }

    fun append(location: Location, zoneId: ZoneId = ZoneId.systemDefault()): AppendResult = lock.withLock {
        val timeMillis = location.time.takeIf { it > 0L } ?: System.currentTimeMillis()
        val localDate = Instant.ofEpochMilli(timeMillis)
            .atZone(zoneId)
            .toLocalDate()
        val iso = DayTitle.iso(localDate)
        val existing = loadUnlocked(iso) ?: DayRecord.empty(iso, DayTitle.format(localDate))
        val point = TrackPoint(
            timeMillis = timeMillis,
            lat = location.latitude,
            lon = location.longitude,
            accuracyMeters = if (location.hasAccuracy()) location.accuracy else null,
        )
        if (!JumpFilter.shouldAccept(existing.points, point)) {
            // GPS teleport — keep the day file unchanged.
            return@withLock AppendResult(existing, geometryChanged = false, wrote = false)
        }
        val last = existing.points.lastOrNull()
        if (last != null) {
            val anchor = MovementConfirmation.Anchor(
                lat = last.lat,
                lon = last.lon,
                candidateLat = pendingCandidateLat.takeIf { pendingDateIso == iso },
                candidateLon = pendingCandidateLon.takeIf { pendingDateIso == iso },
            )
            val result = MovementConfirmation.classify(anchor, point.lat, point.lon, Geo.SAME_PLACE_RADIUS_M)
            when (result.outcome) {
                MovementConfirmation.Outcome.STATIONARY -> {
                    clearPendingCandidate()
                    // Same place (incl. while the sample interval is coarsening) —
                    // refresh time on the existing fix; do not stack another pin
                    // or chase GPS jitter around the spot.
                    val refreshed = last.copy(
                        timeMillis = point.timeMillis,
                        accuracyMeters = point.accuracyMeters ?: last.accuracyMeters,
                    )
                    val points = existing.points.dropLast(1) + refreshed
                    val updated = existing.copy(
                        points = points,
                        distanceMeters = Geo.pathLengthMeters(points),
                    )
                    persistUnlocked(updated)
                    return@withLock AppendResult(updated, geometryChanged = false, wrote = true)
                }
                MovementConfirmation.Outcome.PENDING -> {
                    // A lone (or inconsistent) fix outside the same-place
                    // radius — could be real departure just starting, or
                    // could be indoor/urban GPS multipath scattering to a
                    // different spot each time (see MovementConfirmation's
                    // doc). Note it as the candidate, but don't touch the
                    // track yet — this is exactly what used to draw a
                    // "starburst" of crisscrossing lines around a spot the
                    // device never actually left.
                    pendingDateIso = iso
                    pendingCandidateLat = result.anchor.candidateLat
                    pendingCandidateLon = result.anchor.candidateLon
                    return@withLock AppendResult(existing, geometryChanged = false, wrote = false)
                }
                MovementConfirmation.Outcome.CONFIRMED -> {
                    // Two consistent fixes away from the last point — real
                    // movement. Falls through to append `point` below.
                    clearPendingCandidate()
                }
            }
        }
        val points = existing.points + point
        val updated = existing.copy(
            points = points,
            distanceMeters = Geo.pathLengthMeters(points),
        )
        persistUnlocked(updated)
        AppendResult(updated, geometryChanged = true, wrote = true)
    }

    private fun clearPendingCandidate() {
        pendingDateIso = null
        pendingCandidateLat = null
        pendingCandidateLon = null
    }

    /**
     * Removes the point at [index] for [dateIso], recalculates distance, and
     * rewrites JSON + GPX. Returns the updated record, or null if missing /
     * out of range.
     */
    fun removePointAt(dateIso: String, index: Int): DayRecord? = lock.withLock {
        val existing = loadUnlocked(dateIso) ?: return@withLock null
        if (index !in existing.points.indices) return@withLock null
        val points = existing.points.toMutableList().also { it.removeAt(index) }
        if (points.isEmpty()) {
            // A note or photos keep the day file alive even with zero
            // points left - otherwise deleting the last point would
            // silently take them with it.
            if (existing.note.isNullOrEmpty() && existing.photos.isEmpty()) {
                jsonFile(dateIso).delete()
                gpxFile(dateIso).delete()
                if (memoryToday?.date == dateIso) memoryToday = null
                return@withLock DayRecord.empty(dateIso, existing.title)
            }
            val emptied = existing.copy(points = points, distanceMeters = 0.0)
            persistUnlocked(emptied, writeGpx = true)
            return@withLock emptied
        }
        val updated = existing.copy(
            points = points,
            distanceMeters = Geo.pathLengthMeters(points),
        )
        persistUnlocked(updated, writeGpx = true)
        updated
    }

    /**
     * Sets or clears this day's free-form note - works for any date,
     * including one with no GPS points yet (a fresh JSON file is created
     * just to hold the note) or one in the past. Clearing the note on an
     * otherwise-pointless day deletes the file again instead of leaving an
     * empty husk that [listDates] would then treat as a real day.
     */
    fun setNote(dateIso: String, note: String?): DayRecord = lock.withLock {
        val trimmed = note?.trim()?.take(NOTE_MAX_LENGTH)?.ifEmpty { null }
        val existing = loadUnlocked(dateIso)
            ?: DayRecord.empty(dateIso, DayTitle.format(LocalDate.parse(dateIso)))
        val updated = existing.copy(note = trimmed)
        if (updated.points.isEmpty() && trimmed == null && updated.photos.isEmpty()) {
            jsonFile(dateIso).delete()
            gpxFile(dateIso).delete()
            if (memoryToday?.date == dateIso) memoryToday = null
            return@withLock updated
        }
        persistUnlocked(
            updated,
            writeGpx = updated.points.isNotEmpty() || trimmed != null || updated.photos.isNotEmpty(),
        )
        updated
    }

    /** Replaces this day's photo file-name list (see [PhotoStore]) after an add/delete. */
    fun setPhotos(dateIso: String, photos: List<String>): DayRecord = lock.withLock {
        val existing = loadUnlocked(dateIso)
            ?: DayRecord.empty(dateIso, DayTitle.format(LocalDate.parse(dateIso)))
        val updated = existing.copy(photos = photos)
        if (updated.points.isEmpty() && updated.note.isNullOrEmpty() && photos.isEmpty()) {
            jsonFile(dateIso).delete()
            gpxFile(dateIso).delete()
            if (memoryToday?.date == dateIso) memoryToday = null
            return@withLock updated
        }
        persistUnlocked(
            updated,
            writeGpx = updated.points.isNotEmpty() || !updated.note.isNullOrEmpty() || updated.photos.isNotEmpty(),
        )
        updated
    }

    private fun loadUnlocked(dateIso: String): DayRecord? {
        memoryToday?.takeIf { it.date == dateIso }?.let { return it }
        val file = jsonFile(dateIso)
        if (!file.exists()) return null
        return runCatching { DayJson.fromJson(file.readText()) }.getOrNull()
    }

    private fun persistUnlocked(record: DayRecord, writeGpx: Boolean = false) {
        daysDir()
        // Compact JSON (no pretty-print) — every GPS sample rewrites this file.
        DayJson.writeAtomic(jsonFile(record.date), DayJson.toJson(record))
        // GPX is derived; write on edit/export/backup, not on every sample.
        if (writeGpx) {
            DayJson.writeAtomic(gpxFile(record.date), DayJson.toGpx(record))
        }
        if (record.date == DayTitle.iso(DayTitle.localToday())) {
            memoryToday = record
        } else if (memoryToday?.date == record.date) {
            memoryToday = record
        }
    }

    /** Ensures on-disk GPX matches the JSON day file (backup / share). */
    fun ensureGpx(dateIso: String) = lock.withLock {
        val record = loadUnlocked(dateIso) ?: return@withLock
        DayJson.writeAtomic(gpxFile(dateIso), DayJson.toGpx(record))
    }

    fun ensureGpxForAllDays() = lock.withLock {
        daysDir().listFiles()
            ?.filter { it.isFile && it.name.endsWith(".json") }
            ?.forEach { file ->
                val iso = file.name.removeSuffix(".json")
                val record = loadUnlocked(iso) ?: return@forEach
                DayJson.writeAtomic(gpxFile(iso), DayJson.toGpx(record))
            }
    }

    companion object {
        /** Process-wide — SampleService / UI / backup must serialize on one lock. */
        private val lock = ReentrantLock()
        private var memoryToday: DayRecord? = null

        /** Matches the editor UI's own EditText/TextInputLayout counter cap. */
        const val NOTE_MAX_LENGTH = 500

        // Not-yet-confirmed "away from the last point" candidate fix, for
        // the date it belongs to — see MovementConfirmation. In-memory only
        // (like memoryToday above): losing this on process death just means
        // the next sample starts a fresh candidate, one sample slower to
        // confirm, never anything worse.
        private var pendingDateIso: String? = null
        private var pendingCandidateLat: Double? = null
        private var pendingCandidateLon: Double? = null
    }
}
