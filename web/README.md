# DayAtlas Web (PWA)

iPhone / iPad Safari (veya herhangi bir modern tarayıcı) için Progressive Web
App. Native iOS uygulaması değil — tarayıcıdan açılır, **Ana Ekrana Ekle** ile
ikon gibi kullanılır.

## Adres

CI `main`'e `web/` push’landığında GitHub Pages’e yayınlar:

**https://alid67-git.github.io/DayAtlas/**

İlk seferde repo → Settings → Pages → Source: **GitHub Actions** seçili olmalı.

## Ana ekrana ekleme (iPhone)

1. Safari ile yukarıdaki adresi açın (Chrome’da “Paylaş → Ana Ekrana Ekle” iOS’ta yok; Safari kullanın).
2. Paylaş düğmesi (□↑) → **Ana Ekrana Ekle**.
3. DayAtlas simgesinden açın (standalone, tarayıcı çubuğu gizlenir).

## Ne yapar / ne yapamaz

| | Web PWA | Android uygulaması |
| --- | --- | --- |
| Ön planda seyrek GPS | Evet (ayarlanabilir aralık) | Evet |
| Arka plan / kilit ekranı | **Hayır** (Safari kısıtı) | Evet (AlarmManager) |
| Harita + gün gezme | Evet | Evet |
| GPX dışa aktarma | Evet (Paylaş / indir) | Evet |
| Veri deposu | Bu cihazda IndexedDB | `files/days/` |

iOS Safari, ana ekran PWA’sına sabit aralıklı arka plan konumuna izin vermez.
Günlük iz için uygulamayı açık tutun veya gün içinde ara sıra açın; Android’deki
sessiz arka plan kaydının yerine geçmez.

## Yerel deneme

```bash
cd web
python3 -m http.server 8080
# http://localhost:8080
```

Konum için HTTPS veya localhost gerekir.
