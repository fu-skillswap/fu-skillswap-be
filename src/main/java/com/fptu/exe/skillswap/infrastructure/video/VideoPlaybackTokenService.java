package com.fptu.exe.skillswap.infrastructure.video;

import com.fptu.exe.skillswap.infrastructure.config.VideoPlaybackProperties;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import com.fptu.exe.skillswap.shared.time.TimeProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

/** Short-lived bearer grants for the Nginx video route. */
@Component
@RequiredArgsConstructor
public class VideoPlaybackTokenService {
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private final VideoPlaybackProperties properties;
    private final TimeProvider timeProvider;

    public PlaybackGrant issue(UUID assetId) {
        Instant expiresAt = timeProvider.instant().plusSeconds(properties.getTokenTtlSeconds());
        String payload = assetId + "." + expiresAt.getEpochSecond();
        return new PlaybackGrant(assetId, expiresAt, encode(payload) + "." + encode(sign(payload)));
    }

    public void validate(UUID assetId, String token) {
        try {
            if (!StringUtils.hasText(token)) throw invalidToken();
            String[] parts = token.split("\\.", -1);
            if (parts.length != 2) throw invalidToken();
            String payload = decode(parts[0]);
            String[] claims = payload.split("\\.", -1);
            if (claims.length != 2 || !assetId.toString().equals(claims[0])) throw invalidToken();
            long expiresAt = Long.parseLong(claims[1]);
            byte[] actualSignature = decodeBytes(parts[1]);
            if (!MessageDigest.isEqual(sign(payload), actualSignature)) throw invalidToken();
            if (!timeProvider.instant().isBefore(Instant.ofEpochSecond(expiresAt))) {
                throw new BaseException(ErrorCode.UNAUTHENTICATED, "Playback token đã hết hạn");
            }
        } catch (BaseException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw invalidToken();
        }
    }

    public String playbackUrl(UUID assetId, PlaybackGrant grant) {
        String base = properties.getBaseUrl() == null ? "" : properties.getBaseUrl().replaceAll("/+$", "");
        String path = properties.getStreamPath() == null ? "/stream/videos" : properties.getStreamPath();
        if (!path.startsWith("/")) path = "/" + path;
        path = path.replaceAll("/+$", "");
        return base + path + "/" + assetId + ".mp4?expires=" + grant.expiresAt().getEpochSecond() + "&token=" + grant.token();
    }

    private byte[] sign(String payload) {
        String secret = properties.getSigningSecret();
        if (!StringUtils.hasText(secret)) {
            throw new BaseException(ErrorCode.CONFIGURATION_ERROR, "Thiếu VIDEO_PLAYBACK_SIGNING_SECRET");
        }
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            return mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        } catch (Exception ex) {
            throw new BaseException(ErrorCode.CONFIGURATION_ERROR, "Không thể ký playback token", ex);
        }
    }

    private static String encode(String value) {
        return encode(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String encode(byte[] value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    private static String decode(String value) {
        return new String(decodeBytes(value), StandardCharsets.UTF_8);
    }

    private static byte[] decodeBytes(String value) {
        return Base64.getUrlDecoder().decode(value);
    }

    private BaseException invalidToken() {
        return new BaseException(ErrorCode.UNAUTHENTICATED, "Playback token không hợp lệ");
    }

    public record PlaybackGrant(UUID assetId, Instant expiresAt, String token) {}
}
