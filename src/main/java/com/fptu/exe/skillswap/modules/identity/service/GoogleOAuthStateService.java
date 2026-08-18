package com.fptu.exe.skillswap.modules.identity.service;

import com.fptu.exe.skillswap.modules.identity.dto.response.GoogleAuthorizationContextResponse;
import com.fptu.exe.skillswap.infrastructure.config.CacheProperties;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.cache.CaffeineCacheMetrics;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Objects;
import java.util.UUID;

@Service
public class GoogleOAuthStateService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final Duration stateTtl;
    private final Cache<String, PendingAuthorization> pendingAuthorizations;

    @Autowired
    public GoogleOAuthStateService(CacheProperties cacheProperties, MeterRegistry meterRegistry) {
        this(cacheProperties, meterRegistry, true);
    }

    GoogleOAuthStateService(CacheProperties cacheProperties) {
        this(cacheProperties, null, false);
    }

    private GoogleOAuthStateService(CacheProperties cacheProperties, MeterRegistry meterRegistry, boolean monitorMetrics) {
        CacheProperties.TimedCache settings = cacheProperties.getGoogleOauthState();
        this.stateTtl = settings.getTtl();
        this.pendingAuthorizations = Caffeine.newBuilder()
                .maximumSize(settings.getMaximumSize())
                .expireAfterWrite(stateTtl)
                .recordStats()
                .build();
        if (monitorMetrics) {
            CaffeineCacheMetrics.monitor(meterRegistry, pendingAuthorizations, "google-oauth-state");
        }
    }

    public GoogleAuthorizationContextResponse issueCalendarConnect(
            UUID userId,
            String redirectUri,
            String codeChallenge
    ) {
        if (userId == null) {
            throw new BaseException(ErrorCode.UNAUTHENTICATED, "Chưa xác thực người dùng");
        }
        return issue(GoogleOAuthPurpose.CALENDAR_CONNECT, userId, redirectUri, codeChallenge);
    }

    public void consumeCalendarConnect(
            UUID userId,
            String state,
            String redirectUri,
            String codeVerifier
    ) {
        if (userId == null) {
            throw new BaseException(ErrorCode.UNAUTHENTICATED, "Chưa xác thực người dùng");
        }
        consume(GoogleOAuthPurpose.CALENDAR_CONNECT, userId, state, redirectUri, codeVerifier);
    }

    private GoogleAuthorizationContextResponse issue(
            GoogleOAuthPurpose purpose,
            UUID userId,
            String redirectUri,
            String codeChallenge
    ) {
        requireText(redirectUri, "redirectUri");
        requireText(codeChallenge, "codeChallenge");
        byte[] stateBytes = new byte[32];
        SECURE_RANDOM.nextBytes(stateBytes);
        String state = Base64.getUrlEncoder().withoutPadding().encodeToString(stateBytes);
        Instant expiresAt = Instant.now().plus(stateTtl);
        pendingAuthorizations.put(state, new PendingAuthorization(
                purpose,
                userId,
                redirectUri.trim(),
                codeChallenge.trim()
        ));
        return new GoogleAuthorizationContextResponse(state, expiresAt);
    }

    private void consume(
            GoogleOAuthPurpose purpose,
            UUID userId,
            String state,
            String redirectUri,
            String codeVerifier
    ) {
        requireText(state, "state");
        requireText(redirectUri, "redirectUri");
        requireText(codeVerifier, "codeVerifier");
        PendingAuthorization pending = pendingAuthorizations.asMap().remove(state);
        if (pending == null
                || pending.purpose() != purpose
                || !Objects.equals(pending.userId(), userId)
                || !MessageDigest.isEqual(pending.redirectUri().getBytes(StandardCharsets.UTF_8), redirectUri.trim().getBytes(StandardCharsets.UTF_8))
                || !MessageDigest.isEqual(pending.codeChallenge().getBytes(StandardCharsets.US_ASCII), challenge(codeVerifier).getBytes(StandardCharsets.US_ASCII))) {
            throw new BaseException(ErrorCode.OAUTH_VERIFICATION_FAILED, "OAuth state hoặc PKCE verifier không hợp lệ hoặc đã hết hạn");
        }
    }

    private String challenge(String verifier) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new BaseException(ErrorCode.CONFIGURATION_ERROR, "SHA-256 không khả dụng", exception);
        }
    }

    private void requireText(String value, String field) {
        if (!StringUtils.hasText(value)) {
            throw new BaseException(ErrorCode.BAD_REQUEST, field + " không được để trống");
        }
    }

    private record PendingAuthorization(
            GoogleOAuthPurpose purpose,
            UUID userId,
            String redirectUri,
            String codeChallenge
    ) {
    }
}
