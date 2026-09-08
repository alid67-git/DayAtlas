import CoreLocation
import Foundation

/// Mirrors app/src/main/java/com/dayatlas/app/data/DayStore.kt: one JSON +
/// one GPX file per local calendar day under Documents/days/yyyy-MM-dd.*.
final class DayStore {
    static let shared = DayStore()

    private let lock = NSLock()
    private let fileManager = FileManager.default

    var daysDirectory: URL {
        let docs = fileManager.urls(for: .documentDirectory, in: .userDomainMask)[0]
        let dir = docs.appendingPathComponent("days", isDirectory: true)
        try? fileManager.createDirectory(at: dir, withIntermediateDirectories: true)
        return dir
    }

    func jsonURL(for dateIso: String) -> URL {
        daysDirectory.appendingPathComponent("\(dateIso).json")
    }

    func gpxURL(for dateIso: String) -> URL {
        daysDirectory.appendingPathComponent("\(dateIso).gpx")
    }

    func load(_ dateIso: String) -> DayRecord? {
        lock.lock()
        defer { lock.unlock() }
        return loadUnlocked(dateIso)
    }

    func loadToday(timeZone: TimeZone = .current) -> DayRecord {
        let day = LocalDay.today(timeZone: timeZone)
        return load(day.iso) ?? DayRecord.empty(dateIso: day.iso, title: DayTitle.format(day))
    }

    @discardableResult
    func append(location: CLLocation, timeZone: TimeZone = .current) -> DayRecord {
        lock.lock()
        defer { lock.unlock() }
        let timeMillis = Int64((location.timestamp.timeIntervalSince1970 * 1000).rounded())
        let day = LocalDay.from(date: location.timestamp, timeZone: timeZone)
        let existing = loadUnlocked(day.iso) ?? DayRecord.empty(dateIso: day.iso, title: DayTitle.format(day))
        let point = TrackPoint(
            timeMillis: timeMillis,
            lat: location.coordinate.latitude,
            lon: location.coordinate.longitude,
            acc: location.horizontalAccuracy >= 0 ? location.horizontalAccuracy : nil,
        )
        let points = existing.points + [point]
        let updated = DayRecord(
            date: existing.date,
            title: existing.title,
            points: points,
            distanceMeters: Geo.pathLengthMeters(points),
        )
        persistUnlocked(updated)
        return updated
    }

    private func loadUnlocked(_ dateIso: String) -> DayRecord? {
        guard let data = try? Data(contentsOf: jsonURL(for: dateIso)) else { return nil }
        return try? DayJson.fromJson(data)
    }

    private func persistUnlocked(_ record: DayRecord) {
        _ = daysDirectory
        DayJson.writeAtomic(DayJson.toJson(record), to: jsonURL(for: record.date))
        DayJson.writeAtomic(DayJson.toGpx(record), to: gpxURL(for: record.date))
    }
}
