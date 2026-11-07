import CoreLocation
import SwiftUI
import UniformTypeIdentifiers
import UIKit

/// Mirrors SettingsActivity.kt: daily mode toggle, interval picker,
/// permission shortcuts, Android backup restore, and — since iOS has no
/// BOOT_COMPLETED equivalent — an explicit explanation of reboot resume.
struct SettingsView: View {
    @Environment(\.dismiss) private var dismiss
    @ObservedObject private var locationTracker = LocationTracker.shared
    @State private var dailyMode = AppPrefs.shared.dailyMode
    @State private var interval = AppPrefs.shared.intervalMinutes
    @State private var showImporter = false
    @State private var pendingCandidates: [DayRestore.Candidate] = []
    @State private var showConflictConfirm = false
    @State private var statusMessage: String?
    @State private var statusIsError = false

    private var gpxType: UTType {
        UTType(filenameExtension: "gpx") ?? .xml
    }

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
                    Text("Açıkken uygulama açılınca veya reboot sonrası ilk kilit açmada bugünün kaydı onay sormadan başlar / devam eder.")
                }

                Section("Örnekleme aralığı") {
                    Picker("Dakika", selection: $interval) {
                        Text("3 dakika").tag(3)
                        Text("4 dakika").tag(4)
                        Text("5 dakika (önerilen)").tag(5)
                    }
                    .pickerStyle(.segmented)
                    .onChange(of: interval) { newValue in
                        AppPrefs.shared.intervalMinutes = newValue
                    }
                    Text("Bu aralık yalnızca uygulama ön plandayken uygulanır. Arka planda / kilitliyken iOS yalnızca belirgin (≈500 m) yer değişikliklerinde uyandırır — Apple'ın izin verdiği tek arka plan mekanizması budur.")
                        .font(.footnote)
                        .foregroundColor(.secondary)
                }

                Section("İzinler") {
                    LabeledContent("Konum izni", value: authorizationText)
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

                Section {
                    Button("JSON veya GPX seç…") {
                        showImporter = true
                    }
                    if let statusMessage {
                        Text(statusMessage)
                            .font(.footnote)
                            .foregroundColor(statusIsError ? .red : .secondary)
                    }
                } header: {
                    Text("Android / yedek geri yükle")
                } footer: {
                    Text("Android Drive yedeğindeki yyyy-MM-dd.gpx veya .json dosyalarını seç. Aynı gün telefonda varsa üzerine yazılır.")
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
            .fileImporter(
                isPresented: $showImporter,
                allowedContentTypes: [.json, gpxType],
                allowsMultipleSelection: true
            ) { result in
                handleImport(result)
            }
            .alert("Mevcut günlerin üzerine yazılsın mı?", isPresented: $showConflictConfirm) {
                Button("İptal", role: .cancel) {
                    pendingCandidates = []
                }
                Button("Üzerine yaz", role: .destructive) {
                    finishImport(pendingCandidates.map(\.incoming))
                    pendingCandidates = []
                }
            } message: {
                let conflicts = pendingCandidates.filter(\.hasLocalTrack)
                let labels = conflicts.prefix(5).map(\.dateIso).joined(separator: ", ")
                let more = conflicts.count > 5 ? "…" : ""
                Text("\(conflicts.count) günde zaten rota var (\(labels)\(more)). Devam edersen Android yedeği geçerli olur.")
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

    private func handleImport(_ result: Result<[URL], Error>) {
        switch result {
        case .failure(let error):
            statusIsError = true
            statusMessage = "Seçim iptal / hata: \(error.localizedDescription)"
        case .success(let urls):
            let candidates = DayRestore.candidates(fromFileURLs: urls)
            if candidates.isEmpty {
                statusIsError = true
                statusMessage = "Geçerli gün dosyası bulunamadı (yyyy-MM-dd.gpx / .json)."
                return
            }
            let conflicts = candidates.filter(\.hasLocalTrack)
            if conflicts.isEmpty {
                finishImport(candidates.map(\.incoming))
            } else {
                pendingCandidates = candidates
                showConflictConfirm = true
            }
        }
    }

    private func finishImport(_ records: [DayRecord]) {
        let count = DayRestore.apply(records)
        statusIsError = false
        statusMessage = "\(count) gün geri yüklendi."
        NotificationCenter.default.post(name: .dayAtlasDaysDidChange, object: nil)
    }
}

extension Notification.Name {
    static let dayAtlasDaysDidChange = Notification.Name("dayAtlasDaysDidChange")
}

#Preview {
    SettingsView()
}
