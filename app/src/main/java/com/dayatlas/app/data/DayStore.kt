package com.dayatlas.app.data

import android.content.Context
import android.location.Location
import java.io.File
import java.time.Instant
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
        val points = existing.points + point
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
