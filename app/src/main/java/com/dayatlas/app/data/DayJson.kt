package com.dayatlas.app.data

import org.json.JSONArray
import org.json.JSONObject
import org.xml.sax.InputSource
import java.io.File
import java.io.StringReader
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import javax.xml.parsers.DocumentBuilderFactory

object DayJson {
    private val GPX_TIME: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'").withZone(ZoneOffset.UTC)

    fun toJson(record: DayRecord): String {
        val root = JSONObject()
            .put("version", 1)
            .put("date", record.date)
            .put("title", record.title)
            .put("distanceMeters", record.distanceMeters)
            .put("gpsCheckCount", record.checkCount)
        if (!record.note.isNullOrEmpty()) {
            root.put("note", record.note)
        }
        if (record.photos.isNotEmpty()) {
            root.put("photos", JSONArray(record.photos))
        }
        if (record.dwellNotes.isNotEmpty()) {
            val notes = JSONObject()
            record.dwellNotes.forEach { (key, value) ->
                notes.put(key.toString(), value)
            }
            root.put("dwellNotes", notes)
        }
        val points = JSONArray()
        record.points.forEach { p ->
            val o = JSONObject()
                .put("t", p.timeMillis)
                .put("lat", p.lat)
                .put("lon", p.lon)
            if (p.accuracyMeters != null) {
                o.put("acc", p.accuracyMeters.toDouble())
            }
            points.put(o)
        }
        root.put("points", points)
        // Compact — day files are rewritten on every GPS sample.
        return root.toString()
    }

    fun fromJson(raw: String): DayRecord {
        val root = JSONObject(raw)
        val date = root.getString("date")
        val title = root.optString("title", date)
        val note = root.optString("note", "").ifEmpty { null }
        val photosArr = root.optJSONArray("photos")
        val photos = if (photosArr != null) {
            (0 until photosArr.length()).map { photosArr.getString(it) }
        } else {
            emptyList()
        }
        val dwellNotesObj = root.optJSONObject("dwellNotes")
        val dwellNotes = if (dwellNotesObj != null) {
            buildMap {
                dwellNotesObj.keys().forEach { key ->
                    val millis = key.toLongOrNull() ?: return@forEach
                    val text = dwellNotesObj.optString(key, "").trim()
                    if (text.isNotEmpty()) put(millis, text)
                }
            }
        } else {
            emptyMap()
        }
        val arr = root.optJSONArray("points") ?: JSONArray()
        val points = ArrayList<TrackPoint>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            points.add(
                TrackPoint(
                    timeMillis = o.getLong("t"),
                    lat = o.getDouble("lat"),
                    lon = o.getDouble("lon"),
                    accuracyMeters = if (o.has("acc")) o.getDouble("acc").toFloat() else null,
                ),
            )
        }
        // Always recompute from points so older inflated distances (pre-filter
        // rules / home GPS wander) heal on the next load.
        val distance = Geo.pathLengthMeters(points)
        val gpsCheckCount = if (root.has("gpsCheckCount")) {
            root.getInt("gpsCheckCount").coerceAtLeast(points.size)
        } else {
            points.size
        }
        return DayRecord(
            date = date,
            title = title,
            points = points,
            distanceMeters = distance,
            note = note,
            photos = photos,
            gpsCheckCount = gpsCheckCount,
            dwellNotes = dwellNotes,
        )
    }

    fun toGpx(record: DayRecord): String = toGpx(listOf(record), exportName = record.title)

    /**
     * GPX 1.1 export. **One `<trk>` per calendar day** (empty days skipped) so
     * importers that group by track/day do not collapse a range onto a single
     * day. Each `<trkpt>` carries a UTC `<time>` from the real sample epoch.
     *
     * [exportName] is used as the sole track name when there is only one day;
     * for multi-day ranges each track keeps that day's title (optionally
     * prefixed with [exportName]).
     *
     * A day with a note and/or photos but no points still gets a trackless
     * `<trk>` (just `<name>`/`<desc>`, no `<trkseg>`) — not for the note
     * itself alone, but so that day's `.gpx` file exists at all, which is
     * what [com.dayatlas.app.backup.DriveFolderBackup] scans for to decide
     * which days to back up (including that day's photos). A genuinely
     * empty day (no points, no note, no photos) is still skipped.
     */
    fun toGpx(records: List<DayRecord>, exportName: String? = null): String {
        val withPoints = records.filter {
            it.points.isNotEmpty() ||
                !it.note.isNullOrEmpty() ||
                it.photos.isNotEmpty() ||
                it.dwellNotes.isNotEmpty()
        }
        val sb = StringBuilder()
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        sb.append("<gpx version=\"1.1\" creator=\"DayAtlas\" xmlns=\"http://www.topografix.com/GPX/1/1\">\n")
        withPoints.forEach { record ->
            val name = when {
                withPoints.size == 1 && !exportName.isNullOrBlank() -> exportName
                !exportName.isNullOrBlank() && withPoints.size > 1 ->
                    "$exportName — ${record.title}"
                else -> record.title
            }
            sb.append("  <trk>\n")
            sb.append("    <name>").append(escapeXml(name)).append("</name>\n")
            if (!record.note.isNullOrEmpty()) {
                sb.append("    <desc>").append(escapeXml(record.note)).append("</desc>\n")
            }
            sb.append("    <trkseg>\n")
            record.points.forEach { p ->
                sb.append("      <trkpt lat=\"").append(p.lat).append("\" lon=\"").append(p.lon).append("\">\n")
                sb.append("        <time>").append(formatGpxTime(p.timeMillis)).append("</time>\n")
                if (p.accuracyMeters != null) {
                    sb.append("        <hdop>").append(p.accuracyMeters).append("</hdop>\n")
                }
                sb.append("      </trkpt>\n")
            }
            sb.append("    </trkseg>\n")
            sb.append("  </trk>\n")
        }
        sb.append("</gpx>\n")
        return sb.toString()
    }

    fun formatGpxTime(timeMillis: Long): String =
        GPX_TIME.format(Instant.ofEpochMilli(timeMillis))

    /**
     * Parses a GPX file this app wrote (see [toGpx]) back into a [DayRecord].
     * [dateIso] comes from the backup file's name (`yyyy-MM-dd.gpx`) — GPX
     * has no day-level date field of its own, only a per-point `<time>`.
     * Returns null for content that isn't parseable GPX at all; a track
     * with zero points is still a valid (empty) day.
     */
    fun fromGpx(dateIso: String, raw: String): DayRecord? {
        // javax.xml (not android.util.Xml) so this also runs under plain
        // JVM unit tests, not just on-device.
        val doc = runCatching {
            DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(InputSource(StringReader(raw)))
        }.getOrNull() ?: return null

        val title = doc.getElementsByTagName("name").item(0)?.textContent
        val note = doc.getElementsByTagName("desc").item(0)?.textContent?.ifEmpty { null }

        val trkpts = doc.getElementsByTagName("trkpt")
        val points = ArrayList<TrackPoint>(trkpts.length)
        for (i in 0 until trkpts.length) {
            val el = trkpts.item(i) as? org.w3c.dom.Element ?: continue
            val lat = el.getAttribute("lat").toDoubleOrNull() ?: continue
            val lon = el.getAttribute("lon").toDoubleOrNull() ?: continue
            val timeText = el.getElementsByTagName("time").item(0)?.textContent ?: continue
            val timeMillis = runCatching { Instant.parse(timeText).toEpochMilli() }.getOrNull() ?: continue
            val accuracy = el.getElementsByTagName("hdop").item(0)?.textContent?.toFloatOrNull()
            points.add(TrackPoint(timeMillis, lat, lon, accuracy))
        }
        val sorted = points.sortedBy { it.timeMillis }
        return DayRecord(
            date = dateIso,
            title = title ?: dateIso,
            points = sorted,
            distanceMeters = Geo.pathLengthMeters(sorted),
            note = note,
        )
    }

    fun writeAtomic(file: File, content: String) {
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(content)
        if (file.exists()) file.delete()
        if (!tmp.renameTo(file)) {
            file.writeText(content)
            tmp.delete()
        }
    }

    private fun escapeXml(value: String): String =
        value.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
}
