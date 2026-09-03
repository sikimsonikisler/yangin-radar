package com.yanginradar.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Tek kullanimlik, kisa omurlu meydan okuma (nonce).
 *
 * <p>Iki yerde kullanilir:
 * <ul>
 *   <li>{@link Amac#ATTEST} - App Attest anahtar kaydinda Apple'in imzaladigi nonce</li>
 *   <li>{@link Amac#CEKIM} - fotograf cekiminden hemen once alinir; fotografin
 *       onceden hazirlanip saklanamamasini saglar</li>
 * </ul>
 *
 * <p>Bir nonce yalnizca bir kez harcanir ve suresi dolunca gecersizdir.
 */
@Entity
@Table(name = "capture_challenge")
public class CaptureChallenge {

    public enum Amac {
        ATTEST,
        CEKIM
    }

    /** Istemcinin geri gonderdigi tanitici. */
    @Id
    @Column(length = 64)
    private String id;

    /** Rastgele 32 bayt, base64url. Apple'a ve imzaya giren asil deger. */
    @Column(nullable = false, length = 128)
    private String nonce;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Amac amac;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    @Column(nullable = false)
    private Instant expiresAt;

    /** Harcandigi an. Null ise henuz kullanilmamis. */
    private Instant usedAt;

    protected CaptureChallenge() {
    }

    public CaptureChallenge(String id, String nonce, Amac amac, Instant expiresAt) {
        this.id = id;
        this.nonce = nonce;
        this.amac = amac;
        this.expiresAt = expiresAt;
    }

    public boolean harcanabilir(Amac beklenen) {
        return usedAt == null && amac == beklenen && Instant.now().isBefore(expiresAt);
    }

    public String getId() {
        return id;
    }

    public String getNonce() {
        return nonce;
    }

    public Amac getAmac() {
        return amac;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getUsedAt() {
        return usedAt;
    }

    public void harca() {
        this.usedAt = Instant.now();
    }
}
