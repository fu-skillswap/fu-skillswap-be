package com.fptu.exe.skillswap.infrastructure.config;

import com.fptu.exe.skillswap.infrastructure.testcontainer.AbstractPostgreSQLIntegrationTest;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Runs the production migration chain against PostgreSQL, then lets Hibernate validate every mapped table.
 * This prevents a test-only H2 schema from hiding a production Flyway drift.
 */
@ActiveProfiles("test")
@SpringBootTest(properties = {
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.test.database.replace=none"
})
class FlywayPostgresSchemaValidationTest extends AbstractPostgreSQLIntegrationTest {

    @Autowired
    private DataSource dataSource;

    /** Keep this on the concrete test: the schema gate must never fall back to H2. */
    @DynamicPropertySource
    static void forcePostgresDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", postgres::getDriverClassName);
    }

    @Test
    void flywaySchema_shouldMatchAllJpaMappings() throws SQLException {
        // ApplicationContext startup performs Flyway migration followed by Hibernate schema validation.
        try (var connection = dataSource.getConnection()) {
            org.junit.jupiter.api.Assertions.assertEquals(
                    "PostgreSQL",
                    connection.getMetaData().getDatabaseProductName(),
                    "Schema validation must run against the Testcontainers PostgreSQL database");
        }
    }
}
