import UIKit

final class AppDelegate: NSObject, UIApplicationDelegate {
    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil,
    ) -> Bool {
        // Touch LocationTracker.shared before anything else so its
        // CLLocationManager (and delegate) exists before any queued
        // location event is delivered this launch.
        _ = LocationTracker.shared
        TrackingController.onAppLaunch()
        return true
    }
}
