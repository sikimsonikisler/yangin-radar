# Kaldığımız yer

*Son güncelleme: 2 Eylül 2026*

## Durum: backend çalışıyor ✅ · iOS uygulaması simülatörde çalışıyor ✅

Sunucuyu başlatmak için terminale:

```bash
cd "/Users/reiayanami/Desktop/haydi bakalım" && ./mvnw spring-boot:run
```

Tarayıcı kendiliğinden açılır. Durdurmak için `Ctrl + C`.

Telefondan denemek için (aynı Wi-Fi'da): `http://<mac-ip>:8080`
IP için: `ipconfig getifaddr en0`

## Şu ana kadar biten

- Spring Boot backend — ihbar, fotoğraf doğrulama, güven puanı, canlı akış (SSE)
- Telefon arayüzü — alt sekmeler, kart listesi, 3 adımlı ihbar akışı, ana ekrana eklenebilir (PWA)
- Yetkili paneli — `admin.html`, anahtar: `degistir-beni`
- NASA FIRMS uydu entegrasyonu (anahtar bekliyor)
- **Oy tekilleştirme** — oy ucu artık cihaz kimliği istiyor. Öncesinde aynı kişi bir
  ihbarı sınırsız kez destekleyip güven puanını şişirebiliyordu; bu, kanıt zincirinin
  altını oyan bir açıktı. Bir cihaz bir ihbara tek oy verir, fikrini değiştirirse
  oyu güncellenir. Web ve iOS istemcilerinin ikisi de güncellendi.
- **App Store gizlilik bildirimi** — `PrivacyInfo.xcprivacy`. Apple 2024'ten beri
  zorunlu tutuyor; onsuz yükleme reddediliyor. Şifreleme ihracat beyanı
  (`ITSAppUsesNonExemptEncryption`) da eklendi.
- **Şema göçü (Flyway)** — şema artık `db/migration` altındaki sıralı dosyalarla
  yönetiliyor; Hibernate yalnızca doğruluyor (`ddl-auto: validate`). V1 temel şema,
  V2 sık çalışan sorgular için indeksler (fotoğraf `sha256` tekrar kontrolü ve
  ihbar konum kutusu, ikisi de her istekte çalışıyordu ve indekssizdi).
  Yeni alan gerektiğinde `V3__*.sql` yazılmalı.
- **Kanıt zinciri** — uygulama içi kamera + canlı konum ölçümü + tek kullanımlık kod
  + Apple App Attest imzası. Fotoğrafın gerçekliği artık EXIF'e değil, Apple'ın kök
  sertifikasına kadar doğrulanan bir imzaya dayanıyor. İmza fotoğrafın özetini de
  kapsıyor, yani kare değiştirilemiyor. Ayrıntı: [ios/README.md](ios/README.md)
- **Fotoğraf kuralı** — fotoğraf göndermek için zorunlu değil, ama fotoğrafsız ve
  desteklenmeyen bir vatandaş ihbarı doğrulanamıyor ve yetkiliye iletilemiyor.
  Ayrıntı: [ios/README.md](ios/README.md) "Fotoğraf kuralı"
- **iOS uygulaması** — `ios/` klasöründe, SwiftUI. Harita, liste, detay, 3 adımlı
  ihbar akışı ve ayarlar ekranları hazır; sunucunun `/api` uçlarına bağlanıyor.
  iPhone 17 simülatöründe uçtan uca denendi: dört sekme, ihbar gönderme akışı,
  oy verme, canlı akış (SSE) ve bağlantı sınaması çalışıyor.
  Ayrıntı: [ios/README.md](ios/README.md)

## Sıradaki adımlar

1. **Ücretsiz FIRMS anahtarı al** — 5 dakika, uygulama gerçek yangın verisiyle dolar
   <https://firms.modaps.eosdis.nasa.gov/api/area/>
   Sonra: `export FIRMS_MAP_KEY=anahtar` ve yeniden başlat

2. **Sunucuyu internete taşı** — Railway/Render, HTTPS gelsin
   (telefonda konum izni yalnızca HTTPS'te çalışıyor)

3. **Apple Developer hesabı** — yıllık 99 $, App Store Connect için gerekli

4. **Kanıt zincirini gerçek iPhone'da dene** — *ertelendi.* Mac telefonla güven
   ilişkisi kuramadı: "Trust This Computer" uyarısı hiç çıkmadı, USB'de cihaz
   görünüyor ama Xcode sürüm bilgisini bile okuyamıyor. Kod tarafında eksik yok;
   App Attest ve kamera simülatörde çalışmadığı için yalnızca bu ikisi ekranda
   sınanmadı. Sunucu internete taşındıktan sonra tekrar denenmeli - o zaman
   `.local` uğraşı da kalmaz.

## Yayına çıkmadan önce hatırlanacaklar

- **Harita döşemeleri:** Web arayüzü hâlâ OpenStreetMap'in halka açık sunucusunu
  kullanıyor; yayınlanmış uygulama için politikaları izin vermiyor. MapTiler'a ya da
  kendi döşemelerinize geçilmeli. (iOS uygulaması MapKit kullandığı için bu sorunu yaşamıyor.)
- **iOS ATS istisnası:** `ios/Info.plist` içindeki `NSAllowsLocalNetworking` sadece
  geliştirme içindir; sunucu HTTPS'e taşınınca silinmeli.
- **Bundle kimliği:** `com.yanginradar.app` şu an yer tutucu — gerçek hesapla değişecek.
- **Konumlandırma:** "Yangın gözlem ve topluluk bildirim ağı" çerçevesi korunmalı.
  "İtfaiyeye haber verir" iddiası, OGM/AFAD anlaşması olmadan App Store'dan döner.
- **Yetkili anahtarı** `degistir-beni` sadece geliştirme için — dağıtımda SSO gerekir.
- Maliyet tahmini ve tam yol haritası README'nin "App Store'a giden yol" bölümünde.
