# YanginRadar

Türkiye için canlı yangın takip, ihbar ve **fotoğraf doğrulamalı** veri akışı uygulaması.

Kullanıcılar haritada bir noktayı işaretleyip yangın bildirir, fotoğraf ekler; sistem fotoğrafın
üstverisini otomatik denetler, uydu ısı noktalarıyla ve diğer ihbarlarla çapraz doğrular, sonucu
canlı akış üzerinden tüm açık tarayıcılara anında yayar. Yetkili panelinden ihbarlar incelenir ve
aktarım kuyruğuna yazılır.

---

## ⚠️ Önce şunu okuyun

**Bu uygulama resmi bir ihbar hattı değildir.** Acil durumda **112**, orman yangınları için **177**
aranmalıdır. "Yetkiliye ilet" düğmesi uygulama içinde bir *giden kutusu* kaydı oluşturur ve log'a
yazar — 112, 177 ya da herhangi bir kuruma otomatik bildirim **göndermez**. Gerçek bir kuruma
otomatik ihbar akışı, o kurumun yazılı izni ve resmî entegrasyon kanalı olmadan devreye
alınmamalıdır; yanlış alarm üretmek hem yasal hem operasyonel risktir. Entegrasyon noktası
[`AuthorityNotificationService.transmit`](src/main/java/com/yanginradar/service/AuthorityNotificationService.java)
içinde hazır bırakıldı.

İlk açılışta haritaya **örnek (demo) kayıtlar** yüklenir; bunlar gerçek yangın verisi değildir.
`yanginradar.demo-data: false` ile kapatılır.

---

## IntelliJ'de çalıştırma

1. IntelliJ IDEA'yı açın → **File → Open…** → `Masaüstü / haydi bakalım` klasörünü seçin →
   **Open**. (Klasörü seçin, içindeki bir dosyayı değil.)
2. Sağ altta indirme çubuğu görünür — IntelliJ bağımlılıkları indiriyordur. Bitmesini bekleyin
   (ilk seferde 1–2 dakika).
3. Soldaki ağaçtan şu dosyayı açın:
   `src → main → java → com.yanginradar → YanginRadarApplication`
4. `public class YanginRadarApplication` satırının solundaki **yeşil ▶ üçgene** tıklayın →
   **Run 'YanginRadarApplication'**.
5. Alttaki konsolda `Started YanginRadarApplication` yazısını görünce tarayıcıdan
   **http://localhost:8080** adresini açın.

Durdurmak için konsolun solundaki kırmızı kare (■) düğmesine basın.

> ▶ düğmesi görünmüyorsa proje henüz yüklenmemiştir; biraz bekleyin. Java sürümü sorunu çıkarsa:
> **File → Project Structure → Project → SDK** → `temurin-21` seçin (makinenizde kurulu).

Yetkili paneli: **http://localhost:8080/admin.html** — anahtar: `degistir-beni`
(`yanginradar.moderation.admin-token` ya da `ADMIN_TOKEN` ortam değişkeni ile değiştirilir).

### Ya da terminalden (hiçbir şey kurmadan)

Projede Maven başlatıcısı (`mvnw`) var — Maven'ı kendisi indirir, sizin kurmanız gerekmez:

```bash
cd "/Users/reiayanami/Desktop/haydi bakalım" && ./mvnw spring-boot:run
```

Durdurmak için terminalde **Ctrl + C**.

Testler:

```bash
cd "/Users/reiayanami/Desktop/haydi bakalım" && ./mvnw test
```

> Statik dosyaları (`app.js`, `styles.css`, `*.html`) değiştirdiğinizde tarayıcıda görmek için
> IntelliJ'de projeyi yeniden derleyin (**Cmd/Ctrl + F9**) — çalışan uygulama `target/classes`
> altındaki kopyayı okur. `spring-boot-devtools` bağımlı olduğu için *Build project automatically*
> ayarı açıksa bu kendiliğinden olur.

---

## Telefonda açmak

Uygulama telefon önceliklidir: alt sekme çubuğu, kart listesi, adım adım ihbar akışı.
Ana ekrana eklenince tarayıcı çubuğu olmadan, gerçek bir uygulama gibi açılır.

1. Mac ve telefon **aynı Wi-Fi ağında** olsun.
2. Uygulamayı bilgisayarda çalıştırın.
3. Telefonun tarayıcısında Mac'inizin yerel adresini açın:

   ```
   http://<mac-ip-adresiniz>:8080
   ```

   IP adresini öğrenmek için Mac'te terminale:

   ```bash
   ipconfig getifaddr en0
   ```

4. **iPhone (Safari):** alttaki *Paylaş* → *Ana Ekrana Ekle*.
   **Android (Chrome):** menü → *Uygulamayı yükle*.

> **HTTP üzerinden bilinen sınır:** "Bulunduğum konumu kullan" düğmesi telefonda
> çalışmaz — tarayıcılar konum iznini yalnızca HTTPS'te verir. Haritadan elle nokta
> seçme ve kamerayla fotoğraf çekme sorunsuz çalışır. Uygulamayı HTTPS bir adrese
> kurduğunuzda konum da açılır.

---

## App Store'a giden yol

Bugünkü hâli bir web uygulaması. Mağazaya çıkmak için sırasıyla:

| Adım | Ne gerekiyor | Neden |
|---|---|---|
| 1. Sunucuyu internete taşı | Railway / Render / Fly.io + PostgreSQL + S3 | Telefon `localhost`'a ulaşamaz |
| 2. HTTPS | Yukarıdakiler hazır veriyor | Konum, kamera ve App Store için zorunlu |
| 3. Hesap sistemi | Kayıt / giriş / **hesap silme** | Apple hesap silmeyi şart koşuyor |
| 4. Gizlilik metni | KVKK + App Store gizlilik formu | Onaysız yayına alınmaz |
| 5. Xcode + Apple Developer | Xcode (~7 GB), üyelik 99 $/yıl | Derleme ve gönderim |
| 6. iOS uygulaması | SwiftUI, bu API'ye bağlanır | Kamera, konum, bildirim doğal çalışır |

**Dikkat: içeriğin kendisi bir risk.** Apple, acil durum/afet ihbarı yapan uygulamaları
Guideline 1.4.1 kapsamında sıkı inceler. "İtfaiyeye haber verir" izlenimi veren bir
uygulama, ilgili kurumla (OGM / AFAD) resmî bir anlaşma gösterilmeden reddedilir.
Bu yüzden uygulama baştan **"yangın gözlem ve topluluk bildirim ağı"** olarak
konumlandırıldı ve "resmî ihbar hattı değildir" uyarısı her ekranda duruyor.
Bu çerçeveyi koruyun.

**Neden web sarmalayıcı değil de native?** Apple, sadece siteyi saran uygulamaları
Guideline 4.2 ("yeterli işlevsellik") ile reddediyor. Kamera, konum ve bildirimi
gerçekten kullanan native bir uygulama bu sorunu yaşamaz.

---

## Neler var

| Özellik | Nerede |
|---|---|
| Haritada nokta işaretleyerek ihbar | `static/index.html`, `static/app.js` |
| Fotoğraf doğrulama zinciri | `service/PhotoVerificationService.java` |
| Güven puanı ve otomatik doğrulama | `service/FireReportService.java` |
| Canlı veri akışı (SSE) | `service/LiveFeedService.java` |
| NASA FIRMS uydu ısı noktaları | `service/FirmsIngestService.java` |
| Yetkili paneli ve aktarım kuyruğu | `static/admin.html`, `web/AdminController.java` |
| Telefon arayüzü (alt sekmeler, kartlar) | `static/index.html`, `static/app.js` |
| Ana ekrana eklenebilirlik (PWA) | `static/manifest.json`, `static/sw.js` |
| Açılışta tarayıcıyı açma | `config/TarayiciAcici.java` |

Ek olarak: durum/kaynak/güven/zaman filtreleri, topluluk oyu (doğru / yanlış alarm),
harita ve uydu görüntüsü katmanları, mobil düzen, tarayıcı konumuna otomatik yakınlaşma.

---

## Fotoğraf doğrulama nasıl çalışıyor

Her fotoğraf 0–100 arası puanlanır. Başlangıç 40 puan, üstüne şu kontroller işler:

| Kontrol | Etki | Neden |
|---|---|---|
| Sihirli bayt kontrolü (JPEG/PNG/HEIC/WebP) | geçmezse **ret** | Uzantı değil, dosyanın kendisi doğrulanır |
| Boyut sınırı (12 MB) | aşarsa **ret** | |
| SHA-256 tekrar kontrolü | ilk kez +5 / tekrar −50 | Aynı karenin farklı ihbarlarda dolaşması |
| EXIF GPS ↔ ihbar noktası mesafesi | yakınsa +30 / uzaksa −30 | Fotoğraf gerçekten orada mı çekilmiş |
| GPS Türkiye dışında | −15 | |
| Çekim zamanı tazeliği (≤6 saat) | +20 | Şu an devam eden bir yangının kanıtı mı |
| Çekim zamanı eski | −15 / −30 / −45 (kademeli) | Günler öncesine ait kare kanıt sayılmaz |
| Çekim zamanı gelecekte | −20 | Cihaz saati oynanmış |
| Kamera marka/model imzası | var +10 / yok −10 | Ekran görüntüsü ve indirilmiş görselleri ayıklar |
| Çözünürlük ≥ 640×480 | +5 / −10 | |

Sonuç: **≥70 geçerli**, **35–69 şüpheli**, **<35 geçersiz**. Kullanıcıya ve yetkiliye gerekçelerin
tamamı satır satır gösterilir — kara kutu yok.

**Saat dilimi notu:** EXIF `DateTimeOriginal` saat dilimi taşımaz, kameranın yerel saatidir.
Bunu doğrudan UTC saymak Türkiye'de her fotoğrafı 3 saat ileri kaydırıp "gelecekte çekilmiş"
diye eler. Sıralama: önce GPS zamanı (gerçek UTC) → sonra `OffsetTimeOriginal` → yoksa
`yanginradar.photo.assumed-zone` (varsayılan `Europe/Istanbul`) varsayılır ve bu, rapora yazılır.

Görsel içerik sınıflandırması (duman/alev tanıma) **yapılmaz**; bu katman üstveri tutarlılığını
ölçer. Bir model bağlanacaksa `PhotoVerificationService.contentClassificationHook` hazır.

---

## İhbar güven puanı

```
kaynak ağırlığı            RESMİ 90 · UYDU 60 · VATANDAŞ 20
+ fotoğraf katkısı         (en iyi fotoğraf puanı − 45) × 0,8      →  −36 … +44
+ ikinci geçerli fotoğraf  +8
+ yakında uydu ısı noktası +25          (2,5 km / 3 saat penceresi)
+ bağımsız ihbarlar        her biri +6, en çok +18
+ topluluk desteği         her biri +4, en çok +20
− yanlış alarm bildirimi   her biri −8, en çok −40
```

Fotoğraf katkısının 45 puanda ortalanması bilinçli: düz toplama yapılsaydı "şüpheli" damgası yemiş
bir fotoğraf bile ihbarı güçlendirirdi.

Puan 75'i geçince ihbar **otomatik doğrulanır** — **ancak** kanıtlar çelişiyorsa (fotoğrafın GPS'i
ihbar noktasından uzaksa ya da geçersiz sayılmışsa) otomatik doğrulama yapılmaz, kayıt
`İNCELENİYOR` durumuna alınıp karar bir insana bırakılır.

---

## Uydu verisi (NASA FIRMS)

Ücretsiz bir MAP_KEY alın: <https://firms.modaps.eosdis.nasa.gov/api/area/>

```bash
export FIRMS_MAP_KEY=buraya_anahtariniz
mvn spring-boot:run
```

Anahtar tanımlıysa VIIRS ısı noktaları 15 dakikada bir çekilir, Türkiye kutusuna göre filtrelenir
ve haritaya `UYDU` kaynaklı kayıt olarak işlenir; aynı nokta iki kez eklenmez. Anahtar yoksa servis
sessizce kapalı kalır, uygulama vatandaş ihbarlarıyla çalışmaya devam eder. Panelden **"Uydu
verisini şimdi çek"** ile elle de tetiklenebilir.

---

## API

| Yöntem | Uç | Açıklama |
|---|---|---|
| `GET` | `/api/reports` | Son 200 kayıt |
| `GET` | `/api/reports/{id}` | Tek kayıt |
| `POST` | `/api/reports` | Yeni ihbar (JSON) |
| `POST` | `/api/reports/{id}/photos` | Fotoğraf yükle (multipart `file`) |
| `POST` | `/api/reports/{id}/vote?confirm=true` | Topluluk oyu |
| `GET` | `/api/stats` | Sayaçlar |
| `GET` | `/api/stream` | Canlı akış (SSE) |
| `GET` | `/api/photos/{id}/content` | Fotoğraf içeriği |

Yetkili uçları `X-Admin-Token` başlığı ister:

| Yöntem | Uç |
|---|---|
| `POST` | `/api/admin/reports/{id}/status?status=DOGRULANDI` |
| `POST` | `/api/admin/reports/{id}/dispatch` |
| `GET` | `/api/admin/dispatches` |
| `POST` | `/api/admin/firms/refresh` |
| `GET` | `/api/admin/firms/status` |

Örnek:

```bash
curl -X POST http://localhost:8080/api/reports -H "Content-Type: application/json" -d '{"lat":36.8121,"lon":28.2610,"il":"Muğla","ilce":"Marmaris","severity":"BUYUK_YANGIN","aciklama":"Ormanlık alanda yoğun duman"}'
```

Canlı akış olayları: `yeni-ihbar`, `uydu-noktasi`, `fotograf-eklendi`, `oy-verildi`,
`durum-degisti`, `yetkiliye-iletildi`.

---

## Yapılandırma

Tümü `src/main/resources/application.yml` içinde:

| Ayar | Varsayılan | Ne yapar |
|---|---|---|
| `yanginradar.demo-data` | `true` | İlk açılışta örnek kayıt yükler |
| `yanginradar.photo.max-distance-meters` | `3000` | EXIF GPS ↔ ihbar noktası toleransı |
| `yanginradar.photo.max-age-minutes` | `360` | Fotoğrafın taze sayıldığı süre |
| `yanginradar.photo.assumed-zone` | `Europe/Istanbul` | EXIF'te ofset yoksa varsayılan saat dilimi |
| `yanginradar.moderation.auto-verify-threshold` | `75` | Otomatik doğrulama eşiği |
| `yanginradar.moderation.admin-token` | `degistir-beni` | Yetkili paneli anahtarı |
| `yanginradar.firms.map-key` | *(boş)* | NASA FIRMS anahtarı |

Veritabanı: H2, `./data/yanginradar` altında dosya olarak. Fotoğraflar `./data/uploads`.
H2 konsolu: <http://localhost:8080/h2-console> (JDBC URL `jdbc:h2:file:./data/yanginradar`).
Sıfırdan başlamak için `data/` klasörünü silmeniz yeterli.

---

## Gerçek dağıtımdan önce yapılması gerekenler

Bu proje çalışır durumda, ama halka açık bir servise dönüşecekse şunlar eksik:

- **Kimlik doğrulama.** Yetkili paneli tek bir paylaşılan anahtarla korunuyor; kurumsal SSO/OAuth2
  ile değiştirilmeli. Vatandaş tarafında ihbar ve oy verme kimliksiz — telefon doğrulaması olmadan
  oy sayıları kolayca şişirilebilir.
- **Hız sınırlama.** İhbar ve fotoğraf uçlarında oran sınırı yok.
- **Kalıcı depolama.** H2 dosya veritabanı ve yerel disk yerine PostgreSQL + PostGIS ve nesne
  deposu (S3/GCS) kullanılmalı; mekânsal sorgular şu an kaba bir kutu taramasıyla yapılıyor.
- **KVKK.** İhbar edenin iletişim bilgisi düz metin saklanıyor (panelde maskeleniyor, haritada hiç
  yayınlanmıyor). Aydınlatma metni, saklama süresi ve şifreleme eklenmeli.
- **Gerçek aktarım kanalı** — yukarıdaki uyarıya bakın.

---

## Testler

26 test: mesafe hesapları, fotoğraf doğrulama zinciri (EXIF üreten yardımcıyla — GPS uyuşmazlığı,
saat dilimi, eskime, tekrar kullanım), ve uçtan uca API akışı (ihbar → fotoğraf → oy → yetkili ucu).

```bash
mvn test
```
