package com.yanginradar.dto;

import java.time.Instant;
import java.util.List;

import com.yanginradar.domain.PhotoEvidence;
import com.yanginradar.domain.PhotoVerdict;

/** Fotograf kaniti ve dogrulama sonucunun istemciye acilan hali. */
public record PhotoDto(
        Long id,
        String url,
        PhotoVerdict verdict,
        String verdictLabel,
        int score,
        Double distanceMeters,
        Instant takenAt,
        boolean hasGps,
        /** Uygulamanin cekim aninda olctugu konumun ihbar noktasina uzakligi. */
        Double captureDistanceMeters,
        /** Kare uygulama ici kamerayla mi cekildi? */
        boolean cameraCaptured,
        /** App Attest imzasi dogrulandi mi? */
        boolean deviceAttested,
        List<String> checks,
        Instant uploadedAt) {

    public static PhotoDto from(PhotoEvidence p) {
        List<String> lines = p.getChecks() == null || p.getChecks().isBlank()
                ? List.of()
                : List.of(p.getChecks().split("\n"));
        return new PhotoDto(
                p.getId(),
                "/api/photos/" + p.getId() + "/content",
                p.getVerdict(),
                p.getVerdict().getLabel(),
                p.getScore(),
                p.getDistanceMeters(),
                p.getExifTakenAt(),
                p.getExifLat() != null && p.getExifLon() != null,
                p.getCaptureDistanceMeters(),
                p.isCameraCaptured(),
                p.isDeviceAttested(),
                lines,
                p.getUploadedAt());
    }
}
