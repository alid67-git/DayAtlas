import Foundation

struct DayRecord: Equatable {
    let date: String
    let title: String
    let points: [TrackPoint]
    let distanceMeters: Double

    static func empty(dateIso: String, title: String) -> DayRecord {
        DayRecord(date: dateIso, title: title, points: [], distanceMeters: 0)
    }
}
