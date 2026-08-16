package com.fptu.exe.skillswap.infrastructure.storage;

import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;

public interface StorageGateway extends PublicStorageGateway, PrivateStorageGateway, StorageObjectReader {

    record StorageUploadResult(String objectKey, String publicUrl) {}

    record PresignedUpload(String uploadUrl, String publicUrl, String objectKey) {}

    record ObjectMetadata(String objectKey, String contentType, long sizeBytes, java.util.Map<String, String> metadata) {}

    record PrivatePresignedUpload(String uploadUrl, String objectKey, java.time.Instant expiresAt) {}

    record PrivatePresignedDownload(String downloadUrl, java.time.Instant expiresAt) {}
}
