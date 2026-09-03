package com.yanginradar.service;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.yanginradar.dto.LiveEvent;

/**
 * Canli veri akisi. Tarayicilar {@code /api/stream} adresine EventSource ile baglanir,
 * yeni ihbar / dogrulama / durum degisikligi oldugunda olay yayinlanir.
 */
@Service
public class LiveFeedService {

    private static final Logger log = LoggerFactory.getLogger(LiveFeedService.class);
    private static final long TIMEOUT_MS = 30 * 60 * 1000L;

    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();

    public SseEmitter subscribe() {
        SseEmitter emitter = new SseEmitter(TIMEOUT_MS);
        emitters.add(emitter);
        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> {
            emitters.remove(emitter);
            emitter.complete();
        });
        emitter.onError(e -> emitters.remove(emitter));

        try {
            emitter.send(SseEmitter.event()
                    .name("hosgeldin")
                    .data(LiveEvent.of("hosgeldin", "Canli akisa baglanildi")));
        } catch (IOException e) {
            emitters.remove(emitter);
        }
        return emitter;
    }

    public void publish(String type, Object payload) {
        LiveEvent event = LiveEvent.of(type, payload);
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().name(type).data(event));
            } catch (Exception e) {
                emitters.remove(emitter);
                try {
                    emitter.complete();
                } catch (Exception ignored) {
                    // baglanti zaten kopmus
                }
            }
        }
        log.debug("Canli akis olayi yayinlandi: {} ({} dinleyici)", type, emitters.size());
    }

    /** Proxy'lerin bagalantiyi dusurmemesi icin periyodik yorum satiri. */
    @Scheduled(fixedRate = 25_000)
    public void heartbeat() {
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().comment("ping"));
            } catch (Exception e) {
                emitters.remove(emitter);
            }
        }
    }

    public int listenerCount() {
        return emitters.size();
    }
}
