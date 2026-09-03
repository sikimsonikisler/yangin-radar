package com.yanginradar.web;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/** Hatalari istemcinin gosterebilecegi tek bicimli bir govdeye cevirir. */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> badRequest(IllegalArgumentException e) {
        return body(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> validation(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .collect(Collectors.joining(", "));
        return body(HttpStatus.BAD_REQUEST, "Geçersiz istek — " + msg);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, Object>> tooLarge(MaxUploadSizeExceededException e) {
        return body(HttpStatus.PAYLOAD_TOO_LARGE, "Fotoğraf çok büyük.");
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> statusException(ResponseStatusException e) {
        return body(HttpStatus.valueOf(e.getStatusCode().value()), e.getReason());
    }

    /**
     * Olmayan bir statik dosya istendiginde (tarayicinin kendiliginden aradigi
     * {@code /favicon.ico} gibi) bu 404'tur, sunucu hatasi degil. Asagidaki genel
     * yakalayiciya dusmesine izin verilirse 500 donup log'a yigin izi basar.
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Map<String, Object>> notFound(NoResourceFoundException e) {
        log.debug("Bulunamayan kaynak: {}", e.getResourcePath());
        return body(HttpStatus.NOT_FOUND, "Kaynak bulunamadı: " + e.getResourcePath());
    }

    /** Spring'in kendi durum kodu tasiyan istisnalari kendi kodlariyla donsun. */
    @ExceptionHandler(ErrorResponseException.class)
    public ResponseEntity<Map<String, Object>> errorResponse(ErrorResponseException e) {
        return body(HttpStatus.valueOf(e.getStatusCode().value()),
                e.getBody().getDetail());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> other(Exception e) {
        log.error("Beklenmeyen hata", e);
        return body(HttpStatus.INTERNAL_SERVER_ERROR, "Sunucu hatası: " + e.getClass().getSimpleName());
    }

    private ResponseEntity<Map<String, Object>> body(HttpStatus status, String message) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("zaman", Instant.now());
        map.put("kod", status.value());
        map.put("hata", message == null ? status.getReasonPhrase() : message);
        return ResponseEntity.status(status).body(map);
    }
}
