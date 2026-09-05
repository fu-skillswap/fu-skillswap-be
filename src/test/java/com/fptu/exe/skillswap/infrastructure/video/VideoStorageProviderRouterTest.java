package com.fptu.exe.skillswap.infrastructure.video;

import com.fptu.exe.skillswap.infrastructure.storage.StorageProperties;
import com.fptu.exe.skillswap.modules.course.domain.StorageProviderType;
import com.fptu.exe.skillswap.modules.course.port.VideoStorageProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class VideoStorageProviderRouterTest {

    @Mock private StorageProperties properties;
    @Mock private VideoStorageProvider.Adapter bunny;
    @Mock private VideoStorageProvider.Adapter r2;

    private VideoStorageProviderRouter router;

    @BeforeEach
    void setUp() {
        when(bunny.providerType()).thenReturn(StorageProviderType.BUNNY_VIDEO);
        when(r2.providerType()).thenReturn(StorageProviderType.OBJECT_STORAGE);
        router = new VideoStorageProviderRouter(properties, List.of(bunny, r2));
    }

    @Test
    void selectsBunnyVideoFromConfiguration() {
        when(properties.getVideoProvider()).thenReturn("BUNNY_VIDEO");
        VideoStorageProvider.UploadIntent intent = intent(StorageProviderType.BUNNY_VIDEO);
        when(bunny.createUploadIntent(any())).thenReturn(intent);

        assertThat(router.activeProviderType()).isEqualTo(StorageProviderType.BUNNY_VIDEO);
        assertThat(router.createUploadIntent(request()).providerType()).isEqualTo(StorageProviderType.BUNNY_VIDEO);
        verify(bunny).createUploadIntent(any());
    }

    @Test
    void selectsR2FromConfiguration() {
        when(properties.getVideoProvider()).thenReturn("R2");
        VideoStorageProvider.UploadIntent intent = intent(StorageProviderType.OBJECT_STORAGE);
        when(r2.createUploadIntent(any())).thenReturn(intent);

        assertThat(router.activeProviderType()).isEqualTo(StorageProviderType.OBJECT_STORAGE);
        assertThat(router.createUploadIntent(request()).providerType()).isEqualTo(StorageProviderType.OBJECT_STORAGE);
        verify(r2).createUploadIntent(any());
    }

    @Test
    void routesExistingAssetByPersistedProviderEvenWhenActiveProviderChanges() {
        when(properties.getVideoProvider()).thenReturn("R2");
        VideoStorageProvider.UploadConfirmation confirmation = new VideoStorageProvider.UploadConfirmation(true, "video/mp4", 10L);
        when(bunny.confirmUpload(any())).thenReturn(confirmation);

        assertThat(router.confirmUpload(new VideoStorageProvider.VideoAsset(
                UUID.randomUUID(), "legacy", StorageProviderType.BUNNY_VIDEO,
                "library", "video", null, Instant.now()))).isEqualTo(confirmation);
        verify(bunny).confirmUpload(any());
    }

    private VideoStorageProvider.UploadRequest request() {
        return new VideoStorageProvider.UploadRequest(
                UUID.randomUUID(), UUID.randomUUID(), "video", "video/mp4", 10L,
                null, null, null);
    }

    private VideoStorageProvider.UploadIntent intent(StorageProviderType providerType) {
        return new VideoStorageProvider.UploadIntent(
                providerType, null, null, "video.mp4", "https://upload.example", null,
                Instant.now().plusSeconds(60), "video/mp4", java.util.Map.of(), false);
    }
}
