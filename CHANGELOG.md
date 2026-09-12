# Changelog

## 0.7.22 — 2026-09-12

- **Play Store yayını için altyapı:** Yeni bir `playRelease` derleme türü ve
  imzalama yapılandırması eklendi — repoya asla commit edilmeyen, yerel
  `keystore.properties` dosyasından okunan ayrı bir yükleme anahtarıyla
  imzalanıyor. Bu dosya yoksa (CI dahil her zamanki durum) yeni derleme
  türü hiç var olmuyor; mevcut sideload akışı tamamen değişmeden kalıyor.
  Play Store'a yüklenecek `.aab` şu şekilde alınır:
  `./gradlew bundlePlayRelease`.

## 0.7.21 — 2026-09-12

- **Küçük ekranlara özel sıkıştırma:** Bugün kartları ızgarasının satır
  sınırı artık ekranın gerçek yüksekliğine göre ayarlanıyor. Normal/büyük
  ekranlarda hiçbir şey değişmedi (yine 2 satıra kadar); yükseklik payı
  dar olan cihazlarda ızgara 1 satıra düşüyor, harita için daha fazla yer
  kalıyor.

## 0.7.20 — 2026-09-12

- **Günlük mod artık varsayılan olarak açık:** Ayarlar'a hiç girmemiş
  kurulumlarda uygulama artık otomatik olarak tüm günü kaydediyor —
  elle Başlat/Durdur düğmesi ve ipucu satırı bu durumda hiç görünmüyor.
  İsteyen Ayarlar'dan Günlük Modu kapatıp elle kontrole geçebilir.

## 0.7.19 — 2026-09-12

- **Harita daha uzaktan gösteriliyor:** Rota sığdırılırken bırakılan kenar
  boşluğu artırıldı, tek nokta / tek sıçrama görünümlerinin sabit zoom
  seviyeleri bir kademe düşürüldü — harita artık rotanın etrafında daha
  büyük bir alan gösteriyor, gereğinden fazla yakınlaşmıyor.

## 0.7.18 — 2026-09-11

- **Gün kartları 2 satırla sınırlandı:** 3. satıra geçince (7 kart) üst
  ızgara çok yükseliyor, haritaya yeterince yer kalmıyordu — bu da harita
  render sorununu geri getiriyordu. Izgara artık en fazla 2 satır
  yüksekliğinde; fazlası ızgaranın içinde kaydırılarak görülür, harita
  her zaman aynı boyutta kalır.
- **Kartları sürükleyip sıralama:** uzun basıp sürüklerken artık kart
  belirgin şekilde kalkıyor (büyüme + gölge) — sıralamanın çalıştığı
  net görülüyor.

## 0.7.17 — 2026-09-11

- **Gün gezici + seçili gün kartı haritanın üzerine taşındı:** Bu satırlar
  artık haritanın altında ayrı yer kaplamıyor, haritanın üzerinde yüzen
  bir katman olarak duruyor — harita her zaman tüm ayrılan alanı
  kullanabiliyor.

## 0.7.16 — 2026-09-11

- **Boş / şerit harita, kök sebep:** 0.7.12–0.7.15’teki yeniden sığdırma
  mantığı sonuçtan (harita boyutu değişince) hareket ediyordu; asıl
  sebep her açılışta ilk layout'un bugünün istatistik ızgarası ve
  günlük mod'daki başlat/ipucu satırları henüz boşken çizilmesiydi —
  harita o anda gereğinden büyük bir pay alıyor, veriler gelince
  ızgara büyüyüp harita küçülüyor, osmdroid da yeni boyutu tam
  yansıtamayıp beyaz alan + ince kiremit şeridi bırakıyordu. Artık
  ızgaranın satır sayısı (yer tutucu değerlerle) ve günlük moddaki
  başlat/ipucu görünürlüğü `onCreate`'te, ilk layout'tan önce,
  senkron olarak ayarlanıyor — harita ilk kareden itibaren doğru
  boyutta, sonradan küçülüp yeniden sığdırılması gerekmiyor.

## 0.7.15 — 2026-09-11

- **Boş / şerit harita:** İlk kamera sığdırması çoğu zaman `minHeight`
  ile oluyordu; pane büyüyünce projeksiyon güncellenmediği için uzun
  beyaz alan + altta ince kiremit şeridi kalıyordu. Boyut değişince ve
  layout oturunca kamera yeniden sığdırılıyor. Yatayda istatistikler tek
  satır.

## 0.7.14 — 2026-09-11

- **Boş harita:** Kısa MapView’da `zoomToBoundingBox` kenar boşluğu
  (96px) negatif iç boyut / NaN zoom üretiyordu → beyaz dikdörtgen.
  Kenar boşluğu harita boyutuna göre sınırlanıyor; bozuk zoom’a düşme
  koruması var. Harita kartların hemen altına alındı; bugün için tekrar
  eden gün-özet şeridi gizleniyor.

## 0.7.13 — 2026-09-11

- **ANR / boş harita (güçlü düzeltme):** Canlı GPS artık haritayı
  silip zoomlamıyor; polyline uç noktası güncelleniyor. UI disk okuması
  arka planda; aynı yerdeki örnekler yalnızca saati güncelliyor.
  DayStore süreç geneli kilit + bugün önbelleği.

## 0.7.12 — 2026-09-11

- **ANR / boş harita düzeltmesi:** GPS kaydı disk yazımını ana thread’den
  aldı; UI yenilemesi sönümlendi; her örnekte GPX yazımı kalktı; harita
  zoom animasyonu canlı güncellemede kapatıldı. Yatayda haritaya min.
  yükseklik.

## 0.7.11 — 2026-09-11

- **Gün ortası güncelleme kontrolü:** Her gün yerel saat 12:00’te sessiz
  güncelleme kontrolü (GPS kaydından bağımsız). Öğleden sonra açılış /
  örnekleme yedek olarak kaçan kontrolü tamamlar; günde bir kez.

## 0.7.10 — 2026-09-11

- **Aynı yerde üst üste nokta yok:** Sabit dururken (~80 m) yeni GPS
  ölçümü haritaya ikinci pin eklemez; son noktanın saati güncellenir.
  Aralık seyrelirken spaghetti birikmez.

## 0.7.9 — 2026-09-11

- **Sabit otururken GPS hızı:** Ev GPS titreşimi artık aralığı yeniden
  sıkılaştırmaz. Sabit çapa (~80 m) + ardışık 2 dış ölçüm şartı; tek
  sıçrama seyreltilmiş aralığı bozmaz.

## 0.7.8 — 2026-09-11

- **Tek renk kartlar:** Günlük istatistik kutuları aynı beyaz kart stili.
- **Aktif GPS alma hızı:** Etiket güncellendi (TR/EN/DE).
- **Ev GPS toleransı:** 25 m içindeki titreşim yerinde güncellenir (spaghetti
  büyümez); mesafe/aktif süre/maks. hız kısa ve yavaş segmentleri yok sayar.
  Atlama engeli kayıtta zaten açıktı; mesafe hesabında da uygulanır.
- **3. satır ortalı:** Eksik son satır yatayda ortalanır; kartlar biraz genişletildi.

## 0.7.7 — 2026-09-11

- **Tam uygulama dilleri (TR / EN / DE):** Tüm arayüz metinleri üç dilde.
  Ayarlar → Dil (sistem / Türkçe / English / Deutsch). Yardım metinleri
  uygulama dilini izler; istenirse yardım içinde dil değiştirilebilir.

## 0.7.6 — 2026-09-11

- **Üst sağ ikonlar kaldırıldı:** Marka şeridindeki GPX dışa aktar ve
  Ayarlar düğmeleri çıkarıldı (Rotalar / Daha fazla sekmelerinde zaten
  var).

## 0.7.5 — 2026-09-11

- **Günlük sabit üst / gezici alt:** Üst özet ve kartlar her zaman bugünü
  gösterir; alttaki gün okları önceki günlerin haritasını ve kompakt
  Mesafe / Son nokta / Nokta sayısı şeridini değiştirir.
- **Daraltılmış sürüklenen kartlar:** Tüm gün istatistikleri (hız/süre
  dahil) tek 3 sütunluk ızgarada; uzun basıp yer değiştirilebilir.
- **Dinamik GPS alma hızı:** Kart, ayardaki sabit değer yerine o anki
  etkili örnekleme aralığını gösterir.
- **Modern klasik palet:** Gri / mavi / kırmızı / beyaz.
- **Yedek / GPX:** Otomatik Drive yedeği takvim gününde bir kez; aynı
  boyutlu dosyalar yeniden yüklenmez. Dışa aktarım adı her zaman `.gpx`.

## 0.7.4 — 2026-09-11

- **Rotalar (Adım 4):** Kayıtlı gün listesi (mesafe / nokta / aktif süre).
  Dokununca Günlük haritasında açılır; satırdaki dışa aktar ile tek gün
  GPX; üstteki Aralık GPX ile aralık seçimi.

## 0.7.3 — 2026-09-11

- **İstatistikler (Adım 3):** Bugün / 7 gün / 30 gün / Bu ay / Tümü
  aralığında toplam mesafe, aktif gün, maks/ort. hız, aktif süre, en uzun
  gün ve günlük mesafe çubukları.

## 0.7.2 — 2026-09-11

- **Günlük sekme (Adım 2):** Marka şeridi; 2×2 sürüklenen veri kartları
  (mesafe / son nokta / nokta / GPS alma hızı); üç renkli hız-süre şeridi
  (maks / aktif süre / ort.). Harita ve gün gezici aynı.

## 0.7.1 — 2026-09-11

- **Alt navigasyon (Adım 1):** Günlük / İstatistikler / Rotalar / Daha fazla.
  Mevcut ana ekran Günlük sekmesinde; diğer iki sekme yer tutucu; Daha
  fazla’da Ayarlar, Yardım ve sürüm bilgisi.

## 0.7.0 — 2026-09-10

- **Bordo renk paleti:** Uygulama genelindeki yeşil tema bordo ağırlıklı
  bir palete geçirildi (araç çubuğu, düğmeler, rota çizgisi, başlangıç
  işareti, uygulama simgesi).
- **Yeni gün istatistikleri:** Maksimum hız, ortalama hız ve aktif sürüş
  süresi eklendi (GPS sıçramaları ve >20 dk’lık duraklamalar hesaba
  katılmaz). Haritanın üstündeki kartlar artık uzun basıp sürükleyerek
  sıralanabiliyor; Ayarlar → **Harita gün istatistikleri**’nden
  hangilerinin görüneceği seçilebiliyor.
- **Akıllı günlük yedek:** İlk tam Drive yedeği tamamlandıktan sonra
  günlük otomatik yedek artık tüm geçmişi taramıyor, yalnızca bugünün
  dosyasını kontrol ediyor.
- **Yardım ekranı:** Araç çubuğu taşma menüsüne Türkçe/İngilizce/Almanca
  arasında geçiş yapılabilen kapsamlı bir yardım ekranı eklendi.
- **Küçük düzeltmeler:** Dışa aktar simgesinin rengi (araç çubuğuyla
  uyumsuzdu) düzeltildi; "Günlük mod açıkken…" açıklama metni kaldırıldı;
  Ayarlar ekranının altına GPS kontrol hızı ve geliştirici bilgisi
  eklendi.

## 0.6.13 — 2026-09-10

- **Yedek: tekrar yükleme yok:** "Şimdi yedekle" artık yalnızca yeni veya
  değişen gün dosyalarını yükler; boyutu aynı kalan (zaten yedeklenmiş)
  dosyalar tekrar yüklenmez.
- **Yedekten geri yükle:** Ayarlar'a yeni buton — seçili klasördeki gün
  kayıtlarını telefona geri kopyalar. Yeni telefonda veya uygulamayı
  silip yeniden kurduktan sonra, önce aynı klasörü seçip bu düğmeyle
  verileri geri getirebilirsiniz.

## 0.6.12 — 2026-09-10

- **Sürekli "güncelle" isteği düzeltildi:** başarıyla kurulan güncelleme
  APK'sı diskte silinmeden kalıyordu; uygulama her açıldığında bu dosyayı
  "bekleyen güncelleme" sanıp tekrar kurulum istiyordu (sonsuz döngü).
  Artık uygulama güncellendiğinde (`MY_PACKAGE_REPLACED`) indirilen APK
  dosyası da siliniyor.

## 0.6.11 — 2026-09-10

- **Açılışta çökme düzeltildi:** güncelleme bildirimi için oluşturulan
  `PendingIntent`, Android 14+ (targetSdk 34) hedefli uygulamalarda
  implicit `Intent` ile `FLAG_MUTABLE` kullanmayı yasaklıyor —
  `IllegalArgumentException` ile her `MainActivity.onResume()`'da (yani
  her açılışta) çöküyordu. `FLAG_IMMUTABLE`'a çevrildi.

## 0.6.10 — 2026-09-10

- **CI derleme hatası düzeltildi:** `DriveFolderBackup.kt` içindeki doc
  comment'te backtick içinde geçen `` `files/days/*` `` metni, Kotlin'de
  iç içe blok yorum açıp dosyanın geri kalanını yorum içine alıyordu
  (derleme "Unclosed comment" hatasıyla düşüyordu). Bu yüzden 0.6.9 hiç
  yayınlanamadı ve `android-latest` sürümü 0.6.8'de takılı kaldı —
  telefonlar kendini güncelleyemedi. Metin düzeltildi, derleme artık geçiyor.

## 0.6.9 — 2026-09-10

- **Günlük Google Drive yedek:** Ayarlar’da klasör seç (Drive önerilir) +
  otomatik yedek aç. Günde bir kez tüm `days/*` JSON+GPX seçilen klasöre
  kopyalanır; Drive istemcisi buluta senkronlar. “Şimdi yedekle” ile elle.

## 0.6.8 — 2026-09-10

- **GPS atlama filtresi:** yeni noktalar önceki noktaya göre ~160 km/sa veya
  30 km’den fazla sıçrıyorsa kayda yazılmaz.
- **Atlama temizliği:** haritada turuncu işaretler; “Atlamalar (N)” listesi —
  satıra veya işarete dokununca noktayı siler, mesafeyi yeniden hesaplar.

## 0.6.7 — 2026-09-10

- **Güncelleme kurulumu (sıkılaştırma):** indirme bitince hem broadcast hem
  poll; kurulum her zaman denenir + “kurmak için dokun” bildirimi.
  PendingIntent URI grant (OEM) düzeltmesi. Eski sürümün indirdiği ama
  kurmadığı APK uygulama açılınca hatırlatılır.
  Not: 0.6.5 ve öncesi kendini güncelleyemez — bir kez elle 0.6.7 kurun.

## 0.6.6 — 2026-09-10

- **Otomatik güncelleme kurulumu:** indirme bitince sistem kurulum ekranı /
  “kurmak için dokun” bildirimi geliyor (Android 13+ `DOWNLOAD_COMPLETE`
  receiver + süreç ölümü yüzünden sessiz kalıyordu). Bekleyen APK uygulama
  açılınca hatırlatılıyor.

## 0.6.5 — 2026-09-10

- **Sabit nokta seyreltme:** kullanıcı aralığı taban kalır; ~25 m içinde art
  arda 3 GPS ölçümü olunca etkili aralık bir üst kademeye çıkar
  (30 sn → 1 → 3 → 5 dk). Hareket edince tabana döner — gece otururken
  sık uyanma / pil tüketimini azaltır.

## 0.6.3 — 2026-09-09

- **Seçilen gün özeti:** harita gün gezicinin altında yumuşak yeşil tonlu
  üç hücre (mesafe / son nokta / nokta sayısı). Üst blok bugünün kaydı;
  oklarla gezerken alt şerit o günü gösterir.

## Web PWA — 2026-09-09

- **DayAtlas Web (PWA)** eklendi (`web/`): Safari’den açılıp Ana Ekrana Ekle
  ile kullanılabilir. Ön planda seyrek GPS, harita, gün gezme, GPX dışa
  aktarma. Canlı adres: `https://alid67-git.github.io/DayAtlas/web/`
  (kök URL uygulamaya yönlendirir). iPhone Safari arka plan GPS’e izin
  vermez — Android’in yerini tutmaz.

## 0.6.2 — 2026-09-09

- **Harita boş durumu:** “Bu gün için kayıt yok” yarı saydam kutuda, koyu
  yazı — harita üzerinde okunur.
- **Bugüne dön:** harita gün gezicisinde, bugün dışındayken takvim ikonu.
- **Otomatik güncelleme:** `isFinishing` yüzünden RecentsHider sonrası
  sessiz indirme iptal oluyordu — `applicationContext` ile devam ediyor.
  Arka planda kurulum için “Güncelleme hazır / dokunun” bildirimi.
  Arka plan kontrolü başarısız olunca 24 saat beklemeyi bırakıyor (stamp
  check sonrasına alındı). Üst çubuk inset güçlendirildi.

## 0.6.1 — 2026-09-09

- **GPX aralık dışa aktarma:** her gün ayrı `<trk>` (tek güne yığılma
  düzeltildi); her noktada gerçek UTC `<time>` (`yyyy-MM-dd'T'HH:mm:ss'Z'`).
- **Dosya adı:** kaydet/paylaşırken ismin sonuna `.gpx` otomatik eklenir
  (alanda da `.gpx` soneki görünür).
- **Üst çubuk:** status bar (saat) ile çakışmayı önlemek için toolbar
  sistem inset kadar aşağı kaydırıldı.
- **Örnekleme aralıkları:** 30 sn / 1 dk (önerilen, yeni varsayılan) /
  3 dk / 5 dk. Eski 3/4/5 dk tercihi saniyeye taşınıyor.

## 0.6.0 — 2026-09-09

- **Harita ana ekranın altında.** Ayrı Rota ekranı kaldırıldı; OpenStreetMap
  görünümü ana ekranın alt yarısında. Oklarla önceki günlere gidilebilir.
  Üstte bugünün özeti (mesafe, son nokta, başlat/durdur) duruyor.
- **GPX dışa aktarma.** Araç çubuğundaki paylaş simgesi tek gün veya tarih
  aralığı seçtirir, dosya adını sorar, sistem paylaşım ekranından
  kaydetmeye/göndermeye açar. Aralıkta her gün ayrı `<trkseg>` olur.

## 0.5.3 — 2026-09-09

- **Görev değiştiriciden çıkarma artık görev sonlandırmayla yapılıyor.**
  Manifest `excludeFromRecents` + çalışma zamanı `setExcludeFromRecents`
  bazı OEM arayüzlerinde (özellikle Samsung One UI) tutarsız kalıyordu —
  görev arka planda hayattayken kart yine de görünebiliyordu. "Çalışan
  uygulamalar listesinde görünmeyen" uygulamaların kullandığı yöntem:
  UI tamamen arka plana geçince `AppTask.finishAndRemoveTask()` ile görevi
  kaldırmak. `RecentsHider` bunu uygulama düzeyinde yapıyor (kısa gecikme:
  izin diyaloğu titremesinde görevi bozmamak için). Sistem Ayarları / izin /
  OEM otomatik-başlat ekranlarına çıkarken görev korunuyor ki Geri ile
  dönüş çalışsın; Ana ekran / uygulama değiştiriciden çıkışta kart kalkar.
  AlarmManager + `SampleService` activity'ye bağlı değil — günlük mod
  arka plan kaydı etkilenmez. Yeniden açmak için ana ekran simgesi gerekir
  (zaten `excludeFromRecents` ile de böyleydi).

## 0.5.2 — 2026-09-08

- **Görev değiştiriciden çıkarma (`excludeFromRecents`) güçlendirildi.**
  Kullanıcı, uygulamayı görev değiştiriciden tamamen kapatıp ana ekran
  simgesinden tekrar açtıktan sonra hâlâ ara sıra tam bir kart bıraktığını
  bildirdi — aynı derlemede daha önce bu sorun görülmemişti. Manifest'teki
  statik `android:excludeFromRecents="true"` bayrağının bazı OEM
  arayüzlerinde (bu durumda muhtemelen Samsung One UI) tutarsız
  uygulandığı biliniyor. `MainActivity` artık `onCreate`/`onResume`'da
  `ActivityManager.AppTask.setExcludeFromRecents(true)`'ı da çağırıyor —
  ayrı bir kod yolu, bazı OEM arayüzlerinin statik bayraktan daha
  güvenilir bulduğu bir çalışma zamanı çağrısı. Kesin garanti değil; bu
  bir platform/OEM tutarsızlığı, README'deki otomatik-başlat/pil
  optimizasyonu notlarıyla aynı kategoride.

## 0.5.1 — 2026-09-08

- **Güncelleme kurulumu, "Bilinmeyen uygulamalar yükle" izni verilmeden
  sessizce takılabiliyordu.** Manifest'teki `REQUEST_INSTALL_PACKAGES`
  izni Android 8+'ta tek başına yetmiyor — kullanıcının bu izni DayAtlas
  için ayrıca Ayarlar'dan açması gerekiyor, ama uygulama bunu hiç kontrol
  etmiyordu: indirme bitip kurulum denendiğinde izin yoksa bazı
  cihazlarda görünür bir hata/ekran çıkmadan hiçbir şey olmuyordu.
  `UpdateInstaller` artık kurmadan önce `canRequestPackageInstalls()`'ı
  kontrol ediyor; izin yoksa (yalnızca elle "Güncellemeleri kontrol et"
  akışında — sessiz arka plan kontrolü hâlâ hiçbir şey sormuyor)
  kullanıcıyı doğrudan o izin ekranına yönlendiriyor.

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
