import Foundation

struct TrackPoint: Equatable {
    let timeMillis: Int64
    let lat: Double
    let lon: Double
    let acc: Double?
}
