import Foundation

enum DayJsonError: Error {
    case invalid
}

/// JSON/GPX shape matches app/src/main/java/com/dayatlas/app/data/DayJson.kt
/// exactly (same keys, same file layout under files/days/) so a day recorded
/// on one platform reads the same way on the other.
enum DayJson {
    static func toJson(_ record: DayRecord) -> String {
        var pointsArr: [[String: Any]] = []
        pointsArr.reserveCapacity(record.points.count)
        for p in record.points {
            var o: [String: Any] = ["t": p.timeMillis, "lat": p.lat, "lon": p.lon]
            if let acc = p.acc {
                o["acc"] = acc
            }
            pointsArr.append(o)
        }
        let root: [String: Any] = [
            "version": 1,
            "date": record.date,
            "title": record.title,
            "distanceMeters": record.distanceMeters,
            "points": pointsArr,
        ]
        guard let data = try? JSONSerialization.data(withJSONObject: root, options: [.prettyPrinted]),
              let text = String(data: data, encoding: .utf8) else {
            return "{}"
        }
        return text
    }

    static func fromJson(_ data: Data) throws -> DayRecord {
        guard let root = try JSONSerialization.jsonObject(with: data) as? [String: Any],
              let date = root["date"] as? String else {
            throw DayJsonError.invalid
        }
        let title = (root["title"] as? String) ?? date
        let pointsArr = (root["points"] as? [[String: Any]]) ?? []
        let points: [TrackPoint] = pointsArr.compactMap { o in
            guard let t = (o["t"] as? NSNumber)?.int64Value,
                  let lat = (o["lat"] as? NSNumber)?.doubleValue,
                  let lon = (o["lon"] as? NSNumber)?.doubleValue else {
                return nil
            }
            let acc = (o["acc"] as? NSNumber)?.doubleValue
            return TrackPoint(timeMillis: t, lat: lat, lon: lon, acc: acc)
        }
        let distance = (root["distanceMeters"] as? NSNumber)?.doubleValue ?? Geo.pathLengthMeters(points)
        return DayRecord(date: date, title: title, points: points, distanceMeters: distance)
    }

    /// Parses a DayAtlas (or compatible) GPX track back into a day record.
    /// [dateIso] usually comes from the backup file name (`yyyy-MM-dd.gpx`).
    static func fromGpx(dateIso: String, raw: String) -> DayRecord? {
        let trimmed = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        guard trimmed.range(of: "<gpx", options: [.caseInsensitive, .diacriticInsensitive]) != nil else {
            return nil
        }
        let parser = GpxTrackParser()
        guard parser.parse(trimmed) else { return nil }
        let sorted = parser.points.sorted { $0.timeMillis < $1.timeMillis }
        let title = parser.trackName?.trimmingCharacters(in: .whitespacesAndNewlines)
        return DayRecord(
            date: dateIso,
            title: (title?.isEmpty == false) ? title! : dateIso,
            points: sorted,
            distanceMeters: Geo.pathLengthMeters(sorted),
        )
    }

    static func toGpx(_ record: DayRecord) -> String {
        let formatter = ISO8601DateFormatter()
        var s = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
        s += "<gpx version=\"1.1\" creator=\"DayAtlas\" xmlns=\"http://www.topografix.com/GPX/1/1\">\n"
        s += "  <trk>\n"
        s += "    <name>\(escapeXml(record.title))</name>\n"
        s += "    <trkseg>\n"
        for p in record.points {
            let date = Date(timeIntervalSince1970: Double(p.timeMillis) / 1000.0)
            s += "      <trkpt lat=\"\(p.lat)\" lon=\"\(p.lon)\">\n"
            s += "        <time>\(formatter.string(from: date))</time>\n"
            if let acc = p.acc {
                s += "        <hdop>\(acc)</hdop>\n"
            }
            s += "      </trkpt>\n"
        }
        s += "    </trkseg>\n"
        s += "  </trk>\n"
        s += "</gpx>\n"
        return s
    }

    static func writeAtomic(_ content: String, to url: URL) {
        let tmp = url.deletingLastPathComponent().appendingPathComponent(url.lastPathComponent + ".tmp")
        do {
            try content.write(to: tmp, atomically: false, encoding: .utf8)
            if FileManager.default.fileExists(atPath: url.path) {
                try FileManager.default.removeItem(at: url)
            }
            try FileManager.default.moveItem(at: tmp, to: url)
        } catch {
            try? content.write(to: url, atomically: true, encoding: .utf8)
            try? FileManager.default.removeItem(at: tmp)
        }
    }

    private static func escapeXml(_ value: String) -> String {
        value.replacingOccurrences(of: "&", with: "&amp;")
            .replacingOccurrences(of: "<", with: "&lt;")
            .replacingOccurrences(of: ">", with: "&gt;")
            .replacingOccurrences(of: "\"", with: "&quot;")
    }
}

/// Minimal GPX 1.1 reader for DayAtlas exports (`trk` / `trkpt` / `time` / `hdop`).
private final class GpxTrackParser: NSObject, XMLParserDelegate {
    private(set) var points: [TrackPoint] = []
    private(set) var trackName: String?
    private var inTrk = false
    private var inTrkpt = false
    private var inName = false
    private var inTime = false
    private var inHdop = false
    private var pendingLat: Double?
    private var pendingLon: Double?
    private var pendingTime: String?
    private var pendingHdop: String?
    private var textBuffer = ""
    private var ok = true

    func parse(_ raw: String) -> Bool {
        guard let data = raw.data(using: .utf8) else { return false }
        let parser = XMLParser(data: data)
        parser.delegate = self
        return parser.parse() && ok
    }

    func parser(
        _ parser: XMLParser,
        didStartElement elementName: String,
        namespaceURI: String?,
        qualifiedName qName: String?,
        attributes attributeDict: [String: String] = [:]
    ) {
        let name = elementName.lowercased()
        textBuffer = ""
        switch name {
        case "trk":
            inTrk = true
        case "trkpt":
            inTrkpt = true
            pendingLat = Double(attributeDict["lat"] ?? "")
            pendingLon = Double(attributeDict["lon"] ?? "")
            pendingTime = nil
            pendingHdop = nil
        case "name":
            if inTrk && !inTrkpt { inName = true }
        case "time":
            if inTrkpt { inTime = true }
        case "hdop":
            if inTrkpt { inHdop = true }
        default:
            break
        }
    }

    func parser(_ parser: XMLParser, foundCharacters string: String) {
        if inName || inTime || inHdop {
            textBuffer += string
        }
    }

    func parser(
        _ parser: XMLParser,
        didEndElement elementName: String,
        namespaceURI: String?,
        qualifiedName qName: String?
    ) {
        let name = elementName.lowercased()
        switch name {
        case "name":
            if inName {
                let value = textBuffer.trimmingCharacters(in: .whitespacesAndNewlines)
                if trackName == nil, !value.isEmpty { trackName = value }
                inName = false
            }
        case "time":
            if inTime {
                pendingTime = textBuffer.trimmingCharacters(in: .whitespacesAndNewlines)
                inTime = false
            }
        case "hdop":
            if inHdop {
                pendingHdop = textBuffer.trimmingCharacters(in: .whitespacesAndNewlines)
                inHdop = false
            }
        case "trkpt":
            if let lat = pendingLat, let lon = pendingLon,
               let timeText = pendingTime,
               let millis = Self.parseGpxTimeMillis(timeText) {
                let acc = pendingHdop.flatMap(Double.init)
                points.append(TrackPoint(timeMillis: millis, lat: lat, lon: lon, acc: acc))
            }
            inTrkpt = false
        case "trk":
            inTrk = false
        default:
            break
        }
        textBuffer = ""
    }

    func parser(_ parser: XMLParser, parseErrorOccurred parseError: Error) {
        ok = false
    }

    private static let isoFrac: ISO8601DateFormatter = {
        let f = ISO8601DateFormatter()
        f.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        return f
    }()

    private static let isoBasic: ISO8601DateFormatter = {
        let f = ISO8601DateFormatter()
        f.formatOptions = [.withInternetDateTime]
        return f
    }()

    private static func parseGpxTimeMillis(_ text: String) -> Int64? {
        if let d = isoFrac.date(from: text) ?? isoBasic.date(from: text) {
            return Int64((d.timeIntervalSince1970 * 1000.0).rounded())
        }
        return nil
    }
}
