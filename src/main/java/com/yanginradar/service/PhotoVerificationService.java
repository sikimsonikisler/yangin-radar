package com.yanginradar.service;

import java.io.ByteArrayInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HexFormat;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.drew.imaging.ImageMetadataReader;
import com.drew.lang.GeoLocation;
import com.drew.metadata.Directory;
import com.drew.metadata.Metadata;
import com.drew.metadata.exif.ExifIFD0Directory;
import com.drew.metadata.exif.ExifSubIFDDirectory;
import com.drew.metadata.exif.GpsDirectory;
import com.yanginradar.config.AppProperties;
import com.yanginradar.config.GeoUtils;
import com.yanginradar.domain.PhotoVerdict;
import com.yanginradar.repo.PhotoEvidenceRepository;

/**
 * Fotograf dogrulama zinciri.
 *
 * <p>Sirasiyla su kontroller calisir ve her biri puani etkiler:
 * <ol>
 *   <li>Sihirli bayt kontrolu - dosya gercekten iddia ettigi formatta mi</li>
 *   <li>SHA-256 tekrar kontrolu - ayni kare baska bir ihbarda kullanilmis mi</li>
 *   <li>EXIF GPS - fotografin cekildigi yer ihbar noktasina yakin mi</li>
 *   <li>EXIF cekim zamani - fotograf taze mi</li>
 *   <li>Kamera imzasi - cihaz marka/model bilgisi var mi (ekran goruntusu ayikla)</li>
 *   <li>Cozunurluk - anlamli bir kare mi</li>
 * </ol>
 *
 * <p>Not: Bu katman fotografin GERCEKTEN yangin gosterdigini iddia etmez; ustveri
 * tutarliligini olcer. Gorsel icerik siniflandirmasi ayri bir model isidir ve
 * {@link #contentClassificationHook} noktasindan baglanabilir.
 */
@Service
public class PhotoVerificationService {

    private static final Logger log = LoggerFactory.getLogger(PhotoVerificationService.class);

    /** EXIF OffsetTimeOriginal - modern telefonlar "+03:00" gibi bir deger yazar. */
    private static final int TAG_OFFSET_TIME_ORIGINAL = 0x9011;

    private static final DateTimeFormatter EXIF_ZAMAN =
            DateTimeFormatter.ofPattern("uuuu:MM:dd HH:mm:ss");

    private final AppProperties props;
    private final PhotoEvidenceRepository photoRepo;

    public PhotoVerificationService(AppProperties props, PhotoEvidenceRepository photoRepo) {
        this.props = props;
        this.photoRepo = photoRepo;
    }

    /** Cekim baglami olmayan yukleme (kitapliktan secim, testler). */
    public PhotoAnalysis analyze(byte[] bytes, String originalFilename,
                                 double reportLat, double reportLon) {
        return analyze(bytes, originalFilename, reportLat, reportLon, CaptureContext.YOK);
    }

    public PhotoAnalysis analyze(byte[] bytes, String originalFilename,
                                 double reportLat, double reportLon,
                                 CaptureContext cekim) {
        List<String> checks = new ArrayList<>();
        int score = 40;

        String sha = sha256(bytes);
        String type = detectType(bytes);

        if (type == null) {
            checks.add("[X] Dosya tanınan bir görüntü biçimi değil (JPEG/PNG/HEIC/WebP bekleniyor).");
            return new PhotoAnalysis(sha, "bilinmiyor", null, null, null, null, null,
                    false, false, 0, PhotoVerdict.GECERSIZ, false, checks);
        }
        checks.add("[OK] Biçim doğrulandı: " + type + " (" + (bytes.length / 1024) + " KB).");

        if (bytes.length > props.getPhoto().getMaxSizeBytes()) {
            checks.add("[X] Dosya boyutu sınırın üstünde.");
            return new PhotoAnalysis(sha, type, null, null, null, null, null,
                    false, false, 0, PhotoVerdict.GECERSIZ, false, checks);
        }

        long duplicates = photoRepo.findBySha256(sha).size();
        if (duplicates > 0) {
            score -= 50;
            checks.add("[!] Bu kare sistemde daha önce " + duplicates
                    + " kez kullanılmış — yeniden paylaşım şüphesi.");
        } else {
            score += 5;
            checks.add("[OK] Kare sistemde ilk kez görülüyor.");
        }

        Double exifLat = null;
        Double exifLon = null;
        Instant takenAt = null;
        String zamanKaynagi = null;
        Double distance = null;
        String cameraMake = null;
        String cameraModel = null;
        Integer width = null;
        Integer height = null;

        try {
            Metadata metadata = ImageMetadataReader.readMetadata(new ByteArrayInputStream(bytes));

            GpsDirectory gps = metadata.getFirstDirectoryOfType(GpsDirectory.class);
            if (gps != null) {
                GeoLocation loc = gps.getGeoLocation();
                if (loc != null && !loc.isZero()) {
                    exifLat = loc.getLatitude();
                    exifLon = loc.getLongitude();
                }
                // GPS zamani uydudan gelir ve gercek UTC'dir - en guvenilir kaynak.
                Date gpsDate = gps.getGpsDate();
                if (gpsDate != null) {
                    takenAt = gpsDate.toInstant();
                    zamanKaynagi = "GPS (UTC)";
                }
            }

            ExifSubIFDDirectory sub = metadata.getFirstDirectoryOfType(ExifSubIFDDirectory.class);
            if (sub != null) {
                if (takenAt == null) {
                    ZamanSonucu z = cekimZamani(sub);
                    takenAt = z.an();
                    zamanKaynagi = z.kaynak();
                }
                width = intOrNull(sub, ExifSubIFDDirectory.TAG_EXIF_IMAGE_WIDTH);
                height = intOrNull(sub, ExifSubIFDDirectory.TAG_EXIF_IMAGE_HEIGHT);
            }

            ExifIFD0Directory ifd0 = metadata.getFirstDirectoryOfType(ExifIFD0Directory.class);
            if (ifd0 != null) {
                cameraMake = ifd0.getString(ExifIFD0Directory.TAG_MAKE);
                cameraModel = ifd0.getString(ExifIFD0Directory.TAG_MODEL);
            }
        } catch (Exception e) {
            log.debug("EXIF okunamadi: {}", e.toString());
            checks.add("[!] Üstveri (EXIF) okunamadı.");
        }

        if (exifLat != null && exifLon != null) {
            distance = GeoUtils.distanceMeters(exifLat, exifLon, reportLat, reportLon);
            double limit = props.getPhoto().getMaxDistanceMeters();
            if (distance <= limit) {
                score += 30;
                checks.add(String.format(
                        "[OK] Fotoğrafın GPS konumu ihbar noktasına %.0f m mesafede (sınır %.0f m).",
                        distance, limit));
            } else {
                score -= 30;
                checks.add(String.format(
                        "[X] Fotoğrafın GPS konumu ihbar noktasından %.1f km uzakta (sınır %.0f m).",
                        distance / 1000d, limit));
            }
            if (!GeoUtils.insideTurkiye(exifLat, exifLon)) {
                score -= 15;
                checks.add("[X] Fotoğrafın GPS konumu Türkiye sınırları dışında.");
            }
        } else if (cekim.konumVar()) {
            // EXIF GPS'in yoklugu artik bir sey soylemiyor: konumu uygulamanin kendi
            // olcumunden biliyoruz ve o olcum EXIF'ten daha guvenilir.
            checks.add("[i] Fotoğrafta EXIF GPS yok; konum uygulamanın çekim anındaki "
                    + "kendi ölçümünden alındı.");
        } else {
            score -= 10;
            checks.add("[!] Fotoğrafta GPS üstverisi yok "
                    + "(WhatsApp/Telegram gibi uygulamalar konumu siler).");
        }

        if (takenAt != null) {
            long minutes = Duration.between(takenAt, Instant.now()).toMinutes();
            long limit = props.getPhoto().getMaxAgeMinutes();
            long skew = props.getPhoto().getClockSkewMinutes();
            String kaynakNotu = " [zaman kaynağı: " + zamanKaynagi + "]";

            if (minutes < -skew) {
                score -= 20;
                checks.add("[X] Çekim zamanı " + (-minutes)
                        + " dakika sonrasını gösteriyor — cihaz saati yanlış ya da üstveri oynanmış."
                        + kaynakNotu);
            } else if (minutes <= limit) {
                score += 20;
                checks.add("[OK] Çekim zamanı taze: " + Math.max(0, minutes) + " dakika önce."
                        + kaynakNotu);
            } else {
                // Ceza eskidikce agirlasir: gunler oncesine ait bir kare, konumu tutsa bile
                // su anda devam eden bir yangina kanit olamaz.
                int ceza;
                if (minutes <= limit * 2) {
                    ceza = 15;
                } else if (minutes <= 24 * 60) {
                    ceza = 30;
                } else {
                    ceza = 45;
                }
                score -= ceza;
                checks.add("[X] Fotoğraf eski: " + (minutes / 60) + " saat önce çekilmiş (sınır "
                        + (limit / 60) + " saat, ceza -" + ceza + ")." + kaynakNotu);
            }
        } else {
            score -= 5;
            checks.add("[!] Çekim zamanı üstverisi yok.");
        }

        if (cameraMake != null || cameraModel != null) {
            score += 10;
            checks.add("[OK] Kamera imzası bulundu: "
                    + String.join(" ", nz(cameraMake), nz(cameraModel)).trim() + ".");
        } else {
            score -= 10;
            checks.add("[!] Kamera marka/model bilgisi yok — ekran görüntüsü ya da "
                    + "internetten indirilmiş görsel olabilir.");
        }

        if (width != null && height != null) {
            if (width >= 640 && height >= 480) {
                score += 5;
                checks.add("[OK] Çözünürlük yeterli: " + width + "x" + height + ".");
            } else {
                score -= 10;
                checks.add("[X] Çözünürlük çok düşük: " + width + "x" + height + ".");
            }
        }

        // ---------------------------------------------------- cekim baglami
        // Buradaki degerler EXIF'ten okunmaz; uygulama kareyi cektigi anda kendi
        // olcup gonderir ve App Attest imzasi bu degerleri de kapsar. Bu yuzden
        // EXIF'e gore cok daha agir tartilirlar.

        Double captureDistance = null;

        if (cekim.kamera()) {
            score += 10;
            checks.add("[OK] Kare uygulama içi kamerayla çekildi (kitaplıktan seçilmedi).");
        }

        if (cekim.cihazDogrulandi()) {
            score += 20;
            checks.add("[OK] Cihaz kimliği Apple App Attest ile doğrulandı — "
                    + "istek gerçek bir cihazdaki imzalı uygulamadan geliyor.");
        } else if (cekim.kamera()) {
            checks.add("[!] Cihaz kimliği doğrulanamadı — kare kamerayla çekilmiş görünüyor "
                    + "ama kaynağı kriptografik olarak kanıtlanmadı.");
        }

        if (cekim.konumVar()) {
            captureDistance = GeoUtils.distanceMeters(
                    cekim.lat(), cekim.lon(), reportLat, reportLon);
            double limit = props.getPhoto().getMaxDistanceMeters();

            if (captureDistance <= limit) {
                score += cekim.cihazDogrulandi() ? 25 : 12;
                checks.add(String.format(
                        "[OK] Çekim anındaki cihaz konumu ihbar noktasına %.0f m mesafede%s.",
                        captureDistance,
                        cekim.dogrulukMetre() == null
                                ? ""
                                : String.format(" (ölçüm hatası ±%.0f m)", cekim.dogrulukMetre())));
            } else {
                score -= 35;
                checks.add(String.format(
                        "[X] Çekim anındaki cihaz konumu ihbar noktasından %.1f km uzakta.",
                        captureDistance / 1000d));
            }
        }

        if (cekim.cekimAni() != null) {
            long dakika = Duration.between(cekim.cekimAni(), Instant.now()).toMinutes();
            if (dakika < -props.getPhoto().getClockSkewMinutes()) {
                score -= 20;
                checks.add("[X] Çekim anı geleceği gösteriyor — cihaz saati güvenilmez.");
            } else if (dakika <= props.getPhoto().getMaxAgeMinutes()) {
                score += 15;
                checks.add("[OK] Çekimden bu yana " + Math.max(0, dakika) + " dakika geçmiş.");
            } else {
                score -= 20;
                checks.add("[X] Çekim ile yükleme arasında " + (dakika / 60) + " saat var.");
            }
        }

        score += contentClassificationHook(bytes);
        score = Math.clamp(score, 0, 100);

        PhotoVerdict verdict;
        if (score >= 70) {
            verdict = PhotoVerdict.GECERLI;
        } else if (score >= 35) {
            verdict = PhotoVerdict.SUPHELI;
        } else {
            verdict = PhotoVerdict.GECERSIZ;
        }
        checks.add("=> Fotoğraf puanı: " + score + "/100 — " + verdict.getLabel() + ".");

        return new PhotoAnalysis(sha, type, exifLat, exifLon, takenAt, distance,
                captureDistance, cekim.cihazDogrulandi(), cekim.kamera(),
                score, verdict, true, checks);
    }

    /**
     * Gorsel icerik siniflandirmasi icin baglanti noktasi.
     *
     * <p>Su an notr (0) doner. Duman/alev tanima modeli baglanacaksa buraya
     * baglanmali ve -20 ile +20 arasinda bir duzeltme dondurmelidir.
     */
    protected int contentClassificationHook(byte[] bytes) {
        return 0;
    }

    /** Cozulen cekim ani ve hangi kaynaktan geldigi. */
    private record ZamanSonucu(Instant an, String kaynak) {
        static final ZamanSonucu YOK = new ZamanSonucu(null, null);
    }

    /**
     * EXIF {@code DateTimeOriginal} saat dilimi tasimaz; kameranin o andaki yerel saatidir.
     * Once {@code OffsetTimeOriginal} (0x9011) aranir, yoksa yapilandirilan saat dilimi varsayilir.
     * Bu degeri dogrudan UTC saymak, Turkiye'de her fotografi 3 saat ileri kaydirir.
     */
    private ZamanSonucu cekimZamani(ExifSubIFDDirectory sub) {
        String ham = sub.getString(ExifSubIFDDirectory.TAG_DATETIME_ORIGINAL);
        if (ham == null || ham.isBlank()) {
            return ZamanSonucu.YOK;
        }
        LocalDateTime yerel;
        try {
            yerel = LocalDateTime.parse(ham.trim(), EXIF_ZAMAN);
        } catch (DateTimeParseException e) {
            return ZamanSonucu.YOK;
        }

        String ofset = sub.getString(TAG_OFFSET_TIME_ORIGINAL);
        if (ofset != null && !ofset.isBlank()) {
            try {
                return new ZamanSonucu(
                        yerel.toInstant(ZoneOffset.of(ofset.trim())),
                        "EXIF, saat dilimi " + ofset.trim());
            } catch (DateTimeException ignored) {
                // ofset okunamadi, varsayilana dus
            }
        }

        ZoneId varsayilan;
        try {
            varsayilan = ZoneId.of(props.getPhoto().getAssumedZone());
        } catch (DateTimeException e) {
            varsayilan = ZoneId.systemDefault();
        }
        return new ZamanSonucu(yerel.atZone(varsayilan).toInstant(),
                "EXIF, " + varsayilan.getId() + " varsayıldı");
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    private static Integer intOrNull(Directory dir, int tag) {
        try {
            return dir.containsTag(tag) ? dir.getInt(tag) : null;
        } catch (Exception e) {
            return null;
        }
    }

    /** Uzantiya degil, dosyanin ilk baytlarina bakar. */
    static String detectType(byte[] b) {
        if (b.length < 12) {
            return null;
        }
        if ((b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) {
            return "JPEG";
        }
        if ((b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G') {
            return "PNG";
        }
        if (b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F'
                && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P') {
            return "WebP";
        }
        if (b[4] == 'f' && b[5] == 't' && b[6] == 'y' && b[7] == 'p') {
            String brand = new String(b, 8, 4).toLowerCase();
            if (brand.startsWith("hei") || brand.startsWith("mif") || brand.startsWith("msf")) {
                return "HEIC";
            }
        }
        return null;
    }

    static String sha256(byte[] bytes) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 yok", e);
        }
    }
}
