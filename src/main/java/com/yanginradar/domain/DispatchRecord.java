package com.yanginradar.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Yetkiliye aktarim kaydi (giden kutusu).
 *
 * <p>Bu kayit uygulama icinde tutulur; 112 / 177 gibi gercek acil hatlara otomatik
 * gonderim YAPMAZ. Gercek entegrasyon ilgili kurumun resmi izni ve API'si ile
 * {@code AuthorityNotificationService} uzerinden eklenmelidir.
 */
@Entity
@Table(name = "dispatch_record")
public class DispatchRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long fireReportId;

    /** Hedef birim: OGM_177, AFAD_112, IL_ORMAN_MUDURLUGU vb. */
    @Column(nullable = false)
    private String channel;

    @Column(nullable = false, length = 4000)
    private String payload;

    @Column(nullable = false)
    private String state = "KUYRUKTA";

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getFireReportId() {
        return fireReportId;
    }

    public void setFireReportId(Long fireReportId) {
        this.fireReportId = fireReportId;
    }

    public String getChannel() {
        return channel;
    }

    public void setChannel(String channel) {
        this.channel = channel;
    }

    public String getPayload() {
        return payload;
    }

    public void setPayload(String payload) {
        this.payload = payload;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
