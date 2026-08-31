package com.fptu.exe.skillswap.architecture;

import com.fptu.exe.skillswap.ProjectApplication;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.core.DependencyType;
import org.springframework.modulith.core.Violations;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Required authoritative architectural verification and violation edge audit generator. */
public class ModulithArchitectureAuditTest {

    /** Only this line represents a non-exposed Modulith edge. Cycle details are not edges. */
    private static final Pattern NON_EXPOSED_PATTERN = Pattern.compile(
            "^- Module '([^']+)' depends on non-exposed type ([^ ]+) within module '([^']+)'!$"
    );

    private static final Pattern SOURCE_LOCATION_PATTERN = Pattern.compile(
            ".*\\(([^():]+):(\\d+)\\)$"
    );

    @Test
    void verifiesAllDeclaredModuleBoundaries() {
        ApplicationModules modules = ApplicationModules.of(ProjectApplication.class);

        System.out.println("=== Spring Modulith module inventory & dependency graph ===");
        modules.forEach(module -> {
            System.out.printf("module=%s, basePackage=%s%n", module.getName(), module.getBasePackage().getName());
            System.out.printf("  namedInterfaces=%s%n", module.getNamedInterfaces());
            System.out.printf("  dependencies=%s%n", module.getDependencies(modules, DependencyType.values()));
        });

        Violations violations = modules.detectViolations();
        exportAuditEdges(violations);

        System.out.println("=== Spring Modulith verification ===");
        modules.verify();
    }

    static void exportAuditEdges(Violations violations) {
        Path targetDir = Paths.get("target");
        Path csvPath = targetDir.resolve("modulith-audit-edges.csv");
        Set<AuditEdge> edges = parseNonExposedEdges(violations == null ? List.of() : violations.getMessages());

        try {
            Files.createDirectories(targetDir);
            try (BufferedWriter writer = Files.newBufferedWriter(csvPath)) {
                writer.write("consumer,owner,type,source_file,source_line");
                writer.newLine();
                for (AuditEdge edge : edges) {
                    writer.write(String.format("%s,%s,%s,%s,%s",
                            escapeCsv(edge.consumer()),
                            escapeCsv(edge.owner()),
                            escapeCsv(edge.targetType()),
                            escapeCsv(edge.sourceFile()),
                            escapeCsv(edge.sourceLine())));
                    writer.newLine();
                }
            }
            System.out.printf("Exported %d distinct non-exposed edges to %s%n",
                    edges.size(), csvPath.toAbsolutePath());
        } catch (IOException e) {
            System.err.println("Failed to export modulith-audit-edges.csv: " + e.getMessage());
        }
    }

    /**
     * Parses only Modulith's explicit non-exposed-type lines. In particular, it ignores dependency
     * details printed below "Cycle detected"; those describe a cycle and must not inflate the
     * non-exposed-type inventory. A module-level violation has no source line, so the CSV records
     * the explicit module-boundary location instead of inventing a Java source line.
     */
    static Set<AuditEdge> parseNonExposedEdges(List<String> messages) {
        Comparator<AuditEdge> comparator = Comparator.comparing(AuditEdge::consumer)
                .thenComparing(AuditEdge::owner)
                .thenComparing(AuditEdge::targetType)
                .thenComparing(AuditEdge::sourceFile)
                .thenComparing(AuditEdge::sourceLine);
        Set<AuditEdge> edges = new TreeSet<>(comparator);

        for (String message : messages) {
            String[] lines = message.split("\\r?\\n");
            for (String rawLine : lines) {
                String line = rawLine.trim();
                Matcher matcher = NON_EXPOSED_PATTERN.matcher(line);
                if (!matcher.matches()) {
                    continue;
                }
                String targetType = matcher.group(2);
                String sourceLocation = findSourceLocation(lines, targetType);
                Matcher location = SOURCE_LOCATION_PATTERN.matcher(sourceLocation);
                String sourceFile = location.matches() ? location.group(1) : "module-boundary";
                String sourceLine = location.matches() ? location.group(2) : "0";
                edges.add(new AuditEdge(matcher.group(1), matcher.group(3), targetType, sourceFile, sourceLine));
            }
        }
        return edges;
    }

    private static String findSourceLocation(String[] lines, String targetType) {
        String targetPrefix = "<" + targetType + ".";
        for (String rawLine : lines) {
            String line = rawLine.trim();
            if (line.contains(targetPrefix)) {
                Matcher location = SOURCE_LOCATION_PATTERN.matcher(line);
                if (location.matches()) {
                    return location.group(0);
                }
            }
        }
        return "";
    }

    private static String escapeCsv(String value) {
        if (value == null) {
            return "";
        }
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    record AuditEdge(String consumer, String owner, String targetType, String sourceFile, String sourceLine) {}
}
