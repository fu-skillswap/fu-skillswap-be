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

    @Value("${application.upload.dir:./uploads/files}")
    private String uploadDir;

    private Path uploadPath;

    @PostConstruct
    public void init() {
        uploadPath = Paths.get(uploadDir).toAbsolutePath().normalize();
        try {
            Files.createDirectories(uploadPath);
            log.info("File storage initialized at {}", uploadPath);
        } catch (IOException e) {
            throw new IllegalStateException("Không thể khởi tạo thư mục lưu tệp tại: " + uploadPath, e);
        }
    }

    public String store(MultipartFile file) throws IOException {
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
}
