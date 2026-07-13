package com.fptu.exe.skillswap.infrastructure.storage;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

/**
 * Local file-system storage gateway for development and testing.
 * Activated via {@code @Profile("local")} — no cloud credentials required.
 *
 * <p>Files are stored under the local upload directory (configurable via
 * {@code application.upload.dir}). Presigned URLs are simulated as direct
 * file paths served by a local endpoint.</p>
 */
@Slf4j
@Service
@Profile({"local", "test"})
public class LocalFileStorageGatewayImpl implements StorageGateway {

    private final Path rootDir;
    private final String apiBaseUrl;
    private final String baseUrl;

    public LocalFileStorageGatewayImpl(
            @Value("${application.upload.dir:${java.io.tmpdir}/skillswap-storage}") String uploadDir,
            @Value("${server.port:8080}") int serverPort
    ) {
        this.rootDir = Paths.get(uploadDir).toAbsolutePath().normalize();
        this.apiBaseUrl = "http://localhost:" + serverPort;
        this.baseUrl = "http://localhost:" + serverPort + "/uploads/storage";
        try {
            Files.createDirectories(rootDir);
        } catch (IOException ex) {
            log.warn("Không thể tạo thư mục local storage: {}. File upload sẽ thất bại.", rootDir, ex);
        }
        log.info("LocalFileStorageGatewayImpl initialized. rootDir={}, baseUrl={}", rootDir, baseUrl);
    }

    @Override
    public StorageUploadResult uploadFile(MultipartFile file, String subFolder) {
        String objectKey = buildObjectKey(file.getOriginalFilename(), subFolder);
        Path target = rootDir.resolve(objectKey);
        try {
            Files.createDirectories(target.getParent());
            try (InputStream is = file.getInputStream()) {
                Files.copy(is, target, StandardCopyOption.REPLACE_EXISTING);
            }
            String publicUrl = baseUrl + "/" + objectKey.replace("\\", "/");
            log.info("Local upload success: {}", target);
            return new StorageUploadResult(objectKey, publicUrl);
        } catch (IOException ex) {
            log.error("Local upload failed: {}", target, ex);
            throw new com.fptu.exe.skillswap.shared.exception.BaseException(
                    com.fptu.exe.skillswap.shared.exception.ErrorCode.STORAGE_ERROR,
                    "Không thể lưu file cục bộ: " + ex.getMessage()
            );
        }
    }

    @Override
    public void deleteFile(String objectKey) {
        if (objectKey == null || objectKey.isBlank()) {
            return;
        }
        Path target = rootDir.resolve(objectKey);
        try {
            Files.deleteIfExists(target);
            log.info("Local delete success: {}", target);
        } catch (IOException ex) {
            log.warn("Local delete failed: {}", target, ex);
        }
    }

    @Override
    public PresignedUpload generatePresignedUploadUrl(String originalFilename, String contentType) {
        return generatePresignedUploadUrl(originalFilename, contentType, null);
    }

    @Override
    public PresignedUpload generatePresignedUploadUrl(String originalFilename, String contentType, String objectPrefix) {
        String objectKey = buildObjectKey(originalFilename, objectPrefix);
        String publicUrl = resolvePublicUrl(objectKey);
        log.info("Local presigned URL simulated for key={}", objectKey);
        return new PresignedUpload(
                apiBaseUrl + "/api/files/local-upload?objectKey=" + objectKey,
                publicUrl,
                objectKey
        );
    }

    @Override
    public String resolvePublicUrl(String objectKey) {
        return baseUrl + "/" + objectKey.replace("\\", "/");
    }

    @Override
    public String storageProviderName() {
        return "LOCAL";
    }

    @Override
    public ObjectMetadata headObject(String objectKey) {
        Path target = rootDir.resolve(objectKey).normalize();
        if (!target.startsWith(rootDir)) {
            throw new com.fptu.exe.skillswap.shared.exception.BaseException(
                    com.fptu.exe.skillswap.shared.exception.ErrorCode.BAD_REQUEST,
                    "objectKey không hợp lệ"
            );
        }
        if (!Files.exists(target)) {
            // Local/test profile: file may not exist because we are simulating the
            // presigned-upload flow (client uploads to CDN; local storage cannot verify).
            // Return sentinel metadata — sizeBytes=0 signals "unknown size" to callers.
            return new ObjectMetadata(objectKey, guessContentType(objectKey), 0L);
        }
        try {
            return new ObjectMetadata(
                    objectKey,
                    Files.probeContentType(target),
                    Files.size(target)
            );
        } catch (IOException ex) {
            throw new com.fptu.exe.skillswap.shared.exception.BaseException(
                    com.fptu.exe.skillswap.shared.exception.ErrorCode.STORAGE_ERROR,
                    "Không thể xác minh file đã upload"
            );
        }
    }

    /**
     * Guesses the MIME content-type from the file extension of an object key.
     * Used in local/test mode when the file does not physically exist on disk.
     */
    private String guessContentType(String objectKey) {
        if (objectKey == null) {
            return "application/octet-stream";
        }
        String lower = objectKey.toLowerCase();
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".pdf")) return "application/pdf";
        return "application/octet-stream";
    }

    private String buildObjectKey(String originalFilename, String subFolder) {
        String extension = "";
        if (originalFilename != null) {
            int extensionIndex = originalFilename.lastIndexOf('.');
            if (extensionIndex >= 0) {
                extension = originalFilename.substring(extensionIndex);
            }
        }
        StringBuilder keyBuilder = new StringBuilder();
        if (subFolder != null && !subFolder.isBlank()) {
            keyBuilder.append(subFolder.trim().replaceAll("^/+|/+$", ""));
        } else {
            keyBuilder.append("local");
        }
        keyBuilder.append('/').append(UUID.randomUUID()).append(extension);
        return keyBuilder.toString();
    }
}
