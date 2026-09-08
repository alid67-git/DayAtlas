import Combine
import SwiftUI

/// Mirrors MainActivity.kt: today's title, recording status, distance, last
/// point time, and a manual start/stop toggle that's hidden while daily mode
/// is on (no confirmation dialog in that mode — matches Android).
struct ContentView: View {
    @ObservedObject private var locationTracker = LocationTracker.shared
    @State private var record: DayRecord = DayStore.shared.loadToday()
    @State private var trackingEnabled = AppPrefs.shared.trackingEnabled
    @State private var dailyMode = AppPrefs.shared.dailyMode
    @State private var showSettings = false

    var body: some View {
        NavigationView {
            VStack(alignment: .leading, spacing: 16) {
                Text(record.title)
                    .font(.title2).bold()

                Text(statusText)
                    .font(.subheadline)
                    .foregroundColor(recording ? .green : .secondary)

                HStack(spacing: 32) {
                    VStack(alignment: .leading) {
                        Text("Mesafe").font(.caption).foregroundColor(.secondary)
                        Text(record.points.isEmpty ? "—" : DayTitle.formatDistance(record.distanceMeters))
                            .font(.title3).bold()
                    }
                    VStack(alignment: .leading) {
                        Text("Son nokta").font(.caption).foregroundColor(.secondary)
                        Text(lastPointText).font(.title3).bold()
                    }
                }

                Text("\(record.points.count) nokta")
                    .font(.caption)
                    .foregroundColor(.secondary)

                Spacer().frame(height: 8)

                if dailyMode {
                    Text("Günlük mod açıkken kayıt sessizce devam eder. Durdurmak için Ayarlar'dan günlük modu kapatın.")
                        .font(.footnote)
                        .foregroundColor(.secondary)
                } else {
                    Button(trackingEnabled ? "Durdur" : "Başlat") {
                        toggleTracking()
                    }
                    .buttonStyle(.borderedProminent)
                    Text("Günlük mod kapalı. İzlemek istediğinizde Başlat'a basın (uygulama açıkken 3–5 dk'da bir nokta; arka planda yalnızca belirgin bir yer değişikliğinde).")
                        .font(.footnote)
                        .foregroundColor(.secondary)
                }

                Spacer()
            }
            .padding()
            .navigationTitle("DayAtlas")
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button {
                        showSettings = true
                    } label: {
                        Image(systemName: "gearshape")
                    }
                }
            }
            .sheet(isPresented: $showSettings, onDismiss: refresh) {
                SettingsView()
            }
        }
        .onAppear {
            refresh()
            if dailyMode {
                // Günlük mod: onay diyaloğu yok; yalnızca sistem izin ekranları.
                LocationTracker.shared.requestWhenInUseAuthorization()
            }
        }
        .onReceive(NotificationCenter.default.publisher(for: .dayAtlasPointSaved)) { _ in
            refresh()
        }
    }

    private var recording: Bool { dailyMode || trackingEnabled }

    private var statusText: String {
        if dailyMode { return "Günlük mod — otomatik kayıt" }
        return trackingEnabled ? "Kayıt açık" : "Kayıt kapalı"
    }

    private var lastPointText: String {
        guard let last = record.points.last else { return "—" }
        let date = Date(timeIntervalSince1970: Double(last.timeMillis) / 1000.0)
        let formatter = DateFormatter()
        formatter.dateFormat = "HH:mm"
        return formatter.string(from: date)
    }

    private func toggleTracking() {
        if trackingEnabled {
            TrackingController.stop()
        } else {
            LocationTracker.shared.requestWhenInUseAuthorization()
            TrackingController.start()
        }
        refresh()
    }

    private func refresh() {
        record = DayStore.shared.loadToday()
        trackingEnabled = AppPrefs.shared.trackingEnabled
        dailyMode = AppPrefs.shared.dailyMode
    }
}

#Preview {
    ContentView()
}
