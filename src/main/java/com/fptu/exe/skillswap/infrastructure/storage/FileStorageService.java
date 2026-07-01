package com.fptu.exe.skillswap.infrastructure.storage;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

@Slf4j
@Service
public class FileStorageService {

    private static final String STORAGE_NOT_READY_MESSAGE = "Dịch vụ lưu trữ tệp hiện chưa sẵn sàng. Vui lòng kiểm tra cấu hình application.upload.dir và quyền ghi thư mục.";

    @Value("${application.upload.dir:./uploads/files}")
    private String uploadDir;

    private Path uploadPath;
    private volatile boolean storageReady;

    @PostConstruct
    public void init() {
        uploadPath = Paths.get(uploadDir).toAbsolutePath().normalize();
        try {
            Files.createDirectories(uploadPath);
            storageReady = true;
            log.info("File storage initialized at {}", uploadPath);
        } catch (IOException e) {
            storageReady = false;
            log.error("File storage initialization failed at {}", uploadPath, e);
        }
    }

    public String store(MultipartFile file) throws IOException {
        ensureStorageReady();

        String originalFilename = file.getOriginalFilename();
        String extension = (originalFilename != null && originalFilename.contains("."))
                ? originalFilename.substring(originalFilename.lastIndexOf("."))
                : "";

        String storedFilename = UUID.randomUUID() + extension;
        Path targetLocation = uploadPath.resolve(storedFilename).normalize();

        if (!targetLocation.startsWith(uploadPath)) {
            throw new IOException("Đường dẫn tệp tải lên không hợp lệ");
        }

        try (InputStream inputStream = file.getInputStream()) {
            Files.copy(inputStream, targetLocation, StandardCopyOption.REPLACE_EXISTING);
        }

        String relativePath = "uploads/files/" + storedFilename;
        log.info("File saved successfully: {} -> {}", originalFilename, targetLocation);
        return relativePath;
    }

    public InputStream loadAsStream(String relativePath) throws IOException {
        ensureStorageReady();

        String filename = Paths.get(relativePath).getFileName().toString();
        Path filePath = uploadPath.resolve(filename).normalize();

        if (!filePath.startsWith(uploadPath)) {
            throw new IOException("Đường dẫn tệp yêu cầu không hợp lệ");
        }

        if (!Files.exists(filePath)) {
            throw new IOException("Tệp không tồn tại trên máy chủ: " + filename);
        }

        return Files.newInputStream(filePath);
    }

    public void delete(String relativePath) {
        if (!storageReady || uploadPath == null) {
            log.warn("Skipped deleting file because storage is not ready: {}", relativePath);
            return;
        }
        try {
            String filename = Paths.get(relativePath).getFileName().toString();
            Path filePath = uploadPath.resolve(filename).normalize();
            if (!filePath.startsWith(uploadPath)) {
                log.warn("Skipped deleting invalid file path: {}", relativePath);
                return;
            }
            Files.deleteIfExists(filePath);
            log.info("File deleted: {}", filePath);
        } catch (IOException e) {
            log.warn("Could not delete file: {}, reason={}", relativePath, e.getMessage());
        }
    }

    private void ensureStorageReady() {
        if (!storageReady || uploadPath == null) {
            throw new IllegalStateException(STORAGE_NOT_READY_MESSAGE);
        }
    }
}
