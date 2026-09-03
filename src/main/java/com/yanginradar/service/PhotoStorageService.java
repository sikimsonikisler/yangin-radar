package com.yanginradar.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.yanginradar.config.AppProperties;

import jakarta.annotation.PostConstruct;

/** Fotograflari diske yazar ve geri okur. */
@Service
public class PhotoStorageService {

    private static final Logger log = LoggerFactory.getLogger(PhotoStorageService.class);

    private final AppProperties props;
    private Path root;

    public PhotoStorageService(AppProperties props) {
        this.props = props;
    }

    @PostConstruct
    void init() throws IOException {
        root = Paths.get(props.getStorage().getUploadDir()).toAbsolutePath().normalize();
        Files.createDirectories(root);
        log.info("Fotograf dizini: {}", root);
    }

    /** Dosyayi kaydeder ve diskteki adini dondurur. */
    public String store(byte[] bytes, String detectedType) throws IOException {
        String ext = switch (detectedType == null ? "" : detectedType.toUpperCase(Locale.ROOT)) {
            case "PNG" -> ".png";
            case "WEBP" -> ".webp";
            case "HEIC" -> ".heic";
            default -> ".jpg";
        };
        String name = UUID.randomUUID() + ext;
        Path target = root.resolve(name);
        Files.write(target, bytes);
        return name;
    }

    public Path resolve(String storedFilename) {
        Path p = root.resolve(storedFilename).normalize();
        if (!p.startsWith(root)) {
            throw new IllegalArgumentException("Gecersiz dosya adi: " + storedFilename);
        }
        return p;
    }

    public byte[] read(String storedFilename) throws IOException {
        return Files.readAllBytes(resolve(storedFilename));
    }

    public void copyInto(Path source, String storedFilename) throws IOException {
        Files.copy(source, resolve(storedFilename), StandardCopyOption.REPLACE_EXISTING);
    }
}
