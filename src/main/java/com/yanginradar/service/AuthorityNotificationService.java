package com.yanginradar.service;

import java.time.Instant;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.yanginradar.domain.DispatchRecord;
import com.yanginradar.domain.FireReport;
import com.yanginradar.repo.DispatchRecordRepository;
import com.yanginradar.repo.FireReportRepository;

/**
 * Dogrulanan ihbarlari yetkili birime aktarim kuyruguna yazar.
 *
 * <p><b>Onemli:</b> Bu sinif 112 / 177 gibi gercek acil cagri hatlarina otomatik
 * bildirim GONDERMEZ. Sadece uygulama icinde bir giden kutusu (outbox) kaydi olusturur
 * ve loga yazar. Gercek bir kuruma otomatik ihbar gonderimi, ilgili kurumun yazili izni
 * ve resmi entegrasyon kanali olmadan yapilmamalidir - yanlis alarm uretmek hem
 * yasal hem operasyonel risk dogurur. Entegrasyon eklenecekse {@link #transmit}
 * govdesine baglanmalidir.
 */
@Service
public class AuthorityNotificationService {

    private static final Logger log = LoggerFactory.getLogger(AuthorityNotificationService.class);

    private final DispatchRecordRepository dispatchRepo;
    private final FireReportRepository reportRepo;
    private final FireReportService reportService;

    public AuthorityNotificationService(DispatchRecordRepository dispatchRepo,
                                        FireReportRepository reportRepo,
                                        FireReportService reportService) {
        this.dispatchRepo = dispatchRepo;
        this.reportRepo = reportRepo;
        this.reportService = reportService;
    }

    @Transactional
    public DispatchRecord dispatch(Long reportId, String channel) {
        return dispatch(reportId, channel, false);
    }

    /**
     * Ihbari yetkili birime iletilmek uzere kaydeder.
     *
     * <p>Dayanagi olmayan bir vatandas ihbari - kabul edilmis fotografi da baska bir
     * kaynagin destegi de bulunmayan kayit - varsayilan olarak reddedilir. Amac
     * yetkilinin onune tek basina dogrulanmamis bildirim cikmamasidir. Karar yine de
     * insanindir: {@code zorla} ile bu kapi bilerek asilabilir.
     */
    @Transactional
    public DispatchRecord dispatch(Long reportId, String channel, boolean zorla) {
        FireReport r = reportRepo.findById(reportId)
                .orElseThrow(() -> new IllegalArgumentException("Ihbar bulunamadi: " + reportId));

        if (!zorla && !reportService.dayanakVar(r)) {
            throw new IllegalArgumentException(
                    "Bu ihbarın somut dayanağı yok: kabul edilmiş bir fotoğraf ya da "
                    + "yakındaki başka bir kaynağın desteği bulunmuyor. Yine de iletmek "
                    + "istiyorsanız isteği 'zorla=true' ile yineleyin.");
        }

        String payload = buildPayload(r);

        DispatchRecord record = new DispatchRecord();
        record.setFireReportId(r.getId());
        record.setChannel(channel == null || channel.isBlank() ? "OGM_ILGILI_BIRIM" : channel);
        record.setPayload(payload);
        record.setState(transmit(record) ? "GONDERILDI" : "KUYRUKTA");
        record.setCreatedAt(Instant.now());
        dispatchRepo.save(record);

        reportService.markDispatched(r.getId());

        log.info("Yetkili aktarim kaydi olusturuldu: ihbar #{} -> {} ({})",
                r.getId(), record.getChannel(), record.getState());
        return record;
    }

    /**
     * Gercek gonderim noktasi. Su an her zaman {@code false} doner, yani kayit
     * kuyrukta bekler ve hicbir yere gonderilmez. Resmi entegrasyon burada yapilmali.
     */
    private boolean transmit(DispatchRecord record) {
        return false;
    }

    @Transactional(readOnly = true)
    public List<DispatchRecord> recent() {
        return dispatchRepo.findTop100ByOrderByCreatedAtDesc();
    }

    private String buildPayload(FireReport r) {
        return """
                YANGIN IHBARI AKTARIMI
                Ihbar no      : %d
                Konum         : %.6f, %.6f
                Il / Ilce     : %s / %s
                Buyukluk      : %s
                Kaynak        : %s
                Durum         : %s
                Guven puani   : %d/100
                Fotograf sayi : %d
                Destek / ret  : %d / %d
                Aciklama      : %s
                Ihbar zamani  : %s
                Iletisim      : %s
                """.formatted(
                r.getId(), r.getLat(), r.getLon(),
                nz(r.getIl()), nz(r.getIlce()),
                r.getSeverity().getLabel(),
                r.getSource().getLabel(),
                r.getStatus().getLabel(),
                r.getConfidence(),
                r.getPhotos().size(),
                r.getConfirmCount(), r.getDenyCount(),
                nz(r.getAciklama()),
                r.getCreatedAt(),
                mask(r.getReporterContact()));
    }

    private static String nz(String s) {
        return s == null || s.isBlank() ? "-" : s;
    }

    /** Iletisim bilgisi panelde ve loglarda maskeli gorunur. */
    static String mask(String contact) {
        if (contact == null || contact.isBlank()) {
            return "-";
        }
        String t = contact.trim();
        if (t.length() <= 4) {
            return "*".repeat(t.length());
        }
        return t.substring(0, 2) + "*".repeat(t.length() - 4) + t.substring(t.length() - 2);
    }
}
