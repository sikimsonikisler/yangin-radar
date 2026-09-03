package com.yanginradar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Kanit zinciri: tek kullanimlik kod + cekim baglami.
 *
 * <p>App Attest imzasi burada sinanmaz - imzayi ancak gercek bir Apple cihazi
 * uretebilir. Sinanan sey, imzasiz bir yuklemenin zincirin geri kalanindan
 * dogru sekilde gecmesi ve "cihaz dogrulanmadi" olarak isaretlenmesidir.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:capture-test;DB_CLOSE_DELAY=-1",
        "yanginradar.demo-data=false",
        "yanginradar.firms.enabled=false",
        "yanginradar.storage.upload-dir=${java.io.tmpdir}/yanginradar-capture-test",
        "yanginradar.moderation.admin-token=test-anahtar"
})
class CaptureChainTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper json;

    @Test
    void cekimKoduTekKullanimliktir() throws Exception {
        long id = yeniIhbar(36.8121, 28.2610);
        String kod = cekimKodu();

        // Ilk kullanim gecer
        mockMvc.perform(fotografIsteği(id, kod, 36.8121, 28.2610))
                .andExpect(status().isOk());

        // Ayni kod ikinci kez reddedilir
        mockMvc.perform(fotografIsteği(id, kod, 36.8121, 28.2610))
                .andExpect(status().isBadRequest());
    }

    @Test
    void uydurmaCekimKoduReddedilir() throws Exception {
        long id = yeniIhbar(37.0410, 30.5680);

        mockMvc.perform(fotografIsteği(id, "olmayan-kod", 37.0410, 30.5680))
                .andExpect(status().isBadRequest());
    }

    @Test
    void yakinCekimKonumuPuaniYukseltirVeDogrulanmamisIsaretlenir() throws Exception {
        long id = yeniIhbar(38.4237, 27.1428);

        String cevap = mockMvc.perform(fotografIsteği(id, cekimKodu(), 38.4237, 27.1428))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        JsonNode foto = json.readTree(cevap).get("photos").get(0);

        assertThat(foto.get("cameraCaptured").asBoolean())
                .as("kamerayla cekildigi beyan edildi")
                .isTrue();
        assertThat(foto.get("deviceAttested").asBoolean())
                .as("imza gonderilmedigi icin cihaz dogrulanmamis olmali")
                .isFalse();
        assertThat(foto.get("captureDistanceMeters").asDouble())
                .as("cekim konumu ihbar noktasiyla ayni")
                .isLessThan(50);

        String kontroller = kontrolMetni(foto);
        assertThat(kontroller).contains("uygulama içi kamerayla çekildi");
        assertThat(kontroller).contains("Cihaz kimliği doğrulanamadı");
    }

    @Test
    void uzakCekimKonumuCezalandirilir() throws Exception {
        long id = yeniIhbar(36.8121, 28.2610);

        // Ihbar Marmaris'te, cekim Istanbul'da
        String cevap = mockMvc.perform(fotografIsteği(id, cekimKodu(), 41.0082, 28.9784))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        JsonNode foto = json.readTree(cevap).get("photos").get(0);

        assertThat(foto.get("captureDistanceMeters").asDouble()).isGreaterThan(400_000);
        assertThat(foto.get("verdict").asText())
                .as("cekim konumu tutmayan kare gecerli sayilmamali")
                .isNotEqualTo("GECERLI");
        assertThat(kontrolMetni(foto)).contains("uzakta");
    }

    @Test
    void cekimBeyaniOlmadanEskiDavranisKorunur() throws Exception {
        long id = yeniIhbar(37.9500, 32.4900);

        String cevap = mockMvc.perform(multipart("/api/reports/" + id + "/photos")
                        .file(new MockMultipartFile("file", "duz.jpg", "image/jpeg",
                                ExifTestJpeg.duz(1024, 768))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        JsonNode foto = json.readTree(cevap).get("photos").get(0);
        assertThat(foto.get("cameraCaptured").asBoolean()).isFalse();
        assertThat(foto.get("deviceAttested").asBoolean()).isFalse();
        assertThat(foto.get("captureDistanceMeters").isNull()).isTrue();
    }

    @Test
    void uydurmaImzaGuvenKazandirmaz() throws Exception {
        // Guvenligin can alici noktasi: istemci "ben dogrulandim" diyemez.
        // Guven, imzanin dogrulanmasindan gelir; beyandan degil.
        long id = yeniIhbar(39.9208, 32.8541);

        String cevap = mockMvc.perform(fotografIsteği(id, cekimKodu(), 39.9208, 32.8541)
                        .param("keyId", "c2FodGVfYW5haHRhcl9pZA==")
                        .param("assertion", "c2FodGVfaW16YV92ZXJpc2k="))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        JsonNode foto = json.readTree(cevap).get("photos").get(0);

        assertThat(foto.get("deviceAttested").asBoolean())
                .as("uydurma imza cihazi dogrulanmis saymamali")
                .isFalse();
        assertThat(kontrolMetni(foto)).contains("Cihaz kimliği doğrulanamadı");
    }

    @Test
    void attestKoduUretilir() throws Exception {
        mockMvc.perform(post("/api/attest/challenge"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$.nonce").isNotEmpty())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$.challengeId").isNotEmpty());
    }

    // ------------------------------------------------------------- yardimci

    /**
     * Kontrol satirlarini duz metne cevirir.
     *
     * <p>{@code JsonNode.toString()} ASCII disi harfleri {@code \u00e7} gibi kacis
     * dizilerine cevirir; Turkce metinde arama yapmak icin dugum dugum okumak gerekir.
     */
    private String kontrolMetni(JsonNode foto) {
        StringBuilder sb = new StringBuilder();
        foto.get("checks").forEach(satir -> sb.append(satir.asText()).append('\n'));
        return sb.toString();
    }

    private String cekimKodu() throws Exception {
        String cevap = mockMvc.perform(post("/api/capture/challenge"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return json.readTree(cevap).get("challengeId").asText();
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
            fotografIsteği(long id, String challengeId, double lat, double lon) throws Exception {

        String beyan = """
                {"challengeId":"%s","lat":%s,"lon":%s,"dogrulukMetre":8.0,
                 "cekimAni":"%s","kaynak":"KAMERA"}
                """.formatted(challengeId, lat, lon, Instant.now().toString());

        return multipart("/api/reports/" + id + "/photos")
                .file(new MockMultipartFile("file", "cekim.jpg", "image/jpeg",
                        ExifTestJpeg.duz(1600, 1200)))
                .param("cekim", beyan);
    }

    private long yeniIhbar(double lat, double lon) throws Exception {
        String cevap = mockMvc.perform(post("/api/reports")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lat\":%s,\"lon\":%s,\"severity\":\"BUYUK_YANGIN\"}"
                                .formatted(lat, lon)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return json.readTree(cevap).get("id").asLong();
    }
}
