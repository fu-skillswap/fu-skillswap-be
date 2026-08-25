package com.fptu.exe.skillswap.infrastructure.storage;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

/**
 * Storage bằng file local cho môi trường phát triển và test.
 * Chỉ bật với {@code @Profile("local")}, không cần cloud credential.
 *
 * <p>File được lưu trong thư mục upload local ({@code application.upload.dir}).
 * Presigned URL được giả lập thành đường dẫn do endpoint local phục vụ.</p>
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
    public void uploadFile(String objectKey, java.nio.file.Path file, String contentType, java.util.Map<String, String> metadata) throws IOException {
        Path target = rootDir.resolve(objectKey);
        Files.createDirectories(target.getParent());
        Files.copy(file, target, StandardCopyOption.REPLACE_EXISTING);
        log.info("Local direct path upload success: {}", target);
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
        // Local/test có thể chưa có file vì đang giả lập presigned upload.
        // Trả sizeBytes=0 để báo kích thước chưa xác định.
            return new ObjectMetadata(objectKey, guessContentType(objectKey), 0L, java.util.Collections.emptyMap());
        }
        try {
            return new ObjectMetadata(
                    objectKey,
                    Files.probeContentType(target),
                    Files.size(target),
                    java.util.Collections.emptyMap()
            );
        } catch (IOException ex) {
            throw new com.fptu.exe.skillswap.shared.exception.BaseException(
                    com.fptu.exe.skillswap.shared.exception.ErrorCode.STORAGE_ERROR,
                    "Không thể xác minh file đã upload"
            );
        }
    }

    @Override
    public PrivatePresignedUpload generatePrivateUploadUrl(String objectKey, String contentType, Duration ttl) {
        return new PrivatePresignedUpload(apiBaseUrl + "/api/files/local-upload?objectKey=" + objectKey,
                objectKey, java.time.Instant.now().plus(ttl));
    }

    @Override
    public PrivatePresignedDownload generatePrivateDownloadUrl(String objectKey, Duration ttl, String contentDisposition) {
        // Resource local do controller có kiểm soát quyền trả về, không dùng URL này.
        return new PrivatePresignedDownload(apiBaseUrl + "/api/files/private/" + objectKey,
                java.time.Instant.now().plus(ttl));
    }

    @Override
    public void copyPrivateObject(String sourceObjectKey, String targetObjectKey, String contentType) {
        Path source = resolvePrivatePath(sourceObjectKey);
        Path target = resolvePrivatePath(targetObjectKey);
        try {
            Files.createDirectories(target.getParent());
            Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ex) {
            throw new com.fptu.exe.skillswap.shared.exception.BaseException(
                    com.fptu.exe.skillswap.shared.exception.ErrorCode.STORAGE_ERROR,
                    "Không thể hoàn tất lưu file minh chứng"
            );
        }
    }

    @Override
    public void deletePrivateObject(String objectKey) {
        deleteFile(objectKey);
    }

    @Override
    public InputStream openObject(String objectKey) throws IOException {
        return Files.newInputStream(resolvePrivatePath(objectKey));
    }

    private Path resolvePrivatePath(String objectKey) {
        Path target = rootDir.resolve(objectKey).normalize();
        if (!target.startsWith(rootDir)) {
            throw new com.fptu.exe.skillswap.shared.exception.BaseException(
                    com.fptu.exe.skillswap.shared.exception.ErrorCode.BAD_REQUEST, "objectKey không hợp lệ");
        }
        return target;
    }

    /**
     * Đoán MIME type từ phần mở rộng của object key.
     * Dùng ở local/test khi file chưa có thật trên ổ đĩa.
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
