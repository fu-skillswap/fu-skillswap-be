package com.fptu.exe.skillswap.infrastructure.bunny.course;

import com.fptu.exe.skillswap.infrastructure.bunny.client.BunnyVideoClient;
import com.fptu.exe.skillswap.infrastructure.bunny.config.BunnyStreamProperties;
import com.fptu.exe.skillswap.modules.course.domain.StorageProviderType;
import com.fptu.exe.skillswap.modules.course.port.VideoStorageProvider;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BunnyCourseVideoProviderTest {

    @Mock private BunnyVideoClient client;

    @Test
    void createsAndSignsBunnyUploadThroughProviderAdapter() {
        BunnyStreamProperties properties = new BunnyStreamProperties();
        properties.setApiUrl("https://video.bunnycdn.com");
        properties.setLibraryId("library");
        properties.setApiKey("key");
        BunnyCourseVideoProvider provider = new BunnyCourseVideoProvider(client, properties);
        when(client.createVideo("lesson")).thenReturn(response("video-id"));
        when(client.generateDirectUploadSignature(anyString(), org.mockito.ArgumentMatchers.anyLong()))
                .thenReturn("signature");

        VideoStorageProvider.UploadIntent intent = provider.createUploadIntent(new VideoStorageProvider.UploadRequest(
                UUID.randomUUID(), UUID.randomUUID(), "lesson", "video/mp4", 10L,
                null, null, null));

        assertThat(intent.providerType()).isEqualTo(StorageProviderType.BUNNY_VIDEO);
        assertThat(intent.videoId()).isEqualTo("video-id");
        assertThat(intent.uploadUrl()).isEqualTo("https://video.bunnycdn.com/library/library/videos/video-id");
        assertThat(intent.authorizationSignature()).isEqualTo("signature");
        assertThat(intent.newlyCreated()).isTrue();
        verify(client).createVideo("lesson");
    }

    @Test
    void generatesBunnyPlaybackThroughProviderAdapter() {
        BunnyStreamProperties properties = new BunnyStreamProperties();
        properties.setLibraryId("library");
        properties.setTokenAuthKey("token-key");
        BunnyCourseVideoProvider provider = new BunnyCourseVideoProvider(client, properties);
        when(client.generateSignedPlaybackUrl("video-id", 60, "127.0.0.1"))
                .thenReturn("https://iframe.mediadelivery.net/embed/library/video-id");

        VideoStorageProvider.PlaybackUrl playback = provider.generatePlaybackUrl(new VideoStorageProvider.VideoAsset(
                UUID.randomUUID(), "lesson", StorageProviderType.BUNNY_VIDEO,
                "library", "video-id", null, Instant.now()), "127.0.0.1");

        assertThat(playback.url()).contains("mediadelivery.net");
        assertThat(playback.expiresAt()).isAfter(Instant.now());
    }

    @Test
    void rejectsBunnyPlaybackWhenProviderMetadataIsIncomplete() {
        BunnyStreamProperties properties = new BunnyStreamProperties();
        BunnyCourseVideoProvider provider = new BunnyCourseVideoProvider(client, properties);

        assertThatThrownBy(() -> provider.generatePlaybackUrl(new VideoStorageProvider.VideoAsset(
                UUID.randomUUID(), "lesson", StorageProviderType.BUNNY_VIDEO,
                null, "video-id", null, Instant.now()), null))
                .isInstanceOf(BaseException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.STORAGE_ERROR);
    }

    @Test
    void missingBunnyPlaybackConfigurationFailsWhenLegacyVideoIsAccessed() {
        BunnyStreamProperties properties = new BunnyStreamProperties();
        BunnyCourseVideoProvider provider = new BunnyCourseVideoProvider(client, properties);

        assertThatThrownBy(() -> provider.generatePlaybackUrl(new VideoStorageProvider.VideoAsset(
                UUID.randomUUID(), "lesson", StorageProviderType.BUNNY_VIDEO,
                "library", "video-id", null, Instant.now()), null))
                .isInstanceOf(BaseException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.CONFIGURATION_ERROR);
    }

    private com.fptu.exe.skillswap.infrastructure.bunny.dto.BunnyCreateVideoResponse response(String guid) {
        var response = new com.fptu.exe.skillswap.infrastructure.bunny.dto.BunnyCreateVideoResponse();
        response.setGuid(guid);
        return response;
    }
}
