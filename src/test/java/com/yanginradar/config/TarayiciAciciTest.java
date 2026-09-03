package com.yanginradar.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

class TarayiciAciciTest {

    @Test
    void calisilanIsletimSisteminicinKomutUretir() {
        List<String> komut = TarayiciAcici.tarayiciKomutu("http://localhost:8080");

        assertThat(komut).isNotNull();
        assertThat(komut).last().isEqualTo("http://localhost:8080");
        assertThat(komut.get(0)).isIn("open", "rundll32", "xdg-open");
    }
}
