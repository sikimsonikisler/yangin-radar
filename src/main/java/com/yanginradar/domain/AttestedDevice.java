package com.yanginradar.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

/**
 * App Attest ile dogrulanmis bir cihaz anahtari.
 *
 * <p>Apple, uygulamanin gercekten bizim imzaladigimiz yapi oldugunu ve gercek bir
 * cihazda calistigini kanitlayan bir sertifika zinciri uretir. Kaydi bir kez yapariz;
 * sonraki her fotograf yuklemesinde bu anahtarla uretilmis bir imza (assertion) bekleriz.
 *
 * <p>{@code signCount} her imzada artar. Azalirsa ya da ayni kalirsa, imza tekrar
 * oynatiliyor demektir ve reddedilir.
 */
@Entity
@Table(name = "attested_device")
public class AttestedDevice {

    /** Apple'in urettigi anahtar tanitici (base64). */
    @Id
    @Column(length = 128)
    private String keyId;

    /** CBOR olarak saklanan dogrulanmis kimlik bilgisi (acik anahtar dahil). */
    @Lob
    @Column(nullable = false)
    private byte[] attestedCredentialData;

    /** CBOR olarak saklanan attestation ifadesi. */
    @Lob
    @Column(nullable = false)
    private byte[] attestationStatement;

    /** Son gorulen imza sayaci; her assertion'da kesin olarak artmali. */
    @Column(nullable = false)
    private long signCount;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    @Column(nullable = false)
    private Instant lastSeenAt = Instant.now();

    protected AttestedDevice() {
    }

    public AttestedDevice(String keyId, byte[] attestedCredentialData,
                          byte[] attestationStatement, long signCount) {
        this.keyId = keyId;
        this.attestedCredentialData = attestedCredentialData;
        this.attestationStatement = attestationStatement;
        this.signCount = signCount;
    }

    public String getKeyId() {
        return keyId;
    }

    public byte[] getAttestedCredentialData() {
        return attestedCredentialData;
    }

    public byte[] getAttestationStatement() {
        return attestationStatement;
    }

    public long getSignCount() {
        return signCount;
    }

    public void setSignCount(long signCount) {
        this.signCount = signCount;
        this.lastSeenAt = Instant.now();
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getLastSeenAt() {
        return lastSeenAt;
    }
}
