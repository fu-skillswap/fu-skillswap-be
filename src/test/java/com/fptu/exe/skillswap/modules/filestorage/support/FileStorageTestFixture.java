package com.fptu.exe.skillswap.modules.filestorage.support;

import java.util.UUID;

/** Test fixture providing standardized file asset metadata snapshots. */
public final class FileStorageTestFixture {

    private FileStorageTestFixture() {}

    public static UUID randomFileId() {
        return UUID.randomUUID();
    }

    public static FileAssetSnapshot createSampleAsset(UUID ownerUserId) {
        UUID id = UUID.randomUUID();
        return new FileAssetSnapshot(
                id,
                ownerUserId != null ? ownerUserId : UUID.randomUUID(),
                "certificate.pdf",
                "application/pdf",
                102400L,
                "https://storage.skillswap.vn/files/" + id + "/certificate.pdf"
        );
    }

    public record FileAssetSnapshot(
            UUID fileId,
            UUID ownerUserId,
            String originalFilename,
            String contentType,
            long sizeBytes,
            String url
    ) {}
}
