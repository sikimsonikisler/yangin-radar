package com.yanginradar.domain;

/** Fotograf dogrulama sonucu. */
public enum PhotoVerdict {
    GECERLI("Kanıt olarak geçerli"),
    SUPHELI("Şüpheli — inceleme gerekli"),
    GECERSIZ("Geçersiz");

    private final String label;

    PhotoVerdict(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
