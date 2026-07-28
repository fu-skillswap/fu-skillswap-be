package com.fptu.exe.skillswap.infrastructure.storage;

import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.time.Duration;

/**
 * Fails closed when a deployed environment has not configured object storage.
 *
 * <p>This keeps non-file features available while ensuring no upload or
 * download endpoint can accidentally fall back to a public local path. Local
 * and test profiles continue to use {@link LocalFileStorageGatewayImpl}.</p>
 */
@Service
@Profile("!local & !test")
@ConditionalOnProperty(prefix = "application.storage", name = "enabled", havingValue = "false", matchIfMissing = true)
public class DisabledStorageGateway implements StorageGateway {

    private static final String MESSAGE = "Tính năng tệp hiện chưa được cấu hình trên môi trường này";

    @Override
    public StorageUploadResult uploadFile(MultipartFile file, String subFolder) {
        throw unavailable();
    }

    @Override
    public void deleteFile(String objectKey) {
        throw unavailable();
    }

    @Override
    public PresignedUpload generatePresignedUploadUrl(String originalFilename, String contentType) {
        throw unavailable();
    }

    @Override
    public String resolvePublicUrl(String objectKey) {
        throw unavailable();
    }

    @Override
    public String storageProviderName() {
        return "DISABLED";
    }

    @Override
    public ObjectMetadata headObject(String objectKey) {
        throw unavailable();
    }

    @Override
    public PrivatePresignedUpload generatePrivateUploadUrl(String objectKey, String contentType, Duration ttl) {
        throw unavailable();
    }

    @Override
    public PrivatePresignedDownload generatePrivateDownloadUrl(String objectKey, Duration ttl, String contentDisposition) {
        throw unavailable();
    }

    @Override
    public InputStream openObject(String objectKey) {
        throw unavailable();
    }

    private BaseException unavailable() {
        return new BaseException(ErrorCode.STORAGE_ERROR, MESSAGE);
    }
}
