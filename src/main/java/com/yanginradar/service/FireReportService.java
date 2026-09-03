package com.yanginradar.service;

import java.io.IOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.yanginradar.config.AppProperties;
import com.yanginradar.config.GeoUtils;
import com.yanginradar.domain.FireReport;
import com.yanginradar.domain.PhotoEvidence;
import com.yanginradar.domain.PhotoVerdict;
import com.yanginradar.domain.Vote;
import com.yanginradar.domain.ReportSource;
import com.yanginradar.domain.ReportStatus;
import com.yanginradar.dto.CreateReportRequest;
import com.yanginradar.dto.FireReportDto;
import com.yanginradar.dto.PhotoDto;
import com.yanginradar.dto.StatsDto;
import com.yanginradar.repo.FireReportRepository;
import com.yanginradar.repo.PhotoEvidenceRepository;
import com.yanginradar.repo.VoteRepository;

/** Ihbarlarin olusturulmasi, guven puani hesabi ve durum gecisleri. */
@Service
public class FireReportService {

    private static final Logger log = LoggerFactory.getLogger(FireReportService.class);

    /** Fotograf puaninin ihbar guvenine ne ekleyip ne cikardigini belirleyen esik. */
    private static final int NOTR_FOTOGRAF_PUANI = 45;

    private final FireReportRepository reportRepo;
    private final PhotoEvidenceRepository photoRepo;
    private final VoteRepository voteRepo;
    private final PhotoVerificationService verifier;
    private final PhotoStorageService storage;
    private final LiveFeedService live;
    private final AppProperties props;

    public FireReportService(FireReportRepository reportRepo,
                             PhotoEvidenceRepository photoRepo,
                             VoteRepository voteRepo,
                             PhotoVerificationService verifier,
                             PhotoStorageService storage,
                             LiveFeedService live,
                             AppProperties props) {
        this.reportRepo = reportRepo;
        this.photoRepo = photoRepo;
        this.voteRepo = voteRepo;
        this.verifier = verifier;
        this.storage = storage;
        this.live = live;
        this.props = props;
    }

    // ----------------------------------------------------------------- okuma

    @Transactional(readOnly = true)
    public List<FireReportDto> listAll() {
        return reportRepo.findTop200ByOrderByCreatedAtDesc().stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public FireReportDto getOne(Long id) {
        return toDto(mustFind(id));
    }

    @Transactional(readOnly = true)
    public StatsDto stats() {
        List<FireReport> all = reportRepo.findAll();
        return new StatsDto(
                all.size(),
                count(all, ReportStatus.BEKLIYOR) + count(all, ReportStatus.INCELENIYOR),
                count(all, ReportStatus.DOGRULANDI),
                count(all, ReportStatus.REDDEDILDI),
                count(all, ReportStatus.SONDURULDU),
                all.stream().filter(r -> r.getSource() == ReportSource.UYDU).count(),
                all.stream().filter(r -> r.getSource() == ReportSource.VATANDAS).count(),
                all.stream().filter(FireReport::isDispatched).count(),
                live.listenerCount());
    }

    private static long count(List<FireReport> all, ReportStatus status) {
        return all.stream().filter(r -> r.getStatus() == status).count();
    }

    // ---------------------------------------------------------------- yazma

    @Transactional
    public FireReportDto create(CreateReportRequest req) {
        if (!GeoUtils.insideTurkiye(req.lat(), req.lon())) {
            throw new IllegalArgumentException(
                    "İşaretlenen nokta Türkiye sınırları dışında görünüyor.");
        }

        FireReport r = new FireReport();
        r.setLat(req.lat());
        r.setLon(req.lon());
        r.setIl(trim(req.il()));
        r.setIlce(trim(req.ilce()));
        r.setAciklama(trim(req.aciklama()));
        r.setSeverity(req.severity());
        r.setSource(ReportSource.VATANDAS);
        r.setStatus(ReportStatus.BEKLIYOR);
        r.setReporterAlias(req.reporterAlias() == null || req.reporterAlias().isBlank()
                ? "Anonim" : trim(req.reporterAlias()));
        r.setReporterContact(trim(req.reporterContact()));

        reportRepo.save(r);
        recomputeConfidence(r);
        reportRepo.save(r);

        FireReportDto dto = toDto(r);
        live.publish("yeni-ihbar", dto);
        log.info("Yeni ihbar #{} ({}, {}) guven={}", r.getId(), r.getLat(), r.getLon(),
                r.getConfidence());
        return dto;
    }

    /** Cekim baglami olmayan yukleme (kitapliktan secim, testler). */
    @Transactional
    public FireReportDto attachPhoto(Long reportId, byte[] bytes, String originalFilename)
            throws IOException {
        return attachPhoto(reportId, bytes, originalFilename, CaptureContext.YOK, null);
    }

    /** Ihbara fotograf ekler, dogrulama zincirini calistirir ve guven puanini gunceller. */
    @Transactional
    public FireReportDto attachPhoto(Long reportId, byte[] bytes, String originalFilename,
                                     CaptureContext cekim, String attestedKeyId)
            throws IOException {
        FireReport r = mustFind(reportId);

        PhotoAnalysis analysis = verifier.analyze(bytes, originalFilename,
                r.getLat(), r.getLon(), cekim);
        if (!analysis.kabulEdilir()) {
            throw new IllegalArgumentException(
                    "Fotoğraf kabul edilmedi: " + String.join(" ", analysis.checks()));
        }

        String stored = storage.store(bytes, analysis.detectedType());

        PhotoEvidence p = new PhotoEvidence();
        p.setStoredFilename(stored);
        p.setOriginalFilename(originalFilename);
        p.setContentType("image/" + analysis.detectedType().toLowerCase());
        p.setSizeBytes(bytes.length);
        p.setSha256(analysis.sha256());
        p.setExifLat(analysis.exifLat());
        p.setExifLon(analysis.exifLon());
        p.setExifTakenAt(analysis.takenAt());
        p.setDistanceMeters(analysis.distanceMeters());
        p.setScore(analysis.score());
        p.setVerdict(analysis.verdict());
        p.setChecks(analysis.checksAsText());
        p.setCaptureLat(cekim.lat());
        p.setCaptureLon(cekim.lon());
        p.setCaptureAccuracyMeters(cekim.dogrulukMetre());
        p.setCapturedAt(cekim.cekimAni());
        p.setCaptureDistanceMeters(analysis.captureDistanceMeters());
        p.setCameraCaptured(analysis.kameraylaCekildi());
        p.setDeviceAttested(analysis.cihazDogrulandi());
        p.setAttestedKeyId(attestedKeyId);
        r.addPhoto(p);
        photoRepo.save(p);

        recomputeConfidence(r);
        r.touch();
        reportRepo.save(r);

        FireReportDto dto = toDto(r);
        live.publish("fotograf-eklendi", dto);
        return dto;
    }

    /**
     * Vatandas oyu: baska kullanicilar ihbari destekler veya yalanlar.
     *
     * <p>Bir cihaz bir ihbara yalnizca bir oy verebilir. Ayni oy tekrar gelirse hicbir
     * sey degismez; ters oy gelirse sayaclar buna gore kaydirilir. Bu olmadan tek kisi
     * guven puanini istedigi kadar sisirebiliyordu.
     */
    @Transactional
    public FireReportDto vote(Long reportId, boolean confirm, String voterId) {
        if (voterId == null || voterId.isBlank()) {
            throw new IllegalArgumentException("Oy için cihaz kimliği gerekli.");
        }
        String kimlik = voterId.trim();
        if (kimlik.length() > 64) {
            kimlik = kimlik.substring(0, 64);
        }

        FireReport r = mustFind(reportId);
        Vote onceki = voteRepo.findByFireReportIdAndVoterId(reportId, kimlik).orElse(null);

        if (onceki == null) {
            voteRepo.save(new Vote(reportId, kimlik, confirm));
            if (confirm) {
                r.setConfirmCount(r.getConfirmCount() + 1);
            } else {
                r.setDenyCount(r.getDenyCount() + 1);
            }
        } else if (onceki.isConfirm() != confirm) {
            // Fikir degisikligi: eski oyu geri al, yenisini ekle.
            if (confirm) {
                r.setConfirmCount(r.getConfirmCount() + 1);
                r.setDenyCount(Math.max(0, r.getDenyCount() - 1));
            } else {
                r.setDenyCount(r.getDenyCount() + 1);
                r.setConfirmCount(Math.max(0, r.getConfirmCount() - 1));
            }
            onceki.setConfirm(confirm);
            voteRepo.save(onceki);
        } else {
            // Ayni oy yeniden geldi: sessizce yok say, sayaci sisirme.
            return toDto(r);
        }

        recomputeConfidence(r);
        r.touch();
        reportRepo.save(r);

        FireReportDto dto = toDto(r);
        live.publish("oy-verildi", dto);
        return dto;
    }

    /** Yetkili panelinden manuel durum degisikligi. */
    @Transactional
    public FireReportDto changeStatus(Long reportId, ReportStatus status) {
        FireReport r = mustFind(reportId);
        r.setStatus(status);
        if (status == ReportStatus.DOGRULANDI) {
            r.setConfidence(Math.max(r.getConfidence(), 90));
        } else if (status == ReportStatus.REDDEDILDI) {
            r.setConfidence(0);
        }
        r.touch();
        reportRepo.save(r);

        FireReportDto dto = toDto(r);
        live.publish("durum-degisti", dto);
        return dto;
    }

    @Transactional
    public void markDispatched(Long reportId) {
        FireReport r = mustFind(reportId);
        r.setDispatched(true);
        r.setDispatchedAt(Instant.now());
        r.touch();
        reportRepo.save(r);
        live.publish("yetkiliye-iletildi", toDto(r));
    }

    // ------------------------------------------------------- guven hesabi

    /**
     * Guven puani = kaynak agirligi + fotograf kaniti + uydu ortusmesi
     * + capraz ihbar destegi + topluluk oylari.
     */
    void recomputeConfidence(FireReport r) {
        int score = switch (r.getSource()) {
            case RESMI -> 90;
            case UYDU -> 60;
            case VATANDAS -> 20;
        };

        List<PhotoEvidence> photos = r.getId() == null
                ? List.of()
                : photoRepo.findByFireReportIdOrderByUploadedAtAsc(r.getId());

        // Fotograf katkisi NOTR_PUAN etrafinda ortalanir: iyi bir kare puani yukseltir,
        // konumu ya da zamani tutmayan bir kare dusurur. Duz toplama yapilirsa
        // "supheli" damgasi yemis bir fotograf bile ihbari guclendirirdi.
        int bestPhoto = photos.stream().mapToInt(PhotoEvidence::getScore).max().orElse(-1);
        if (bestPhoto >= 0) {
            score += (int) Math.round((bestPhoto - NOTR_FOTOGRAF_PUANI) * 0.8);
            long gecerli = photos.stream()
                    .filter(p -> p.getVerdict() == PhotoVerdict.GECERLI).count();
            if (gecerli >= 2) {
                score += 8;
            }
            if (photos.stream().allMatch(p -> p.getVerdict() == PhotoVerdict.GECERSIZ)) {
                score -= 25;
            }
        }

        Destek destek = destek(r);
        score += destek.bonus();

        score += Math.min(20, r.getConfirmCount() * 4);
        score -= Math.min(40, r.getDenyCount() * 8);

        score = Math.clamp(score, 0, 100);
        r.setConfidence(score);

        if (r.getStatus() != ReportStatus.BEKLIYOR) {
            return;
        }
        if (score < props.getModeration().getAutoVerifyThreshold()) {
            return;
        }
        // Puan ne olursa olsun, dayanagi olmayan bir vatandas ihbari yukselmez.
        if (!dayanakVar(r, photos, destek)) {
            log.info("Ihbar #{} guven puani {} ancak somut dayanagi yok - Bekliyor'da kaliyor",
                    r.getId(), score);
            return;
        }
        // Kanitlar birbiriyle celisiyorsa puan yuksek olsa bile otomatik dogrulama yapilmaz;
        // karar bir insana birakilir.
        if (celiskiliKanitVar(photos)) {
            r.setStatus(ReportStatus.INCELENIYOR);
            log.info("Ihbar #{} guven puani {} ancak celiskili fotograf kaniti var - "
                    + "manuel incelemeye alindi", r.getId(), score);
            return;
        }
        r.setStatus(ReportStatus.DOGRULANDI);
        log.info("Ihbar #{} guven puani {} ile otomatik dogrulandi", r.getId(), score);
    }

    /**
     * Bu ihbar "Bekliyor"un ustune cikabilir mi?
     *
     * <p>Kural: bir vatandas ihbarinin yukselebilmesi icin en az bir somut dayanak
     * gerekir - ya kabul edilmis (GECERLI) bir fotograf, ya da baska bir kaynagin
     * destegi (yakindaki bagimsiz ihbar veya uydu isi noktasi).
     *
     * <p>Topluluk oylari bilerek dayanak sayilmaz: oy ucu kimlik dogrulamasi
     * istemiyor, dolayisiyla tek basina cogaltilabilir bir sinyal.
     *
     * <p>Uydu ve resmi kaynaklar bu kuralin disindadir; onlarin dayanagi
     * kaynagin kendisidir. Yetkili panelinden yapilan elle durum degisikligi de
     * bu kuraldan etkilenmez - orada karari veren bir insandir.
     */
    boolean dayanakVar(FireReport r, List<PhotoEvidence> photos, Destek destek) {
        if (r.getSource() != ReportSource.VATANDAS) {
            return true;
        }
        boolean gecerliFotograf = photos.stream()
                .anyMatch(p -> p.getVerdict() == PhotoVerdict.GECERLI);
        return gecerliFotograf || destek.var();
    }

    /** Yetkiliye iletim oncesi kullanilan dis kapi. */
    @Transactional(readOnly = true)
    public boolean dayanakVar(FireReport r) {
        List<PhotoEvidence> photos = r.getId() == null
                ? List.of()
                : photoRepo.findByFireReportIdOrderByUploadedAtAsc(r.getId());
        return dayanakVar(r, photos, destek(r));
    }

    /** Konumu ihbar noktasindan uzak ya da gecersiz sayilmis bir fotograf var mi? */
    private boolean celiskiliKanitVar(List<PhotoEvidence> photos) {
        double limit = props.getPhoto().getMaxDistanceMeters();
        return photos.stream().anyMatch(p ->
                p.getVerdict() == PhotoVerdict.GECERSIZ
                        || (p.getDistanceMeters() != null && p.getDistanceMeters() > limit));
    }

    /**
     * Yakindaki bagimsiz ihbarlarin ve uydu ortusmesinin sonucu.
     *
     * <p>{@link #var()} bu ihbarin baska bir kaynakca desteklenip desteklenmedigini
     * soyler; puan katkisindan ayri tutulur cunku yukselme kurali puana degil
     * dayanagin varligina bakar.
     */
    private record Destek(int bonus, int bagimsizIhbar, boolean uyduOrtusmesi) {
        boolean var() {
            return bagimsizIhbar > 0 || uyduOrtusmesi;
        }
    }

    /** Yakindaki bagimsiz ihbarlar ve uydu isi noktalari puani yukseltir. */
    private Destek destek(FireReport r) {
        double radius = props.getModeration().getCorroborationRadiusMeters();
        Instant since = Instant.now().minus(
                props.getModeration().getCorroborationWindowMinutes(), ChronoUnit.MINUTES);

        double dLat = GeoUtils.latDelta(radius);
        double dLon = GeoUtils.lonDelta(radius, r.getLat());

        List<FireReport> nearby = reportRepo.findNearby(
                r.getLat() - dLat, r.getLat() + dLat,
                r.getLon() - dLon, r.getLon() + dLon,
                since);

        int bonus = 0;
        int independent = 0;
        boolean satellite = false;

        for (FireReport other : nearby) {
            if (Objects.equals(other.getId(), r.getId())) {
                continue;
            }
            if (GeoUtils.distanceMeters(r.getLat(), r.getLon(),
                    other.getLat(), other.getLon()) > radius) {
                continue;
            }
            if (other.getSource() == ReportSource.UYDU) {
                satellite = true;
            } else {
                independent++;
            }
        }

        if (satellite && r.getSource() != ReportSource.UYDU) {
            bonus += 25;
        }
        bonus += Math.min(18, independent * 6);
        return new Destek(bonus, independent, satellite);
    }

    // ------------------------------------------------------------- yardimci

    FireReport mustFind(Long id) {
        return reportRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("İhbar bulunamadı: " + id));
    }

    FireReportDto toDto(FireReport r) {
        List<PhotoDto> photos = r.getId() == null
                ? List.of()
                : photoRepo.findByFireReportIdOrderByUploadedAtAsc(r.getId()).stream()
                        .map(PhotoDto::from)
                        .toList();
        return FireReportDto.from(r, photos);
    }

    private static String trim(String s) {
        return s == null ? null : s.trim();
    }
}
