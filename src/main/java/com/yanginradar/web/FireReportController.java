package com.yanginradar.web;

import java.io.IOException;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yanginradar.dto.CaptureEnvelope;
import com.yanginradar.dto.CreateReportRequest;
import com.yanginradar.dto.FireReportDto;
import com.yanginradar.dto.StatsDto;
import com.yanginradar.service.FireReportService;
import com.yanginradar.service.LiveFeedService;
import com.yanginradar.service.PhotoIntakeService;

import jakarta.validation.Valid;

/** Vatandasa acik uclar: harita verisi, ihbar olusturma, fotograf yukleme, canli akis. */
@RestController
@RequestMapping("/api")
public class FireReportController {

    private final FireReportService service;
    private final LiveFeedService live;
    private final PhotoIntakeService intake;
    private final ObjectMapper json;

    public FireReportController(FireReportService service, LiveFeedService live,
                                PhotoIntakeService intake, ObjectMapper json) {
        this.service = service;
        this.live = live;
        this.intake = intake;
        this.json = json;
    }

    @GetMapping("/reports")
    public List<FireReportDto> list() {
        return service.listAll();
    }

    @GetMapping("/reports/{id}")
    public FireReportDto one(@PathVariable Long id) {
        return service.getOne(id);
    }

    @PostMapping("/reports")
    @ResponseStatus(HttpStatus.CREATED)
    public FireReportDto create(@Valid @RequestBody CreateReportRequest request) {
        return service.create(request);
    }

    /**
     * Fotograf ekler.
     *
     * <p>{@code cekim} alani doldurulursa kanit zinciri devreye girer: tek kullanimlik
     * kod harcanir, varsa App Attest imzasi dogrulanir ve cekim anindaki konum
     * puanlamaya katilir. Bos birakilirsa fotograf yalnizca EXIF ile degerlendirilir.
     *
     * @param cekim     {@link CaptureEnvelope} biciminde JSON metni (istege bagli)
     * @param keyId     App Attest anahtar tanitici (istege bagli)
     * @param assertion App Attest imzasi, base64 (istege bagli)
     */
    @PostMapping(value = "/reports/{id}/photos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public FireReportDto uploadPhoto(@PathVariable Long id,
                                     @RequestPart("file") MultipartFile file,
                                     @RequestParam(required = false) String cekim,
                                     @RequestParam(required = false) String keyId,
                                     @RequestParam(required = false) String assertion)
            throws IOException {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("Boş dosya gönderildi.");
        }

        CaptureEnvelope beyan = null;
        if (cekim != null && !cekim.isBlank()) {
            try {
                beyan = json.readValue(cekim, CaptureEnvelope.class);
            } catch (Exception e) {
                throw new IllegalArgumentException("Çekim bilgisi okunamadı: " + e.getMessage());
            }
        }

        return intake.al(id, file.getBytes(), file.getOriginalFilename(),
                beyan, keyId, assertion);
    }

    /**
     * Ihbari destekler ya da yalanlar.
     *
     * <p>{@code voterId} cihazda uretilen, kisisel bilgi tasimayan bir kimliktir ve
     * zorunludur: onsuz ayni kisi guven puanini sinirsiz sisirebiliyordu.
     */
    @PostMapping("/reports/{id}/vote")
    public FireReportDto vote(@PathVariable Long id,
                              @RequestParam(defaultValue = "true") boolean confirm,
                              @RequestParam(required = false) String voterId) {
        return service.vote(id, confirm, voterId);
    }

    @GetMapping("/stats")
    public StatsDto stats() {
        return service.stats();
    }

    /** Canli veri akisi. Tarayici: {@code new EventSource('/api/stream')} */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream() {
        return live.subscribe();
    }
}
