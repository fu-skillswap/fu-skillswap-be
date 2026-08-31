package com.fptu.exe.skillswap.modules.filestorage.port;

import java.util.UUID;

/** Public file-registration API used by the Mentor verification workflow. */
public interface VerificationDocumentStoragePort {

    VerificationDocumentMetadata registerVerificationDocument(VerificationDocumentRegistration command);

    record VerificationDocumentRegistration(
            UUID ownerUserId,
            String originalFilename,
            String storageProvider,
            String storageKey,
            String contentType,
            long sizeBytes
    ) { }

    record VerificationDocumentMetadata(
            UUID fileId,
            String originalFilename,
            String contentType,
            Long sizeBytes,
            String privateUrl
    ) { }
}
