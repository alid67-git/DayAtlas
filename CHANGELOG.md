# Changelog

## Unreleased

- **iOS companion app eklendi** (`ios/`): Swift/SwiftUI, Android ile aynı
  gün dosyası formatı (`days/yyyy-MM-dd.json` + `.gpx`). Arka plan
  tetikleyicisi Android'den farklı — Apple sabit zamanlı arka plan
  örneklemeye izin vermediği için `CLLocationManager` significant-location-
  change (mesafe bazlı, ~500 m) kullanılıyor; ön planda ek bir zamanlayıcı
  ile 3/4/5 dk aralığı korunuyor. Reboot sonrası devam etme için üç şart
  (Her Zaman izni önceden verilmiş, cihazın bir kez kilidi açılmış,
  uygulama force-quit edilmemiş) `ios/README.md`'de ve Ayarlar ekranında
  açıklanıyor. Henüz Xcode projesi yok; `ios/project.yml`'den XcodeGen ile
  üretiliyor. Dağıtım şimdilik ücretsiz kişisel imzalama ile Xcode'dan
  cihaza kurulum (TestFlight/App Store yok).
- Android: cihaz üreticisi biliniyorsa (Xiaomi, Huawei/Honor, Oppo/Realme,
  Vivo, Samsung, OnePlus, Meizu, Asus) Ayarlar'a doğrudan o üreticinin
  otomatik başlatma/korumalı uygulamalar ekranına götüren bir kısayol
  düğmesi eklendi (`OemAutostart`). Reboot sonrası kaydın OEM tarafından
  kesilmesi riskini azaltmaya yardımcı olur; izni açmak yine kullanıcının
  elinde.
- Android: `MainActivity` görev değiştirici (son kullanılan uygulamalar)
  ekranında artık kart bırakmıyor (`android:excludeFromRecents="true"`).
  Bu yalnızca o kalabalık listeyi temizler — bildirim, sistem konum
  göstergesi, Ayarlar'daki uygulama kaydı gibi hiçbir şeffaflık öğesi
  etkilenmez, uygulama gizlenmiyor. Bedeli: uygulamayı tekrar açmak için
  görev değiştirici yerine ana ekran simgesi kullanılmalı.

## 0.2.0 — 2026-09-07

- Klasik RideAtlas/MediaAtlas versiyon-yükseltme kurgusu eklendi: CI artık her
  main push'unda tek bir rolling `android-latest` GitHub release'ini
  (`DayAtlas.apk` içerir) güncelliyor
- Uygulama açılışta bu release'i sessizce kontrol ediyor (yalnızca release
  build'de, best-effort); yeni sürüm varsa hiç sormadan/uyarı vermeden
  indirmeye başlıyor (DownloadManager + FileProvider + sistem yükleyici).
  Android'in kendi kurulum onay ekranı yine de çıkar - bu OS kısıtı, hiçbir
  normal uygulama atlayamaz
- Ayarlar'a "Güncelleme" bölümü eklendi: mevcut sürüm etiketi + elle
  "Güncellemeleri kontrol et" düğmesi (bu, manuel akışta bulunca sorar)
- Her yeni sürümde bir kere gösterilen "Yenilikler" diyaloğu eklendi
- (Bu sürümü main'e almadan önce main'de zaten kırık olan, bizim eklediğimiz
  koddan bağımsız bir test hatası da düzeltildi: DayJsonTest, Android'in
  local-unit-test stub'ındaki org.json üzerinde patlıyordu — gerçek org.json
  kütüphanesi test bağımlılığı olarak eklendi)

## 0.1.0 — 2026-08-29

- İlk iskelet: uygulama adı DayAtlas, paket `com.dayatlas.app`
- Seyrek konum örneği (3 / 4 / 5 dk, varsayılan 5) — AlarmManager + kısa ön plan servisi + `getCurrentLocation`
- Yerel takvim gününe göre JSON + GPX dosyası (`files/days/yyyy-MM-dd.*`)
- Opsiyonel günlük mod; BOOT_COMPLETED ile sessiz devam
- Ana ekran ve ayarlar (Türkçe)
- GitHub Actions: debug + release APK
