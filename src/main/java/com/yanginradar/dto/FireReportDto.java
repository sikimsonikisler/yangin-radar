package com.yanginradar.dto;

import java.time.Instant;
import java.util.List;

import com.yanginradar.domain.FireReport;
import com.yanginradar.domain.ReportSource;
import com.yanginradar.domain.ReportStatus;
import com.yanginradar.domain.Severity;

/** Haritaya ve canli akisa gonderilen yangin kaydi. Kisisel iletisim bilgisi tasimaz. */
public record FireReportDto(
        Long id,
        double lat,
        double lon,
        String il,
        String ilce,
        String aciklama,
        Severity severity,
        String severityLabel,
        ReportSource source,
        String sourceLabel,
        ReportStatus status,
        String statusLabel,
        int confidence,
        String reporterAlias,
        int confirmCount,
        int denyCount,
        Double brightness,
        boolean dispatched,
        int photoCount,
        List<PhotoDto> photos,
        Instant createdAt,
        Instant updatedAt) {

    public static FireReportDto from(FireReport r, List<PhotoDto> photos) {
        return new FireReportDto(
                r.getId(),
                r.getLat(),
                r.getLon(),
                r.getIl(),
                r.getIlce(),
                r.getAciklama(),
                r.getSeverity(),
                r.getSeverity().getLabel(),
                r.getSource(),
                r.getSource().getLabel(),
                r.getStatus(),
                r.getStatus().getLabel(),
                r.getConfidence(),
                r.getReporterAlias(),
                r.getConfirmCount(),
                r.getDenyCount(),
                r.getBrightness(),
                r.isDispatched(),
                photos.size(),
                photos,
                r.getCreatedAt(),
                r.getUpdatedAt());
    }
}
