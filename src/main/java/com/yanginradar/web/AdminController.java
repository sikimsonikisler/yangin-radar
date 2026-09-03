package com.yanginradar.web;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.yanginradar.config.AppProperties;
import com.yanginradar.domain.DispatchRecord;
import com.yanginradar.domain.ReportStatus;
import com.yanginradar.dto.FireReportDto;
import com.yanginradar.service.AuthorityNotificationService;
import com.yanginradar.service.FireReportService;
import com.yanginradar.service.FirmsIngestService;

/**
 * Yetkili paneli uclari. Basit bir paylasilan anahtarla korunur
 * ({@code X-Admin-Token} basligi). Gercek dagitimda bunun yerine kurumsal
 * kimlik dogrulama (OAuth2 / SSO) kullanilmalidir.
 */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final FireReportService reportService;
    private final AuthorityNotificationService authority;
    private final FirmsIngestService firms;
    private final AppProperties props;

    public AdminController(FireReportService reportService,
                           AuthorityNotificationService authority,
                           FirmsIngestService firms,
                           AppProperties props) {
        this.reportService = reportService;
        this.authority = authority;
        this.firms = firms;
        this.props = props;
    }

    private void requireToken(String token) {
        if (token == null || !token.equals(props.getModeration().getAdminToken())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Yetkili anahtarı geçersiz.");
        }
    }

    @PostMapping("/reports/{id}/status")
    public FireReportDto changeStatus(@RequestHeader(value = "X-Admin-Token", required = false) String token,
                                      @PathVariable Long id,
                                      @RequestParam ReportStatus status) {
        requireToken(token);
        return reportService.changeStatus(id, status);
    }

    @PostMapping("/reports/{id}/dispatch")
    public Map<String, Object> dispatch(@RequestHeader(value = "X-Admin-Token", required = false) String token,
                                        @PathVariable Long id,
                                        @RequestParam(required = false) String channel,
                                        @RequestParam(defaultValue = "false") boolean zorla) {
        requireToken(token);
        DispatchRecord record = authority.dispatch(id, channel, zorla);
        return Map.of(
                "id", record.getId(),
                "channel", record.getChannel(),
                "state", record.getState(),
                "payload", record.getPayload(),
                "not", "Bu kayit uygulama icinde tutulur; hicbir acil cagri hattina "
                        + "otomatik bildirim gonderilmemistir.");
    }

    @GetMapping("/dispatches")
    public List<Map<String, Object>> dispatches(
            @RequestHeader(value = "X-Admin-Token", required = false) String token) {
        requireToken(token);
        return authority.recent().stream()
                .map(d -> Map.<String, Object>of(
                        "id", d.getId(),
                        "fireReportId", d.getFireReportId(),
                        "channel", d.getChannel(),
                        "state", d.getState(),
                        "createdAt", d.getCreatedAt(),
                        "payload", d.getPayload()))
                .toList();
    }

    @PostMapping("/firms/refresh")
    public Map<String, Object> refreshFirms(
            @RequestHeader(value = "X-Admin-Token", required = false) String token) throws Exception {
        requireToken(token);
        if (!firms.isActive()) {
            return Map.of("aktif", false,
                    "mesaj", "FIRMS anahtari tanimli degil. yanginradar.firms.map-key "
                            + "ayarina veya FIRMS_MAP_KEY ortam degiskenine ucretsiz anahtari girin.");
        }
        int added = firms.ingestOnce();
        return Map.of("aktif", true, "eklenen", added, "zaman", Instant.now());
    }

    @GetMapping("/firms/status")
    public Map<String, Object> firmsStatus(
            @RequestHeader(value = "X-Admin-Token", required = false) String token) {
        requireToken(token);
        return Map.of(
                "aktif", firms.isActive(),
                "sonCalisma", String.valueOf(firms.getLastRun()),
                "sonSonuc", firms.getLastResult(),
                "dataset", props.getFirms().getDataset());
    }
}
