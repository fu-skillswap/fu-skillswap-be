package com.fptu.exe.skillswap.infrastructure.storage;

import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;

public interface StorageGateway {

    StorageUploadResult uploadFile(MultipartFile file, String subFolder);

    void uploadFile(String objectKey, java.nio.file.Path file, String contentType, java.util.Map<String, String> metadata) throws IOException;

    void deleteFile(String objectKey);

    PresignedUpload generatePresignedUploadUrl(String originalFilename, String contentType);

    default PresignedUpload generatePresignedUploadUrl(String originalFilename, String contentType, String objectPrefix) {
        return generatePresignedUploadUrl(originalFilename, contentType);
    }

    String resolvePublicUrl(String objectKey);

    String storageProviderName();

    ObjectMetadata headObject(String objectKey);

    PrivatePresignedUpload generatePrivateUploadUrl(String objectKey, String contentType, Duration ttl);

    PrivatePresignedDownload generatePrivateDownloadUrl(String objectKey, Duration ttl, String contentDisposition);

    InputStream openObject(String objectKey) throws IOException;

    record StorageUploadResult(String objectKey, String publicUrl) {}

    record PresignedUpload(String uploadUrl, String publicUrl, String objectKey) {}

    record ObjectMetadata(String objectKey, String contentType, long sizeBytes, java.util.Map<String, String> metadata) {}

    record PrivatePresignedUpload(String uploadUrl, String objectKey, java.time.Instant expiresAt) {}

    record PrivatePresignedDownload(String downloadUrl, java.time.Instant expiresAt) {}
}
