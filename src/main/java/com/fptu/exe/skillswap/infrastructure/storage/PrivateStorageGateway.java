package com.fptu.exe.skillswap.infrastructure.storage;

import java.time.Duration;

public interface PrivateStorageGateway {

    StorageGateway.PrivatePresignedUpload generatePrivateUploadUrl(String objectKey, String contentType, Duration ttl);

    StorageGateway.PrivatePresignedDownload generatePrivateDownloadUrl(String objectKey, Duration ttl, String contentDisposition);
}
