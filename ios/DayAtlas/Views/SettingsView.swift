import CoreLocation
import SwiftUI
import UIKit

/// Mirrors SettingsActivity.kt: daily mode toggle, interval picker,
/// permission shortcuts, and — since iOS has no BOOT_COMPLETED equivalent —
/// an explicit explanation of what "auto-resume after reboot" actually
/// requires on this platform.
struct SettingsView: View {
    @Environment(\.dismiss) private var dismiss
    @ObservedObject private var locationTracker = LocationTracker.shared
    @State private var dailyMode = AppPrefs.shared.dailyMode
    @State private var interval = AppPrefs.shared.intervalSeconds

    var body: some View {
        NavigationView {
            Form {
                Section {
                    Toggle("Günlük mod", isOn: $dailyMode)
                        .onChange(of: dailyMode) { newValue in
                            if newValue {
                                LocationTracker.shared.requestWhenInUseAuthorization()
                            }
                            TrackingController.setDailyMode(newValue)
                        }
                } footer: {
                    Text("Açıkken uygulama açılınca veya (Her Zaman izniyle) belirgin yer değişikliğinde bugünün kaydı devam eder. Force-quit etme.")
                }

                Section("Ön plan aralığı") {
                    Picker("Aralık", selection: $interval) {
                        Text("30 sn").tag(30)
                        Text("1 dk").tag(60)
                        Text("3 dk").tag(180)
                        Text("5 dk").tag(300)
                    }
                    .pickerStyle(.segmented)
                    .onChange(of: interval) { newValue in
                        AppPrefs.shared.intervalSeconds = newValue
                        if AppPrefs.shared.trackingEnabled {
                            LocationTracker.shared.resume()
                        }
                    }
                    Text("Bu aralık yalnızca uygulama ön plandayken. Arka planda / kilitliyken iOS yalnızca belirgin (≈500 m) yer değişikliğinde uyandırır — myTracks gibi native uygulamalarla aynı sınıf kısıt (web PWA bunu hiç yapamaz).")
                        .font(.footnote)
                        .foregroundColor(.secondary)
                }

                Section("İzinler") {
                    LabeledContent("Konum izni", value: authorizationText)
                    if locationTracker.authorizationStatus != .authorizedAlways {
                        Text("Arka plan kaydı için \"Her Zaman\" zorunlu.")
                            .font(.footnote)
                            .foregroundColor(.orange)
                    }
                    Button("Ayarlar uygulamasını aç") {
                        if let url = URL(string: UIApplication.openSettingsURLString) {
                            UIApplication.shared.open(url)
                        }
                    }
                    if locationTracker.authorizationStatus == .authorizedWhenInUse {
                        Button("\"Her Zaman\" izni iste") {
                            LocationTracker.shared.requestAlwaysAuthorizationIfNeeded()
                        }
                    }
                }
                Section("Reboot sonrası devam etme — iOS kısıtları") {
                    Text("""
                    1) "Her Zaman" izni reboot'tan önce verilmiş olmalı.
                    2) Reboot sonrası dosya şifrelemesi nedeniyle telefon en az bir kez kilidi açılana kadar hiçbir konum olayı iletilmez.
                    3) Uygulamayı görev listesinden yukarı kaydırarak (force-quit) kapattıysan iOS onu konum olayı için tekrar başlatmaz — elle bir kez açman gerekir.
                    Bunlar Apple'ın platform kısıtlarıdır; DayAtlas bunları aşamaz (Android tarafındaki OEM otomatik-başlat kısıtına benzer).
                    """)
                    .font(.footnote)
                    .foregroundColor(.secondary)
                }

                Section("Dosyalar") {
                    Text(DayStore.shared.daysDirectory.path)
                        .font(.footnote)
                        .foregroundColor(.secondary)
                }
            }
            .navigationTitle("Ayarlar")
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Kapat") { dismiss() }
                }
            }
        }
    }

    private var authorizationText: String {
        switch locationTracker.authorizationStatus {
        case .authorizedAlways: return "Her Zaman"
        case .authorizedWhenInUse: return "Yalnızca uygulama açıkken"
        case .denied: return "Reddedildi"
        case .restricted: return "Kısıtlı"
        case .notDetermined: return "Sorulmadı"
        @unknown default: return "Bilinmiyor"
        }
    }
}

#Preview {
    SettingsView()
}
