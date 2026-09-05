package com.fptu.exe.skillswap.modules.course.port;

import com.fptu.exe.skillswap.modules.course.domain.StorageProviderType;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Provider-neutral video capability owned by the Course module.
 *
 * <p>The Course service talks to this contract only. Provider adapters may
 * keep their own identifiers, but those identifiers never become part of the
 * Course service's provider-specific control flow.</p>
 */
public interface VideoStorageProvider {

    StorageProviderType activeProviderType();

    void validateUploadRequest(String contentType, long sizeBytes);

    UploadIntent createUploadIntent(UploadRequest request);

    UploadConfirmation confirmUpload(VideoAsset asset);

    void validateStoredAsset(VideoAsset asset);

    void deleteVideo(VideoAsset asset);

    PlaybackUrl generatePlaybackUrl(VideoAsset asset, String clientIp);

    /** Provider adapter contract used by the configuration-backed router. */
    interface Adapter {
        StorageProviderType providerType();

        default void validateUploadRequest(String contentType, long sizeBytes) {
        }

        UploadIntent createUploadIntent(UploadRequest request);

        UploadConfirmation confirmUpload(VideoAsset asset);

        default void validateStoredAsset(VideoAsset asset) {
        }

        void deleteVideo(VideoAsset asset);

        PlaybackUrl generatePlaybackUrl(VideoAsset asset, String clientIp);
    }

    record UploadRequest(
            UUID ownerId,
            UUID assetId,
            String title,
            String contentType,
            Long sizeBytes,
            String existingLibraryId,
            String existingVideoId,
            String existingObjectKey
    ) {
    }

    record UploadIntent(
            StorageProviderType providerType,
            String libraryId,
            String videoId,
            String objectKey,
            String uploadUrl,
            String authorizationSignature,
            Instant expiresAt,
            String contentType,
            Map<String, String> requiredHeaders,
            boolean newlyCreated
    ) {
        public long expirationTimestamp() {
            return expiresAt == null ? 0L : expiresAt.getEpochSecond();
        }
    }

    record UploadConfirmation(boolean ready, String contentType, long sizeBytes) {
        public static UploadConfirmation pending() {
            return new UploadConfirmation(false, null, 0L);
        }
    }

    record VideoAsset(
            UUID assetId,
            String title,
            StorageProviderType providerType,
            String libraryId,
            String videoId,
            String objectKey,
            Instant uploadExpiresAt,
            String contentType,
            Long sizeBytes
    ) {
        public VideoAsset(
                UUID assetId,
                String title,
                StorageProviderType providerType,
                String libraryId,
                String videoId,
                String objectKey,
                Instant uploadExpiresAt
        ) {
            this(assetId, title, providerType, libraryId, videoId, objectKey, uploadExpiresAt, null, null);
        }
    }

    record PlaybackUrl(String url, Instant expiresAt) {
    }
}
