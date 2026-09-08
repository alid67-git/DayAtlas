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
