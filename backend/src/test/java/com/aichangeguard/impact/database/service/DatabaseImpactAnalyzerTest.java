package com.aichangeguard.impact.database.service;

import com.aichangeguard.analysis.model.AnnotationMetadata;
import com.aichangeguard.analysis.model.FieldMetadata;
import com.aichangeguard.analysis.model.ParsedFileMetadata;
import com.aichangeguard.analysis.model.SourceAnalysisReport;
import com.aichangeguard.analysis.model.SourceRange;
import com.aichangeguard.analysis.model.TypeKind;
import com.aichangeguard.analysis.model.TypeMetadata;
import com.aichangeguard.impact.database.model.DatabaseImpactReport;
import com.aichangeguard.impact.database.model.FlywayMigrationFile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class DatabaseImpactAnalyzerTest {

    private DatabaseImpactAnalyzer analyzer;
    private FlywayMigrationParser migrationParser;

    @BeforeEach
    void setUp() {
        SqlStatementParser sqlParser = new SqlStatementParser();
        migrationParser = new FlywayMigrationParser(sqlParser);
        JpaEntityExtractor entityExtractor = new JpaEntityExtractor();
        SpringDataRepositoryExtractor repoExtractor = new SpringDataRepositoryExtractor();
        DatabaseComparator comparator = new DatabaseComparator();

        analyzer = new DatabaseImpactAnalyzer(entityExtractor, repoExtractor, migrationParser, comparator);
    }

    private SourceAnalysisReport createReportWithEntity(String entityName, String tableName, String fieldName, String fieldType, boolean isNullable) {
        AnnotationMetadata colAnno = new AnnotationMetadata("Column", Map.of("nullable", String.valueOf(isNullable)), SourceRange.UNKNOWN);
        FieldMetadata idField = new FieldMetadata("id", "id", "Long", Set.of("private"), List.of(new AnnotationMetadata("Id", Collections.emptyMap(), SourceRange.UNKNOWN)), SourceRange.UNKNOWN);
        FieldMetadata dataField = new FieldMetadata(fieldName, fieldName, fieldType, Set.of("private"), List.of(colAnno), SourceRange.UNKNOWN);

        TypeMetadata type = new TypeMetadata(
                "com.example." + entityName, "com.example." + entityName, entityName,
                TypeKind.CLASS, Set.of("public"),
                List.of(new AnnotationMetadata("Entity", Collections.emptyMap(), SourceRange.UNKNOWN),
                        new AnnotationMetadata("Table", Map.of("name", "\"" + tableName + "\""), SourceRange.UNKNOWN)),
                Collections.emptyList(), Collections.emptyList(),
                List.of(idField, dataField), Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
                SourceRange.UNKNOWN
        );

        ParsedFileMetadata file = new ParsedFileMetadata(
                "src/main/java/com/example/" + entityName + ".java", "com.example",
                Collections.emptyList(), List.of(type), Collections.emptyList(), true, SourceRange.UNKNOWN
        );

        return new SourceAnalysisReport("/repo", 1, 1, 0, 100.0, List.of(file), Collections.emptyList(), Instant.now(), Collections.emptyList());
    }

    @Test
    @DisplayName("Should run complete database impact analysis across entities and migrations")
    void shouldRunCompleteAnalysis() {
        SourceAnalysisReport baseReport = createReportWithEntity("User", "users", "email", "String", true);
        SourceAnalysisReport targetReport = createReportWithEntity("User", "users", "email", "String", false);

        FlywayMigrationFile migration = migrationParser.parseContent(
                "db/migration/V2__add_phone_and_drop_audit.sql",
                "ALTER TABLE users ADD COLUMN phone VARCHAR(20) NOT NULL;\n" +
                "DROP TABLE audit_history;\n" +
                "DO $$ BEGIN PERFORM 1; END $$;"
        );

        DatabaseImpactReport report = analyzer.analyze(
                "/test/repo", "sha-base", "sha-target",
                baseReport, targetReport,
                Collections.emptyList(), List.of(migration)
        );

        assertThat(report).isNotNull();
        assertThat(report.repositoryPath()).isEqualTo("/test/repo");
        assertThat(report.baseRevision()).isEqualTo("sha-base");
        assertThat(report.targetRevision()).isEqualTo("sha-target");
        assertThat(report.isComplete()).isTrue();

        // Findings:
        // 1. Entity: email became non-nullable (POTENTIALLY_BREAKING)
        // 2. Migration: New migration file (INFORMATIONAL)
        // 3. Migration: ADD COLUMN phone NOT NULL without DEFAULT (POTENTIALLY_BREAKING)
        // 4. Migration: DROP TABLE audit_history (DESTRUCTIVE)
        // 5. Migration: DO $$ block (UNKNOWN)

        assertThat(report.destructiveCount()).isGreaterThanOrEqualTo(1);
        assertThat(report.potentiallyBreakingCount()).isGreaterThanOrEqualTo(2);
        assertThat(report.unknownCount()).isGreaterThanOrEqualTo(1);

        // Mandatory limitations
        assertThat(report.limitations()).isNotEmpty();
        assertThat(report.limitations().size()).isGreaterThanOrEqualTo(5);
        assertThat(report.limitations()).anyMatch(l -> l.contains("heuristic"));
        assertThat(report.limitations()).anyMatch(l -> l.contains("completeness"));
    }

    @Test
    @DisplayName("Should handle null and empty reports gracefully")
    void shouldHandleNullReportsGracefully() {
        DatabaseImpactReport report = analyzer.analyze("/repo", "base", "target", null, null, null, null);

        assertThat(report).isNotNull();
        assertThat(report.findings()).isEmpty();
        assertThat(report.destructiveCount()).isZero();
        assertThat(report.isComplete()).isTrue();
        assertThat(report.limitations()).isNotEmpty();
    }
}

