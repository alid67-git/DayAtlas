package com.dayatlas.app.export

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.dayatlas.app.R
import com.dayatlas.app.data.DayJson
import com.dayatlas.app.data.DayRecord
import com.dayatlas.app.data.DayStore
import com.dayatlas.app.data.DayTitle
import java.io.File
import java.time.LocalDate

object GpxExporter {
    fun defaultFileName(from: LocalDate, to: LocalDate): String =
        if (from == to) {
            "DayAtlas-${DayTitle.iso(from)}"
        } else {
            "DayAtlas-${DayTitle.iso(from)}_${DayTitle.iso(to)}"
        }

    fun defaultTrackName(from: LocalDate, to: LocalDate): String =
        if (from == to) {
            DayTitle.format(from)
        } else {
            "${DayTitle.format(from)} – ${DayTitle.format(to)}"
        }

    /** Stem only (no extension). Strips a trailing extension so share always ends in `.gpx`. */
    fun sanitizeFileName(raw: String): String {
        val trimmed = raw.trim().ifEmpty { "DayAtlas" }
        val withoutExt = trimmed.replace(Regex("""\.[A-Za-z0-9]{1,8}$"""), "")
        val cleaned = withoutExt
            .replace(Regex("""[\\/:*?"<>|]"""), "-")
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(80)
        return cleaned.ifEmpty { "DayAtlas" }
    }

    /** Final on-disk / share display name, always ending in `.gpx`. */
    fun withGpxExtension(stemOrName: String): String {
        val stem = sanitizeFileName(stemOrName)
        return "$stem.gpx"
    }

    /**
     * Writes a shareable GPX into cache and returns a chooser intent, or null
     * if there are no points in the range.
     */
    fun buildShareIntent(
        context: Context,
        store: DayStore,
        from: LocalDate,
        to: LocalDate,
        fileNameStem: String,
    ): Intent? {
        val records = store.loadRange(from, to)
        if (records.isEmpty()) return null
        return buildShareIntent(context, records, fileNameStem)
    }

    fun buildShareIntent(
        context: Context,
        records: List<DayRecord>,
        fileNameStem: String,
    ): Intent? {
        if (records.none { it.points.isNotEmpty() }) return null
        val app = context.applicationContext
        val dir = File(app.cacheDir, "exports").also { it.mkdirs() }
        val fileName = withGpxExtension(fileNameStem)
        val file = File(dir, fileName)
        val exportLabel = sanitizeFileName(fileNameStem)
        DayJson.writeAtomic(file, DayJson.toGpx(records, exportName = exportLabel))
        val uri = FileProvider.getUriForFile(
            app,
            "${app.packageName}.fileprovider",
            file,
        )
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "application/gpx+xml"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, fileName)
            putExtra(Intent.EXTRA_TITLE, fileName)
            clipData = android.content.ClipData.newUri(app.contentResolver, fileName, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(send, app.getString(R.string.export_share_title)).apply {
            // Chooser should also grant read to the target app.
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
}
