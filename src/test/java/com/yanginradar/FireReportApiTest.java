package com.yanginradar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.time.ZoneId;

import javax.imageio.ImageIO;

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
import com.yanginradar.repo.FireReportRepository;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:test;DB_CLOSE_DELAY=-1",
        "yanginradar.demo-data=false",
        "yanginradar.firms.enabled=false",
        "yanginradar.storage.upload-dir=${java.io.tmpdir}/yanginradar-test-uploads",
        "yanginradar.moderation.admin-token=test-anahtar"
})
class FireReportApiTest {

    private static final ZoneId ISTANBUL = ZoneId.of("Europe/Istanbul");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper json;

    @Autowired
    private FireReportRepository repo;

    @Test
    void ihbarOlusturulurVeListelenir() throws Exception {
        String govde = """
                {"lat":36.8121,"lon":28.2610,"il":"Muğla","ilce":"Marmaris",
                 "aciklama":"Test ihbari","severity":"KUCUK_YANGIN","reporterAlias":"Test"}
                """;

        mockMvc.perform(post("/api/reports")
                        .contentType(MediaType.APPLICATION_JSON).content(govde))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("BEKLIYOR"))
                .andExpect(jsonPath("$.source").value("VATANDAS"))
                .andExpect(jsonPath("$.photoCount").value(0));

        mockMvc.perform(get("/api/reports"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].il").value("Muğla"));

        assertThat(repo.count()).isPositive();
    }

    @Test
    void turkiyeDisiNoktaReddedilir() throws Exception {
        String govde = """
                {"lat":48.85,"lon":2.35,"severity":"KUCUK_YANGIN"}
                """;

        mockMvc.perform(post("/api/reports")
                        .contentType(MediaType.APPLICATION_JSON).content(govde))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.hata").value(
                        org.hamcrest.Matchers.containsString("Türkiye sınırları dışında")));
    }

    @Test
    void gecersizBuyuklukDegeriReddedilir() throws Exception {
        mockMvc.perform(post("/api/reports")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lat\":36.8,\"lon\":28.2}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void fotografYuklenirVeDogrulamaRaporuUretilir() throws Exception {
        String cevap = mockMvc.perform(post("/api/reports")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lat\":37.041,\"lon\":30.568,\"severity\":\"BUYUK_YANGIN\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        long id = json.readTree(cevap).get("id").asLong();

        MockMultipartFile dosya = new MockMultipartFile(
                "file", "yangin.jpg", "image/jpeg", jpeg());

        String sonuc = mockMvc.perform(multipart("/api/reports/" + id + "/photos").file(dosya))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.photoCount").value(1))
                .andReturn().getResponse().getContentAsString();

        JsonNode foto = json.readTree(sonuc).get("photos").get(0);
        assertThat(foto.get("checks").size()).isGreaterThan(3);
        assertThat(foto.get("score").asInt()).isBetween(0, 100);
        assertThat(foto.get("hasGps").asBoolean()).isFalse();
    }

    @Test
    void yetkiliUclariAnahtarsizCalismaz() throws Exception {
        mockMvc.perform(post("/api/admin/reports/1/status?status=DOGRULANDI"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/admin/dispatches").header("X-Admin-Token", "yanlis"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/admin/dispatches").header("X-Admin-Token", "test-anahtar"))
                .andExpect(status().isOk());
    }

    @Test
    void ayniCihazSayaciSisiremez() throws Exception {
        long id = yeniIhbar(36.5000, 29.1000, "BUYUK_YANGIN");

        // Ayni cihaz on kez destekliyor
        for (int i = 0; i < 10; i++) {
            mockMvc.perform(post("/api/reports/" + id + "/vote")
                            .param("confirm", "true")
                            .param("voterId", "ayni-cihaz"))
                    .andExpect(status().isOk());
        }

        String cevap = mockMvc.perform(get("/api/reports/" + id))
                .andReturn().getResponse().getContentAsString();
        assertThat(json.readTree(cevap).get("confirmCount").asInt())
                .as("ayni cihazin tekrar oylari sayilmamali")
                .isEqualTo(1);
    }

    @Test
    void farkliCihazlarAyriAyriSayilir() throws Exception {
        long id = yeniIhbar(36.5100, 29.1100, "BUYUK_YANGIN");

        for (String cihaz : new String[] {"cihaz-a", "cihaz-b", "cihaz-c"}) {
            mockMvc.perform(post("/api/reports/" + id + "/vote")
                            .param("confirm", "true")
                            .param("voterId", cihaz))
                    .andExpect(status().isOk());
        }

        String cevap = mockMvc.perform(get("/api/reports/" + id))
                .andReturn().getResponse().getContentAsString();
        assertThat(json.readTree(cevap).get("confirmCount").asInt()).isEqualTo(3);
    }

    @Test
    void fikirDegistirinceOySayaclariKayar() throws Exception {
        long id = yeniIhbar(36.5200, 29.1200, "BUYUK_YANGIN");

        mockMvc.perform(post("/api/reports/" + id + "/vote")
                        .param("confirm", "true").param("voterId", "kararsiz"))
                .andExpect(status().isOk());
        String cevap = mockMvc.perform(post("/api/reports/" + id + "/vote")
                        .param("confirm", "false").param("voterId", "kararsiz"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode kayit = json.readTree(cevap);
        assertThat(kayit.get("confirmCount").asInt()).as("eski oy geri alinmali").isZero();
        assertThat(kayit.get("denyCount").asInt()).isEqualTo(1);
    }

    @Test
    void kimliksizOyReddedilir() throws Exception {
        long id = yeniIhbar(36.5300, 29.1300, "BUYUK_YANGIN");

        mockMvc.perform(post("/api/reports/" + id + "/vote").param("confirm", "true"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void oyVermeGuvenPuaniniEtkiler() throws Exception {
        String cevap = mockMvc.perform(post("/api/reports")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lat\":38.419,\"lon\":27.128,\"severity\":\"DUMAN_GORULDU\"}"))
                .andReturn().getResponse().getContentAsString();

        long id = json.readTree(cevap).get("id").asLong();
        int once = json.readTree(cevap).get("confidence").asInt();

        String sonra = mockMvc.perform(post("/api/reports/" + id + "/vote")
                        .param("confirm", "true")
                        .param("voterId", "puan-sinamasi"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(json.readTree(sonra).get("confidence").asInt()).isGreaterThan(once);
    }

    @Test
    void konumuTutmayanFotografOtomatikDogrulamayiEngeller() throws Exception {
        // Uydu kaydi + iki bagimsiz ihbar: normalde otomatik dogrulanacak bir kume
        yeniIhbar(36.8121, 28.2610, "BUYUK_YANGIN");
        long id = yeniIhbar(36.8121, 28.2610, "BUYUK_YANGIN");

        // ...ama fotograf 470 km oteden geliyor
        byte[] uzak = ExifTestJpeg.ile(41.0082, 28.9784, LocalDateTime.now(ISTANBUL).minusMinutes(15), null);
        String sonuc = mockMvc.perform(multipart("/api/reports/" + id + "/photos")
                        .file(new MockMultipartFile("file", "uzak.jpg", "image/jpeg", uzak)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode kayit = json.readTree(sonuc);
        assertThat(kayit.get("status").asText())
                .as("celiskili kanit otomatik dogrulamayi engellemeli")
                .isNotEqualTo("DOGRULANDI");
        assertThat(kayit.get("photos").get(0).get("distanceMeters").asDouble())
                .isGreaterThan(400_000);
    }

    @Test
    void yerindeCekilmisFotografGuveniYukseltir() throws Exception {
        long id = yeniIhbar(37.9500, 32.4900, "KUCUK_YANGIN");
        int once = json.readTree(mockMvc.perform(get("/api/reports/" + id))
                .andReturn().getResponse().getContentAsString()).get("confidence").asInt();

        byte[] yakin = ExifTestJpeg.ile(37.9500, 32.4900, LocalDateTime.now(ISTANBUL).minusMinutes(10), null);
        String sonuc = mockMvc.perform(multipart("/api/reports/" + id + "/photos")
                        .file(new MockMultipartFile("file", "yakin.jpg", "image/jpeg", yakin)))
                .andReturn().getResponse().getContentAsString();

        JsonNode kayit = json.readTree(sonuc);
        assertThat(kayit.get("confidence").asInt()).isGreaterThan(once);
        assertThat(kayit.get("photos").get(0).get("verdict").asText()).isEqualTo("GECERLI");
    }

    @Test
    void olmayanStatikDosya404DonerSunucuHatasiDegil() throws Exception {
        // Tarayici /favicon.ico gibi dosyalari kendiliginden ister; bu 404'tur, 500 degil
        mockMvc.perform(get("/boyle-bir-dosya-yok.png"))
                .andExpect(status().isNotFound());
    }

    @Test
    void faviconServisEdilir() throws Exception {
        mockMvc.perform(get("/favicon.ico"))
                .andExpect(status().isOk());
    }

    @Test
    void dayanaksizIhbarYetkiliyeIletilmez() throws Exception {
        // Tek basina duran, fotografsiz bir vatandas ihbari
        long id = yeniIhbar(39.9208, 32.8541, "BUYUK_YANGIN");

        mockMvc.perform(post("/api/admin/reports/" + id + "/dispatch")
                        .header("X-Admin-Token", "test-anahtar"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.hata").value(org.hamcrest.Matchers.containsString("dayanağı yok")));

        // Karar yine insanda: bilerek zorlanabilmeli
        mockMvc.perform(post("/api/admin/reports/" + id + "/dispatch")
                        .header("X-Admin-Token", "test-anahtar")
                        .param("zorla", "true"))
                .andExpect(status().isOk());
    }

    @Test
    void yerindeCekilmisFotografYetkiliyeIletmeyiAcar() throws Exception {
        long id = yeniIhbar(38.4237, 27.1428, "BUYUK_YANGIN");

        byte[] yakin = ExifTestJpeg.ile(38.4237, 27.1428,
                LocalDateTime.now(ISTANBUL).minusMinutes(5), null);
        mockMvc.perform(multipart("/api/reports/" + id + "/photos")
                        .file(new MockMultipartFile("file", "yakin.jpg", "image/jpeg", yakin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.photos[0].verdict").value("GECERLI"));

        mockMvc.perform(post("/api/admin/reports/" + id + "/dispatch")
                        .header("X-Admin-Token", "test-anahtar"))
                .andExpect(status().isOk());
    }

    @Test
    void yakindakiBaskaIhbarDaDayanakSayilir() throws Exception {
        // Ayni noktada iki bagimsiz ihbar: fotograf yok ama birbirlerini destekliyorlar
        yeniIhbar(37.0410, 30.5680, "KUCUK_YANGIN");
        long ikinci = yeniIhbar(37.0410, 30.5680, "KUCUK_YANGIN");

        mockMvc.perform(post("/api/admin/reports/" + ikinci + "/dispatch")
                        .header("X-Admin-Token", "test-anahtar"))
                .andExpect(status().isOk());
    }

    private long yeniIhbar(double lat, double lon, String severity) throws Exception {
        String cevap = mockMvc.perform(post("/api/reports")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lat\":%s,\"lon\":%s,\"severity\":\"%s\"}"
                                .formatted(lat, lon, severity)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(cevap).get("id").asLong();
    }

    private static byte[] jpeg() throws Exception {
        BufferedImage img = new BufferedImage(1024, 768, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(new Color(180, 60, 15));
        g.fillRect(0, 0, 1024, 768);
        g.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "jpg", out);
        return out.toByteArray();
    }
}
