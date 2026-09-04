package com.fptu.exe.skillswap.infrastructure.video;

import com.fptu.exe.skillswap.infrastructure.storage.StorageGateway;
import com.fptu.exe.skillswap.modules.course.domain.CourseMaterial;
import com.fptu.exe.skillswap.modules.course.domain.CourseMaterialType;
import com.fptu.exe.skillswap.modules.course.domain.MaterialStatus;
import com.fptu.exe.skillswap.modules.course.domain.StorageProviderType;
import com.fptu.exe.skillswap.modules.course.repository.CourseMaterialRepository;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VideoStreamingAuthorizationServiceTest {
    @Mock private CourseMaterialRepository materialRepository;
    @Mock private StorageGateway storageGateway;
    @Mock private VideoPlaybackTokenService tokenService;
    @InjectMocks private VideoStreamingAuthorizationService service;

    private final UUID assetId = UUID.randomUUID();

    @Test
    void readyR2VideoReturnsPrivateSourceUrlForNginx() {
        CourseMaterial material = material(MaterialStatus.READY);
        when(materialRepository.findActiveWithCurriculumById(assetId)).thenReturn(Optional.of(material));
        when(storageGateway.generatePrivateDownloadUrl(eq(material.getVideoObjectKey()), any(), any()))
                .thenReturn(new StorageGateway.PrivatePresignedDownload(
                        "https://account.r2.cloudflarestorage.com/private/video.mp4?signature=x",
                        Instant.parse("2026-09-04T03:01:00Z")));

        VideoStreamingAuthorizationService.StreamGrant grant = service.authorize(assetId, "valid-token");

        verify(tokenService).validate(assetId, "valid-token");
        assertThat(grant.sourceUrl()).startsWith("https://account.r2.cloudflarestorage.com/");
        assertThat(grant.sourceHost()).isEqualTo("account.r2.cloudflarestorage.com");
        assertThat(grant.contentType()).isEqualTo("video/mp4");
    }

    @Test
    void unavailableVideoIsRejectedBeforeGeneratingSourceUrl() {
        CourseMaterial material = material(MaterialStatus.UPLOADING);
        when(materialRepository.findActiveWithCurriculumById(assetId)).thenReturn(Optional.of(material));

        assertThatThrownBy(() -> service.authorize(assetId, "valid-token"))
                .isInstanceOf(BaseException.class)
                .hasMessageContaining("chưa sẵn sàng");
        verify(storageGateway, never()).generatePrivateDownloadUrl(any(), any(), any());
    }

    @Test
    void missingVideoIsRejected() {
        when(materialRepository.findActiveWithCurriculumById(assetId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.authorize(assetId, "valid-token"))
                .isInstanceOf(BaseException.class)
                .hasMessageContaining("Không tìm thấy video");
    }

    private CourseMaterial material(MaterialStatus status) {
        return CourseMaterial.builder()
                .id(assetId)
                .materialType(CourseMaterialType.VIDEO)
                .storageProviderType(StorageProviderType.OBJECT_STORAGE)
                .videoObjectKey("course-materials/videos/asset.mp4")
                .status(status)
                .build();
    }
}
