package com.fptu.exe.skillswap.infrastructure.bunny.course;

import com.fptu.exe.skillswap.infrastructure.bunny.client.BunnyVideoClient;
import com.fptu.exe.skillswap.infrastructure.bunny.config.BunnyStreamProperties;
import com.fptu.exe.skillswap.modules.course.port.CourseVideoProvider;
import com.fptu.exe.skillswap.modules.course.domain.StorageProviderType;
import com.fptu.exe.skillswap.modules.course.port.VideoStorageProvider;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;

/** Adapter Bunny Stream cho hợp đồng video do module Course sở hữu. */
@Component
@RequiredArgsConstructor
public class BunnyCourseVideoProvider implements CourseVideoProvider, VideoStorageProvider.Adapter {

    private final BunnyVideoClient bunnyVideoClient;
    private final BunnyStreamProperties bunnyStreamProperties;

    @Override
    public CreatedVideo createVideo(String title) {
        var created = bunnyVideoClient.createVideo(title);
        return new CreatedVideo(bunnyStreamProperties.getLibraryId(), created.getGuid());
    }

    @Override
    public void deleteVideo(String videoId) {
        bunnyVideoClient.deleteVideo(videoId);
    }

    @Override
    public String generateDirectUploadSignature(String videoId, long expirationTimestamp) {
        return bunnyVideoClient.generateDirectUploadSignature(videoId, expirationTimestamp);
    }

    @Override
    public String generateSignedPlaybackUrl(String videoId, long ttlSeconds, String clientIp) {
        return bunnyVideoClient.generateSignedPlaybackUrl(videoId, ttlSeconds, clientIp);
    }

    @Override
    public StorageProviderType providerType() {
        return StorageProviderType.BUNNY_VIDEO;
    }

    @Override
    public VideoStorageProvider.UploadIntent createUploadIntent(VideoStorageProvider.UploadRequest request) {
        if (request.existingVideoId() != null && !request.existingVideoId().isBlank()) {
            requireMetadata(request.existingLibraryId() != null && !request.existingLibraryId().isBlank());
            long expiry = Instant.now().plusSeconds(7200).getEpochSecond();
            return new VideoStorageProvider.UploadIntent(
                    providerType(), request.existingLibraryId(), request.existingVideoId(), null,
                    uploadUrl(request.existingLibraryId(), request.existingVideoId()),
                    bunnyVideoClient.generateDirectUploadSignature(request.existingVideoId(), expiry),
                    Instant.ofEpochSecond(expiry), null, Map.of(), false);
        }
        requireMetadata(bunnyStreamProperties.getLibraryId() != null && !bunnyStreamProperties.getLibraryId().isBlank());
        var created = bunnyVideoClient.createVideo(request.title());
        requireMetadata(created != null && created.getGuid() != null && !created.getGuid().isBlank());
        long expiry = Instant.now().plusSeconds(7200).getEpochSecond();
        return new VideoStorageProvider.UploadIntent(
                providerType(), bunnyStreamProperties.getLibraryId(), created.getGuid(), null,
                uploadUrl(bunnyStreamProperties.getLibraryId(), created.getGuid()),
                bunnyVideoClient.generateDirectUploadSignature(created.getGuid(), expiry),
                Instant.ofEpochSecond(expiry), null, Map.of(), true);
    }

    @Override
    public VideoStorageProvider.UploadConfirmation confirmUpload(VideoStorageProvider.VideoAsset asset) {
        validateStoredAsset(asset);
        // Bunny marks processing/ready through its webhook lifecycle.
        return VideoStorageProvider.UploadConfirmation.pending();
    }

    @Override
    public void validateStoredAsset(VideoStorageProvider.VideoAsset asset) {
        requireMetadata(asset.providerType() == StorageProviderType.BUNNY_VIDEO
                && asset.libraryId() != null && !asset.libraryId().isBlank()
                && asset.videoId() != null && !asset.videoId().isBlank());
    }

    @Override
    public void deleteVideo(VideoStorageProvider.VideoAsset asset) {
        validateStoredAsset(asset);
        bunnyVideoClient.deleteVideo(asset.videoId());
    }

    @Override
    public VideoStorageProvider.PlaybackUrl generatePlaybackUrl(VideoStorageProvider.VideoAsset asset, String clientIp) {
        validateStoredAsset(asset);
        requirePlaybackConfiguration();
        String url = bunnyVideoClient.generateSignedPlaybackUrl(asset.videoId(), 60, clientIp);
        return new VideoStorageProvider.PlaybackUrl(url, Instant.now().plusSeconds(60));
    }

    private void requirePlaybackConfiguration() {
        if (bunnyStreamProperties.getLibraryId() == null || bunnyStreamProperties.getLibraryId().isBlank()
                || bunnyStreamProperties.getTokenAuthKey() == null || bunnyStreamProperties.getTokenAuthKey().isBlank()) {
            throw new BaseException(
                    ErrorCode.CONFIGURATION_ERROR,
                    "Bunny Stream playback configuration is missing"
            );
        }
    }

    private void requireMetadata(boolean valid) {
        if (!valid) {
            throw new BaseException(ErrorCode.STORAGE_ERROR, "Video storage metadata không hợp lệ");
        }
    }

    private String uploadUrl(String libraryId, String videoId) {
        return String.format("%s/library/%s/videos", bunnyStreamProperties.getApiUrl(), libraryId)
                + "/" + videoId;
    }
}
