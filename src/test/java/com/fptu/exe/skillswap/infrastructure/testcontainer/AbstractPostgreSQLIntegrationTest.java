package com.fptu.exe.skillswap.infrastructure.testcontainer;

import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;

@ExtendWith(AbstractPostgreSQLIntegrationTest.DockerAvailableCondition.class)
public abstract class AbstractPostgreSQLIntegrationTest {

    private static final String EXTERNAL_DB_URL = System.getenv("TEST_DATASOURCE_URL");
    private static final String EXTERNAL_DB_USER = System.getenv().getOrDefault("TEST_DATASOURCE_USERNAME", "test");
    private static final String EXTERNAL_DB_PASSWORD = System.getenv().getOrDefault("TEST_DATASOURCE_PASSWORD", "test");

    protected static final PostgreSQLContainer<?> postgres;

    static {
        if (isExternalDbConfigured()) {
            postgres = null;
        } else if (isDockerAvailable()) {
            PostgreSQLContainer<?> container = new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("skillswap_test")
                    .withUsername("test")
                    .withPassword("test");
            container.start();
            postgres = container;
        } else {
            postgres = null;
        }
    }

    public static boolean isExternalDbConfigured() {
        return EXTERNAL_DB_URL != null && !EXTERNAL_DB_URL.isBlank();
    }

    public static boolean isPostgresAvailable() {
        return isExternalDbConfigured() || (postgres != null && postgres.isRunning());
    }

    public static String getPostgresJdbcUrl() {
        if (isExternalDbConfigured()) {
            return EXTERNAL_DB_URL;
        }
        requirePostgresAvailable();
        return postgres.getJdbcUrl();
    }

    public static String getPostgresUsername() {
        if (isExternalDbConfigured()) {
            return EXTERNAL_DB_USER;
        }
        requirePostgresAvailable();
        return postgres.getUsername();
    }

    public static String getPostgresPassword() {
        if (isExternalDbConfigured()) {
            return EXTERNAL_DB_PASSWORD;
        }
        requirePostgresAvailable();
        return postgres.getPassword();
    }

    public static String getPostgresDriverClassName() {
        return "org.postgresql.Driver";
    }

    @DynamicPropertySource
    public static void setPostgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", AbstractPostgreSQLIntegrationTest::getPostgresJdbcUrl);
        registry.add("spring.datasource.username", AbstractPostgreSQLIntegrationTest::getPostgresUsername);
        registry.add("spring.datasource.password", AbstractPostgreSQLIntegrationTest::getPostgresPassword);
        registry.add("spring.datasource.driver-class-name", AbstractPostgreSQLIntegrationTest::getPostgresDriverClassName);
    }

    private static void requirePostgresAvailable() {
        if (!isPostgresAvailable()) {
            throw new IllegalStateException(
                    "PostgreSQL integration tests require TEST_DATASOURCE_URL or a running Docker environment");
        }
    }

    private static boolean isDockerAvailable() {
        try {
            return DockerClientFactory.instance().isDockerAvailable();
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static class DockerAvailableCondition implements ExecutionCondition {
        @Override
        public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext context) {
            if (isPostgresAvailable()) {
                return ConditionEvaluationResult.enabled(isExternalDbConfigured()
                        ? "External PostgreSQL datasource configured"
                        : "Docker environment is available for Testcontainers");
            }
            if ("true".equalsIgnoreCase(System.getenv("CI"))) {
                return ConditionEvaluationResult.enabled(
                        "CI must fail clearly when its PostgreSQL prerequisite is unavailable");
            }
            return ConditionEvaluationResult.disabled(
                    "Neither external PostgreSQL nor Docker is available; skipping PostgreSQL integration test");
        }
    }
}
