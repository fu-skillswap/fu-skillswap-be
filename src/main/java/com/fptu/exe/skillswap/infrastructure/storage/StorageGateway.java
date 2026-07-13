package com.fptu.exe.skillswap.infrastructure.storage;

import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

public interface StorageGateway {

    StorageUploadResult uploadFile(MultipartFile file, String subFolder);

    void deleteFile(String objectKey);

    PresignedUpload generatePresignedUploadUrl(String originalFilename, String contentType);

    default PresignedUpload generatePresignedUploadUrl(String originalFilename, String contentType, String objectPrefix) {
        return generatePresignedUploadUrl(originalFilename, contentType);
    }

    String resolvePublicUrl(String objectKey);

    String storageProviderName();

    ObjectMetadata headObject(String objectKey);

    record StorageUploadResult(String objectKey, String publicUrl) {}

    record PresignedUpload(String uploadUrl, String publicUrl, String objectKey) {}

    record ObjectMetadata(String objectKey, String contentType, long sizeBytes) {}
}
