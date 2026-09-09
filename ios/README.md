# DayAtlas — iOS

Android'deki DayAtlas'ın native Swift/SwiftUI karşılığı. Aynı ürün fikri
(seyrek, pil dostu günlük iz), aynı dosya formatı (`Documents/days/yyyy-MM-dd.json`
ve `.gpx`, Android'le birebir aynı JSON şeması) — ama arka planda **farklı**
bir mekanizmayla çalışır, çünkü Apple ikisine de izin vermiyor:

| | Android | iOS (native) | Web PWA |
| --- | --- | --- | --- |
| Arka plan tetikleyici | `AlarmManager` (sabit dk) | significant-location-change (**~500 m**) | Yok |
| Reboot sonrası devam | `BOOT_COMPLETED` (OEM izin verirse) | Konum olayıyla (aşağıdaki 3 şart) | Yok |
| Ön planda örnekleme | Aynı zamanlayıcı | Timer `requestLocation()` (30 sn–5 dk) | Timer (sekme açıkken) |

Bu yüzden iOS native, myTracks gibi **App Store / Xcode uygulaması** sınıfındadır
(web “Ana Ekrana Ekle” değildir). Arka planda Android’deki “her N dakikada bir”
davranışını **birebir** tekrar edemez; mesafe tetiklidir. `Info.plist` içinde
`UIBackgroundModes = location` tanımlıdır — bu olmadan SLC arka plan uyanması
çalışmaz.

## Reboot sonrası otomatik devam etme — gerçek kısıtlar

Üçüncü parti bir iOS uygulamasının Android'deki `BOOT_COMPLETED` dengi yok.
En yakın alternatif, `startMonitoringSignificantLocationChanges()` ile
kaydolmuş bir uygulamayı iOS'un konum olayı geldiğinde arka planda (gerekirse
süreç ölmüşse yeniden başlatarak) uyandırmasıdır. Bunun çalışması için üç şart
**hepsi birden** sağlanmalı:

1. **"Her Zaman" izni önceden verilmiş olmalı.** Yalnızca "Uygulamayı
   kullanırken" izniyle, uygulama arka plana düşer düşmez konum olayları
   durur.
2. **Reboot sonrası cihaz en az bir kez kilidi açılmış olmalı.** Dosya
   şifrelemesi (`NSFileProtectionComplete`) yüzünden ilk kilit açılana kadar
   hiçbir arka plan kodu (konum olayı dahil) çalışamaz — bu iOS'un veri
   koruma modelidir, uygulama bunu aşamaz.
3. **Kullanıcı uygulamayı görev listesinden yukarı kaydırarak kapatmamış
   olmalı.** Force-quit sonrası iOS o uygulamayı konum olayı için bir daha
   yeniden başlatmaz; kullanıcı elle bir kez açana kadar.

Bu üçü de Android README'sindeki "OEM otomatik başlatmayı keserse
`BootReceiver` hiç çalışmaz" notunun iOS karşılığıdır: uygulama kodu bunları
aşamaz, yalnızca doğru izinleri istemek ve kullanıcıyı bilgilendirmek elimizde
(bkz. Ayarlar ekranındaki "Reboot sonrası devam etme" bölümü).

## Kurulum (Xcode projesi üretme)

Bu depoda hazır bir `.xcodeproj` **yok** — proje, [XcodeGen](https://github.com/yonaskolb/XcodeGen)
ile `project.yml`'den üretiliyor. Bunun sebebi: elle yazılmış bir
`project.pbxproj`'un bu ortamda (macOS/Xcode yok) doğrulanamaması; XcodeGen
deterministik ve gözden geçirilebilir bir kaynaktan projeyi her seferinde
aynı şekilde üretir.

```bash
brew install xcodegen
cd ios
xcodegen generate
open DayAtlas.xcodeproj
```

Xcode açılınca:

1. `DayAtlas` hedefi → **Signing & Capabilities** → kendi Apple ID'nizi
   (ücretsiz kişisel takım) seçin. Şu an için App Store/TestFlight
   hesabı gerekmiyor; ücretsiz imzalamayla cihaza kurulum 7 gün geçerli
   olur, süre dolunca Xcode'dan tekrar "Run" yeterli.
2. Gerçek bir cihaza bağlayıp **Run** edin — simülatörde arka plan konum /
   significant-location-change testi anlamlı değildir.
3. Uygulamada Ayarlar → Günlük mod'u açın; sistem önce "Uygulamayı
   kullanırken", ardından "Her Zaman" izni soracak — ikisini de kabul edin.
4. Reboot testi: cihazı yeniden başlatın, kilidi açın, birkaç dakika bekleyin
   (uygulamayı elle açmadan). Bir sonraki ~500 m'lik hareketle yeni bir nokta
   düşmeli. Düşmezse yukarıdaki 3 şarttan biri sağlanmamıştır — bu beklenen
   iOS davranışıdır, hata değildir.

## Dağıtım / güncelleme

Android'deki gibi bir "sessiz APK indir/kur" akışı iOS'ta **mümkün değil** —
Apple, App Store/TestFlight dışında uygulamaların kendini güncellemesine izin
vermiyor. Şimdilik ücretsiz kişisel imzalamayla doğrudan Xcode üzerinden
cihaza kurulum yeterli; ileride kalıcı/OTA dağıtım istenirse Apple Developer
Program üyeliği (TestFlight) gerekecek.

## Testler

```bash
xcodegen generate
xcodebuild test -project DayAtlas.xcodeproj -scheme DayAtlas -destination 'platform=iOS Simulator,name=iPhone 15'
```

`DayAtlasTests/DayLogicTests.swift`, Android'deki `DayLogicTest.kt` ile aynı
beklentileri (mesafe hesabı, Türkçe gün başlığı, JSON round-trip) kontrol
eder — iki platformun aynı gün dosyasını üretip okuyabildiğinden emin olmak
için.
