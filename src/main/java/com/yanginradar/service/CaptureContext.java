package com.yanginradar.service;

import java.time.Instant;

/**
 * Fotograf cekildigi anda uygulamanin kendi olctugu baglam.
 *
 * <p>EXIF'ten <b>okunmaz</b> - uygulama kamerayla kareyi cektigi anda cihazin canli
 * konum olcumunu ve saatini buraya yazar. Aradaki fark onemli: EXIF yazilabilir bir
 * alandir, buraya yazilan deger ise App Attest imzasiyla korunur.
 *
 * @param lat            cekim anindaki enlem, olculemediyse null
 * @param lon            cekim anindaki boylam, olculemediyse null
 * @param dogrulukMetre  konum olcumunun yaticap hatasi
 * @param cekimAni       cihazin cekim anindaki saati
 * @param kamera         kare uygulama ici kamerayla mi cekildi (kitapliktan secim degil)
 * @param cihazDogrulandi App Attest imzasi dogrulandi mi
 */
public record CaptureContext(
        Double lat,
        Double lon,
        Double dogrulukMetre,
        Instant cekimAni,
        boolean kamera,
        boolean cihazDogrulandi) {

    /** Cekim baglami olmayan yukleme - kitapliktan secilmis fotograf. */
    public static final CaptureContext YOK =
            new CaptureContext(null, null, null, null, false, false);

    public boolean konumVar() {
        return lat != null && lon != null;
    }

    public boolean bosMu() {
        return !konumVar() && cekimAni == null && !kamera && !cihazDogrulandi;
    }
}
