package com.fptu.exe.skillswap.infrastructure.storage;

import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;

public interface PublicStorageGateway {

    StorageGateway.StorageUploadResult uploadFile(MultipartFile file, String subFolder);

    void uploadFile(String objectKey, Path file, String contentType, Map<String, String> metadata) throws IOException;

    void deleteFile(String objectKey);

    StorageGateway.PresignedUpload generatePresignedUploadUrl(String originalFilename, String contentType);

    default StorageGateway.PresignedUpload generatePresignedUploadUrl(String originalFilename, String contentType, String objectPrefix) {
        return generatePresignedUploadUrl(originalFilename, contentType);
    }

    String resolvePublicUrl(String objectKey);

    String storageProviderName();
}
