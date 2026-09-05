package com.fptu.exe.skillswap.infrastructure.storage;

import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class VideoStoragePolicyTest {

    @Test
    void acceptsConfiguredMvpVideoTypeAndNormalizesIt() {
        StorageProperties properties = new StorageProperties();
        properties.setAllowedVideoContentTypes(List.of("video/mp4"));

        VideoStoragePolicy policy = new VideoStoragePolicy(properties);

        assertEquals("video/mp4", policy.normalizeContentType(" VIDEO/MP4 "));
    }

    @Test
    void rejectsUnsupportedVideoType() {
        VideoStoragePolicy policy = new VideoStoragePolicy(new StorageProperties());

        BaseException exception = assertThrows(BaseException.class,
                () -> policy.normalizeContentType("video/quicktime"));

        assertEquals(ErrorCode.UNSUPPORTED_MEDIA_TYPE, exception.getErrorCode());
    }

    @Test
    void rejectsEmptyOrOversizedVideo() {
        StorageProperties properties = new StorageProperties();
        properties.setMaxVideoSizeMb(1);
        VideoStoragePolicy policy = new VideoStoragePolicy(properties);

        BaseException empty = assertThrows(BaseException.class, () -> policy.validateSize(0));
        BaseException oversized = assertThrows(BaseException.class, () -> policy.validateSize(1024L * 1024L + 1));

        assertEquals(ErrorCode.PAYLOAD_TOO_LARGE, empty.getErrorCode());
        assertEquals(ErrorCode.PAYLOAD_TOO_LARGE, oversized.getErrorCode());
    }

    @Test
    void exposesConfiguredProviderNeutralPrefix() {
        StorageProperties properties = new StorageProperties();
        properties.setVideoPrefix("course-video-assets");

        assertEquals("course-video-assets", new VideoStoragePolicy(properties).videoPrefix());
    }

    @Test
    void capsVideoUploadIntentTtlAtFifteenMinutes() {
        StorageProperties properties = new StorageProperties();
        properties.setPresignedTtlMinutes(60);

        assertEquals(Duration.ofMinutes(15), new VideoStoragePolicy(properties).uploadTtl());
    }
}
