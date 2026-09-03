package com.yanginradar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.yanginradar.config.AppProperties;
import com.yanginradar.domain.PhotoEvidence;
import com.yanginradar.domain.PhotoVerdict;
import com.yanginradar.repo.PhotoEvidenceRepository;
import com.yanginradar.service.PhotoAnalysis;
import com.yanginradar.service.PhotoVerificationService;

class PhotoVerificationServiceTest {

    private PhotoEvidenceRepository photoRepo;
    private PhotoVerificationService service;

    @BeforeEach
    void setUp() {
        photoRepo = mock(PhotoEvidenceRepository.class);
        when(photoRepo.findBySha256(anyString())).thenReturn(List.of());
        service = new PhotoVerificationService(new AppProperties(), photoRepo);
    }

    @Test
    void gorselOlmayanDosyaReddedilir() {
        byte[] sahte = "bu bir metin dosyasi, resim degil".getBytes();
        PhotoAnalysis sonuc = service.analyze(sahte, "not.txt", 36.8, 28.2);

        assertThat(sonuc.verdict()).isEqualTo(PhotoVerdict.GECERSIZ);
        assertThat(sonuc.kabulEdilir()).isFalse();
        assertThat(sonuc.score()).isZero();
        assertThat(sonuc.checks()).anyMatch(s -> s.contains("tanınan bir görüntü biçimi değil"));
    }

    @Test
    void ustverisizJpegSupheliDusukPuanAlir() throws Exception {
        PhotoAnalysis sonuc = service.analyze(jpeg(800, 600), "IMG_0001.jpg", 36.8, 28.2);

        assertThat(sonuc.detectedType()).isEqualTo("JPEG");
        assertThat(sonuc.exifLat()).isNull();
        assertThat(sonuc.score()).isLessThan(70);
        assertThat(sonuc.checks()).anyMatch(s -> s.contains("GPS üstverisi yok"));
        assertThat(sonuc.checks()).anyMatch(s -> s.contains("Kamera marka/model bilgisi yok"));
    }

    @Test
    void ayniKareIkinciKezKullanilirsaPuanDuser() throws Exception {
        byte[] kare = jpeg(800, 600);
        PhotoAnalysis ilk = service.analyze(kare, "a.jpg", 36.8, 28.2);

        when(photoRepo.findBySha256(anyString())).thenReturn(List.of(new PhotoEvidence()));
        PhotoAnalysis ikinci = service.analyze(kare, "a.jpg", 36.8, 28.2);

        assertThat(ikinci.score()).isLessThan(ilk.score());
        assertThat(ikinci.checks()).anyMatch(s -> s.contains("yeniden paylaşım şüphesi"));
    }

    @Test
    void ayniIcerikAyniOzeti() throws Exception {
        byte[] kare = jpeg(320, 240);
        assertThat(service.analyze(kare, "a.jpg", 36.8, 28.2).sha256())
                .isEqualTo(service.analyze(kare, "b.jpg", 36.8, 28.2).sha256())
                .hasSize(64);
    }

    // ------------------------------------------------- EXIF tasiyan fotograflar

    @Test
    void konumuVeZamaniTutanFotografGecerliSayilir() throws Exception {
        byte[] foto = ExifTestJpeg.ile(36.8121, 28.2610, dakikaOnce(20), null);
        PhotoAnalysis sonuc = service.analyze(foto, "IMG_1.jpg", 36.8121, 28.2610);

        assertThat(sonuc.exifLat()).isCloseTo(36.8121, within(0.001));
        assertThat(sonuc.distanceMeters()).isLessThan(100);
        assertThat(sonuc.verdict()).isEqualTo(PhotoVerdict.GECERLI);
        assertThat(sonuc.checks()).anyMatch(c -> c.contains("Çekim zamanı taze"));
    }

    /**
     * EXIF DateTimeOriginal saat dilimi tasimaz. UTC sanilirsa Turkiye'de cekilen
     * her fotograf 3 saat ileri kayar ve "gelecekte cekilmis" diye elenir.
     */
    @Test
    void saatDilimsizCekimZamaniGelecegeKaymaz() throws Exception {
        byte[] foto = ExifTestJpeg.ile(36.8121, 28.2610, dakikaOnce(5), null);
        PhotoAnalysis sonuc = service.analyze(foto, "IMG_2.jpg", 36.8121, 28.2610);

        assertThat(sonuc.checks()).noneMatch(c -> c.contains("sonrasını gösteriyor"));
        assertThat(sonuc.takenAt()).isBefore(Instant.now().plusSeconds(60));
        assertThat(sonuc.checks()).anyMatch(c -> c.contains("Europe/Istanbul varsayıldı"));
    }

    @Test
    void fotograftakiSaatDilimiOfsetiVarsaOnaUyulur() throws Exception {
        // Cihaz UTC'de kurulu: yerel saat = simdi(UTC), ofset +00:00
        LocalDateTime utcSimdi = LocalDateTime.now(ZoneOffset.UTC).minusMinutes(10);
        byte[] foto = ExifTestJpeg.ile(36.8121, 28.2610, utcSimdi, "+00:00");
        PhotoAnalysis sonuc = service.analyze(foto, "IMG_3.jpg", 36.8121, 28.2610);

        assertThat(sonuc.checks()).anyMatch(c -> c.contains("saat dilimi +00:00"));
        assertThat(sonuc.checks()).anyMatch(c -> c.contains("Çekim zamanı taze"));
    }

    @Test
    void baskaSehirdeCekilmisFotografMesafedenElenir() throws Exception {
        // Istanbul'da cekilmis, Marmaris'teki bir ihbara eklenmis
        byte[] foto = ExifTestJpeg.ile(41.0082, 28.9784, dakikaOnce(15), null);
        PhotoAnalysis sonuc = service.analyze(foto, "IMG_4.jpg", 36.8121, 28.2610);

        assertThat(sonuc.distanceMeters()).isGreaterThan(400_000);
        assertThat(sonuc.checks()).anyMatch(c -> c.contains("km uzakta"));
        assertThat(sonuc.verdict()).isNotEqualTo(PhotoVerdict.GECERLI);
    }

    @Test
    void gununEskisiFotografTazeSayilmaz() throws Exception {
        byte[] foto = ExifTestJpeg.ile(36.8121, 28.2610, dakikaOnce(3 * 24 * 60), null);
        PhotoAnalysis sonuc = service.analyze(foto, "IMG_5.jpg", 36.8121, 28.2610);

        assertThat(sonuc.checks()).anyMatch(c -> c.contains("Fotoğraf eski"));
        assertThat(sonuc.verdict()).isNotEqualTo(PhotoVerdict.GECERLI);
    }

    @Test
    void cihazSaatiIleriAlinmisFotografYakalanir() throws Exception {
        byte[] foto = ExifTestJpeg.ile(36.8121, 28.2610, dakikaOnce(-180), null);
        PhotoAnalysis sonuc = service.analyze(foto, "IMG_6.jpg", 36.8121, 28.2610);

        assertThat(sonuc.checks()).anyMatch(c -> c.contains("sonrasını gösteriyor"));
    }

    @Test
    void supheliAmaGercekFotografSaklanabilirKalir() throws Exception {
        // Konumu tutmayan bir kare: puani dusuk ama kanit olarak panelde gorunmeli
        byte[] foto = ExifTestJpeg.ile(41.0082, 28.9784, dakikaOnce(15), null);
        PhotoAnalysis sonuc = service.analyze(foto, "uzak.jpg", 36.8121, 28.2610);

        assertThat(sonuc.kabulEdilir()).isTrue();
        assertThat(sonuc.verdict()).isNotEqualTo(PhotoVerdict.GECERLI);
    }

    private static LocalDateTime dakikaOnce(long dakika) {
        return LocalDateTime.now(ZoneId.of("Europe/Istanbul")).minusMinutes(dakika);
    }

    /** EXIF tasimayan, gecerli bir JPEG uretir. */
    private static byte[] jpeg(int w, int h) throws Exception {
        return ExifTestJpeg.duz(w, h);
    }
}
