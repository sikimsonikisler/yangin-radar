package com.yanginradar.domain;

/** Kaydin nereden geldigi. */
public enum ReportSource {
    VATANDAS("Vatandaş ihbarı"),
    UYDU("Uydu tespiti"),
    RESMI("Resmî kaynak");

    private final String label;

    ReportSource(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
