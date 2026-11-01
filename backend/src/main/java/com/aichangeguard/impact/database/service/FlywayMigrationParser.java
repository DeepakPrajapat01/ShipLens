package com.aichangeguard.impact.database.service;

import com.aichangeguard.impact.database.model.FlywayMigrationFile;
import com.aichangeguard.impact.database.model.SqlStatementInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Discovers and parses Flyway migration scripts.
 *
 * <p>Supports standard Flyway naming conventions:
 * <ul>
 *   <li>Versioned: {@code V<Version>__<Description>.sql}</li>
 *   <li>Undo: {@code U<Version>__<Description>.sql}</li>
 *   <li>Repeatable: {@code R__<Description>.sql}</li>
 * </ul>
 */
@Service
public class FlywayMigrationParser {

    private static final Logger log = LoggerFactory.getLogger(FlywayMigrationParser.class);

    // V<version>__<description>.sql
    private static final Pattern VERSIONED_PATTERN = Pattern.compile("^V([0-9]+(?:[._][0-9]+)*)__(.+)\\.sql$", Pattern.CASE_INSENSITIVE);
    private static final Pattern UNDO_PATTERN = Pattern.compile("^U([0-9]+(?:[._][0-9]+)*)__(.+)\\.sql$", Pattern.CASE_INSENSITIVE);
    private static final Pattern REPEATABLE_PATTERN = Pattern.compile("^R__(.+)\\.sql$", Pattern.CASE_INSENSITIVE);

    private final SqlStatementParser statementParser;

    public FlywayMigrationParser(SqlStatementParser statementParser) {
        this.statementParser = statementParser;
    }

    /**
     * Parses migration content directly (useful in test fixtures or Git blob diffs).
     */
    public FlywayMigrationFile parseContent(String relativePath, String sqlContent) {
        String fileName = extractFileName(relativePath);
        Matcher vm = VERSIONED_PATTERN.matcher(fileName);
        Matcher um = UNDO_PATTERN.matcher(fileName);
        Matcher rm = REPEATABLE_PATTERN.matcher(fileName);

        String version = null;
        String description = null;
        String migrationType = "UNKNOWN";
        boolean isValid = false;
        String error = null;

        if (vm.matches()) {
            version = vm.group(1).replace('_', '.');
            description = vm.group(2).replace('_', ' ');
            migrationType = "VERSIONED";
            isValid = true;
        } else if (um.matches()) {
            version = um.group(1).replace('_', '.');
            description = um.group(2).replace('_', ' ');
            migrationType = "UNDO";
            isValid = true;
        } else if (rm.matches()) {
            description = rm.group(1).replace('_', ' ');
            migrationType = "REPEATABLE";
            isValid = true;
        } else {
            error = "File '" + fileName + "' does not match Flyway naming convention (e.g. V1__init.sql)";
            migrationType = "INVALID_FORMAT";
        }

        List<SqlStatementInfo> statements = isValid ? statementParser.parseScript(sqlContent) : Collections.emptyList();

        return new FlywayMigrationFile(
                relativePath,
                fileName,
                version,
                description,
                migrationType,
                statements,
                isValid,
                error,
                statements.size()
        );
    }

    /**
     * Discovers all Flyway migration files located within the given repository directory.
     */
    public List<FlywayMigrationFile> discoverInRepository(String repoRoot) {
        if (repoRoot == null || repoRoot.isBlank()) {
            return Collections.emptyList();
        }

        Path rootPath = Paths.get(repoRoot);
        if (!Files.exists(rootPath) || !Files.isDirectory(rootPath)) {
            return Collections.emptyList();
        }

        List<FlywayMigrationFile> migrations = new ArrayList<>();
        List<Path> candidateDirs = List.of(
                rootPath.resolve("src/main/resources/db/migration"),
                rootPath.resolve("src/test/resources/db/migration"),
                rootPath.resolve("db/migration")
        );

        for (Path dir : candidateDirs) {
            if (Files.exists(dir) && Files.isDirectory(dir)) {
                try (Stream<Path> stream = Files.walk(dir)) {
                    stream.filter(Files::isRegularFile)
                            .filter(p -> p.getFileName().toString().toLowerCase().endsWith(".sql"))
                            .forEach(p -> {
                                try {
                                    String relative = rootPath.relativize(p).toString().replace('\\', '/');
                                    String content = Files.readString(p);
                                    migrations.add(parseContent(relative, content));
                                } catch (IOException e) {
                                    log.warn("Failed to read migration file '{}': {}", p, e.getMessage());
                                }
                            });
                } catch (IOException e) {
                    log.warn("Error walking directory '{}': {}", dir, e.getMessage());
                }
            }
        }

        // Sort versioned migrations by version
        migrations.sort(Comparator.comparing(FlywayMigrationFile::fileName));
        log.info("Discovered {} Flyway migration files in repository '{}'", migrations.size(), repoRoot);
        return migrations;
    }

    private String extractFileName(String path) {
        if (path == null) return "";
        int idx = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
        return idx >= 0 ? path.substring(idx + 1) : path;
    }
}

