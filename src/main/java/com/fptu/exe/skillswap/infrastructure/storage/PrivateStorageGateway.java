package com.fptu.exe.skillswap.infrastructure.storage;

import java.time.Duration;

public interface PrivateStorageGateway {

    StorageGateway.PrivatePresignedUpload generatePrivateUploadUrl(String objectKey, String contentType, Duration ttl);

    StorageGateway.PrivatePresignedDownload generatePrivateDownloadUrl(String objectKey, Duration ttl, String contentDisposition);

    /**
     * Promotes a client-uploaded private object to a server-owned key.  The final
     * key is never exposed as an upload target, so evidence cannot be changed
     * after it has been confirmed.
     */
    void copyPrivateObject(String sourceObjectKey, String targetObjectKey, String contentType);

    void deletePrivateObject(String objectKey);
}
