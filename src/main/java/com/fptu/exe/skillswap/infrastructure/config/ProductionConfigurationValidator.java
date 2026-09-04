package com.fptu.exe.skillswap.infrastructure.config;

import com.fptu.exe.skillswap.infrastructure.bunny.config.BunnyStreamProperties;
import com.fptu.exe.skillswap.infrastructure.storage.StorageProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Fail-fast validation for the production deployment contract.
 *
 * This validates presence and safe shape only. It deliberately does not call
 * external providers during startup; provider reachability belongs to health
 * monitoring and deployment smoke tests.
 */
@Component
@Profile("prod")
// The production profile defaults this property to true. A false value is
// reserved for explicit non-production tests that reuse the prod profile.
@ConditionalOnProperty(prefix = "application.production-validation", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class ProductionConfigurationValidator implements SmartInitializingSingleton {

    private final JwtProperties jwtProperties;
    private final PaymentProperties paymentProperties;
    private final GoogleApiProperties googleApiProperties;
    private final StorageProperties storageProperties;
    private final BunnyStreamProperties bunnyStreamProperties;

    @Value("${application.cors.allowed-origin-patterns:}")
    private String allowedOrigins;

    @Value("${DEPLOY_ENV:production}")
    private String deployEnv;

    @Value("${application.mail.enabled:false}")
    private boolean mailEnabled;

    @Value("${application.mail.from:}")
    private String mailFrom;

    @Value("${spring.mail.host:}")
    private String mailHost;

    @Value("${spring.mail.username:}")
    private String mailUsername;

    @Value("${spring.mail.password:}")
    private String mailPassword;

    @Value("${CURSOR_AES_KEY:}")
    private String cursorAesKey;

    @Value("${CURSOR_HMAC_KEY:}")
    private String cursorHmacKey;

    @Value("${JWT_REFRESH_COOKIE_SECURE:true}")
    private boolean refreshCookieSecure;

    @Override
    public void afterSingletonsInstantiated() {
        List<String> missing = new ArrayList<>();

        require(missing, "JWT_SECRET_KEY", jwtProperties.getJwt().getSecretKey(), 32);
        rejectDevelopmentValue(missing, "JWT_SECRET_KEY", jwtProperties.getJwt().getSecretKey());
        require(missing, "JWT_ISSUER", jwtProperties.getJwt().getIssuer(), 1);
        require(missing, "JWT_AUDIENCE", jwtProperties.getJwt().getAudience(), 1);
        require(missing, "CURSOR_AES_KEY", cursorAesKey, 32);
        require(missing, "CURSOR_HMAC_KEY", cursorHmacKey, 32);
        if (!refreshCookieSecure) {
            missing.add("JWT_REFRESH_COOKIE_SECURE=true");
        }

        validateDeployEnvironment(missing, deployEnv);
        validateProductionCors(missing, allowedOrigins, deployEnv);

        require(missing, "PAYOS_CLIENT_ID", paymentProperties.getPayos().getClientId(), 1);
        require(missing, "PAYOS_API_KEY", paymentProperties.getPayos().getApiKey(), 1);
        require(missing, "PAYOS_CHECKSUM_KEY", paymentProperties.getPayos().getChecksumKey(), 1);
        requireHttps(missing, "PAYOS_RETURN_URL", paymentProperties.getPayos().getReturnUrl());
        requireHttps(missing, "PAYOS_CANCEL_URL", paymentProperties.getPayos().getCancelUrl());
        requireHttps(missing, "PAYOS_WEBHOOK_URL", paymentProperties.getPayos().getWebhookUrl());
        require(missing, "PAYOS_WEBHOOK_SECRET", paymentProperties.getPayos().getWebhookSecret(), 1);

        require(missing, "GOOGLE_CLIENT_ID", googleApiProperties.getClientId(), 1);
        require(missing, "GOOGLE_CLIENT_SECRET", googleApiProperties.getClientSecret(), 1);
        requireHttps(missing, "GOOGLE_CALENDAR_REDIRECT_URI", googleApiProperties.getCalendarRedirectUri());
        require(missing, "GOOGLE_TOKEN_ENCRYPTION_KEY", googleApiProperties.getTokenEncryptionKey(), 1);

        validateVideoProvider(missing, storageProperties.getVideoProvider(), bunnyStreamProperties);

        if (!storageProperties.isEnabled()) {
            missing.add("STORAGE_ENABLED=true");
        }
        requireHttps(missing, "STORAGE_ENDPOINT", storageProperties.getEndpoint());
        require(missing, "STORAGE_ACCESS_KEY", storageProperties.getAccessKey(), 1);
        require(missing, "STORAGE_SECRET_KEY", storageProperties.getSecretKey(), 1);
        require(missing, "STORAGE_BUCKET", storageProperties.getBucket(), 1);

        if (!mailEnabled) {
            missing.add("APPLICATION_MAIL_ENABLED=true");
        }
        require(missing, "APPLICATION_MAIL_FROM", mailFrom, 1);
        require(missing, "SPRING_MAIL_HOST", mailHost, 1);
        require(missing, "SPRING_MAIL_USERNAME", mailUsername, 1);
        require(missing, "SPRING_MAIL_PASSWORD", mailPassword, 1);

        if (!missing.isEmpty()) {
            throw new IllegalStateException(
                    "Production configuration is incomplete or unsafe. Check: " + String.join(", ", missing));
        }
    }

    private static void require(List<String> failures, String name, String value, int minimumLength) {
        if (!StringUtils.hasText(value) || value.trim().length() < minimumLength) {
            failures.add(name);
        }
    }

    private void requireHttps(List<String> failures, String name, String value) {
        require(failures, name, value, 1);
        if (StringUtils.hasText(value) && !value.trim().startsWith("https://")) {
            failures.add(name + "=https");
        }
    }

    private void rejectDevelopmentValue(List<String> failures, String name, String value) {
        if (containsDevelopmentValue(value)) {
            failures.add(name + " without development placeholder");
        }
    }

    static void validateVideoProvider(
            List<String> failures,
            String provider,
            BunnyStreamProperties bunnyStreamProperties
    ) {
        String normalized = StringUtils.hasText(provider)
                ? provider.trim().toUpperCase(Locale.ROOT)
                : "R2";
        if ("R2".equals(normalized)) {
            return;
        }
        if ("BUNNY".equals(normalized)) {
            require(failures, "BUNNY_STREAM_API_KEY", bunnyStreamProperties.getApiKey(), 1);
            require(failures, "BUNNY_STREAM_LIBRARY_ID", bunnyStreamProperties.getLibraryId(), 1);
            require(failures, "BUNNY_STREAM_TOKEN_AUTH_KEY", bunnyStreamProperties.getTokenAuthKey(), 1);
            require(failures, "BUNNY_STREAM_WEBHOOK_SECRET", bunnyStreamProperties.getWebhookSecret(), 1);
            return;
        }
        failures.add("VIDEO_STORAGE_PROVIDER must be R2 or BUNNY");
    }

    static void validateProductionCors(List<String> failures, String allowedOrigins) {
        validateProductionCors(failures, allowedOrigins, "production");
    }

    static void validateProductionCors(List<String> failures, String allowedOrigins, String deployEnv) {
        require(failures, "CORS_ALLOWED_ORIGIN_PATTERNS", allowedOrigins, 1);
        if (!StringUtils.hasText(allowedOrigins)) {
            return;
        }

        String normalizedDeployEnv = normalizeDeployEnvironment(deployEnv);
        boolean containsUnsafeOrigin = false;
        boolean containsNonHttpsOrigin = false;
        for (String origin : allowedOrigins.split(",", -1)) {
            String normalizedOrigin = origin.trim().toLowerCase(Locale.ROOT);
            if (!StringUtils.hasText(normalizedOrigin) || normalizedOrigin.contains("*")) {
                containsUnsafeOrigin = true;
            } else if (isLocalOrigin(normalizedOrigin)) {
                if (!("development".equals(normalizedDeployEnv) || "staging".equals(normalizedDeployEnv))) {
                    containsUnsafeOrigin = true;
                }
            } else if (containsDevelopmentValue(normalizedOrigin)) {
                containsUnsafeOrigin = true;
            } else if (!normalizedOrigin.startsWith("https://")) {
                containsNonHttpsOrigin = true;
            }
        }

        if (containsUnsafeOrigin) {
            failures.add("CORS_ALLOWED_ORIGIN_PATTERNS without localhost, 127.0.0.1, or wildcard");
        }
        if (containsNonHttpsOrigin) {
            failures.add("CORS_ALLOWED_ORIGIN_PATTERNS must contain only explicit HTTPS frontend origins");
        }
    }

    static void validateDeployEnvironment(List<String> failures, String deployEnv) {
        if (!isSupportedDeployEnvironment(deployEnv)) {
            failures.add("DEPLOY_ENV must be development, staging, or production");
        }
    }

    private static String normalizeDeployEnvironment(String deployEnv) {
        return isSupportedDeployEnvironment(deployEnv) ? deployEnv.trim().toLowerCase(Locale.ROOT) : "production";
    }

    private static boolean isSupportedDeployEnvironment(String deployEnv) {
        if (!StringUtils.hasText(deployEnv)) {
            return false;
        }
        return switch (deployEnv.trim().toLowerCase(Locale.ROOT)) {
            case "development", "staging", "production" -> true;
            default -> false;
        };
    }

    private static boolean isLocalOrigin(String origin) {
        return origin.contains("localhost") || origin.contains("127.0.0.1");
    }

    private static boolean containsDevelopmentValue(String value) {
        if (!StringUtils.hasText(value)) {
            return false;
        }
        String normalized = value.trim().toLowerCase();
        return normalized.equals("change-me")
                || normalized.startsWith("replace-with")
                || normalized.startsWith("test-")
                || normalized.contains("localhost")
                || normalized.contains("127.0.0.1")
                || normalized.equals("*");
    }
}
