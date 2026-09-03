package com.yanginradar.config;

import java.util.List;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Uygulama hazir olunca adresi konsola basar ve sayfayi varsayilan tarayicida acar.
 *
 * <p>Isletim sisteminin kendi komutu kullanilir ({@code open} / {@code start} /
 * {@code xdg-open}). {@code java.awt.Desktop} tercih edilmedi: Spring Boot varsayilan
 * olarak headless modda calisir ve orada {@code Desktop} devre disi kalir.
 *
 * <p>Sunucuya kurulurken {@code yanginradar.tarayiciyi-ac: false} ile kapatilir.
 */
@Component
public class TarayiciAcici {

    private static final Logger log = LoggerFactory.getLogger(TarayiciAcici.class);

    private final boolean acik;
    private final int port;

    public TarayiciAcici(@Value("${yanginradar.tarayiciyi-ac:true}") boolean acik,
                         @Value("${server.port:8080}") int port) {
        this.acik = acik;
        this.port = port;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void ac() {
        String adres = "http://localhost:" + port;

        log.info("");
        log.info("   ============================================================");
        log.info("    YanginRadar hazir");
        log.info("");
        log.info("    Harita         ->  {}", adres);
        log.info("    Yetkili paneli ->  {}/admin.html   (anahtar: degistir-beni)", adres);
        log.info("");
        log.info("    Durdurmak icin: kirmizi kare (IntelliJ) ya da Ctrl+C (terminal)");
        log.info("   ============================================================");
        log.info("");

        if (!acik) {
            return;
        }

        List<String> komut = tarayiciKomutu(adres);
        if (komut == null) {
            log.info("Bu isletim sisteminde otomatik acma yok, adresi elle acin: {}", adres);
            return;
        }

        try {
            new ProcessBuilder(komut).start();
            log.info("Tarayici aciliyor... Acilmadiysa adresi elle yazin: {}", adres);
        } catch (Exception e) {
            // Tarayici acilamamasi uygulamayi durdurmaz - adres zaten yukarida yazili
            log.info("Tarayici otomatik acilamadi, adresi elle acin: {}", adres);
        }
    }

    /** Isletim sistemine gore tarayici acma komutu; bilinmiyorsa null. */
    static List<String> tarayiciKomutu(String adres) {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (os.contains("mac")) {
            return List.of("open", adres);
        }
        if (os.contains("win")) {
            return List.of("rundll32", "url.dll,FileProtocolHandler", adres);
        }
        if (os.contains("nix") || os.contains("nux")) {
            return List.of("xdg-open", adres);
        }
        return null;
    }
}
