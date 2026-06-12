package com.fptu.exe.skillswap.infrastructure.storage;

import com.fptu.exe.skillswap.infrastructure.config.R2Properties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnBean(S3Client.class)
public class R2DocumentStorageService {

    private final S3Client s3Client;
    private final R2Properties r2Properties;

    public R2UploadResult upload(MultipartFile file, String subFolder) throws IOException {
        String objectKey = buildObjectKey(file.getOriginalFilename(), subFolder);
        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(r2Properties.getBucket())
                .key(objectKey)
                .contentType(file.getContentType())
                .build();

        s3Client.putObject(request, RequestBody.fromInputStream(file.getInputStream(), file.getSize()));
        String fileUrl = buildPublicUrl(objectKey);
        log.info("R2 upload success: bucket={}, key={}", r2Properties.getBucket(), objectKey);
        return new R2UploadResult(objectKey, fileUrl);
    }

    public void delete(String objectKey) {
        if (objectKey == null || objectKey.isBlank()) {
            return;
        }
        s3Client.deleteObject(DeleteObjectRequest.builder()
                .bucket(r2Properties.getBucket())
                .key(objectKey)
                .build());
        log.info("R2 delete success: bucket={}, key={}", r2Properties.getBucket(), objectKey);
    }

    private String buildObjectKey(String originalFilename, String subFolder) {
        String extension = "";
        if (originalFilename != null) {
            int extensionIndex = originalFilename.lastIndexOf('.');
            if (extensionIndex >= 0) {
                extension = originalFilename.substring(extensionIndex);
            }
        }
        StringBuilder keyBuilder = new StringBuilder(r2Properties.getDocumentsPrefix());
        if (subFolder != null && !subFolder.isBlank()) {
            keyBuilder.append('/').append(subFolder.trim());
        }
        keyBuilder.append('/').append(UUID.randomUUID()).append(extension);
        return keyBuilder.toString();
    }

    private String buildPublicUrl(String objectKey) {
        String endpoint = r2Properties.getEndpoint();
        if (endpoint.endsWith("/")) {
            endpoint = endpoint.substring(0, endpoint.length() - 1);
        }
        return endpoint + "/" + r2Properties.getBucket() + "/" + objectKey;
    }

    public record R2UploadResult(String objectKey, String fileUrl) {
    }
}
