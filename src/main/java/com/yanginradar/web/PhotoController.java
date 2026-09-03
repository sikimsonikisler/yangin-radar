package com.yanginradar.web;

import java.io.IOException;

import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.yanginradar.domain.PhotoEvidence;
import com.yanginradar.repo.PhotoEvidenceRepository;
import com.yanginradar.service.PhotoStorageService;

import java.time.Duration;

/** Yuklenen fotograflarin icerigini servis eder. */
@RestController
@RequestMapping("/api/photos")
public class PhotoController {

    private final PhotoEvidenceRepository photoRepo;
    private final PhotoStorageService storage;

    public PhotoController(PhotoEvidenceRepository photoRepo, PhotoStorageService storage) {
        this.photoRepo = photoRepo;
        this.storage = storage;
    }

    @GetMapping("/{id}/content")
    public ResponseEntity<byte[]> content(@PathVariable Long id) throws IOException {
        PhotoEvidence p = photoRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Fotoğraf bulunamadı: " + id));

        byte[] bytes = storage.read(p.getStoredFilename());
        MediaType type;
        try {
            type = MediaType.parseMediaType(p.getContentType());
        } catch (Exception e) {
            type = MediaType.IMAGE_JPEG;
        }

        return ResponseEntity.ok()
                .contentType(type)
                .cacheControl(CacheControl.maxAge(Duration.ofHours(1)).cachePublic())
                .body(bytes);
    }
}
