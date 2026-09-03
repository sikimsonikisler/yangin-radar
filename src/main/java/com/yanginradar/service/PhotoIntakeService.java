package com.yanginradar.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.yanginradar.domain.CaptureChallenge;
import com.yanginradar.dto.CaptureEnvelope;
import com.yanginradar.dto.FireReportDto;

/**
 * Fotograf yuklemesinin kanit zinciri.
 *
 * <p>Sirasiyla:
 * <ol>
 *   <li><b>Tek kullanimlik kod</b> - sunucunun cekimden hemen once urettigi nonce
 *       harcanir. Ikinci kez kabul edilmez, suresi kisadir. Onceden hazirlanmis
 *       bir kare bu kapidan gecemez.</li>
 *   <li><b>App Attest imzasi</b> - varsa dogrulanir. Imza; nonce, ihbar numarasi ve
 *       <b>fotografin ozeti</b> uzerinden atilir, yani kare degistirilirse imza tutmaz.</li>
 *   <li><b>Cekim baglami</b> - dogrulanan degerler {@link CaptureContext} olarak
 *       puanlamaya gecer.</li>
 * </ol>
 *
 * <p>Onemli olan sudur: istemci "ben dogrulandim" diyemez. Guven, imzanin
 * dogrulanmasindan gelir; beyandan degil.
 */
@Service
public class PhotoIntakeService {

    private static final Logger log = LoggerFactory.getLogger(PhotoIntakeService.class);

    private final FireReportService reportService;
    private final CaptureChallengeService challengeService;
    private final AppAttestService attestService;

    public PhotoIntakeService(FireReportService reportService,
                              CaptureChallengeService challengeService,
                              AppAttestService attestService) {
        this.reportService = reportService;
        this.challengeService = challengeService;
        this.attestService = attestService;
    }

    public FireReportDto al(Long reportId, byte[] bytes, String dosyaAdi,
                            CaptureEnvelope beyan, String keyId, String assertion)
            throws IOException {

        if (beyan == null || beyan.challengeId() == null || beyan.challengeId().isBlank()) {
            // Cekim beyani yok: kitapliktan secilmis fotograf. Yalnizca EXIF ile degerlendirilir.
            return reportService.attachPhoto(reportId, bytes, dosyaAdi, CaptureContext.YOK, null);
        }

        CaptureChallenge kod = challengeService.harca(beyan.challengeId(), CaptureChallenge.Amac.CEKIM);

        boolean dogrulandi = false;
        String dogrulananKey = null;

        if (attestService.acik() && keyId != null && !keyId.isBlank()
                && assertion != null && !assertion.isBlank()) {

            byte[] imzalanan = imzalanacakVeri(kod.getNonce(), reportId, bytes);
            dogrulandi = attestService.imzayiDogrula(keyId, assertion, imzalanan, kod.getNonce());
            if (dogrulandi) {
                dogrulananKey = keyId;
            } else {
                log.warn("Ihbar #{} icin App Attest imzasi dogrulanamadi - fotograf "
                        + "dogrulanmamis sayiliyor", reportId);
            }
        }

        CaptureContext baglam = new CaptureContext(
                beyan.lat(),
                beyan.lon(),
                beyan.dogrulukMetre(),
                beyan.cekimAni(),
                beyan.kameraylaCekildi(),
                dogrulandi);

        return reportService.attachPhoto(reportId, bytes, dosyaAdi, baglam, dogrulananKey);
    }

    /**
     * Imzanin kapsadigi veri: {@code nonce|ihbarNo|fotografOzeti}.
     *
     * <p>Fotografin SHA-256 ozeti imzaya dahildir; boylece imza gecerli bir karenin
     * yerine baska bir kare konulamaz. Istemci ayni diziyi ayni sirayla uretir.
     */
    public static byte[] imzalanacakVeri(String nonce, Long reportId, byte[] fotograf) {
        String ozet = HexFormat.of().formatHex(sha256(fotograf));
        return (nonce + "|" + reportId + "|" + ozet).getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] sha256(byte[] veri) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(veri);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
