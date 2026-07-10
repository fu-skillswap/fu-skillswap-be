package com.fptu.exe.skillswap.infrastructure.storage;

import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

public interface StorageGateway {

    StorageUploadResult uploadFile(MultipartFile file, String subFolder);

    void deleteFile(String objectKey);

    PresignedUpload generatePresignedUploadUrl(String originalFilename, String contentType);

    record StorageUploadResult(String objectKey, String publicUrl) {}

    record PresignedUpload(String uploadUrl, String publicUrl, String objectKey) {}
}
