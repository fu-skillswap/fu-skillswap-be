package com.fptu.exe.skillswap.modules.filestorage.port;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Public asset contract for feature modules.  It deliberately exposes only
 * immutable metadata; the {@code StoredFile} JPA aggregate never crosses the
 * file-storage boundary.
 */
public interface PublicAssetUploadPort {

    UploadIntent createBlogImageIntent(UUID ownerUserId, UploadRequest request);

    FileAssetMetadata confirmBlogImage(UUID ownerUserId, UUID intentId);

    UploadIntent createPortfolioImageIntent(UUID ownerUserId, UploadRequest request);

    FileAssetMetadata confirmPortfolioImage(UUID ownerUserId, UUID intentId);

    FileAssetMetadata requireOwnedPortfolioImage(UUID ownerUserId, UUID assetId);

    FileAssetMetadata requireOwnedBlogImage(UUID ownerUserId, UUID assetId);

    void requireOwnedBlogImageUrl(UUID ownerUserId, String publicUrl);

    record UploadRequest(
            @NotBlank @Size(max = 180) String filename,
            @NotBlank @Size(max = 120) String contentType
    ) { }

    record UploadIntent(
            UUID uploadIntentId,
            String uploadUrl,
            LocalDateTime expiresAt,
            Map<String, String> requiredHeaders
    ) { }

    record FileAssetMetadata(
            UUID assetId,
            String publicUrl,
            String contentType,
            long sizeBytes
    ) { }
}
