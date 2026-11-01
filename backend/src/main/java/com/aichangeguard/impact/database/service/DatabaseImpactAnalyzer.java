package com.aichangeguard.impact.database.service;

import com.aichangeguard.analysis.model.SourceAnalysisReport;
import com.aichangeguard.impact.database.model.DatabaseChangeFinding;
import com.aichangeguard.impact.database.model.DatabaseChangeSeverity;
import com.aichangeguard.impact.database.model.DatabaseImpactReport;
import com.aichangeguard.impact.database.model.FlywayMigrationFile;
import com.aichangeguard.impact.database.model.JpaEntity;
import com.aichangeguard.impact.database.model.SpringDataRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Orchestrating service for database impact analysis between revisions.
 *
 * <p>Integrates JPA entity extraction, Spring Data repository inspection,
 * and Flyway SQL migration analysis into an evidence-backed {@link DatabaseImpactReport}.</p>
 *
 * <h3>Engineering Principles</h3>
 * <ul>
 *   <li>Never executes SQL migrations or connects to databases.</li>
 *   <li>Distinguishes migration-based deterministic facts from entity-inferred expectations.</li>
 *   <li>Explicitly marks unsupported SQL as UNKNOWN.</li>
 *   <li>Mandatory limitations are included on every report to prevent false safety claims.</li>
 * </ul>
 */
@Service
public class DatabaseImpactAnalyzer {

    private static final Logger log = LoggerFactory.getLogger(DatabaseImpactAnalyzer.class);

    private final JpaEntityExtractor entityExtractor;
    private final SpringDataRepositoryExtractor repositoryExtractor;
    private final FlywayMigrationParser migrationParser;
    private final DatabaseComparator comparator;

    public DatabaseImpactAnalyzer(JpaEntityExtractor entityExtractor,
                                  SpringDataRepositoryExtractor repositoryExtractor,
                                  FlywayMigrationParser migrationParser,
                                  DatabaseComparator comparator) {
        this.entityExtractor = entityExtractor;
        this.repositoryExtractor = repositoryExtractor;
        this.migrationParser = migrationParser;
        this.comparator = comparator;
    }

    /**
     * Analyzes database impact by auto-discovering Flyway migrations from the repository path.
     */
    public DatabaseImpactReport analyze(String repositoryPath,
                                        String baseRevision,
                                        String targetRevision,
                                        SourceAnalysisReport baseReport,
                                        SourceAnalysisReport targetReport) {
        List<FlywayMigrationFile> migrations = repositoryPath != null
                ? migrationParser.discoverInRepository(repositoryPath)
                : Collections.emptyList();
        // With local repo scan, target migrations are the discovered files; base migrations empty
        return analyze(repositoryPath, baseRevision, targetRevision, baseReport, targetReport, Collections.emptyList(), migrations);
    }

    /**
     * Analyzes database impact with explicit base and target migration files.
     */
    public DatabaseImpactReport analyze(String repositoryPath,
                                        String baseRevision,
                                        String targetRevision,
                                        SourceAnalysisReport baseReport,
                                        SourceAnalysisReport targetReport,
                                        List<FlywayMigrationFile> baseMigrations,
                                        List<FlywayMigrationFile> targetMigrations) {
        log.info("Starting database impact analysis for '{}': {} -> {}", repositoryPath, baseRevision, targetRevision);

        List<String> warnings = new ArrayList<>();

        // 1. Extract JPA Entities
        List<JpaEntity> baseEntities = Collections.emptyList();
        List<JpaEntity> targetEntities = Collections.emptyList();
        try {
            baseEntities = entityExtractor.extractEntities(baseReport);
        } catch (Exception e) {
            log.error("Failed to extract JPA entities from base revision: {}", e.getMessage(), e);
            warnings.add("Base revision entity extraction failed: " + e.getMessage());
        }

        try {
            targetEntities = entityExtractor.extractEntities(targetReport);
        } catch (Exception e) {
            log.error("Failed to extract JPA entities from target revision: {}", e.getMessage(), e);
            warnings.add("Target revision entity extraction failed: " + e.getMessage());
        }

        // 2. Extract Spring Data Repositories
        List<SpringDataRepository> baseRepos = Collections.emptyList();
        List<SpringDataRepository> targetRepos = Collections.emptyList();
        try {
            baseRepos = repositoryExtractor.extractRepositories(baseReport);
        } catch (Exception e) {
            log.error("Failed to extract repositories from base revision: {}", e.getMessage(), e);
            warnings.add("Base revision repository extraction failed: " + e.getMessage());
        }

        try {
            targetRepos = repositoryExtractor.extractRepositories(targetReport);
        } catch (Exception e) {
            log.error("Failed to extract repositories from target revision: {}", e.getMessage(), e);
            warnings.add("Target revision repository extraction failed: " + e.getMessage());
        }

        // 3. Compare components
        List<DatabaseChangeFinding> findings = new ArrayList<>();
        try {
            findings.addAll(comparator.compareEntities(baseEntities, targetEntities));
        } catch (Exception e) {
            log.error("Failed to compare entities: {}", e.getMessage(), e);
            warnings.add("Entity comparison failed: " + e.getMessage());
        }

        try {
            findings.addAll(comparator.compareRepositories(baseRepos, targetRepos));
        } catch (Exception e) {
            log.error("Failed to compare repositories: {}", e.getMessage(), e);
            warnings.add("Repository comparison failed: " + e.getMessage());
        }

        List<FlywayMigrationFile> safeBaseMigrations = baseMigrations != null ? baseMigrations : Collections.emptyList();
        List<FlywayMigrationFile> safeTargetMigrations = targetMigrations != null ? targetMigrations : Collections.emptyList();

        try {
            findings.addAll(comparator.evaluateMigrations(safeBaseMigrations, safeTargetMigrations));
        } catch (Exception e) {
            log.error("Failed to evaluate migrations: {}", e.getMessage(), e);
            warnings.add("Migration evaluation failed: " + e.getMessage());
        }

        // 4. Compute statistics
        int destructiveCount = (int) findings.stream().filter(f -> f.severity() == DatabaseChangeSeverity.DESTRUCTIVE).count();
        int potentiallyBreakingCount = (int) findings.stream().filter(f -> f.severity() == DatabaseChangeSeverity.POTENTIALLY_BREAKING).count();
        int nonBreakingCount = (int) findings.stream().filter(f -> f.severity() == DatabaseChangeSeverity.NON_BREAKING).count();
        int informationalCount = (int) findings.stream().filter(f -> f.severity() == DatabaseChangeSeverity.INFORMATIONAL).count();
        int unknownCount = (int) findings.stream().filter(f -> f.severity() == DatabaseChangeSeverity.UNKNOWN).count();

        boolean isComplete = warnings.isEmpty();
        List<String> limitations = buildLimitations();

        DatabaseImpactReport report = new DatabaseImpactReport(
                repositoryPath,
                baseRevision,
                targetRevision,
                findings,
                baseEntities,
                targetEntities,
                baseRepos,
                targetRepos,
                safeTargetMigrations,
                destructiveCount,
                potentiallyBreakingCount,
                nonBreakingCount,
                informationalCount,
                unknownCount,
                isComplete,
                Instant.now(),
                warnings,
                limitations
        );

        log.info("Database impact analysis complete: {} findings ({} destructive, {} potentially breaking, {} unknown)",
                findings.size(), destructiveCount, potentiallyBreakingCount, unknownCount);

        return report;
    }

    private List<String> buildLimitations() {
        return List.of(
                "Static JPA entity analysis is heuristic; Java entity mappings do not guarantee deployed database schema state.",
                "SQL migrations are parsed syntactically without database execution or schema simulation.",
                "Unsupported procedural statements, triggers, stored functions, and dynamic SQL are classified as UNKNOWN.",
                "Runtime Spring physical/implicit naming strategy overrides are not evaluated.",
                "Database engine constraints and cascade behaviors enforced at the DBMS level without JPA/Flyway declarations cannot be observed statically.",
                "Data-bearing migration risks (e.g. adding NOT NULL to non-empty tables) depend on actual production data volume and distribution.",
                "This analysis does not claim completeness. Missing findings are not proof of schema safety."
        );
    }
}

