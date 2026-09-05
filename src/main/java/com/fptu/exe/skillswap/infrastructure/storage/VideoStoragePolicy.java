package com.fptu.exe.skillswap.infrastructure.storage;

import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Locale;
import java.util.UUID;

/**
 * Provider-neutral validation rules for the future course-video upload flow.
 *
 * <p>This policy is intentionally separate from the Bunny adapter and does not
 * change the existing Bunny upload or playback path.</p>
 */
@Component
@RequiredArgsConstructor
public class VideoStoragePolicy {

    private final StorageProperties properties;

    public String normalizeContentType(String contentType) {
        String normalized = contentType == null ? "" : contentType.trim().toLowerCase(Locale.ROOT);
        if (!properties.getAllowedVideoContentTypes().stream()
                .map(value -> value == null ? "" : value.trim().toLowerCase(Locale.ROOT))
                .anyMatch(normalized::equals)) {
            throw new BaseException(ErrorCode.UNSUPPORTED_MEDIA_TYPE,
                    "Video chỉ hỗ trợ các định dạng: " + String.join(", ", properties.getAllowedVideoContentTypes()));
        }
        return normalized;
    }

    public void validateSize(long sizeBytes) {
        long maximumBytes = Math.multiplyExact((long) properties.getMaxVideoSizeMb(), 1024L * 1024L);
        if (sizeBytes <= 0 || sizeBytes > maximumBytes) {
            throw new BaseException(ErrorCode.PAYLOAD_TOO_LARGE,
                    "Video vượt quá giới hạn " + properties.getMaxVideoSizeMb() + " MiB");
        }
    }

    public String videoPrefix() {
        return properties.getVideoPrefix();
    }

    public Duration uploadTtl() {
        return Duration.ofMinutes(Math.min(Math.max(1, properties.getPresignedTtlMinutes()), 15));
    }

    /**
     * Builds an opaque provider-neutral key. The original filename is not put
     * in the key, so user input cannot influence storage paths.
     */
    public String objectKey(UUID ownerId, UUID assetId) {
        String prefix = videoPrefix() == null ? "" : videoPrefix().trim();
        if (prefix.endsWith("/")) prefix = prefix.substring(0, prefix.length() - 1);
        return prefix + "/" + ownerId + "/" + assetId + ".mp4";
    }
}
