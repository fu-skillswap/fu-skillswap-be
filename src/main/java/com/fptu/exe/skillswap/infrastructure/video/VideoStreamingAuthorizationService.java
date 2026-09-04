package com.fptu.exe.skillswap.infrastructure.video;

import com.fptu.exe.skillswap.infrastructure.storage.StorageGateway;
import com.fptu.exe.skillswap.modules.course.domain.CourseMaterial;
import com.fptu.exe.skillswap.modules.course.domain.CourseMaterialType;
import com.fptu.exe.skillswap.modules.course.domain.MaterialStatus;
import com.fptu.exe.skillswap.modules.course.domain.StorageProviderType;
import com.fptu.exe.skillswap.modules.course.repository.CourseMaterialRepository;
import com.fptu.exe.skillswap.shared.exception.BadRequestException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import com.fptu.exe.skillswap.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.net.URI;
import java.util.UUID;

/**
 * Internal Nginx auth-request handler. It returns a short-lived R2 GET URL
 * only after validating the API-issued playback grant and material state.
 */
@Service
@RequiredArgsConstructor
public class VideoStreamingAuthorizationService {
    private final CourseMaterialRepository materialRepository;
    private final StorageGateway storageGateway;
    private final VideoPlaybackTokenService tokenService;

    public StreamGrant authorize(UUID assetId, String token) {
        tokenService.validate(assetId, token);
        CourseMaterial material = materialRepository.findActiveWithCurriculumById(assetId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy video"));
        if (material.getMaterialType() != CourseMaterialType.VIDEO
                || material.getStorageProviderType() != StorageProviderType.OBJECT_STORAGE
                || material.getVideoObjectKey() == null) {
            throw new ResourceNotFoundException("Không tìm thấy video");
        }
        if (material.getStatus() != MaterialStatus.READY) {
            throw new BadRequestException(ErrorCode.BAD_REQUEST, "Video chưa sẵn sàng để phát");
        }
        StorageGateway.PrivatePresignedDownload source = storageGateway.generatePrivateDownloadUrl(
                material.getVideoObjectKey(), Duration.ofMinutes(1), "inline; filename=\"course-video.mp4\"");
        return new StreamGrant(source.downloadUrl(), URI.create(source.downloadUrl()).getHost(), "video/mp4");
    }

    public record StreamGrant(String sourceUrl, String sourceHost, String contentType) {}
}
