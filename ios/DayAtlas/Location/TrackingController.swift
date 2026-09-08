import Foundation

/// Mirrors app/src/main/java/com/dayatlas/app/location/TrackingController.kt.
enum TrackingController {
    /// Called from AppDelegate.didFinishLaunchingWithOptions on every launch,
    /// including a silent relaunch that iOS performs to deliver a
    /// significant-location-change event (which is what can happen right
    /// after the device reboots, if the user already granted "Always" and
    /// hasn't force-quit the app since).
    static func onAppLaunch() {
        let prefs = AppPrefs.shared
        if prefs.dailyMode {
            prefs.trackingEnabled = true
        }
        if prefs.trackingEnabled {
            LocationTracker.shared.resume()
        }
    }

    static func start(sampleSoon: Bool = true) {
        AppPrefs.shared.trackingEnabled = true
        LocationTracker.shared.resume()
        if sampleSoon {
            LocationTracker.shared.sampleNow()
        }
    }

    static func stop() {
        AppPrefs.shared.trackingEnabled = false
        LocationTracker.shared.pause()
    }

    static func setDailyMode(_ enabled: Bool) {
        AppPrefs.shared.dailyMode = enabled
        if enabled {
            start(sampleSoon: true)
        } else {
            stop()
        }
    }
}
