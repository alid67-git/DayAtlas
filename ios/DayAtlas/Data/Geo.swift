import Foundation

/// Matches app/src/main/java/com/dayatlas/app/data/Geo.kt so both platforms
/// compute the same distance for the same points.
enum Geo {
    private static let earthRadiusM = 6_371_000.0

    static func haversineMeters(_ lat1: Double, _ lon1: Double, _ lat2: Double, _ lon2: Double) -> Double {
        let dLat = (lat2 - lat1) * .pi / 180
        let dLon = (lon2 - lon1) * .pi / 180
        let a = sin(dLat / 2) * sin(dLat / 2) +
            cos(lat1 * .pi / 180) * cos(lat2 * .pi / 180) *
            sin(dLon / 2) * sin(dLon / 2)
        let c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return earthRadiusM * c
    }

    static func pathLengthMeters(_ points: [TrackPoint]) -> Double {
        guard points.count >= 2 else { return 0 }
        var sum = 0.0
        for i in 1..<points.count {
            let prev = points[i - 1]
            let cur = points[i]
            sum += haversineMeters(prev.lat, prev.lon, cur.lat, cur.lon)
        }
        return sum
    }
}
