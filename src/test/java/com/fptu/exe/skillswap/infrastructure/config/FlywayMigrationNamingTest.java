package com.fptu.exe.skillswap.infrastructure.config;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class FlywayMigrationNamingTest {

    private static final Path MIGRATION_DIR = Path.of("src/main/resources/db/migration");
    private static final Path MAIN_JAVA_DIR = Path.of("src/main/java");
    private static final Pattern VERSIONED_SQL = Pattern.compile("^V(\\d+)__.+\\.sql$");
    private static final Pattern ENTITY_TABLE = Pattern.compile("@Table\\s*\\(\\s*name\\s*=\\s*\"([^\"]+)\"");

    @Test
    void migrationVersions_shouldBeUniqueAndWellFormed() throws IOException {
        List<String> filenames;
        try (var stream = Files.list(MIGRATION_DIR)) {
            filenames = stream
                    .filter(Files::isRegularFile)
                    .map(path -> path.getFileName().toString())
                    .filter(name -> name.endsWith(".sql"))
                    .sorted()
                    .toList();
        }

        List<String> malformed = filenames.stream()
                .filter(name -> !VERSIONED_SQL.matcher(name).matches())
                .toList();

        assertTrue(malformed.isEmpty(), () -> "Malformed Flyway migration filenames: " + malformed);

        Map<String, List<String>> byVersion = filenames.stream()
                .collect(Collectors.groupingBy(FlywayMigrationNamingTest::extractVersion));

        Map<String, List<String>> duplicates = byVersion.entrySet().stream()
                .filter(entry -> entry.getValue().size() > 1)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        assertTrue(duplicates.isEmpty(), () -> "Duplicate Flyway migration versions: " + duplicates);
    }

    @Test
    void entityTables_shouldHaveCreateTableMigration() throws IOException {
        String migrationSql = readAllMigrationSql().toLowerCase();
        List<String> entityTables = findEntityTables();

        List<String> missingTables = entityTables.stream()
                .filter(table -> !hasCreateTableStatement(migrationSql, table))
                .toList();

        assertTrue(missingTables.isEmpty(), () -> "Entity tables missing CREATE TABLE migration: " + missingTables);
    }

    @Test
    void courseEnrollment_completedAtMapping_shouldHaveMigration() throws IOException {
        String migrationSql = readAllMigrationSql().toLowerCase();

        assertTrue(
                migrationSql.contains("alter table course_enrollments")
                        && migrationSql.contains("completed_at timestamp(6) with time zone"),
                "CourseEnrollment.completedAt requires a completed_at migration on course_enrollments");
    }

    @Test
    void courseSummaryMappings_shouldHaveMigration() throws IOException {
        String migrationSql = readAllMigrationSql().toLowerCase();

        assertTrue(migrationSql.contains("alter table courses"), "Course summary fields require an ALTER TABLE courses migration");
        for (String column : List.of(
                "total_chapters int not null default 0",
                "total_lectures int not null default 0",
                "total_duration_seconds int not null default 0",
                "average_rating numeric(3, 2) not null default 0.00",
                "review_count int not null default 0",
                "enrolled_count int not null default 0")) {
            assertTrue(migrationSql.contains(column), () -> "Course summary migration is missing " + column);
        }
    }

    @Test
    void paymentOrderTargetMappings_shouldHaveMigration() throws IOException {
        String migrationSql = readAllMigrationSql().toLowerCase();

        assertTrue(
                migrationSql.contains("alter table payment_orders")
                        && migrationSql.contains("target_type varchar(30)")
                        && migrationSql.contains("target_id uuid"),
                "PaymentOrder typed target mappings require target_type and target_id on payment_orders");
        assertTrue(
                migrationSql.contains("alter column booking_id drop not null"),
                "Typed payment targets require the legacy booking_id column to be nullable");
        assertTrue(
                migrationSql.contains("on payment_orders(target_type, target_id)"),
                "PaymentOrder typed target lookup requires its unique target index");
    }

    private static String extractVersion(String filename) {
        Matcher matcher = VERSIONED_SQL.matcher(filename);
        if (!matcher.matches()) {
            return filename;
        }
        return matcher.group(1);
    }

    private static String readAllMigrationSql() throws IOException {
        StringBuilder builder = new StringBuilder();
        try (var stream = Files.list(MIGRATION_DIR)) {
            for (Path path : stream.filter(Files::isRegularFile).filter(path -> path.toString().endsWith(".sql")).toList()) {
                builder.append(Files.readString(path)).append('\n');
            }
        }
        return builder.toString();
    }

    private static List<String> findEntityTables() throws IOException {
        List<String> tables = new ArrayList<>();
        try (var stream = Files.walk(MAIN_JAVA_DIR)) {
            for (Path path : stream.filter(Files::isRegularFile).filter(path -> path.toString().endsWith(".java")).toList()) {
                Matcher matcher = ENTITY_TABLE.matcher(Files.readString(path));
                while (matcher.find()) {
                    tables.add(matcher.group(1));
                }
            }
        }
        return tables.stream().distinct().sorted().toList();
    }

    private static boolean hasCreateTableStatement(String migrationSql, String tableName) {
        String plain = "create table " + tableName.toLowerCase();
        String conditional = "create table if not exists " + tableName.toLowerCase();
        String quoted = "create table \"" + tableName.toLowerCase() + "\"";
        String quotedConditional = "create table if not exists \"" + tableName.toLowerCase() + "\"";
        return migrationSql.contains(plain)
                || migrationSql.contains(conditional)
                || migrationSql.contains(quoted)
                || migrationSql.contains(quotedConditional);
    }
}
