package com.yanginradar.domain;

/** Ihbarin dogrulama durumu. */
public enum ReportStatus {
    BEKLIYOR("Bekliyor"),
    INCELENIYOR("İnceleniyor"),
    DOGRULANDI("Doğrulandı"),
    REDDEDILDI("Reddedildi"),
    SONDURULDU("Söndürüldü");

    private final String label;

    ReportStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
