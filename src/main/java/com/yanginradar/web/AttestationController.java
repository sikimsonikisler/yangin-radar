package com.yanginradar.web;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.yanginradar.domain.CaptureChallenge;
import com.yanginradar.service.AppAttestService;
import com.yanginradar.service.CaptureChallengeService;

import jakarta.validation.constraints.NotBlank;

/**
 * Kanit zincirinin acilis uclari.
 *
 * <p>Uygulama once buradan tek kullanimlik bir kod alir; fotografi ondan sonra ceker.
 * Kodun omru kisadir ve bir kez harcanir - boylece onceden hazirlanmis bir kare
 * "az once cekildi" diye sunulamaz.
 */
@RestController
@RequestMapping("/api")
public class AttestationController {

    private final CaptureChallengeService challenges;
    private final AppAttestService attest;

    public AttestationController(CaptureChallengeService challenges, AppAttestService attest) {
        this.challenges = challenges;
        this.attest = attest;
    }

    /** App Attest anahtar kaydi icin meydan okuma. */
    @PostMapping("/attest/challenge")
    public Map<String, Object> attestKodu() {
        CaptureChallenge c = challenges.uret(CaptureChallenge.Amac.ATTEST);
        return Map.of(
                "challengeId", c.getId(),
                "nonce", c.getNonce(),
                "expiresAt", c.getExpiresAt(),
                "enabled", attest.acik());
    }

    /** Cihazin App Attest anahtarini kaydeder. Cihaz basina bir kez yapilir. */
    @PostMapping("/attest/register")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> cihazKaydet(@RequestBody KayitIstegi istek) {
        CaptureChallenge kod = challenges.harca(istek.challengeId(), CaptureChallenge.Amac.ATTEST);
        attest.cihaziKaydet(kod.getNonce(), istek.keyId(), istek.attestationObject());
        return Map.of("kayitli", true);
    }

    /** Fotograf cekiminden hemen once alinan tek kullanimlik kod. */
    @PostMapping("/capture/challenge")
    public Map<String, Object> cekimKodu() {
        CaptureChallenge c = challenges.uret(CaptureChallenge.Amac.CEKIM);
        return Map.of(
                "challengeId", c.getId(),
                "nonce", c.getNonce(),
                "expiresAt", c.getExpiresAt());
    }

    public record KayitIstegi(
            @NotBlank String challengeId,
            @NotBlank String keyId,
            @NotBlank String attestationObject) {
    }
}
