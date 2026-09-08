import XCTest
@testable import DayAtlas

/// Mirrors app/src/test/java/com/dayatlas/app/data/DayLogicTest.kt so the
/// same expectations are checked on both platforms.
final class GeoTests: XCTestCase {
    func testNearbyPointsHaveExpectedDistance() {
        let meters = Geo.haversineMeters(41.0, 29.0, 41.0, 29.001)
        XCTAssertTrue((70.0...110.0).contains(meters), "expected ~85m, was \(meters)")
    }

    func testPathLengthSumsSegments() {
        let points = [
            TrackPoint(timeMillis: 1, lat: 41.0, lon: 29.0, acc: nil),
            TrackPoint(timeMillis: 2, lat: 41.0, lon: 29.001, acc: nil),
            TrackPoint(timeMillis: 3, lat: 41.0, lon: 29.002, acc: nil),
        ]
        let len = Geo.pathLengthMeters(points)
        XCTAssertTrue((140.0...220.0).contains(len))
    }
}

final class DayTitleTests: XCTestCase {
    func testTurkishTitleUsesLocalCalendarDay() {
        let day = LocalDay(year: 2026, month: 8, day: 28)
        XCTAssertEqual(DayTitle.format(day), "Günlük 28 Ağu 2026")
    }

    func testIsoIsYearMonthDay() {
        let day = LocalDay(year: 2026, month: 8, day: 28)
        XCTAssertEqual(day.iso, "2026-08-28")
    }
}

final class DayJsonTests: XCTestCase {
    func testRoundTripPreservesPoints() throws {
        let record = DayRecord(
            date: "2026-08-28",
            title: "Günlük 28 Ağu 2026",
            points: [TrackPoint(timeMillis: 1_000, lat: 41.01, lon: 29.02, acc: 12.5)],
            distanceMeters: 0,
        )
        let parsed = try DayJson.fromJson(Data(DayJson.toJson(record).utf8))
        XCTAssertEqual(record.date, parsed.date)
        XCTAssertEqual(record.title, parsed.title)
        XCTAssertEqual(1, parsed.points.count)
        XCTAssertEqual(41.01, parsed.points[0].lat, accuracy: 1e-6)
        XCTAssertTrue(DayJson.toGpx(record).contains("<trkpt"))
    }
}
