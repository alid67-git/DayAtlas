# DayAtlas

Hafif **günlük iz defteri** — Android. Amaç: nereye ve ne kadar gidildiğinin seyrek, pil dostu kaydı.

Paket: `com.dayatlas.app` · Dil: Türkçe (v0.1) · Sürüm: 0.1.0

Bu depo **RideAtlas değildir**. RideAtlas sık GPS, canlı harita ve zengin sürüş kaydıdır. DayAtlas ayrı bir ürün: seyrek örnekleme, özet iz, az pil. RideAtlas kodu kopyalanmaz; monorepo yoktur.

İki platform, iki ayrı native uygulama, aynı gün dosyası formatı:

- **Android** (bu dosyanın geri kalanı) — `app/`, Kotlin.
- **iOS** — `ios/`, Swift/SwiftUI. Kurulum, arka plan modeli ve reboot
  sonrası devam etme kısıtları için bkz. [`ios/README.md`](ios/README.md).
  Apple'ın platform kuralları yüzünden arka plan tetiklemesi Android'den
  farklıdır (sabit dakika yerine ~500 m hareket eşiği) — ayrıntı ios/README.md'de.

## Neden native Kotlin?

Flutter motoru (~4–8 MB) ve Google Play Services yığını yok. AlarmManager, konum, boot ve pil muafiyeti doğrudan platform API’si. v1 hedefi küçük APK ve kolay bakım. Rota ekranındaki OpenStreetMap haritası (osmdroid) bunun tek istisnası — o da Play Services/API key gerektirmez, bkz. aşağı.

## Ne yapar (v1)

- **Opsiyonel günlük mod** (Ayarlar): açıkken uygulama veya telefon açılınca onay sormadan bugünün kaydına başlar / devam eder.
- **Seyrek GPS:** varsayılan **5 dakika** (3 / 4 / 5 ayarlanabilir). Sürekli location stream yok.
- **Gün dosyası:** her **cihaz yerel** takvim günü (`yyyy-MM-dd`) ayrı kayıt. Gece yarısında yeni dosya. UTC ile gün bölünmez.
- **İçerik:** zaman damgalı az nokta + mesafe özeti. `files/days/yyyy-MM-dd.json` ve `.gpx`.
- **UI:** bugün kayıtta mı, mesafe, son nokta saati, başlat/durdur (günlük mod kapalıyken).
- **Harita (ana ekranın altı):** bugünün / seçilen günün noktaları
  OpenStreetMap üzerinde çizgi olarak (osmdroid — Play Services yok, API
  key yok). Oklarla önceki günlere gidilir. Karolar yalnızca uygulama
  ön plandayken indirilir/çizilir.
- **GPX dışa aktarma:** araç çubuğundan tek gün veya tarih aralığı + dosya
  adı; sistem paylaşım ekranı ile kaydet/gönder.

## Ne yapmaz

Analiz, foto, Android Auto, topo harita, canlı harita/canlı konum takibi, sık GPS. Harita geçmiş bir günün *bitmiş* rotasını gösterir — RideAtlas'taki gibi canlı, takip eden bir harita değildir.

## Harita ve pil

Harita (osmdroid) ana ekranın altındaki bir ön plan görünüm bileşenidir:
yalnızca MainActivity görünürken karo indirir/çizer (`onResume`/`onPause`
ile bağlı). Uygulama arka plandayken harita kodu çalışmaz — sıfır pil,
sıfır ağ (ana ekran açıkken karo indirmesi olabilir). DayAtlas'ın gerçek
arka plan pil maliyeti tamamen `SampleService`'ten gelir. Canlı konum/
pusula takibi bilerek eklenmedi.

## GPX dışa aktarma

Her gün zaten `files/days/yyyy-MM-dd.gpx` olarak yazılır (uygulama içi
depo). Araç çubuğundaki dışa aktarma, seçilen gün veya aralığı tek bir
GPX dosyasında birleştirip sistem paylaşım ekranına verir (Dosyalar,
Drive, e-posta vb.). Dosya adı kullanıcıdan sorulur; aralıkta her gün
ayrı bir `<trkseg>` olur.

## Nasıl çalışır (örnekleme)

1. `AlarmManager.setExactAndAllowWhileIdle` bir sonraki örneği planlar (tam alarm yoksa `setAndAllowWhileIdle`).
2. Alarm `SampleReceiver` → kısa ömürlü `SampleService` (foreground type `location`).
3. Servis `LocationManager.getCurrentLocation` ile **tek nokta** alır (Play Services yok), gün dosyasına yazar, kendini kapatır.
4. WorkManager kullanılmaz (minimum periyot 15 dk; 3–5 dk için uygun değil).

Kısa FGS, sürekli yüksek frekanslı servis değildir. Doze altında 3–5 dk tam tutmayabilir; pil muafiyeti bunu iyileştirir.

## İzinler (ilk açılış, bir kez)

| İzin | Neden |
| --- | --- |
| Konum (kesin / yaklaşık) | Nokta almak |
| **Her zaman izin ver** (`ACCESS_BACKGROUND_LOCATION`) | Ekran kapalıyken / arka planda örnek |
| Bildirimler (Android 13+) | Kısa FGS bildirimi (zorunlu) |
| Pil optimizasyonu muafiyeti | Alarm’ın uyku modunda çalışması |
| Tam alarm (`SCHEDULE_EXACT_ALARM`) | 3–5 dk aralığına yaklaşmak |

Sistem izin pencereleri kaçınılmazdır. Günlük mod açıkken **“kayıt başlasın mı?”** diye sormayız.

## OEM / reboot notları

`BOOT_COMPLETED` ile günlük mod açıksa kayıt sessizce yeniden planlanır. Birçok üretici bunu keser:

| Üretici | Tipik ayar |
| --- | --- |
| Xiaomi / HyperOS / MIUI | Otomatik başlat, pil tasarrufu istisnası |
| Huawei / Honor | Korumalı uygulamalar, “manuel olarak yönet” |
| Oppo / Realme / ColorOS | Uygulama başlatma / arka plan dondurma |
| Samsung | Uyku modu / kullanılmayan uygulamaları derin uyutma — DayAtlas’ı hariç tut |
| OnePlus | Pille optimize etme, otomatik başlat |

Pil bitip telefon açılınca: kilidi açın (dosyalar kullanıcı şifresine bağlı), uygulamayı bir kez açmanız gerekebilir. OEM “otomatik başlat” kapalıysa BootReceiver hiç çalışmaz — bu Android sınırıdır, uygulama aşamaz.

Ayarlar ekranında, cihaz üreticisi yukarıdaki listede tanınıyorsa bir
“Otomatik başlatmayı aç” düğmesi belirir; bu düğme o üreticinin otomatik
başlatma/korumalı uygulamalar ekranına doğrudan götürür (dokümante edilmemiş
sistem ekranlarına best-effort deep link — bulunamazsa kullanıcıyı üretici
ayarlarında elle aramaya yönlendirir). İzni açık hâle getirmek yine kullanıcının
elinde; uygulama bunu zorlayamaz.

### Görev değiştirici (son uygulamalar)

DayAtlas görev değiştiricide kart bırakmamaya çalışır. Yalnızca
`excludeFromRecents` bazı OEM arayüzlerinde (Samsung One UI) yetmediği
için, UI arka plana geçince görev de sonlandırılır
(`AppTask.finishAndRemoveTask`). Bu, listede görünmeyen uygulamaların
yaptığı şeyle aynı sınıftır: bayrak yetmezse görevi gerçekten kaldırmak.

- Yeniden açmak için ana ekran simgesini kullanın (görev değiştiricide
  kart olmaz).
- Günlük kayıt bundan etkilenmez — örnekleme AlarmManager + kısa
  `SampleService` ile activity'den bağımsız sürer.
- Sistem izin / Ayarlar / OEM otomatik-başlat ekranlarına giderken görev
  bilinçli olarak tutulur; o ekranlardan Geri ile dönüş çalışır.
- Bildirim, konum göstergesi, Ayarlar'daki uygulama kaydı gibi şeffaflık
  öğeleri bilerek değiştirilmez — yalnızca görev değiştirici kartı.

## Güncelleme

RideAtlas/MediaAtlas ile aynı kurgu: CI (`android.yml`) her `main` push’unda
`DayAtlas.apk`’yi tek bir rolling `android-latest` GitHub release’ine
yüklüyor. Uygulama açılışta (yalnızca release build’de) bu release’i sessizce
kontrol ediyor; daha yeni bir sürüm varsa hiç sormadan/uyarı vermeden
indirmeye başlıyor.

Uygulama günlerce hiç açılmadan (yalnızca günlük mod arka planda) çalışabildiği
için bu açılış-anı kontrolü tek başına yetmez: `SampleService` de zaten her
3–5 dk’da bir çalıştığı için, günde en fazla bir kez aynı sessiz kontrolü o
döngüye de ekliyor (`AppPrefs.lastUpdateCheckMillis`). Yeni alarm/servis
eklenmedi — mevcut örnekleme tetiklemesine, zaten tutulan wake-lock’a binen bir
ek adım. Yalnızca günlük mod veya manuel kayıt açıkken çalışır; ikisi de
kapalıyken uygulamayı hiç açmıyorsanız güncelleme kontrolü de olmaz.

`release` build tipi, repoya bilerek commit edilmiş sabit bir keystore ile
imzalanıyor (`app/dayatlas-debug.keystore`) — Android'in kendiliğinden
oluşturduğu varsayılan debug anahtarıyla değil. O varsayılan anahtar her CI
runner'ında sıfırdan üretildiği için, onunla imzalarsak her CI derlemesi
farklı bir anahtarla çıkar ve Android imzası eşleşmeyen bir APK'yı
"güncelleme" olarak kabul etmez (indirme biter, kurulum sessizce başarısız
olur). Sabit keystore bunu çözer; RideAtlas/MediaAtlas'ta da aynı kurgu var.
Sideload-only bir anahtar (Play Store için değil), commit edilmesi bu yüzden
sorun değil.

Kurulum anında Android’in kendi “bu uygulamayı yükle”
ekranı yine de çıkar — bu işletim sistemi kısıtıdır, hiçbir normal (root
olmayan) uygulama atlayamaz. Ayarlar’da elle kontrol için de bir düğme var;
o akış bulduğunda indirmeden önce sorar. Debug-keystore ile imzalı sideload
build’dir, Play Store’a yayın için değildir.

## Derleme

JDK 17 + Android SDK (compile/target SDK 35).

```bash
./gradlew assembleDebug assembleRelease
```

Windows: `gradlew.bat assembleDebug`

APK:

- Debug: `app/build/outputs/apk/debug/`
- Release: `app/build/outputs/apk/release/` (v0.1 debug keystore ile imzalı; üretim keystore sonra)

CI her push’ta aynı APK’ları artifact olarak yükler.

## Nasıl test edilir

1. Debug APK kur, uygulamayı aç.
2. Konum: **Her zaman izin ver**. Pil: **optimize etme**. Gerekirse tam alarm izni.
3. OEM ise “otomatik başlat”ı aç.
4. Ayarlar → **Günlük mod** açık, aralık 3 dk (hızlı deneme).
5. Ana ekranda “Günlük mod — otomatik kayıt”, mesafe/son nokta bir süre sonra dolmalı. Onay diyaloğu olmamalı.
6. Dosyalar:
   ```bash
   adb shell run-as com.dayatlas.app ls files/days
   ```
   `yyyy-MM-dd.json` ve `.gpx` beklenir. JSON `date` alanı yerel gündür.
7. **Reboot:** günlük mod açıkken yeniden başlat, kilidi aç, ~bir aralık bekle. Uygulama açılmadan nokta düşmeli (OEM izin veriyorsa). Düşmezse otomatik başlat/pil ayarını kontrol et; README’deki beklenti budur, garanti değil.
8. Günlük modu kapat: kayıt durur. Manuel **Başlat** / **Durdur** görünür. Gece yarısı yine yerel güne göre yeni dosya.

## Kayıt formatı

JSON özeti:

```json
{
  "version": 1,
  "date": "2026-08-28",
  "title": "Günlük 28 Ağu 2026",
  "distanceMeters": 1234.5,
  "points": [{ "t": 1756512000000, "lat": 41.01, "lon": 29.02, "acc": 12.3 }]
}
```

`t` Unix milisaniye (anlık zaman). Gün anahtarı yerel takvim tarihidir.

## Lisans

MIT — bakınız `LICENSE`.
