package com.fptu.exe.skillswap.modules.identity.service;

import com.fptu.exe.skillswap.infrastructure.config.CacheProperties;
import com.fptu.exe.skillswap.modules.identity.dto.response.GoogleLoginNonceResponse;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.cache.CaffeineCacheMetrics;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

@Service
public class GoogleLoginNonceService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final Duration nonceTtl;
    private final Cache<String, Boolean> pendingNonces;

    @Autowired
    public GoogleLoginNonceService(CacheProperties cacheProperties, MeterRegistry meterRegistry) {
        this(cacheProperties, meterRegistry, true);
    }

    GoogleLoginNonceService(CacheProperties cacheProperties) {
        this(cacheProperties, null, false);
    }

    private GoogleLoginNonceService(
            CacheProperties cacheProperties,
            MeterRegistry meterRegistry,
            boolean monitorMetrics
    ) {
        CacheProperties.TimedCache settings = cacheProperties.getGoogleOauthState();
        nonceTtl = settings.getTtl();
        pendingNonces = Caffeine.newBuilder()
                .maximumSize(settings.getMaximumSize())
                .expireAfterWrite(nonceTtl)
                .recordStats()
                .build();
        if (monitorMetrics) {
            CaffeineCacheMetrics.monitor(meterRegistry, pendingNonces, "google-login-nonce");
        }
    }

    public GoogleLoginNonceResponse issue() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        String nonce = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        pendingNonces.put(nonce, Boolean.TRUE);
        return new GoogleLoginNonceResponse(nonce, Instant.now().plus(nonceTtl));
    }

    public void consume(String nonce) {
        if (!StringUtils.hasText(nonce) || pendingNonces.asMap().remove(nonce) == null) {
            throw new BaseException(
                    ErrorCode.OAUTH_VERIFICATION_FAILED,
                    "Nonce đăng nhập Google không hợp lệ, đã dùng hoặc đã hết hạn"
            );
        }
    }
}
