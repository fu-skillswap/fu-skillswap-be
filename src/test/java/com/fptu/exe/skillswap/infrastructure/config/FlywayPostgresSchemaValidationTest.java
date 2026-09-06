package com.fptu.exe.skillswap.infrastructure.config;

import com.fptu.exe.skillswap.infrastructure.testcontainer.AbstractPostgreSQLIntegrationTest;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
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
        registry.add("spring.datasource.url", AbstractPostgreSQLIntegrationTest::getPostgresJdbcUrl);
        registry.add("spring.datasource.username", AbstractPostgreSQLIntegrationTest::getPostgresUsername);
        registry.add("spring.datasource.password", AbstractPostgreSQLIntegrationTest::getPostgresPassword);
        registry.add("spring.datasource.driver-class-name", AbstractPostgreSQLIntegrationTest::getPostgresDriverClassName);
    }

    @Test
    void flywaySchema_shouldMatchAllJpaMappings() throws SQLException {
        // ApplicationContext startup performs Flyway migration followed by Hibernate schema validation.
        try (var connection = dataSource.getConnection()) {
            org.junit.jupiter.api.Assertions.assertEquals(
                    "PostgreSQL",
                    connection.getMetaData().getDatabaseProductName(),
                    "Schema validation must run against a PostgreSQL database");
        }
    }

    @Test
    void v124VerificationMetadata_shouldBePresentOnMigratedSchema() throws SQLException {
        try (var connection = dataSource.getConnection();
             var statement = connection.createStatement();
             var result = statement.executeQuery("select column_name from information_schema.columns "
                     + "where table_schema = current_schema() and table_name = 'mentor_verification_documents'")) {
            Set<String> columns = new java.util.HashSet<>();
            while (result.next()) {
                columns.add(result.getString(1));
            }
            org.junit.jupiter.api.Assertions.assertTrue(columns.containsAll(Set.of(
                    "original_filename", "content_type", "size_bytes", "file_url")),
                    "V124 verification metadata columns are missing: " + columns);
        }
    }

    @Test
    void v135ForumActionLogColumns_shouldRemainNullableDuringRollingDeployment() throws SQLException {
        try (var connection = dataSource.getConnection();
             var statement = connection.createStatement();
             var result = statement.executeQuery("select column_name, is_nullable from information_schema.columns "
                     + "where table_schema = current_schema() and table_name = 'forum_action_logs' "
                     + "and column_name in ('target_type', 'metadata')")) {
            Map<String, String> nullability = new HashMap<>();
            while (result.next()) {
                nullability.put(result.getString("column_name"), result.getString("is_nullable"));
            }
            org.junit.jupiter.api.Assertions.assertEquals(
                    Map.of("target_type", "YES", "metadata", "YES"),
                    nullability,
                    "V135 must keep new audit columns nullable until the later contract deployment");
        }
    }
}
