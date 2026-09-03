package com.yanginradar.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** Bir ihbara eklenen fotograf ve otomatik dogrulama sonucu. */
@Entity
@Table(name = "photo_evidence")
public class PhotoEvidence {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "fire_report_id", nullable = false)
    private FireReport fireReport;

    @Column(nullable = false)
    private String storedFilename;

    private String originalFilename;

    private String contentType;

    private long sizeBytes;

    /** Ayni fotografin farkli ihbarlarda tekrar kullanilmasini yakalamak icin. */
    @Column(nullable = false, length = 64)
    private String sha256;

    private Double exifLat;
    private Double exifLon;
    private Instant exifTakenAt;

    /** EXIF konumu ile ihbar noktasi arasindaki mesafe (metre). EXIF yoksa null. */
    private Double distanceMeters;

    // --- Cekim baglami: uygulamanin cekim aninda kendi olctugu degerler ---

    private Double captureLat;
    private Double captureLon;
    private Double captureAccuracyMeters;
    private Instant capturedAt;

    /** Cekim konumunun ihbar noktasina uzakligi (metre). */
    private Double captureDistanceMeters;

    // columnDefinition: var olan satirlar icin varsayilan sart - aksi halde dolu bir
    // tabloya NOT NULL sutun eklenemiyor ve semanin guncellenmesi sessizce basarisiz oluyor.

    /** Kare uygulama ici kamerayla mi cekildi? */
    @Column(nullable = false, columnDefinition = "boolean default false")
    private boolean cameraCaptured = false;

    /** App Attest imzasi dogrulandi mi? */
    @Column(nullable = false, columnDefinition = "boolean default false")
    private boolean deviceAttested = false;

    /** Imzayi ureten App Attest anahtari (kisaltilmis). */
    @Column(length = 128)
    private String attestedKeyId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private PhotoVerdict verdict = PhotoVerdict.SUPHELI;

    /** 0-100 arasi fotograf guven puani. */
    @Column(nullable = false)
    private int score = 0;

    /** Calisan kontrollerin insan tarafindan okunabilir ozeti, satir satir. */
    @Column(length = 2000)
    private String checks;

    @Column(nullable = false)
    private Instant uploadedAt = Instant.now();

    public Double getCaptureLat() {
        return captureLat;
    }

    public void setCaptureLat(Double captureLat) {
        this.captureLat = captureLat;
    }

    public Double getCaptureLon() {
        return captureLon;
    }

    public void setCaptureLon(Double captureLon) {
        this.captureLon = captureLon;
    }

    public Double getCaptureAccuracyMeters() {
        return captureAccuracyMeters;
    }

    public void setCaptureAccuracyMeters(Double captureAccuracyMeters) {
        this.captureAccuracyMeters = captureAccuracyMeters;
    }

    public Instant getCapturedAt() {
        return capturedAt;
    }

    public void setCapturedAt(Instant capturedAt) {
        this.capturedAt = capturedAt;
    }

    public Double getCaptureDistanceMeters() {
        return captureDistanceMeters;
    }

    public void setCaptureDistanceMeters(Double captureDistanceMeters) {
        this.captureDistanceMeters = captureDistanceMeters;
    }

    public boolean isCameraCaptured() {
        return cameraCaptured;
    }

    public void setCameraCaptured(boolean cameraCaptured) {
        this.cameraCaptured = cameraCaptured;
    }

    public boolean isDeviceAttested() {
        return deviceAttested;
    }

    public void setDeviceAttested(boolean deviceAttested) {
        this.deviceAttested = deviceAttested;
    }

    public String getAttestedKeyId() {
        return attestedKeyId;
    }

    public void setAttestedKeyId(String attestedKeyId) {
        this.attestedKeyId = attestedKeyId;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public FireReport getFireReport() {
        return fireReport;
    }

    public void setFireReport(FireReport fireReport) {
        this.fireReport = fireReport;
    }

    public String getStoredFilename() {
        return storedFilename;
    }

    public void setStoredFilename(String storedFilename) {
        this.storedFilename = storedFilename;
    }

    public String getOriginalFilename() {
        return originalFilename;
    }

    public void setOriginalFilename(String originalFilename) {
        this.originalFilename = originalFilename;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public void setSizeBytes(long sizeBytes) {
        this.sizeBytes = sizeBytes;
    }

    public String getSha256() {
        return sha256;
    }

    public void setSha256(String sha256) {
        this.sha256 = sha256;
    }

    public Double getExifLat() {
        return exifLat;
    }

    public void setExifLat(Double exifLat) {
        this.exifLat = exifLat;
    }

    public Double getExifLon() {
        return exifLon;
    }

    public void setExifLon(Double exifLon) {
        this.exifLon = exifLon;
    }

    public Instant getExifTakenAt() {
        return exifTakenAt;
    }

    public void setExifTakenAt(Instant exifTakenAt) {
        this.exifTakenAt = exifTakenAt;
    }

    public Double getDistanceMeters() {
        return distanceMeters;
    }

    public void setDistanceMeters(Double distanceMeters) {
        this.distanceMeters = distanceMeters;
    }

    public PhotoVerdict getVerdict() {
        return verdict;
    }

    public void setVerdict(PhotoVerdict verdict) {
        this.verdict = verdict;
    }

    public int getScore() {
        return score;
    }

    public void setScore(int score) {
        this.score = score;
    }

    public String getChecks() {
        return checks;
    }

    public void setChecks(String checks) {
        this.checks = checks;
    }

    public Instant getUploadedAt() {
        return uploadedAt;
    }

    public void setUploadedAt(Instant uploadedAt) {
        this.uploadedAt = uploadedAt;
    }
}
