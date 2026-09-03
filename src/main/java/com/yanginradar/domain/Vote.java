package com.yanginradar.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * Bir ihbara verilmis tek oy.
 *
 * <p>Neden var: oy ucu eskiden hicbir kimlik istemiyordu, yani ayni kisi bir ihbari
 * istedigi kadar destekleyip guven puanini sisirebiliyordu. Bu tablo (ihbar, oy veren)
 * ikilisini benzersiz tutarak bunu engeller.
 *
 * <p>{@code voterId} cihazda uretilen, kisisel bilgi tasimayan bir kimliktir. Kararli
 * bir kimlik dogrulamasi <b>degildir</b>: kullanici uygulamayi silip yeniden kurarsa
 * yeni bir kimlik alir. Yani bu katman gundelik tekrar oyu keser, kararli bir saldirganı
 * degil. Kriptografik guvence isteniyorsa App Attest anahtarina baglanmasi gerekir.
 */
@Entity
@Table(name = "vote",
       uniqueConstraints = @UniqueConstraint(name = "uk_vote_ihbar_veren",
                                             columnNames = {"fire_report_id", "voter_id"}))
public class Vote {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "fire_report_id", nullable = false)
    private Long fireReportId;

    @Column(name = "voter_id", nullable = false, length = 64)
    private String voterId;

    /** true = "Doğruluyorum", false = "Göremiyorum". */
    @Column(nullable = false)
    private boolean confirm;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    @Column(nullable = false)
    private Instant updatedAt = Instant.now();

    protected Vote() {
    }

    public Vote(Long fireReportId, String voterId, boolean confirm) {
        this.fireReportId = fireReportId;
        this.voterId = voterId;
        this.confirm = confirm;
    }

    public Long getId() {
        return id;
    }

    public Long getFireReportId() {
        return fireReportId;
    }

    public String getVoterId() {
        return voterId;
    }

    public boolean isConfirm() {
        return confirm;
    }

    public void setConfirm(boolean confirm) {
        this.confirm = confirm;
        this.updatedAt = Instant.now();
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
