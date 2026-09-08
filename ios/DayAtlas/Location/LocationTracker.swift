import CoreLocation
import Foundation
import UIKit

extension Notification.Name {
    static let dayAtlasPointSaved = Notification.Name("com.dayatlas.app.POINT_SAVED")
}

/// The iOS analog of SampleScheduler.kt + SampleService.kt, but built on
/// CLLocationManager's significant-location-change monitoring instead of
/// AlarmManager — that's the one mechanism Apple lets a backgrounded or
/// killed-and-relaunched app use to keep receiving location events, including
/// right after a reboot. It is distance-triggered (~500 m of movement), not
/// timer-triggered, so it does not reproduce Android's fixed 3/4/5-minute
/// cadence in the background. While the app is in the foreground we layer a
/// plain Timer on top with `requestLocation()` to approximate that cadence,
/// since a foreground timer is not something Apple restricts.
///
/// Hard iOS constraints this can't get around (see also ios/README.md):
/// - Needs "Always" location authorization granted *before* the event that
///   should wake the app (a reboot, or the app being force-quit).
/// - After a reboot, no location event is delivered until the user unlocks
///   the device once (NSFileProtectionComplete blocks background code until
///   first unlock).
/// - If the user swipes the app away from the app switcher, iOS will not
///   relaunch it for location events again until it is opened by hand.
final class LocationTracker: NSObject, ObservableObject, CLLocationManagerDelegate {
    static let shared = LocationTracker()

    @Published private(set) var authorizationStatus: CLAuthorizationStatus

    private let manager = CLLocationManager()
    private var foregroundTimer: Timer?

    override init() {
        authorizationStatus = manager.authorizationStatus
        super.init()
        manager.delegate = self
        manager.desiredAccuracy = kCLLocationAccuracyHundredMeters
        NotificationCenter.default.addObserver(
            self, selector: #selector(appDidBecomeActive),
            name: UIApplication.didBecomeActiveNotification, object: nil,
        )
        NotificationCenter.default.addObserver(
            self, selector: #selector(appWillResignActive),
            name: UIApplication.willResignActiveNotification, object: nil,
        )
    }

    func requestWhenInUseAuthorization() {
        manager.requestWhenInUseAuthorization()
    }

    /// Call only after when-in-use is already granted; Apple requires the
    /// two-step flow and silently ignores an early "Always" request.
    func requestAlwaysAuthorizationIfNeeded() {
        if manager.authorizationStatus == .authorizedWhenInUse {
            manager.requestAlwaysAuthorization()
        }
    }

    var hasAnyLocationPermission: Bool {
        switch manager.authorizationStatus {
        case .authorizedAlways, .authorizedWhenInUse: return true
        default: return false
        }
    }

    /// Re-arms significant-location-change monitoring. Must be called again
    /// on every fresh process launch (including a location-triggered
    /// relaunch after reboot) — a CLLocationManager's monitoring state does
    /// not survive the previous process exiting.
    func resume() {
        guard AppPrefs.shared.trackingEnabled, hasAnyLocationPermission else { return }
        manager.startMonitoringSignificantLocationChanges()
        if UIApplication.shared.applicationState == .active {
            startForegroundTimer()
        }
    }

    func pause() {
        manager.stopMonitoringSignificantLocationChanges()
        stopForegroundTimer()
    }

    /// One-shot sample; only meaningful (and only ever called) while the app
    /// is in the foreground.
    func sampleNow() {
        guard hasAnyLocationPermission else { return }
        manager.requestLocation()
    }

    @objc private func appDidBecomeActive() {
        guard AppPrefs.shared.trackingEnabled else { return }
        startForegroundTimer()
        sampleNow()
    }

    @objc private func appWillResignActive() {
        stopForegroundTimer()
    }

    private func startForegroundTimer() {
        stopForegroundTimer()
        let interval = TimeInterval(AppPrefs.shared.intervalMinutes * 60)
        foregroundTimer = Timer.scheduledTimer(withTimeInterval: interval, repeats: true) { [weak self] _ in
            self?.sampleNow()
        }
    }

    private func stopForegroundTimer() {
        foregroundTimer?.invalidate()
        foregroundTimer = nil
    }

    // MARK: - CLLocationManagerDelegate

    func locationManagerDidChangeAuthorization(_ manager: CLLocationManager) {
        authorizationStatus = manager.authorizationStatus
        requestAlwaysAuthorizationIfNeeded()
        if hasAnyLocationPermission {
            resume()
        }
    }

    func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
        guard let location = locations.last else { return }
        // A significant-location-change (or relaunch) delivery can arrive
        // with the app about to be suspended again within seconds; wrap the
        // synchronous file write in a background task so it isn't cut off
        // mid-write.
        let taskId = UIApplication.shared.beginBackgroundTask(withName: "DayAtlas.save")
        DayStore.shared.append(location: location)
        NotificationCenter.default.post(name: .dayAtlasPointSaved, object: nil)
        if taskId != .invalid {
            UIApplication.shared.endBackgroundTask(taskId)
        }
    }

    func locationManager(_ manager: CLLocationManager, didFailWithError error: Error) {
        // Best-effort, silent — matches LocationSampler.kt's timeout-to-nil handling.
    }
}
