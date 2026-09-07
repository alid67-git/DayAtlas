# Changelog

## 0.2.0 — 2026-09-07

- Klasik RideAtlas/MediaAtlas versiyon-yükseltme kurgusu eklendi: CI artık her
  main push'unda tek bir rolling `android-latest` GitHub release'ini
  (`DayAtlas.apk` içerir) güncelliyor
- Uygulama açılışta bu release'i kontrol ediyor (yalnızca release build'de,
  sessiz/best-effort); yeni sürüm varsa indirip kurmayı öneriyor
  (DownloadManager + FileProvider + sistem yükleyici)
- Ayarlar'a "Güncelleme" bölümü eklendi: mevcut sürüm etiketi + elle
  "Güncellemeleri kontrol et" düğmesi
- Her yeni sürümde bir kere gösterilen "Yenilikler" diyaloğu eklendi

## 0.1.0 — 2026-08-29

- İlk iskelet: uygulama adı DayAtlas, paket `com.dayatlas.app`
- Seyrek konum örneği (3 / 4 / 5 dk, varsayılan 5) — AlarmManager + kısa ön plan servisi + `getCurrentLocation`
- Yerel takvim gününe göre JSON + GPX dosyası (`files/days/yyyy-MM-dd.*`)
- Opsiyonel günlük mod; BOOT_COMPLETED ile sessiz devam
- Ana ekran ve ayarlar (Türkçe)
- GitHub Actions: debug + release APK
