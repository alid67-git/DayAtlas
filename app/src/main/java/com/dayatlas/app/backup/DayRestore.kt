package com.dayatlas.app.backup

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.dayatlas.app.data.DayJson
import com.dayatlas.app.data.DayRecord
import com.dayatlas.app.data.DayStore
import com.dayatlas.app.data.DayTitle
import com.dayatlas.app.data.SpeedStats
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

/**
 * Builds restore candidates from GPX (Drive folder or picked files) and
 * applies approved days via [DayStore.replaceDay].
 */
object DayRestore {
    private val DATE_FILE = Regex("""^(\d{4}-\d{2}-\d{2})\.gpx$""", RegexOption.IGNORE_CASE)

    data class Snapshot(
        val dateIso: String,
        val distanceMeters: Double,
        val pointCount: Int,
        val checkCount: Int,
        val activeMillis: Long,
        val notePreview: String?,
        val photoCount: Int,
    )

    data class Candidate(
        val dateIso: String,
        val incoming: DayRecord,
        val current: DayRecord?,
    ) {
        val hasLocalTrack: Boolean
            get() = current != null && current.points.isNotEmpty()
    }

    fun snapshot(record: DayRecord): Snapshot {
        val speed = SpeedStats.compute(record.points)
        val note = record.note?.trim()?.takeIf { it.isNotEmpty() }?.let { text ->
            if (text.length <= 80) text else text.take(77) + "…"
        }
        return Snapshot(
            dateIso = record.date,
            distanceMeters = record.distanceMeters,
            pointCount = record.points.size,
            checkCount = record.checkCount,
            activeMillis = speed.activeMillis,
            notePreview = note,
            photoCount = record.photos.size,
        )
    }

    fun dateIsoFromFileName(name: String?): String? {
        if (name.isNullOrBlank()) return null
        val base = name.substringAfterLast('/').substringAfterLast('\\')
        return DATE_FILE.matchEntire(base)?.groupValues?.get(1)
    }

    fun dateIsoFromRecord(record: DayRecord): String? {
        val first = record.points.minByOrNull { it.timeMillis } ?: return null
        return DayTitle.iso(
            Instant.ofEpochMilli(first.timeMillis)
                .atZone(ZoneId.systemDefault())
                .toLocalDate(),
        )
    }

    fun parseGpx(dateIso: String, raw: String): DayRecord? = DayJson.fromGpx(dateIso, raw)

    fun displayName(context: Context, uri: Uri): String? {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) return cursor.getString(idx)
                }
            }
        return uri.lastPathSegment
    }

    fun candidateFromGpxText(
        store: DayStore,
        preferredDateIso: String?,
        raw: String,
    ): Candidate? {
        val probeDate = preferredDateIso
            ?: run {
                // Parse with a placeholder date, then re-key from first point.
                val probe = parseGpx("1970-01-01", raw) ?: return null
                dateIsoFromRecord(probe)
            }
            ?: return null
        val incoming = parseGpx(probeDate, raw) ?: return null
        if (incoming.points.isEmpty() && incoming.note.isNullOrEmpty()) return null
        val current = store.load(probeDate)
        return Candidate(dateIso = probeDate, incoming = incoming, current = current)
    }

    fun candidatesFromUris(context: Context, uris: List<Uri>): List<Candidate> {
        val store = DayStore(context)
        val byDate = linkedMapOf<String, Candidate>()
        for (uri in uris) {
            val name = displayName(context, uri)
            val preferred = dateIsoFromFileName(name)
            val raw = context.contentResolver.openInputStream(uri)
                ?.use { String(it.readBytes(), Charsets.UTF_8) }
                ?: continue
            val candidate = candidateFromGpxText(store, preferred, raw) ?: continue
            byDate[candidate.dateIso] = candidate
        }
        return byDate.values.sortedBy { it.dateIso }
    }

    fun apply(context: Context, approved: List<DayRecord>): Int {
        if (approved.isEmpty()) return 0
        val store = DayStore(context)
        var count = 0
        for (record in approved) {
            store.replaceDay(record)
            count++
        }
        return count
    }

    fun formatSnapshotLine(context: Context, snap: Snapshot): String {
        val distance = if (snap.distanceMeters <= 0) {
            "—"
        } else {
            DayTitle.formatDistance(snap.distanceMeters)
        }
        val duration = if (snap.activeMillis <= 0) {
            "—"
        } else {
            DayTitle.formatDuration(snap.activeMillis)
        }
        val base = context.getString(
            com.dayatlas.app.R.string.restore_day_stats,
            distance,
            snap.pointCount,
            duration,
        )
        val note = snap.notePreview?.let {
            "\n" + context.getString(com.dayatlas.app.R.string.restore_day_note, it)
        }.orEmpty()
        val photos = if (snap.photoCount > 0) {
            "\n" + context.getString(com.dayatlas.app.R.string.restore_day_photos, snap.photoCount)
        } else {
            ""
        }
        return base + note + photos
    }

    fun formatCompareMessage(context: Context, candidate: Candidate): String {
        val dateLabel = runCatching {
            DayTitle.format(LocalDate.parse(candidate.dateIso), Locale.getDefault())
        }.getOrElse { candidate.dateIso }
        val incoming = formatSnapshotLine(context, snapshot(candidate.incoming))
        return if (!candidate.hasLocalTrack) {
            context.getString(com.dayatlas.app.R.string.restore_day_new_message, dateLabel, incoming)
        } else {
            val current = formatSnapshotLine(context, snapshot(candidate.current!!))
            context.getString(
                com.dayatlas.app.R.string.restore_day_conflict_message,
                dateLabel,
                current,
                incoming,
            )
        }
    }
}
