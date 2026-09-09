package com.dayatlas.app.data

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.Instant

object DayJson {
    fun toJson(record: DayRecord): String {
        val root = JSONObject()
            .put("version", 1)
            .put("date", record.date)
            .put("title", record.title)
            .put("distanceMeters", record.distanceMeters)
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
        return root.toString(2)
    }

    fun fromJson(raw: String): DayRecord {
        val root = JSONObject(raw)
        val date = root.getString("date")
        val title = root.optString("title", date)
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
        val distance = if (root.has("distanceMeters")) {
            root.getDouble("distanceMeters")
        } else {
            Geo.pathLengthMeters(points)
        }
        return DayRecord(date = date, title = title, points = points, distanceMeters = distance)
    }

    fun toGpx(record: DayRecord): String = toGpx(record.title, listOf(record))

    /**
     * GPX 1.1 with one track named [trackName] and one `<trkseg>` per day that
     * has points (empty days skipped). Used for both single-day rewrite and
     * user-triggered range export.
     */
    fun toGpx(trackName: String, records: List<DayRecord>): String {
        val sb = StringBuilder()
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        sb.append("<gpx version=\"1.1\" creator=\"DayAtlas\" xmlns=\"http://www.topografix.com/GPX/1/1\">\n")
        sb.append("  <trk>\n")
        sb.append("    <name>").append(escapeXml(trackName)).append("</name>\n")
        records.forEach { record ->
            if (record.points.isEmpty()) return@forEach
            sb.append("    <trkseg>\n")
            record.points.forEach { p ->
                sb.append("      <trkpt lat=\"").append(p.lat).append("\" lon=\"").append(p.lon).append("\">\n")
                sb.append("        <time>").append(Instant.ofEpochMilli(p.timeMillis)).append("</time>\n")
                if (p.accuracyMeters != null) {
                    sb.append("        <hdop>").append(p.accuracyMeters).append("</hdop>\n")
                }
                sb.append("      </trkpt>\n")
            }
            sb.append("    </trkseg>\n")
        }
        sb.append("  </trk>\n")
        sb.append("</gpx>\n")
        return sb.toString()
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
