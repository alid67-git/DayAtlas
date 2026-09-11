package com.dayatlas.app.data

import android.content.Context
import android.location.Location
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class DayStore(context: Context) {
    private val appContext = context.applicationContext
    private val lock = ReentrantLock()

    fun daysDir(): File = File(appContext.filesDir, "days").also { it.mkdirs() }

    fun jsonFile(dateIso: String): File = File(daysDir(), "$dateIso.json")

    fun gpxFile(dateIso: String): File = File(daysDir(), "$dateIso.gpx")

    fun load(dateIso: String): DayRecord? = lock.withLock {
        val file = jsonFile(dateIso)
        if (!file.exists()) return@withLock null
        runCatching { DayJson.fromJson(file.readText()) }.getOrNull()
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

    fun append(location: Location, zoneId: ZoneId = ZoneId.systemDefault()): DayRecord = lock.withLock {
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
            return@withLock existing
        }
        val last = existing.points.lastOrNull()
        if (last != null) {
            val drift = Geo.haversineMeters(last.lat, last.lon, point.lat, point.lon)
            if (drift < Geo.PATH_NOISE_FLOOR_M) {
                // Still at home / same spot — refresh the last fix in place so
                // "Son nokta" advances without growing spaghetti or distance.
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
                return@withLock updated
            }
        }
        val points = existing.points + point
        val updated = existing.copy(
            points = points,
            distanceMeters = Geo.pathLengthMeters(points),
        )
        persistUnlocked(updated)
        updated
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
            return@withLock DayRecord.empty(dateIso, existing.title)
        }
        val updated = existing.copy(
            points = points,
            distanceMeters = Geo.pathLengthMeters(points),
        )
        persistUnlocked(updated)
        updated
    }

    private fun loadUnlocked(dateIso: String): DayRecord? {
        val file = jsonFile(dateIso)
        if (!file.exists()) return null
        return runCatching { DayJson.fromJson(file.readText()) }.getOrNull()
    }

    private fun persistUnlocked(record: DayRecord) {
        daysDir()
        DayJson.writeAtomic(jsonFile(record.date), DayJson.toJson(record))
        DayJson.writeAtomic(gpxFile(record.date), DayJson.toGpx(record))
    }
}
