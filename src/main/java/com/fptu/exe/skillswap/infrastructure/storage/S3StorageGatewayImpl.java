package com.fptu.exe.skillswap.infrastructure.storage;

import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.UUID;

@Slf4j
@RequiredArgsConstructor
public class S3StorageGatewayImpl implements StorageGateway {

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final StorageProperties properties;

    @Override
    public StorageUploadResult uploadFile(MultipartFile file, String subFolder) {
        String objectKey = buildObjectKey(file.getOriginalFilename(), subFolder);
        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(properties.getBucket())
                .key(objectKey)
                .contentType(file.getContentType())
                .build();

        try {
            s3Client.putObject(request, RequestBody.fromInputStream(file.getInputStream(), file.getSize()));
            String fileUrl = buildPublicUrl(objectKey);
            log.info("S3/R2 upload success: bucket={}, key={}", properties.getBucket(), objectKey);
            return new StorageUploadResult(objectKey, fileUrl);
        } catch (IOException | S3Exception ex) {
            log.error("Lỗi khi upload file lên S3/R2. bucket={}, key={}", properties.getBucket(), objectKey, ex);
            throw new BaseException(ErrorCode.STORAGE_ERROR, "Không thể tải lên tệp tin");
        }
    }

    @Override
    public void uploadFile(String objectKey, java.nio.file.Path file, String contentType, java.util.Map<String, String> metadata) {
        PutObjectRequest.Builder builder = PutObjectRequest.builder()
                .bucket(properties.getBucket())
                .key(objectKey)
                .contentType(contentType);
        
        if (metadata != null && !metadata.isEmpty()) {
            builder.metadata(metadata);
        }

        PutObjectRequest request = builder.build();

        try {
            s3Client.putObject(request, RequestBody.fromFile(file));
            log.info("S3/R2 upload success: bucket={}, key={}", properties.getBucket(), objectKey);
        } catch (S3Exception ex) {
            log.error("Lỗi khi upload file lên S3/R2. bucket={}, key={}", properties.getBucket(), objectKey, ex);
            throw new BaseException(ErrorCode.STORAGE_ERROR, "Không thể tải lên tệp tin");
        }
    }

    @Override
    public void deleteFile(String objectKey) {
        if (objectKey == null || objectKey.isBlank()) {
            return;
        }
        try {
            s3Client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(properties.getBucket())
                    .key(objectKey)
                    .build());
            log.info("S3/R2 delete success: bucket={}, key={}", properties.getBucket(), objectKey);
        } catch (S3Exception ex) {
            log.error("Lỗi khi xoá file trên S3/R2. bucket={}, key={}", properties.getBucket(), objectKey, ex);
            throw new BaseException(ErrorCode.STORAGE_ERROR, "Không thể xoá tệp tin");
        }
    }

    @Override
    public PresignedUpload generatePresignedUploadUrl(String originalFilename, String contentType) {
        return generatePresignedUploadUrl(originalFilename, contentType, null);
    }

    @Override
    public PresignedUpload generatePresignedUploadUrl(String originalFilename, String contentType, String objectPrefix) {
        String objectKey = buildObjectKey(originalFilename, objectPrefix);

        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(properties.getBucket())
                .key(objectKey)
                .contentType(contentType)
                .build();

        PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                .signatureDuration(Duration.ofMinutes(Math.min(Math.max(properties.getPresignedTtlMinutes(), 1), 15)))
                .putObjectRequest(putObjectRequest)
                .build();

        try {
            PresignedPutObjectRequest presignedRequest = s3Presigner.presignPutObject(presignRequest);
            String publicUrl = buildPublicUrl(objectKey);
            return new PresignedUpload(presignedRequest.url().toString(), publicUrl, objectKey);
        } catch (S3Exception ex) {
            log.error("Lỗi khi tạo presigned URL S3/R2. bucket={}, key={}", properties.getBucket(), objectKey, ex);
            throw new BaseException(ErrorCode.STORAGE_ERROR, "Không thể tạo liên kết tải lên");
        }
    }

    @Override
    public String resolvePublicUrl(String objectKey) {
        return buildPublicUrl(objectKey);
    }

    @Override
    public String storageProviderName() {
        return "R2";
    }

    @Override
    public ObjectMetadata headObject(String objectKey) {
        try {
            var response = s3Client.headObject(HeadObjectRequest.builder()
                    .bucket(properties.getBucket())
                    .key(objectKey)
                    .build());
            return new ObjectMetadata(
                    objectKey,
                    response.contentType(),
                    response.contentLength() == null ? 0L : response.contentLength(),
                    response.metadata()
            );
        } catch (S3Exception ex) {
            if (ex.statusCode() == 404) {
                throw new BaseException(ErrorCode.BAD_REQUEST, "File upload chưa tồn tại trên storage");
            }
            log.error("Lỗi khi head object trên S3/R2. bucket={}, key={}", properties.getBucket(), objectKey, ex);
            throw new BaseException(ErrorCode.STORAGE_ERROR, "Không thể xác minh file đã upload");
        }
    }

    @Override
    public PrivatePresignedUpload generatePrivateUploadUrl(String objectKey, String contentType, Duration ttl) {
        PutObjectPresignRequest request = PutObjectPresignRequest.builder()
                .signatureDuration(ttl)
                .putObjectRequest(PutObjectRequest.builder().bucket(properties.getBucket()).key(objectKey).contentType(contentType).build())
                .build();
        PresignedPutObjectRequest presigned = s3Presigner.presignPutObject(request);
        return new PrivatePresignedUpload(presigned.url().toString(), objectKey, java.time.Instant.now().plus(ttl));
    }

    @Override
    public PrivatePresignedDownload generatePrivateDownloadUrl(String objectKey, Duration ttl, String contentDisposition) {
        GetObjectPresignRequest request = GetObjectPresignRequest.builder()
                .signatureDuration(ttl)
                .getObjectRequest(GetObjectRequest.builder().bucket(properties.getBucket()).key(objectKey)
                        .responseContentDisposition(contentDisposition).build())
                .build();
        PresignedGetObjectRequest presigned = s3Presigner.presignGetObject(request);
        return new PrivatePresignedDownload(presigned.url().toString(), java.time.Instant.now().plus(ttl));
    }

    @Override
    public InputStream openObject(String objectKey) {
        return s3Client.getObject(GetObjectRequest.builder().bucket(properties.getBucket()).key(objectKey).build());
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
        if (properties.getDocumentsPrefix() != null && !properties.getDocumentsPrefix().isBlank()) {
            keyBuilder.append(properties.getDocumentsPrefix());
        } else {
            keyBuilder.append("skillswap");
        }
        if (subFolder != null && !subFolder.isBlank()) {
            keyBuilder.append('/').append(subFolder.trim());
        }
        keyBuilder.append('/').append(UUID.randomUUID()).append(extension);
        return keyBuilder.toString();
    }

    private String buildPublicUrl(String objectKey) {
        String publicUrlPrefix = properties.getPublicUrlPrefix();
        if (publicUrlPrefix != null && !publicUrlPrefix.isBlank()) {
            if (publicUrlPrefix.endsWith("/")) {
                publicUrlPrefix = publicUrlPrefix.substring(0, publicUrlPrefix.length() - 1);
            }
            return publicUrlPrefix + "/" + objectKey;
        }
        
        String endpoint = properties.getEndpoint();
        if (endpoint.endsWith("/")) {
            endpoint = endpoint.substring(0, endpoint.length() - 1);
        }
        return endpoint + "/" + properties.getBucket() + "/" + objectKey;
    }
}
