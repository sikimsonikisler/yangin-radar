package com.yanginradar.dto;

import java.time.Instant;

/**
 * Fotografla birlikte gelen cekim beyani.
 *
 * <p>Bu degerler istemciden gelir, yani tek baslarina guvenilmez. Guvenilirligi
 * iki sey saglar: {@code challengeId} sunucunun az once urettigi tek kullanimlik
 * bir koddur (kare onceden hazirlanamaz), ve varsa App Attest imzasi bu alanlarin
 * ustunden gecer (degistirilirse imza tutmaz).
 *
 * @param challengeId sunucudan alinan cekim kodunun tanitici degeri
 * @param lat         cekim anindaki enlem
 * @param lon         cekim anindaki boylam
 * @param dogrulukMetre konum olcumunun hata payi
 * @param cekimAni    cihazin cekim anindaki saati
 * @param kaynak      "KAMERA" (uygulama ici cekim) ya da "KITAPLIK"
 */
public record CaptureEnvelope(
        String challengeId,
        Double lat,
        Double lon,
        Double dogrulukMetre,
        Instant cekimAni,
        String kaynak) {

    public boolean kameraylaCekildi() {
        return "KAMERA".equalsIgnoreCase(kaynak);
    }
}
