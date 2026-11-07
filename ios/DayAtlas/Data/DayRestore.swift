import Foundation

/// Builds restore candidates from Android Drive backups (`.gpx` / `.json`)
/// and writes approved days into [DayStore] — same role as Android
/// `com.dayatlas.app.backup.DayRestore`.
enum DayRestore {
    private static let dateFile = try! NSRegularExpression(
        pattern: #"^(\d{4}-\d{2}-\d{2})\.(gpx|json)$"#,
        options: [.caseInsensitive],
    )

    struct Candidate: Identifiable {
        var id: String { dateIso }
        let dateIso: String
        let incoming: DayRecord
        let current: DayRecord?

        var hasLocalTrack: Bool {
            guard let current else { return false }
            return !current.points.isEmpty
        }
    }

    static func dateIsoFromFileName(_ name: String?) -> String? {
        guard let name, !name.isEmpty else { return nil }
        let base = (name as NSString).lastPathComponent
        let range = NSRange(base.startIndex..<base.endIndex, in: base)
        guard let match = dateFile.firstMatch(in: base, options: [], range: range),
              match.numberOfRanges >= 2,
              let dateRange = Range(match.range(at: 1), in: base) else {
            return nil
        }
        return String(base[dateRange])
    }

    static func dateIsoFromRecord(_ record: DayRecord) -> String? {
        guard let first = record.points.min(by: { $0.timeMillis < $1.timeMillis }) else {
            return nil
        }
        return LocalDay.from(
            date: Date(timeIntervalSince1970: Double(first.timeMillis) / 1000.0),
        ).iso
    }

    static func candidate(fromFileURL url: URL) -> Candidate? {
        let accessed = url.startAccessingSecurityScopedResource()
        defer {
            if accessed { url.stopAccessingSecurityScopedResource() }
        }
        guard let data = try? Data(contentsOf: url),
              let raw = String(data: data, encoding: .utf8) else {
            return nil
        }
        let name = url.lastPathComponent
        let preferred = dateIsoFromFileName(name)
        let ext = url.pathExtension.lowercased()
        if ext == "json" || raw.trimmingCharacters(in: .whitespacesAndNewlines).hasPrefix("{") {
            return candidateFromJson(preferred: preferred, data: data)
        }
        return candidateFromGpx(preferred: preferred, raw: raw)
    }

    static func candidates(fromFileURLs urls: [URL]) -> [Candidate] {
        var byDate: [String: Candidate] = [:]
        for url in urls {
            guard let candidate = candidate(fromFileURL: url) else { continue }
            byDate[candidate.dateIso] = candidate
        }
        return byDate.values.sorted { $0.dateIso < $1.dateIso }
    }

    @discardableResult
    static func apply(_ records: [DayRecord]) -> Int {
        var count = 0
        for record in records where !record.points.isEmpty || !record.date.isEmpty {
            DayStore.shared.replaceDay(record)
            count += 1
        }
        return count
    }

    private static func candidateFromJson(preferred: String?, data: Data) -> Candidate? {
        guard let parsed = try? DayJson.fromJson(data) else { return nil }
        let dateIso = preferred
            ?? (parsed.date.range(of: #"^\d{4}-\d{2}-\d{2}$"#, options: .regularExpression) != nil
                ? parsed.date
                : nil)
            ?? dateIsoFromRecord(parsed)
        guard let dateIso else { return nil }
        let distance = Geo.pathLengthMeters(parsed.points)
        let incoming = DayRecord(
            date: dateIso,
            title: parsed.title.isEmpty ? dateIso : parsed.title,
            points: parsed.points,
            distanceMeters: distance,
        )
        if incoming.points.isEmpty { return nil }
        return Candidate(dateIso: dateIso, incoming: incoming, current: DayStore.shared.load(dateIso))
    }

    private static func candidateFromGpx(preferred: String?, raw: String) -> Candidate? {
        let probeDate = preferred
            ?? {
                guard let probe = DayJson.fromGpx(dateIso: "1970-01-01", raw: raw) else { return nil }
                return dateIsoFromRecord(probe)
            }()
        guard let probeDate,
              let incoming = DayJson.fromGpx(dateIso: probeDate, raw: raw),
              !incoming.points.isEmpty else {
            return nil
        }
        return Candidate(dateIso: probeDate, incoming: incoming, current: DayStore.shared.load(probeDate))
    }
}
