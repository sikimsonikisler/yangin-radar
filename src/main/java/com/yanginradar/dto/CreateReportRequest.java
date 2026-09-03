package com.yanginradar.dto;

import com.yanginradar.domain.Severity;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Haritada isaretlenen yeni yangin ihbari. */
public record CreateReportRequest(

        @NotNull
        @DecimalMin(value = "-90.0") @DecimalMax(value = "90.0")
        Double lat,

        @NotNull
        @DecimalMin(value = "-180.0") @DecimalMax(value = "180.0")
        Double lon,

        @Size(max = 80) String il,

        @Size(max = 80) String ilce,

        @Size(max = 1000) String aciklama,

        @NotNull Severity severity,

        @Size(max = 60) String reporterAlias,

        @Size(max = 120) String reporterContact) {
}
