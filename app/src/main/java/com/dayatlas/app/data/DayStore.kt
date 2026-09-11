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
    /** False when a teleport jump was rejected and nothing was written. */
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
            val drift = Geo.haversineMeters(last.lat, last.lon, point.lat, point.lon)
            if (drift < Geo.SAME_PLACE_RADIUS_M) {
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
        }
        val points = existing.points + point
        val updated = existing.copy(
            points = points,
            distanceMeters = Geo.pathLengthMeters(points),
        )
        persistUnlocked(updated)
        AppendResult(updated, geometryChanged = true, wrote = true)
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
            jsonFile(dateIso).delete()
            gpxFile(dateIso).delete()
            if (memoryToday?.date == dateIso) memoryToday = null
            return@withLock DayRecord.empty(dateIso, existing.title)
        }
        val updated = existing.copy(
            points = points,
            distanceMeters = Geo.pathLengthMeters(points),
        )
        persistUnlocked(updated, writeGpx = true)
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
    }
}
