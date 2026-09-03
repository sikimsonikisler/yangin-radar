package com.yanginradar.dto;

import java.time.Instant;

/** SSE ile yayinlanan canli akis olayi. */
public record LiveEvent(String type, Instant at, Object data) {

    public static LiveEvent of(String type, Object data) {
        return new LiveEvent(type, Instant.now(), data);
    }
}
