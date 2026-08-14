package com.fptu.exe.skillswap.infrastructure.config;

import com.fptu.exe.skillswap.infrastructure.testcontainer.AbstractPostgreSQLIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Runs the production migration chain against PostgreSQL, then lets Hibernate validate every mapped table.
 * This prevents a test-only H2 schema from hiding a production Flyway drift.
 */
@ActiveProfiles("test")
@SpringBootTest(properties = {
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate"
})
class FlywayPostgresSchemaValidationTest extends AbstractPostgreSQLIntegrationTest {

    @Test
    void flywaySchema_shouldMatchAllJpaMappings() {
        // ApplicationContext startup performs Flyway migration followed by Hibernate schema validation.
    }
}
