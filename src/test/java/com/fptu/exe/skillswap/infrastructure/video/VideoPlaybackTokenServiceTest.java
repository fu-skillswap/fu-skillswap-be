package com.fptu.exe.skillswap.infrastructure.video;

import com.fptu.exe.skillswap.infrastructure.config.VideoPlaybackProperties;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.time.TimeProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VideoPlaybackTokenServiceTest {
    private static final Instant NOW = Instant.parse("2026-09-04T03:00:00Z");
    private final UUID assetId = UUID.randomUUID();
    private VideoPlaybackTokenService service;

    @BeforeEach
    void setUp() {
        VideoPlaybackProperties properties = new VideoPlaybackProperties();
        properties.setSigningSecret("test-video-playback-secret");
        properties.setTokenTtlSeconds(300);
        service = new VideoPlaybackTokenService(properties, TimeProvider.fixedUtc(NOW));
    }

    @Test
    void issuedTokenIsBoundToAssetAndHasShortExpiry() {
        VideoPlaybackTokenService.PlaybackGrant grant = service.issue(assetId);

        service.validate(assetId, grant.token());
        assertThat(grant.expiresAt()).isEqualTo(NOW.plusSeconds(300));
        assertThat(service.playbackUrl(assetId, grant))
                .isEqualTo("/stream/videos/" + assetId + ".mp4?expires=" + grant.expiresAt().getEpochSecond() + "&token=" + grant.token());
    }

    @Test
    void tokenForAnotherAssetIsRejected() {
        VideoPlaybackTokenService.PlaybackGrant grant = service.issue(assetId);

        assertThatThrownBy(() -> service.validate(UUID.randomUUID(), grant.token()))
                .isInstanceOf(BaseException.class)
                .hasMessageContaining("không hợp lệ");
    }

    @Test
    void expiredTokenIsRejected() {
        VideoPlaybackProperties properties = new VideoPlaybackProperties();
        properties.setSigningSecret("test-video-playback-secret");
        properties.setTokenTtlSeconds(300);
        VideoPlaybackTokenService expiredService = new VideoPlaybackTokenService(properties,
                TimeProvider.fixedUtc(NOW.plusSeconds(300)));
        VideoPlaybackTokenService.PlaybackGrant grant = service.issue(assetId);

        assertThatThrownBy(() -> expiredService.validate(assetId, grant.token()))
                .isInstanceOf(BaseException.class)
                .hasMessageContaining("hết hạn");
    }
}
