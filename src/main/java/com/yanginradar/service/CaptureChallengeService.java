package com.yanginradar.service;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.yanginradar.domain.CaptureChallenge;
import com.yanginradar.repo.CaptureChallengeRepository;

/**
 * Tek kullanimlik meydan okuma (nonce) uretir ve harcar.
 *
 * <p>Amaci: fotografin cekim aninin sunucu tarafindan belirlenmesi. Istemci once
 * buradan bir nonce alir, sonra fotografi ceker ve nonce'u geri getirir. Boylece
 * onceden hazirlanip saklanmis bir kare kullanilamaz - nonce o an uretilmistir ve
 * kisa surede olur.
 */
@Service
public class CaptureChallengeService {

    private static final Logger log = LoggerFactory.getLogger(CaptureChallengeService.class);
    private static final SecureRandom RASTGELE = new SecureRandom();

    /** Nonce omru. Cekim + yukleme icin bol, saklamak icin cok kisa. */
    private static final long OMUR_DAKIKA = 10;

    private final CaptureChallengeRepository repo;

    public CaptureChallengeService(CaptureChallengeRepository repo) {
        this.repo = repo;
    }

    @Transactional
    public CaptureChallenge uret(CaptureChallenge.Amac amac) {
        byte[] ham = new byte[32];
        RASTGELE.nextBytes(ham);

        CaptureChallenge c = new CaptureChallenge(
                UUID.randomUUID().toString(),
                Base64.getUrlEncoder().withoutPadding().encodeToString(ham),
                amac,
                Instant.now().plus(OMUR_DAKIKA, ChronoUnit.MINUTES));

        return repo.save(c);
    }

    /**
     * Nonce'u dogrular ve harcar. Ayni nonce ikinci kez kabul edilmez.
     *
     * @throws IllegalArgumentException gecersiz, suresi dolmus ya da harcanmis ise
     */
    @Transactional
    public CaptureChallenge harca(String challengeId, CaptureChallenge.Amac amac) {
        CaptureChallenge c = repo.findById(challengeId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Geçersiz doğrulama kodu. Fotoğrafı yeniden çekin."));

        if (!c.harcanabilir(amac)) {
            throw new IllegalArgumentException(
                    "Doğrulama kodunun süresi dolmuş ya da daha önce kullanılmış. "
                    + "Fotoğrafı yeniden çekin.");
        }

        c.harca();
        return repo.save(c);
    }

    /** Suresi dolmus kayitlar birikmesin. */
    @Scheduled(fixedDelay = 30 * 60 * 1000L)
    @Transactional
    public void temizle() {
        int silinen = repo.deleteExpired(Instant.now().minus(1, ChronoUnit.HOURS));
        if (silinen > 0) {
            log.debug("{} suresi dolmus meydan okuma silindi", silinen);
        }
    }
}
