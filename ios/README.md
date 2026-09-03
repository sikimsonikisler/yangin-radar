# Yangın Radar — iOS uygulaması

SwiftUI ile yazılmış native iOS istemcisi. Kök dizindeki Spring Boot sunucusunun
`/api` uçlarına bağlanır; kendi veritabanı ya da iş mantığı yoktur.

## Hızlı başlangıç

1. **Sunucuyu çalıştır** (ayrı bir terminalde):

   ```bash
   cd "/Users/reiayanami/Desktop/haydi bakalım" && ./mvnw spring-boot:run
   ```

2. **iOS platformunu indir** (bir kereliğine, ~7–10 GB):

   ```bash
   xcodebuild -downloadPlatform iOS
   ```

3. **Uygulamayı derle ve simülatörde çalıştır:**

   ```bash
   cd "/Users/reiayanami/Desktop/haydi bakalım/ios" && xcodebuild -scheme YanginRadar -destination 'platform=iOS Simulator,name=iPhone 17' build
   ```

   Ya da Xcode'da: `YanginRadar.xcodeproj` dosyasını çift tıkla, üstteki cihaz
   listesinden bir iPhone seç, `⌘R`.

Simülatörde sunucu adresi `http://localhost:8080` olarak gelir; Mac'te çalışan
sunucuyu doğrudan görür. Gerçek telefonda Ayarlar sekmesinden Mac'in yerel ağ
adresini (`ipconfig getifaddr en0`) yazmak gerekir.

## Klasör düzeni

```
ios/
├── Info.plist                  Yalnızca yerel ağ (ATS) istisnası
├── YanginRadar.xcodeproj/
└── YanginRadar/
    ├── YanginRadarApp.swift    Giriş noktası
    ├── Models/                 Sunucu DTO'larının Swift karşılıkları
    │   ├── Enums.swift         Severity / ReportStatus / ReportSource / PhotoVerdict
    │   └── FireReport.swift    FireReport, PhotoEvidence, Stats, CreateReportRequest
    ├── Services/
    │   ├── APIClient.swift     REST çağrıları (async/await)
    │   ├── LiveFeedClient.swift SSE canlı akış, kopunca kendiliğinden yeniden bağlanır
    │   ├── JSONBicim.swift     Sunucunun ISO-8601 tarih biçimini çözer
    │   └── LocationProvider.swift  Konum izni + il/ilçe çevirisi
    ├── State/AppStore.swift    Tek veri kaynağı (@Observable)
    ├── Views/                  Harita, Liste, Detay, İhbar akışı, Ayarlar
    └── Assets.xcassets/        Uygulama ikonu ve vurgu rengi
```

## Ekranlar

| Sekme | Ne yapar |
|---|---|
| **Harita** | Tüm kayıtlar MapKit üzerinde; işaret boyutu büyüklüğe, solgunluğu duruma göre. Üstte canlı sayaçlar ve son olay şeridi. |
| **İhbarlar** | Süzgeçli ve aranabilir liste; satıra dokununca detay. |
| **İhbar Et** | Üç adım — haritadan yer seç, büyüklük seç, anlat + fotoğraf ekle. |
| **Ayarlar** | Sunucu adresi, bağlantı sınama, takma ad, özet sayaçlar. |

## Kanıt zinciri

Fotoğrafın gerçekliği **EXIF'e dayanmaz**. EXIF yazılabilir bir alandır: herhangi bir
görsele istenen GPS, zaman ve cihaz imzası tek komutla yazılabilir. Zincir bu yüzden
dört ayrı halkadan oluşur ve her halka istemcinin *söylediğine* değil, doğrulanabilir
bir şeye dayanır.

```
1. Uygulama içi kamera      kare kitaplıktan seçilmez, o an çekilir
2. Canlı konum ölçümü       EXIF'ten okunmaz; çekim anında cihazdan ölçülür
3. Tek kullanımlık kod      sunucudan çekimden hemen önce alınır, bir kez harcanır
4. App Attest imzası        Apple'ın kök sertifikasına kadar doğrulanır
```

**İmza neyi kapsar:** `nonce | ihbarNo | SHA-256(fotoğraf)`. Yani imzalı bir yüklemede
kare değiştirilemez — başka bir fotoğraf konursa özet tutmaz.

**En önemli özellik:** istemci "ben doğrulandım" diyemez. Güven, sunucunun imzayı
doğrulamasından gelir. Uydurma bir `keyId`/`assertion` gönderilirse sunucu bunu
sessizce reddeder ve fotoğrafı "cihaz doğrulanmadı" olarak işaretler. Bunun testi:
`CaptureChainTest.uydurmaImzaGuvenKazandirmaz`.

### Uçlar

| Uç | Ne yapar |
|---|---|
| `POST /api/attest/challenge` | App Attest anahtar kaydı için nonce |
| `POST /api/attest/register` | Cihazın anahtarını doğrulayıp kaydeder (cihaz başına bir kez) |
| `POST /api/capture/challenge` | Çekimden hemen önce alınan tek kullanımlık kod |
| `POST /api/reports/{id}/photos` | `cekim`, `keyId`, `assertion` alanlarını da kabul eder |

### Kurulum

App Attest'in çalışması için sunucuya Apple Team ID'niz gerekir:

```bash
export APPLE_TEAM_ID=ABCDE12345
```

Boş bırakılırsa uygulama çalışmaya devam eder, ancak hiçbir imza doğrulanamaz ve
tüm fotoğraflar "cihaz doğrulanmadı" sayılır.

### Sınırlar — dürüstçe

- **App Attest simülatörde çalışmaz.** `DCAppAttestService.isSupported` false döner.
  Gerçek cihazda sınanması gerekir.
- **Kamera simülatörde yoktur.** Kamera ekranı bunu açıkça söyleyip düşer.
- Bu katman fotoğrafın **kaynağını** kanıtlar, **içeriğini** değil. Gerçek bir
  cihazdan, gerçekten o noktada çekilmiş bir mangal fotoğrafı da zinciri geçer.
  İçerik sınıflandırması ayrı bir iştir; `PhotoVerificationService`'teki
  `contentClassificationHook` bunun için ayrılmış noktadır.

## Fotoğraf kuralı

Fotoğraf **göndermek için değil, yükselmek için** zorunludur.

- Fotoğrafsız ihbar gönderilebilir ve haritada görünür.
- Ama bir vatandaş ihbarı, **kabul edilmiş (GECERLI) bir fotoğrafı** ya da
  **yakındaki başka bir kaynağın desteği** (bağımsız ihbar / uydu ısı noktası)
  olmadan "Bekliyor"un üstüne çıkamaz.
- Aynı kayıt yetkiliye de iletilemez: `POST /api/admin/reports/{id}/dispatch`
  böyle bir kayıtta 400 döner. Karar yine insanındır — `?zorla=true` ile bilerek
  aşılabilir.
- Topluluk oyları bilerek dayanak sayılmaz; oy ucu kimlik doğrulaması istemiyor,
  dolayısıyla tek başına çoğaltılabilir bir sinyal.

Uygulama bunu kullanıcıya üç yerde söyler: ihbar akışının fotoğraf bölümünde,
özet satırındaki "Kanıt" alanında ve gönderim sonrası ekranda.

## Sunucuyla sözleşme

| Uç | Kullanan |
|---|---|
| `GET /api/reports` | Açılışta ve yenilemede liste |
| `GET /api/stats` | Üstteki sayaçlar |
| `POST /api/reports` | İhbar akışının sonu |
| `POST /api/reports/{id}/photos` | Fotoğraf ekleme (multipart, alan adı `file`) |
| `POST /api/reports/{id}/vote?confirm=` | Detaydaki "Doğruluyorum / Göremiyorum" |
| `GET /api/stream` | Canlı akış (SSE) |
| `GET /api/photos/{id}/content` | Detaydaki fotoğraf karesi |

Sunucudaki bir enum'a yeni bir değer eklenirse `Models/Enums.swift` de güncellenmeli;
aksi halde o kaydın çözümlemesi başarısız olur.

## App Store hazırlığı

- `PrivacyInfo.xcprivacy` — Apple'ın zorunlu tuttuğu gizlilik bildirimi. Toplanan
  veri türleri (konum, fotoğraf, kullanıcı içeriği, cihaz kimliği) ve kullanılan
  gerekçeli API'ler (`UserDefaults` / `CA92.1`) burada bildiriliyor. Yeni bir veri
  türü toplanmaya başlanırsa bu dosya da güncellenmeli.
- `ITSAppUsesNonExemptEncryption = NO` — yalnızca HTTPS ve Apple'ın kendi
  çerçeveleri kullanıldığı için muafiyet kapsamında.

## Yayına çıkmadan önce

- `Info.plist` içindeki `NSAllowsLocalNetworking` istisnası yalnızca geliştirme
  içindir. Sunucu HTTPS'e taşınınca bu bölüm tamamen silinmeli.
- `PRODUCT_BUNDLE_IDENTIFIER` şu an `com.yanginradar.app`. Apple Developer
  hesabındaki gerçek kimlikle değiştirilmeli.
- Konumlandırma çerçevesi korunmalı: uygulama **gözlem ve topluluk bildirim ağıdır**,
  resmî ihbar hattı değildir. Ayarlar ekranındaki metin ve ihbar akışındaki
  112/177 uyarısı bu yüzden var — kaldırılmamalı.
