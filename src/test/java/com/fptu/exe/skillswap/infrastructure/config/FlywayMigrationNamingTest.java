package com.fptu.exe.skillswap.infrastructure.config;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class FlywayMigrationNamingTest {

    private static final Path MIGRATION_DIR = Path.of("src/main/resources/db/migration");
    private static final Pattern VERSIONED_SQL = Pattern.compile("^V(\\d+)__.+\\.sql$");

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

    private static String extractVersion(String filename) {
        Matcher matcher = VERSIONED_SQL.matcher(filename);
        if (!matcher.matches()) {
            return filename;
        }
        return matcher.group(1);
    }
}
