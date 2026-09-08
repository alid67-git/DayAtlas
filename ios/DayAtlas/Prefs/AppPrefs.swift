import Foundation

/// Mirrors app/src/main/java/com/dayatlas/app/prefs/AppPrefs.kt.
final class AppPrefs {
    static let shared = AppPrefs()

    static let allowedIntervals = [3, 4, 5]
    static let defaultIntervalMinutes = 5

    private let defaults = UserDefaults.standard

    private enum Keys {
        static let dailyMode = "daily_mode"
        static let tracking = "tracking_enabled"
        static let interval = "interval_minutes"
        static let lastSeenBuildNote = "last_seen_build_note_version"
    }

    var dailyMode: Bool {
        get { defaults.bool(forKey: Keys.dailyMode) }
        set { defaults.set(newValue, forKey: Keys.dailyMode) }
    }

    var trackingEnabled: Bool {
        get { defaults.bool(forKey: Keys.tracking) }
        set { defaults.set(newValue, forKey: Keys.tracking) }
    }

    var intervalMinutes: Int {
        get {
            let raw = defaults.integer(forKey: Keys.interval)
            return Self.allowedIntervals.contains(raw) ? raw : Self.defaultIntervalMinutes
        }
        set {
            let clamped = Self.allowedIntervals.contains(newValue) ? newValue : Self.defaultIntervalMinutes
            defaults.set(clamped, forKey: Keys.interval)
        }
    }

    var lastSeenBuildNoteVersion: String? {
        get { defaults.string(forKey: Keys.lastSeenBuildNote) }
        set { defaults.set(newValue, forKey: Keys.lastSeenBuildNote) }
    }
}
