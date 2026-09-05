package com.fptu.exe.skillswap.infrastructure.video;

import com.fptu.exe.skillswap.infrastructure.storage.StorageProperties;
import com.fptu.exe.skillswap.modules.course.domain.StorageProviderType;
import com.fptu.exe.skillswap.modules.course.port.VideoStorageProvider;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/** Routes new video operations by configuration and existing assets by row metadata. */
@Component
@RequiredArgsConstructor
public class VideoStorageProviderRouter implements VideoStorageProvider {

    private final StorageProperties properties;
    private final List<VideoStorageProvider.Adapter> adapters;

    @Override
    public StorageProviderType activeProviderType() {
        return configuredProviderType();
    }

    @Override
    public void validateUploadRequest(String contentType, long sizeBytes) {
        adapterFor(configuredProviderType()).validateUploadRequest(contentType, sizeBytes);
    }

    @Override
    public UploadIntent createUploadIntent(UploadRequest request) {
        return adapterFor(providerFor(request)).createUploadIntent(request);
    }

    @Override
    public UploadConfirmation confirmUpload(VideoAsset asset) {
        return adapterFor(asset.providerType()).confirmUpload(asset);
    }

    @Override
    public void validateStoredAsset(VideoAsset asset) {
        adapterFor(asset.providerType()).validateStoredAsset(asset);
    }

    @Override
    public void deleteVideo(VideoAsset asset) {
        adapterFor(asset.providerType()).deleteVideo(asset);
    }

    @Override
    public PlaybackUrl generatePlaybackUrl(VideoAsset asset, String clientIp) {
        VideoStorageProvider.Adapter adapter = adapterFor(asset.providerType());
        adapter.validateStoredAsset(asset);
        return adapter.generatePlaybackUrl(asset, clientIp);
    }

    private VideoStorageProvider.Adapter adapterFor(StorageProviderType providerType) {
        return adapters.stream()
                .filter(adapter -> adapter.providerType() == providerType)
                .findFirst()
                .orElseThrow(() -> new BaseException(
                        ErrorCode.CONFIGURATION_ERROR,
                        "Không tìm thấy video storage provider: " + providerType));
    }

    private StorageProviderType configuredProviderType() {
        String configured = properties.getVideoProvider();
        if (configured == null || configured.isBlank()) {
            return StorageProviderType.OBJECT_STORAGE;
        }
        return switch (configured.trim().toUpperCase(java.util.Locale.ROOT)) {
            case "R2", "OBJECT_STORAGE" -> StorageProviderType.OBJECT_STORAGE;
            case "BUNNY", "BUNNY_VIDEO" -> StorageProviderType.BUNNY_VIDEO;
            default -> throw new BaseException(
                    ErrorCode.CONFIGURATION_ERROR,
                    "VIDEO_STORAGE_PROVIDER phải là R2 hoặc BUNNY_VIDEO");
        };
    }

    private StorageProviderType providerFor(UploadRequest request) {
        if (request.existingVideoId() != null && !request.existingVideoId().isBlank()) {
            return StorageProviderType.BUNNY_VIDEO;
        }
        if (request.existingObjectKey() != null && !request.existingObjectKey().isBlank()) {
            return StorageProviderType.OBJECT_STORAGE;
        }
        return configuredProviderType();
    }
}
