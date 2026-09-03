package com.yanginradar.service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.yanginradar.domain.FireReport;
import com.yanginradar.domain.ReportSource;
import com.yanginradar.domain.ReportStatus;
import com.yanginradar.domain.Severity;
import com.yanginradar.repo.FireReportRepository;

/**
 * Ilk aciliste harita bos kalmasin diye ornek kayitlar ekler.
 * {@code yanginradar.demo-data=false} ile kapatilir.
 *
 * <p>Bu kayitlar GERCEK yangin verisi degildir; sadece arayuzu denemek icindir.
 */
@Component
public class DemoDataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);

    private final FireReportRepository repo;
    private final FireReportService service;
    private final boolean enabled;

    public DemoDataSeeder(FireReportRepository repo,
                          FireReportService service,
                          @Value("${yanginradar.demo-data:true}") boolean enabled) {
        this.repo = repo;
        this.service = service;
        this.enabled = enabled;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (!enabled || repo.count() > 0) {
            return;
        }

        seed(36.8121, 28.2610, "Muğla", "Marmaris", Severity.BUYUK_YANGIN,
                ReportSource.VATANDAS, ReportStatus.DOGRULANDI,
                "ÖRNEK VERİ — Ormanlık alanda yoğun duman, rüzgâr kuzeybatıdan.", 45, 8, 0);
        seed(36.8195, 28.2705, "Muğla", "Marmaris", Severity.HIZLA_YAYILIYOR,
                ReportSource.UYDU, ReportStatus.DOGRULANDI,
                "ÖRNEK VERİ — Uydu ısı anomalisi, FRP 128 MW.", 40, 0, 0);
        seed(37.0410, 30.5680, "Antalya", "Manavgat", Severity.KUCUK_YANGIN,
                ReportSource.VATANDAS, ReportStatus.BEKLIYOR,
                "ÖRNEK VERİ — Yol kenarında ateş görüldü, henüz küçük.", 25, 1, 0);
        seed(39.9250, 32.8360, "Ankara", "Çankaya", Severity.DUMAN_GORULDU,
                ReportSource.VATANDAS, ReportStatus.REDDEDILDI,
                "ÖRNEK VERİ — Yanlış alarm, inşaat tozu olduğu anlaşıldı.", 180, 0, 6);
        seed(38.4190, 27.1280, "İzmir", "Konak", Severity.KUCUK_YANGIN,
                ReportSource.VATANDAS, ReportStatus.SONDURULDU,
                "ÖRNEK VERİ — Çöp konteyneri yangını, ekipler söndürdü.", 300, 4, 0);
        seed(40.7650, 30.3780, "Sakarya", "Serdivan", Severity.DUMAN_GORULDU,
                ReportSource.VATANDAS, ReportStatus.BEKLIYOR,
                "ÖRNEK VERİ — Tepede duman var, fotoğraf yüklenemedi.", 12, 0, 1);

        log.info("Demo verisi yuklendi: {} kayit", repo.count());
    }

    private void seed(double lat, double lon, String il, String ilce, Severity severity,
                      ReportSource source, ReportStatus status, String aciklama,
                      int minutesAgo, int confirms, int denies) {
        FireReport r = new FireReport();
        r.setLat(lat);
        r.setLon(lon);
        r.setIl(il);
        r.setIlce(ilce);
        r.setSeverity(severity);
        r.setSource(source);
        r.setStatus(status);
        r.setAciklama(aciklama);
        r.setReporterAlias(source == ReportSource.UYDU ? "VIIRS / NOAA-20" : "Örnek kullanıcı");
        r.setConfirmCount(confirms);
        r.setDenyCount(denies);
        Instant t = Instant.now().minus(minutesAgo, ChronoUnit.MINUTES);
        r.setCreatedAt(t);
        r.setUpdatedAt(t);
        repo.save(r);

        ReportStatus original = r.getStatus();
        service.recomputeConfidence(r);
        r.setStatus(original);
        repo.save(r);
    }
}
