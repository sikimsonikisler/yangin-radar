package com.yanginradar.service;

import java.time.Instant;
import java.util.List;

import com.yanginradar.domain.PhotoVerdict;

/** Bir fotograf uzerinde calisan tum kontrollerin sonucu. */
public record PhotoAnalysis(
        String sha256,
        String detectedType,
        Double exifLat,
        Double exifLon,
        Instant takenAt,
        Double distanceMeters,
        /** Uygulamanin cekim aninda olctugu konumun ihbar noktasina uzakligi. */
        Double captureDistanceMeters,
        boolean cihazDogrulandi,
        boolean kameraylaCekildi,
        int score,
        PhotoVerdict verdict,
        /**
         * Dosya hic saklanabilir mi? Yalnizca sert redler icin false olur:
         * goruntu olmayan dosya, boyut asimi. Supheli ama gercek bir fotograf
         * saklanir - yetkili panelinde dogrulama raporuyla birlikte gorunsun diye.
         */
        boolean kabulEdilir,
        List<String> checks) {

    public String checksAsText() {
        return String.join("\n", checks);
    }
}
