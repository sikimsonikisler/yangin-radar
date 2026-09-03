package com.yanginradar.service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.yanginradar.config.AppProperties;
import com.yanginradar.config.GeoUtils;
import com.yanginradar.domain.FireReport;
import com.yanginradar.domain.ReportSource;
import com.yanginradar.domain.ReportStatus;
import com.yanginradar.domain.Severity;
import com.yanginradar.repo.FireReportRepository;

/**
 * NASA FIRMS uydu isi noktalarini (VIIRS/MODIS) cekip haritaya isler.
 *
 * <p>Ucretsiz bir MAP_KEY gerekir: https://firms.modaps.eosdis.nasa.gov/api/area/
 * Anahtar {@code yanginradar.firms.map-key} ayarina veya {@code FIRMS_MAP_KEY}
 * ortam degiskenine yazilir. Anahtar yoksa servis sessizce devre disi kalir ve
 * uygulama vatandas ihbarlari + demo verisiyle calisir.
 */
@Service
public class FirmsIngestService {

    private static final Logger log = LoggerFactory.getLogger(FirmsIngestService.class);

    private final AppProperties props;
    private final FireReportRepository reportRepo;
    private final FireReportService reportService;
    private final LiveFeedService live;

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private Instant lastRun;
    private String lastResult = "henuz calismadi";

    public FirmsIngestService(AppProperties props,
                              FireReportRepository reportRepo,
                              FireReportService reportService,
                              LiveFeedService live) {
        this.props = props;
        this.reportRepo = reportRepo;
        this.reportService = reportService;
        this.live = live;
    }

    public boolean isActive() {
        return props.getFirms().isEnabled() && !props.getFirms().getMapKey().isBlank();
    }

    public String getLastResult() {
        return lastResult;
    }

    public Instant getLastRun() {
        return lastRun;
    }

    /** Uygulama acildiktan 20 sn sonra, sonra her 15 dakikada bir. */
    @Scheduled(initialDelay = 20_000, fixedDelay = 15 * 60 * 1000)
    public void scheduledIngest() {
        if (!isActive()) {
            return;
        }
        try {
            int added = ingestOnce();
            lastResult = added + " yeni uydu isi noktasi";
        } catch (Exception e) {
            lastResult = "hata: " + e.getMessage();
            log.warn("FIRMS cekimi basarisiz: {}", e.toString());
        }
        lastRun = Instant.now();
    }

    /** Tek seferlik cekim. Eklenen yeni kayit sayisini dondurur. */
    public int ingestOnce() throws Exception {
        AppProperties.Firms f = props.getFirms();
        String url = "%s/%s/%s/%s/%d".formatted(
                f.getBaseUrl(), f.getMapKey(), f.getDataset(), f.getArea(), f.getDayRange());

        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(45))
                .header("Accept", "text/csv")
                .GET()
                .build();

        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IllegalStateException("FIRMS HTTP " + response.statusCode());
        }
        return persist(response.body(), f.getDataset());
    }

    @Transactional
    int persist(String csv, String dataset) {
        List<String> lines = csv.lines().filter(l -> !l.isBlank()).toList();
        if (lines.size() < 2) {
            log.info("FIRMS: veri satiri yok");
            return 0;
        }

        Map<String, Integer> col = new HashMap<>();
        String[] header = lines.get(0).split(",", -1);
        for (int i = 0; i < header.length; i++) {
            col.put(header[i].trim().toLowerCase(), i);
        }
        if (!col.containsKey("latitude") || !col.containsKey("longitude")) {
            log.warn("FIRMS: beklenmeyen baslik satiri -> {}", lines.get(0));
            return 0;
        }

        int added = 0;
        for (String line : lines.subList(1, lines.size())) {
            String[] c = line.split(",", -1);
            try {
                double lat = Double.parseDouble(c[col.get("latitude")]);
                double lon = Double.parseDouble(c[col.get("longitude")]);
                if (!GeoUtils.insideTurkiye(lat, lon)) {
                    continue;
                }

                String acqDate = value(c, col, "acq_date");
                String acqTime = value(c, col, "acq_time");
                String externalId = dataset + ":" + lat + ":" + lon + ":" + acqDate + ":" + acqTime;
                if (reportRepo.existsByExternalId(externalId)) {
                    continue;
                }

                FireReport r = new FireReport();
                r.setExternalId(externalId);
                r.setLat(lat);
                r.setLon(lon);
                r.setSource(ReportSource.UYDU);
                r.setStatus(ReportStatus.DOGRULANDI);
                r.setReporterAlias(value(c, col, "satellite") + " / " + value(c, col, "instrument"));
                r.setBrightness(parseDoubleOrNull(firstNonNull(
                        value(c, col, "bright_ti4"), value(c, col, "brightness"))));
                r.setSeverity(severityFrom(
                        parseDoubleOrNull(value(c, col, "frp")), r.getBrightness()));
                r.setAciklama("Uydu tarafindan tespit edilen isi anomalisi. FIRMS guven: "
                        + value(c, col, "confidence") + ", FRP: " + value(c, col, "frp"));
                r.setCreatedAt(parseAcq(acqDate, acqTime));
                r.setUpdatedAt(Instant.now());

                reportRepo.save(r);
                reportService.recomputeConfidence(r);
                reportRepo.save(r);
                added++;
                live.publish("uydu-noktasi", reportService.toDto(r));
            } catch (Exception e) {
                log.debug("FIRMS satiri atlandi: {} ({})", line, e.toString());
            }
        }
        log.info("FIRMS: {} yeni isi noktasi eklendi", added);
        return added;
    }

    private static String firstNonNull(String a, String b) {
        return a != null && !a.isBlank() ? a : b;
    }

    private static String value(String[] c, Map<String, Integer> col, String name) {
        Integer i = col.get(name);
        return i == null || i >= c.length ? "" : c[i].trim();
    }

    private static Double parseDoubleOrNull(String s) {
        try {
            return s == null || s.isBlank() ? null : Double.parseDouble(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** FIRMS acq_time "HHmm" biciminde ve UTC. */
    private static Instant parseAcq(String date, String time) {
        try {
            LocalDate d = LocalDate.parse(date);
            String t = time == null ? "" : time.trim();
            while (t.length() < 4) {
                t = "0" + t;
            }
            LocalTime lt = LocalTime.of(Integer.parseInt(t.substring(0, 2)),
                    Integer.parseInt(t.substring(2, 4)));
            return d.atTime(lt).toInstant(ZoneOffset.UTC);
        } catch (Exception e) {
            return Instant.now();
        }
    }

    /** Yayilma gucu (FRP, MW) ve parlakliktan kaba bir buyukluk tahmini. */
    private static Severity severityFrom(Double frp, Double brightness) {
        double f = frp == null ? 0 : frp;
        if (f >= 100) {
            return Severity.HIZLA_YAYILIYOR;
        }
        if (f >= 30) {
            return Severity.BUYUK_YANGIN;
        }
        if (f >= 5 || (brightness != null && brightness > 330)) {
            return Severity.KUCUK_YANGIN;
        }
        return Severity.DUMAN_GORULDU;
    }
}
