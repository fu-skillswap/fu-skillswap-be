package com.fptu.exe.skillswap.infrastructure.video;

import com.fptu.exe.skillswap.infrastructure.storage.StorageGateway;
import com.fptu.exe.skillswap.infrastructure.storage.VideoStoragePolicy;
import com.fptu.exe.skillswap.modules.course.domain.StorageProviderType;
import com.fptu.exe.skillswap.modules.course.port.VideoStorageProvider;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import com.fptu.exe.skillswap.shared.time.TimeProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

/** Cloudflare R2/S3-compatible video adapter. */
@Component
@RequiredArgsConstructor
public class R2VideoStorageProvider implements VideoStorageProvider.Adapter {

    private final StorageGateway storageGateway;
    private final VideoStoragePolicy videoStoragePolicy;
    private final VideoPlaybackTokenService videoPlaybackTokenService;
    private final TimeProvider timeProvider;

    @Override
    public StorageProviderType providerType() {
        return StorageProviderType.OBJECT_STORAGE;
    }

    @Override
    public void validateUploadRequest(String contentType, long sizeBytes) {
        videoStoragePolicy.normalizeContentType(contentType);
        videoStoragePolicy.validateSize(sizeBytes);
    }

    @Override
    public VideoStorageProvider.UploadIntent createUploadIntent(VideoStorageProvider.UploadRequest request) {
        requireStorageMetadata(request.ownerId() != null && request.assetId() != null);
        String contentType = request.contentType() == null
                ? "video/mp4" : videoStoragePolicy.normalizeContentType(request.contentType());
        if (request.sizeBytes() != null) {
            videoStoragePolicy.validateSize(request.sizeBytes());
        }
        String expectedObjectKey = videoStoragePolicy.objectKey(request.ownerId(), request.assetId());
        String objectKey = request.existingObjectKey() == null || request.existingObjectKey().isBlank()
                ? expectedObjectKey : request.existingObjectKey();
        requireStorageMetadata(expectedObjectKey.equals(objectKey));
        StorageGateway.PrivatePresignedUpload upload = storageGateway.generatePrivateUploadUrl(
                objectKey, contentType, videoStoragePolicy.uploadTtl());
        return new VideoStorageProvider.UploadIntent(
                providerType(), null, null, objectKey, upload.uploadUrl(), null,
                upload.expiresAt(), contentType, Map.of("Content-Type", contentType), false);
    }

    @Override
    public VideoStorageProvider.UploadConfirmation confirmUpload(VideoStorageProvider.VideoAsset asset) {
        requireStorageMetadata(asset.objectKey() != null && !asset.objectKey().isBlank());
        if (asset.uploadExpiresAt() == null || !timeProvider.instant().isBefore(asset.uploadExpiresAt())) {
            throw new com.fptu.exe.skillswap.shared.exception.BadRequestException(
                    com.fptu.exe.skillswap.shared.exception.ErrorCode.BAD_REQUEST,
                    "Video upload intent đã hết hạn; hãy tạo intent mới");
        }
        StorageGateway.ObjectMetadata object = storageGateway.headObject(asset.objectKey());
        String contentType = videoStoragePolicy.normalizeContentType(object.contentType());
        videoStoragePolicy.validateSize(object.sizeBytes());
        if (asset.contentType() != null && !contentType.equals(videoStoragePolicy.normalizeContentType(asset.contentType()))) {
            throw new com.fptu.exe.skillswap.shared.exception.BadRequestException(
                    ErrorCode.UNSUPPORTED_MEDIA_TYPE,
                    "Content-Type của video không khớp với upload intent");
        }
        return new VideoStorageProvider.UploadConfirmation(true, contentType, object.sizeBytes());
    }

    @Override
    public void validateStoredAsset(VideoStorageProvider.VideoAsset asset) {
        requireStorageMetadata(asset.providerType() == StorageProviderType.OBJECT_STORAGE);
        requireStorageMetadata(asset.objectKey() != null && !asset.objectKey().isBlank());
        requireStorageMetadata(asset.contentType() != null && !asset.contentType().isBlank());
        requireStorageMetadata(asset.sizeBytes() != null && asset.sizeBytes() > 0);
        try {
            videoStoragePolicy.normalizeContentType(asset.contentType());
            videoStoragePolicy.validateSize(asset.sizeBytes());
        } catch (BaseException ex) {
            throw new BaseException(ErrorCode.STORAGE_ERROR, "Video storage metadata không hợp lệ", ex);
        }
    }

    @Override
    public void deleteVideo(VideoStorageProvider.VideoAsset asset) {
        requireStorageMetadata(asset.objectKey() != null && !asset.objectKey().isBlank());
        storageGateway.deletePrivateObject(asset.objectKey());
    }

    @Override
    public VideoStorageProvider.PlaybackUrl generatePlaybackUrl(VideoStorageProvider.VideoAsset asset, String clientIp) {
        validateStoredAsset(asset);
        VideoPlaybackTokenService.PlaybackGrant grant = videoPlaybackTokenService.issue(asset.assetId());
        return new VideoStorageProvider.PlaybackUrl(
                videoPlaybackTokenService.playbackUrl(asset.assetId(), grant), grant.expiresAt());
    }

    private void requireStorageMetadata(boolean valid) {
        if (!valid) {
            throw new BaseException(ErrorCode.STORAGE_ERROR, "Video storage metadata không hợp lệ");
        }
    }
}
