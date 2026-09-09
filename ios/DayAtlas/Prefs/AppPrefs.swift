import Foundation

/// Mirrors app/src/main/java/com/dayatlas/app/prefs/AppPrefs.kt.
final class AppPrefs {
    static let shared = AppPrefs()

    /// Foreground timer only (seconds). Background uses significant-location-change.
    static let allowedIntervalSeconds = [30, 60, 180, 300]
    static let defaultIntervalSeconds = 60

    private let defaults = UserDefaults.standard

    private enum Keys {
        static let dailyMode = "daily_mode"
        static let tracking = "tracking_enabled"
        static let intervalSeconds = "interval_seconds"
        static let intervalMinutesLegacy = "interval_minutes"
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

    var intervalSeconds: Int {
        get {
            if defaults.object(forKey: Keys.intervalSeconds) != nil {
                let raw = defaults.integer(forKey: Keys.intervalSeconds)
                return Self.allowedIntervalSeconds.contains(raw) ? raw : Self.defaultIntervalSeconds
            }
            // Migrate old 3/4/5 minute prefs.
            let legacy = defaults.integer(forKey: Keys.intervalMinutesLegacy)
            let migrated: Int
            switch legacy {
            case 3: migrated = 180
            case 4: migrated = 180
            case 5: migrated = 300
            default: migrated = Self.defaultIntervalSeconds
            }
            defaults.set(migrated, forKey: Keys.intervalSeconds)
            defaults.removeObject(forKey: Keys.intervalMinutesLegacy)
            return migrated
        }
        set {
            let clamped = Self.allowedIntervalSeconds.contains(newValue)
                ? newValue : Self.defaultIntervalSeconds
            defaults.set(clamped, forKey: Keys.intervalSeconds)
            defaults.removeObject(forKey: Keys.intervalMinutesLegacy)
        }
    }

    var lastSeenBuildNoteVersion: String? {
        get { defaults.string(forKey: Keys.lastSeenBuildNote) }
        set { defaults.set(newValue, forKey: Keys.lastSeenBuildNote) }
    }
}
