# Changelog

## 0.5.0 — 2026-09-08

- **Rota ekranı artık gerçek bir OpenStreetMap haritası kullanıyor**
  (osmdroid), 0.4.0'daki sade-çizgi görünümün yerine. Play Services yok,
  API key yok. Harita tamamen ön plan bileşeni — yalnızca Rota ekranı
  açıkken karo indirir/çizer (`MapView.onResume`/`onPause`e bağlı), arka
  planda hiçbir şey çalışmaz; DayAtlas'ın pil profiline etkisi yok. Canlı
  konum/pusula overlay'i bilerek eklenmedi (rota ekranı bitmiş bir günü
  gösterir, GPS'i tekrar açmaz). `RoutePathView` kaldırıldı, `DayAtlasApp`
  osmdroid'in User-Agent'ını ve karo önbellek yolunu (uygulama içi, izin
  gerektirmeyen bir dizin) bir kez yapılandırıyor.

## 0.4.0 — 2026-09-08

- **Rota ekranı eklendi.** Araç çubuğundaki yeni simge, günün kaydedilen
  noktalarını sade bir çizgi olarak (harita/tile arka planı yok, tamamen
  yerel çizim — `RoutePathView`, yeni bağımlılık veya internet gerekmez)
  ekrana sığdırılmış şekilde gösteriyor. Açılışta bugünü gösterir; ok
  düğmeleriyle önceki günlere gidilebilir (o gün için kayıt yoksa boş durum
  mesajı çıkar, geleceğe gidilemez). Başlangıç noktası yeşil, son nokta
  kırmızı ile işaretleniyor.

## 0.3.0 — 2026-09-08

- **Kalıcı imzalama anahtarı eklendi (`app/dayatlas-debug.keystore`).**
  Reponun public yapılması ve sürüm numarasının artırılması self-update'i
  hâlâ çalıştırmadı — asıl sebep şuydu: `release` build tipi Android'in
  varsayılan `debug` imzalama yapılandırmasını kullanıyordu, ve o
  varsayılan anahtar her CI runner'ında yoktan var edildiği için (her
  runner'da o dosya baştan oluşturuluyor) **her CI derlemesi farklı bir
  anahtarla imzalanıyordu**. Android, mevcut kurulu uygulamayla imzası
  eşleşmeyen bir APK'yı "güncelleme" olarak kabul etmiyor — indirme
  başarıyla bitiyor ama kurulum sessizce başarısız oluyor, eski sürüm
  yerinde kalıyordu. RideAtlas/MediaAtlas'takiyle aynı çözüm: sabit,
  repoya bilerek commit edilmiş bir sideload-only keystore (Play Store'a
  yayın için değil, üretim keystore'u ayrı bir konudur). **Tek seferlik
  bedel:** telefonda hâlâ eski (rastgele anahtarla imzalı) bir sürüm
  kuruluysa, bu geçişte de imza uyuşmayacak — o yüzden bu güncellemeye
  geçerken bir kez elle kaldırıp yeniden kurmak gerekiyor
  (`files/days/*.json`/`.gpx` uygulama iç deposunda olduğu için kaldırma
  sırasında silinir — önemsiyorsan önce `adb pull` ile yedekle). Bundan
  sonraki tüm güncellemeler bu sabit anahtarla imzalanacağı için sorunsuz
  kurulacak.
- **Sürüm numarası artırıldı (0.2.0 → 0.3.0).** Bu, sıradan bir bakım
  detayı değil: CI, rolling `android-latest` release'inin adına
  `versionName`'i gömüyor (`v0.2.0` gibi) ve `UpdateChecker` de "zaten
  güncel misin" kararını tam olarak bu isim karşılaştırmasıyla veriyor.
  Önceki birkaç commit (iOS uygulaması, OEM otomatik-başlat kısayolu,
  görev değiştiriciden çıkarma) `versionName`'i artırmadan main'e gitmişti
  — yani release'in APK içeriği değişmiş olsa da adı hâlâ `v0.2.0`
  kalıyordu ve kurulu 0.2.0 sürümü kendini hep "zaten güncel" sanıp hiç
  güncelleme göstermiyordu. Sürüm sabit kaldığı sürece bu döngü kırılmaz;
  bundan sonra gerçek bir davranış değişikliği içeren her commit'te
  `versionName`/`versionCode` de artırılmalı.
- Android: güncelleme kontrolü artık yalnızca uygulama açılışında değil,
  arka planda da çalışıyor. `SampleService`'in zaten her 3–5 dk'da bir
  çalışan döngüsüne günde en fazla bir kez sessiz bir kontrol eklendi
  (`AppPrefs.lastUpdateCheckMillis`) — yeni alarm/servis yok, mevcut
  wake-lock'a biniyor. Böylece günlerce hiç açılmadan çalışan bir kurulum
  da yeni sürümü fark edip indirebiliyor (yalnızca günlük mod/manuel kayıt
  açıkken; ikisi de kapalıysa hâlâ uygulamayı açmak gerekir).
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
