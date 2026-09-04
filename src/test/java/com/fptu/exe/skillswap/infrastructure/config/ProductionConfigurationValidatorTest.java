package com.fptu.exe.skillswap.infrastructure.config;

import com.fptu.exe.skillswap.infrastructure.bunny.config.BunnyStreamProperties;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ProductionConfigurationValidatorTest {

    @Test
    void r2ProviderDoesNotRequireBunnyCredentials() {
        List<String> failures = new ArrayList<>();

        ProductionConfigurationValidator.validateVideoProvider(
                failures,
                "R2",
                new BunnyStreamProperties()
        );

        assertThat(failures).isEmpty();
    }

    @Test
    void bunnyProviderRequiresExistingBunnyCredentials() {
        BunnyStreamProperties bunny = new BunnyStreamProperties();
        bunny.setApiKey("api-key");
        bunny.setLibraryId("library-id");
        bunny.setTokenAuthKey("token-key");
        bunny.setWebhookSecret("webhook-secret");
        List<String> failures = new ArrayList<>();

        ProductionConfigurationValidator.validateVideoProvider(failures, "bUnNy", bunny);

        assertThat(failures).isEmpty();
    }

    @Test
    void unknownVideoProviderIsRejected() {
        List<String> failures = new ArrayList<>();

        ProductionConfigurationValidator.validateVideoProvider(
                failures,
                "BUNNY_STREAM",
                new BunnyStreamProperties()
        );

        assertThat(failures).containsExactly("VIDEO_STORAGE_PROVIDER must be R2 or BUNNY");
    }

    @Test
    void productionCorsRejectsLocalhost() {
        List<String> failures = new ArrayList<>();

        ProductionConfigurationValidator.validateProductionCors(
                failures,
                "https://app.skillswap.asia,http://localhost:3000"
        );

        assertThat(failures).containsExactly(
                "CORS_ALLOWED_ORIGIN_PATTERNS without localhost, 127.0.0.1, or wildcard"
        );
    }

    @Test
    void productionCorsRejectsNonHttpsFrontendOrigin() {
        List<String> failures = new ArrayList<>();

        ProductionConfigurationValidator.validateProductionCors(
                failures,
                "http://real-domain.com"
        );

        assertThat(failures).containsExactly(
                "CORS_ALLOWED_ORIGIN_PATTERNS must contain only explicit HTTPS frontend origins"
        );
    }

    @Test
    void productionCorsAllowsExplicitHttpsFrontendOrigin() {
        List<String> failures = new ArrayList<>();

        ProductionConfigurationValidator.validateProductionCors(
                failures,
                "https://real-domain.com"
        );

        assertThat(failures).isEmpty();
    }

    @Test
    void developmentCorsAllowsExplicitLocalhostOrigins() {
        WebConfig webConfig = new WebConfig(new MockEnvironment()
                .withProperty(
                        "application.cors.allowed-origin-patterns",
                        "http://localhost:3000,http://localhost:5173,http://127.0.0.1:3000"
                ));

        assertThat(webConfig.corsConfigurationSource()
                .getCorsConfiguration(new MockHttpServletRequest("GET", "/api/test"))
                .getAllowedOrigins())
                .containsExactly(
                        "http://localhost:3000",
                        "http://localhost:5173",
                        "http://127.0.0.1:3000"
                );
    }

}
