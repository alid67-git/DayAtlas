import Foundation

/// A local calendar day, independent of time zone conversion ambiguity —
/// mirrors Android's `java.time.LocalDate` usage in DayTitle.kt/DayStore.kt.
struct LocalDay: Equatable {
    let year: Int
    let month: Int
    let day: Int

    static func today(timeZone: TimeZone = .current) -> LocalDay {
        from(date: Date(), timeZone: timeZone)
    }

    static func from(date: Date, timeZone: TimeZone = .current) -> LocalDay {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = timeZone
        let comps = calendar.dateComponents([.year, .month, .day], from: date)
        return LocalDay(year: comps.year ?? 1970, month: comps.month ?? 1, day: comps.day ?? 1)
    }

    var iso: String { String(format: "%04d-%02d-%02d", year, month, day) }
}

enum DayTitle {
    private static let monthsTr = [
        "Oca", "Şub", "Mar", "Nis", "May", "Haz",
        "Tem", "Ağu", "Eyl", "Eki", "Kas", "Ara",
    ]

    static func format(_ day: LocalDay) -> String {
        "Günlük \(day.day) \(monthsTr[day.month - 1]) \(day.year)"
    }

    static func formatDistance(_ meters: Double) -> String {
        if meters < 1000 {
            return "\(Int(meters)) m"
        }
        return String(format: "%.1f km", meters / 1000.0)
    }
}
