package com.fptu.exe.skillswap.infrastructure.config;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class FlywayMigrationNamingTest {

    private static final Path MIGRATION_DIR = Path.of("src/main/resources/db/migration");
    private static final Path MAIN_JAVA_DIR = Path.of("src/main/java");
    private static final Path APPROVED_CONTRACT_MIGRATIONS = Path.of("scripts/approved-contract-migrations.txt");
    private static final int ROLLOUT_POLICY_MIN_VERSION = 69;
    private static final Pattern VERSIONED_SQL = Pattern.compile("^V(\\d+)__.+\\.sql$");
    private static final Pattern ENTITY_TABLE = Pattern.compile("@Table\\s*\\(\\s*name\\s*=\\s*\"([^\"]+)\"");
    private static final Pattern ROLLOUT_POLICY = Pattern.compile(
            "^\\s*--\\s*rollout:\\s*(EXPAND|CONTRACT)\\s*$",
            Pattern.CASE_INSENSITIVE
    );

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
    void governedMigrations_shouldDeclareAnApprovedRolloutPolicy() throws IOException {
        Set<String> approvedContracts = readApprovedContractMigrations();
        Set<String> declaredContracts = new HashSet<>();
        List<String> violations = new ArrayList<>();

        try (var stream = Files.list(MIGRATION_DIR)) {
            for (Path migration : stream.filter(Files::isRegularFile)
                    .filter(path -> VERSIONED_SQL.matcher(path.getFileName().toString()).matches())
                    .toList()) {
                String filename = migration.getFileName().toString();
                int version = Integer.parseInt(extractVersion(filename));
                if (version < ROLLOUT_POLICY_MIN_VERSION) {
                    continue;
                }

                String policy = readRolloutPolicy(migration);
                if (policy == null) {
                    violations.add(filename + " is missing EXPAND/CONTRACT in its first eight lines");
                } else if ("CONTRACT".equals(policy)) {
                    declaredContracts.add(filename);
                    if (!approvedContracts.contains(filename)) {
                        violations.add(filename + " is CONTRACT but is not approved");
                    }
                }
            }
        }

        Set<String> staleApprovals = new HashSet<>(approvedContracts);
        staleApprovals.removeAll(declaredContracts);
        if (!staleApprovals.isEmpty()) {
            violations.add("Approved CONTRACT entries without matching CONTRACT migrations: " + staleApprovals);
        }

        assertTrue(violations.isEmpty(), () -> "Migration rollout policy violations: " + violations);
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

    @Test
    void utcRolloutMigrations_shouldBeExpandOnlyAndReleaseVisible() throws IOException {
        List<String> expected = List.of(
                "V111__expand_utc_columns_booking_slot.sql",
                "V112__expand_utc_columns_payment.sql",
                "V113__expand_utc_columns_session_reschedule.sql",
                "V114__expand_utc_columns_google_calendar.sql"
        );
        for (String filename : expected) {
            Path migration = MIGRATION_DIR.resolve(filename);
            assertTrue(Files.isRegularFile(migration), () -> "Missing UTC rollout migration: " + filename);
            String sql = Files.readString(migration).toLowerCase();
            assertTrue(sql.contains("timestamptz"), () -> filename + " must add UTC timestamptz columns");
            assertFalse(sql.contains("drop column"), () -> filename + " must remain expand-only");
        }

        // The destructive contract migration is deliberately outside Flyway's version sequence
        // until the dual-write rollout is proven. Deferred work must not reserve a V-number:
        // otherwise a later normal migration would make the contract impossible to apply in order.
        assertTrue(Files.isRegularFile(Path.of(
                "src/main/resources/db/deferred-migrations/contract__drop_legacy_timestamp_columns_and_triggers.sql.disabled")));
    }

    @Test
    void utcRolloutMigrations_shouldCoverCoreLifecycleTables() throws IOException {
        String sql = readAllMigrationSql().toLowerCase();
        for (String table : List.of(
                "bookings", "mentor_availability_slots", "payment_orders", "payment_attempts",
                "sessions", "booking_events", "google_calendar_connections", "google_calendar_sync_jobs")) {
            assertTrue(sql.contains("alter table " + table), () -> "UTC rollout must alter table " + table);
        }
        for (String column : List.of(
                "selected_start_time_utc", "pending_expire_at_utc", "expires_at_utc",
                "scheduled_start_time_utc", "created_at_utc", "run_after_utc")) {
            assertTrue(sql.contains(column), () -> "UTC rollout is missing " + column);
        }
    }

    private static String extractVersion(String filename) {
        Matcher matcher = VERSIONED_SQL.matcher(filename);
        if (!matcher.matches()) {
            return filename;
        }
        return matcher.group(1);
    }

    private static String readRolloutPolicy(Path migration) throws IOException {
        List<String> lines = Files.readAllLines(migration);
        for (int index = 0; index < Math.min(8, lines.size()); index++) {
            Matcher matcher = ROLLOUT_POLICY.matcher(lines.get(index));
            if (matcher.matches()) {
                return matcher.group(1).toUpperCase();
            }
        }
        return null;
    }

    private static Set<String> readApprovedContractMigrations() throws IOException {
        if (!Files.isRegularFile(APPROVED_CONTRACT_MIGRATIONS)) {
            return Set.of();
        }
        return Files.readAllLines(APPROVED_CONTRACT_MIGRATIONS).stream()
                .map(String::trim)
                .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                .collect(Collectors.toSet());
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
