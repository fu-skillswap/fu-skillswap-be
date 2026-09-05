package com.fptu.exe.skillswap.infrastructure.video;

import com.fptu.exe.skillswap.infrastructure.storage.StorageGateway;
import com.fptu.exe.skillswap.infrastructure.storage.StorageProperties;
import com.fptu.exe.skillswap.infrastructure.storage.VideoStoragePolicy;
import com.fptu.exe.skillswap.modules.course.domain.StorageProviderType;
import com.fptu.exe.skillswap.modules.course.port.VideoStorageProvider;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import com.fptu.exe.skillswap.shared.time.TimeProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class R2VideoStorageProviderTest {

    @Mock private StorageGateway storageGateway;
    @Mock private VideoStoragePolicy policy;
    @Mock private VideoPlaybackTokenService tokenService;

    @Test
    void createsR2UploadIntentThroughProviderAdapter() {
        Instant expiry = Instant.parse("2026-09-05T01:15:00Z");
        when(policy.normalizeContentType("video/mp4")).thenReturn("video/mp4");
        when(policy.objectKey(UUID.fromString("00000000-0000-0000-0000-000000000001"), UUID.fromString("00000000-0000-0000-0000-000000000002")))
                .thenReturn("course-video/asset.mp4");
        when(policy.uploadTtl()).thenReturn(Duration.ofMinutes(15));
        when(storageGateway.generatePrivateUploadUrl("course-video/asset.mp4", "video/mp4", Duration.ofMinutes(15)))
                .thenReturn(new StorageGateway.PrivatePresignedUpload("https://r2.example/upload", "course-video/asset.mp4", expiry));

        R2VideoStorageProvider provider = new R2VideoStorageProvider(storageGateway, policy, tokenService,
                TimeProvider.fixedUtc(Instant.parse("2026-09-05T01:00:00Z")));
        VideoStorageProvider.UploadIntent intent = provider.createUploadIntent(new VideoStorageProvider.UploadRequest(
                UUID.fromString("00000000-0000-0000-0000-000000000001"),
                UUID.fromString("00000000-0000-0000-0000-000000000002"),
                "lesson", "video/mp4", 100L, null, null, null));

        assertThat(intent.providerType()).isEqualTo(StorageProviderType.OBJECT_STORAGE);
        assertThat(intent.uploadUrl()).isEqualTo("https://r2.example/upload");
        assertThat(intent.objectKey()).isEqualTo("course-video/asset.mp4");
        assertThat(intent.requiredHeaders()).containsEntry("Content-Type", "video/mp4");
    }

    @Test
    void confirmsR2UploadAndGeneratesPlaybackUrl() {
        Instant now = Instant.parse("2026-09-05T01:00:00Z");
        Instant expiry = now.plusSeconds(900);
        when(storageGateway.headObject("course-video/asset.mp4"))
                .thenReturn(new StorageGateway.ObjectMetadata("course-video/asset.mp4", "video/mp4", 100L, Map.of()));
        when(policy.normalizeContentType("video/mp4")).thenReturn("video/mp4");
        VideoPlaybackTokenService.PlaybackGrant grant = new VideoPlaybackTokenService.PlaybackGrant(
                UUID.fromString("00000000-0000-0000-0000-000000000002"), expiry, "token");
        when(tokenService.issue(grant.assetId())).thenReturn(grant);
        when(tokenService.playbackUrl(grant.assetId(), grant)).thenReturn("/stream/videos/asset.mp4?token=token");

        R2VideoStorageProvider provider = new R2VideoStorageProvider(storageGateway, policy, tokenService,
                TimeProvider.fixedUtc(now));
        VideoStorageProvider.VideoAsset asset = new VideoStorageProvider.VideoAsset(
                grant.assetId(), "lesson", StorageProviderType.OBJECT_STORAGE, null, null,
                "course-video/asset.mp4", expiry, "video/mp4", 100L);

        assertThat(provider.confirmUpload(asset)).isEqualTo(new VideoStorageProvider.UploadConfirmation(true, "video/mp4", 100L));
        VideoStorageProvider.PlaybackUrl playback = provider.generatePlaybackUrl(asset, null);
        assertThat(playback.url()).isEqualTo("/stream/videos/asset.mp4?token=token");
        verify(storageGateway).headObject("course-video/asset.mp4");
    }

    @Test
    void rejectsR2PlaybackWhenStoredMetadataIsIncomplete() {
        R2VideoStorageProvider provider = new R2VideoStorageProvider(storageGateway, policy, tokenService,
                TimeProvider.fixedUtc(Instant.parse("2026-09-05T01:00:00Z")));

        assertThatThrownBy(() -> provider.generatePlaybackUrl(new VideoStorageProvider.VideoAsset(
                UUID.randomUUID(), "lesson", StorageProviderType.OBJECT_STORAGE, null, null,
                "course-video/asset.mp4", Instant.now(), null, 100L), null))
                .isInstanceOf(BaseException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.STORAGE_ERROR);
    }

    @Test
    void deletesR2ObjectThroughStorageGateway() {
        R2VideoStorageProvider provider = new R2VideoStorageProvider(storageGateway, policy, tokenService,
                TimeProvider.fixedUtc(Instant.parse("2026-09-05T01:00:00Z")));

        provider.deleteVideo(new VideoStorageProvider.VideoAsset(
                UUID.randomUUID(), "lesson", StorageProviderType.OBJECT_STORAGE, null, null,
                "course-video/asset.mp4", null));

        verify(storageGateway).deletePrivateObject("course-video/asset.mp4");
    }

    @Test
    void rejectsConfirmationWhenR2ObjectIsMissing() {
        Instant now = Instant.parse("2026-09-05T01:00:00Z");
        when(storageGateway.headObject("course-video/asset.mp4"))
                .thenThrow(new BaseException(ErrorCode.BAD_REQUEST, "File upload chưa tồn tại trên storage"));

        R2VideoStorageProvider provider = new R2VideoStorageProvider(storageGateway, policy, tokenService,
                TimeProvider.fixedUtc(now));

        assertThatThrownBy(() -> provider.confirmUpload(new VideoStorageProvider.VideoAsset(
                UUID.randomUUID(), "lesson", StorageProviderType.OBJECT_STORAGE, null, null,
                "course-video/asset.mp4", now.plusSeconds(900))))
                .isInstanceOf(BaseException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.BAD_REQUEST);
    }
}
