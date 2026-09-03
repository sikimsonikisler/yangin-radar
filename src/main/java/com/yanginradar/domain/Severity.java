package com.yanginradar.domain;

/** Ihbarin bildirdigi yangin buyuklugu. */
public enum Severity {
    DUMAN_GORULDU("Duman görüldü", 1),
    KUCUK_YANGIN("Küçük yangın", 2),
    BUYUK_YANGIN("Büyük yangın", 3),
    HIZLA_YAYILIYOR("Hızla yayılıyor", 4);

    private final String label;
    private final int weight;

    Severity(String label, int weight) {
        this.label = label;
        this.weight = weight;
    }

    public String getLabel() {
        return label;
    }

    public int getWeight() {
        return weight;
    }
}
