package com.fptu.exe.skillswap.infrastructure.testcontainer;

import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@ExtendWith(AbstractPostgreSQLIntegrationTest.DockerAvailableCondition.class)
@Testcontainers
public abstract class AbstractPostgreSQLIntegrationTest {

    @Container
    protected static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("skillswap_test")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void setPostgresProperties(DynamicPropertyRegistry registry) {
        if (isDockerAvailable()) {
            registry.add("spring.datasource.url", postgres::getJdbcUrl);
            registry.add("spring.datasource.username", postgres::getUsername);
            registry.add("spring.datasource.password", postgres::getPassword);
            registry.add("spring.datasource.driver-class-name", postgres::getDriverClassName);
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
            if (isDockerAvailable()) {
                return ConditionEvaluationResult.enabled("Docker environment is available for Testcontainers");
            }
            if ("true".equalsIgnoreCase(System.getenv("CI"))) {
                return ConditionEvaluationResult.enabled(
                        "CI must fail clearly when its PostgreSQL Testcontainers prerequisite is unavailable");
            }
            return ConditionEvaluationResult.disabled("Docker environment is not available; skipping Testcontainers integration test");
        }
    }
}
