package com.yanginradar.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

/** Haritada bir noktayi isaretleyen yangin kaydi (vatandas ihbari veya uydu isi noktasi). */
@Entity
@Table(name = "fire_report")
public class FireReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private double lat;

    @Column(nullable = false)
    private double lon;

    private String il;
    private String ilce;

    @Column(length = 1000)
    private String aciklama;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private Severity severity = Severity.DUMAN_GORULDU;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ReportSource source = ReportSource.VATANDAS;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ReportStatus status = ReportStatus.BEKLIYOR;

    /** 0-100. Fotograf kaniti, uydu ortusmesi ve capraz ihbarlardan hesaplanir. */
    @Column(nullable = false)
    private int confidence = 0;

    private String reporterAlias;

    /** Yetkiliye aktarilirken kullanilacak iletisim bilgisi. Panelde maskelenir. */
    private String reporterContact;

    @Column(nullable = false)
    private int confirmCount = 0;

    @Column(nullable = false)
    private int denyCount = 0;

    /** Uydu kaydiysa FIRMS satir kimligi - ayni noktanin iki kez girilmesini engeller. */
    @Column(unique = true)
    private String externalId;

    /** FIRMS parlaklik degeri (Kelvin). Uydu kayitlarinda dolu. */
    private Double brightness;

    @Column(nullable = false)
    private boolean dispatched = false;

    private Instant dispatchedAt;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    @Column(nullable = false)
    private Instant updatedAt = Instant.now();

    @OneToMany(mappedBy = "fireReport", cascade = CascadeType.ALL, orphanRemoval = true,
               fetch = FetchType.LAZY)
    @OrderBy("uploadedAt ASC")
    private List<PhotoEvidence> photos = new ArrayList<>();

    public void touch() {
        this.updatedAt = Instant.now();
    }

    public void addPhoto(PhotoEvidence photo) {
        photos.add(photo);
        photo.setFireReport(this);
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public double getLat() {
        return lat;
    }

    public void setLat(double lat) {
        this.lat = lat;
    }

    public double getLon() {
        return lon;
    }

    public void setLon(double lon) {
        this.lon = lon;
    }

    public String getIl() {
        return il;
    }

    public void setIl(String il) {
        this.il = il;
    }

    public String getIlce() {
        return ilce;
    }

    public void setIlce(String ilce) {
        this.ilce = ilce;
    }

    public String getAciklama() {
        return aciklama;
    }

    public void setAciklama(String aciklama) {
        this.aciklama = aciklama;
    }

    public Severity getSeverity() {
        return severity;
    }

    public void setSeverity(Severity severity) {
        this.severity = severity;
    }

    public ReportSource getSource() {
        return source;
    }

    public void setSource(ReportSource source) {
        this.source = source;
    }

    public ReportStatus getStatus() {
        return status;
    }

    public void setStatus(ReportStatus status) {
        this.status = status;
    }

    public int getConfidence() {
        return confidence;
    }

    public void setConfidence(int confidence) {
        this.confidence = confidence;
    }

    public String getReporterAlias() {
        return reporterAlias;
    }

    public void setReporterAlias(String reporterAlias) {
        this.reporterAlias = reporterAlias;
    }

    public String getReporterContact() {
        return reporterContact;
    }

    public void setReporterContact(String reporterContact) {
        this.reporterContact = reporterContact;
    }

    public int getConfirmCount() {
        return confirmCount;
    }

    public void setConfirmCount(int confirmCount) {
        this.confirmCount = confirmCount;
    }

    public int getDenyCount() {
        return denyCount;
    }

    public void setDenyCount(int denyCount) {
        this.denyCount = denyCount;
    }

    public String getExternalId() {
        return externalId;
    }

    public void setExternalId(String externalId) {
        this.externalId = externalId;
    }

    public Double getBrightness() {
        return brightness;
    }

    public void setBrightness(Double brightness) {
        this.brightness = brightness;
    }

    public boolean isDispatched() {
        return dispatched;
    }

    public void setDispatched(boolean dispatched) {
        this.dispatched = dispatched;
    }

    public Instant getDispatchedAt() {
        return dispatchedAt;
    }

    public void setDispatchedAt(Instant dispatchedAt) {
        this.dispatchedAt = dispatchedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public List<PhotoEvidence> getPhotos() {
        return photos;
    }

    public void setPhotos(List<PhotoEvidence> photos) {
        this.photos = photos;
    }
}
