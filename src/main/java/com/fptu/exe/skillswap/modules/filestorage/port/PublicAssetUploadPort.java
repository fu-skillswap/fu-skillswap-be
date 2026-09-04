package com.fptu.exe.skillswap.modules.filestorage.port;

import com.fptu.exe.skillswap.shared.dto.response.ProviderNeutralUploadMetadata;
import io.swagger.v3.oas.annotations.media.Schema;

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
            @Schema(description = "ID upload intent do backend tạo; FE dùng để gọi API confirm tương ứng.")
            UUID uploadIntentId,
            @Schema(description = "URL upload tạm thời; FE dùng ngay và không lưu làm URL cố định.")
            String uploadUrl,
            @Schema(description = "Thời điểm URL hết hạn; sau thời điểm này cần tạo intent mới.")
            LocalDateTime expiresAt,
            @Schema(description = "Header FE phải gửi khi upload, ví dụ Content-Type.")
            Map<String, String> requiredHeaders,
            @Schema(description = "Metadata upload trung lập với provider. FE ưu tiên dùng object này khi có.")
            ProviderNeutralUploadMetadata metadata
    ) {
        /** Keeps source compatibility for callers using the original four-argument contract. */
        public UploadIntent(UUID uploadIntentId, String uploadUrl, LocalDateTime expiresAt,
                Map<String, String> requiredHeaders) {
            this(uploadIntentId, uploadUrl, expiresAt, requiredHeaders, null);
        }
    }

    record FileAssetMetadata(
            @Schema(description = "ID asset ổn định để FE lưu tham chiếu trong nghiệp vụ.")
            UUID assetId,
            @Schema(description = "URL public của asset sau khi xác nhận.")
            String publicUrl,
            @Schema(description = "MIME type của asset.")
            String contentType,
            long sizeBytes,
            @Schema(description = "Metadata asset trung lập với provider. FE dùng assetId và publicUrl; không cần storage key.")
            ProviderNeutralUploadMetadata metadata
    ) {
        /** Keeps source compatibility for callers using the original four-argument contract. */
        public FileAssetMetadata(UUID assetId, String publicUrl, String contentType, long sizeBytes) {
            this(assetId, publicUrl, contentType, sizeBytes,
                    new ProviderNeutralUploadMetadata(assetId, null, publicUrl, null,
                            "PUBLIC_ASSET", Map.of()));
        }
    }
}
