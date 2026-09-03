package com.yanginradar;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.yanginradar.config.GeoUtils;

class GeoUtilsTest {

    @Test
    void ankaraIstanbulArasiYaklasik350Km() {
        double d = GeoUtils.distanceMeters(39.9334, 32.8597, 41.0082, 28.9784);
        assertThat(d).isBetween(340_000d, 360_000d);
    }

    @Test
    void ayniNoktaSifirMesafe() {
        assertThat(GeoUtils.distanceMeters(36.81, 28.26, 36.81, 28.26)).isZero();
    }

    @Test
    void turkiyeKutusuSinirlariniAyirtEder() {
        assertThat(GeoUtils.insideTurkiye(39.93, 32.86)).isTrue();   // Ankara
        assertThat(GeoUtils.insideTurkiye(48.85, 2.35)).isFalse();   // Paris
        assertThat(GeoUtils.insideTurkiye(30.04, 31.24)).isFalse();  // Kahire
    }

    @Test
    void kutuDeltalariMakulMesafeUretir() {
        double lat = GeoUtils.latDelta(1000);
        double lon = GeoUtils.lonDelta(1000, 39.0);
        assertThat(GeoUtils.distanceMeters(39.0, 32.0, 39.0 + lat, 32.0)).isBetween(950d, 1050d);
        assertThat(GeoUtils.distanceMeters(39.0, 32.0, 39.0, 32.0 + lon)).isBetween(950d, 1050d);
    }
}
