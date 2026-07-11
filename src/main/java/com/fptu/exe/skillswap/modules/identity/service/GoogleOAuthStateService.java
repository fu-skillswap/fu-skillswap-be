package com.fptu.exe.skillswap.modules.identity.service;

import com.fptu.exe.skillswap.modules.identity.dto.response.GoogleAuthorizationContextResponse;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

@Service
public class GoogleOAuthStateService {

    private static final Duration STATE_TTL = Duration.ofMinutes(5);
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final Cache<String, PendingAuthorization> pendingAuthorizations = Caffeine.newBuilder()
            .maximumSize(10_000)
            .expireAfterWrite(STATE_TTL)
            .build();

    public GoogleAuthorizationContextResponse issue(String redirectUri, String codeChallenge) {
        requireText(redirectUri, "redirectUri");
        requireText(codeChallenge, "codeChallenge");
        byte[] stateBytes = new byte[32];
        SECURE_RANDOM.nextBytes(stateBytes);
        String state = Base64.getUrlEncoder().withoutPadding().encodeToString(stateBytes);
        Instant expiresAt = Instant.now().plus(STATE_TTL);
        pendingAuthorizations.put(state, new PendingAuthorization(redirectUri.trim(), codeChallenge.trim()));
        return new GoogleAuthorizationContextResponse(state, expiresAt);
    }

    public void consume(String state, String redirectUri, String codeVerifier) {
        requireText(state, "state");
        requireText(redirectUri, "redirectUri");
        requireText(codeVerifier, "codeVerifier");
        PendingAuthorization pending = pendingAuthorizations.asMap().remove(state);
        if (pending == null
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

    private record PendingAuthorization(String redirectUri, String codeChallenge) {
    }
}
