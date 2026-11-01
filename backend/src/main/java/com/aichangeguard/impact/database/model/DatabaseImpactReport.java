package com.aichangeguard.impact.database.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;

/**
 * Aggregated report representing database impact analysis across revisions.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record DatabaseImpactReport(
        String repositoryPath,
        String baseRevision,
        String targetRevision,
        List<DatabaseChangeFinding> findings,
        List<JpaEntity> baseEntities,
        List<JpaEntity> targetEntities,
        List<SpringDataRepository> baseRepositories,
        List<SpringDataRepository> targetRepositories,
        List<FlywayMigrationFile> migrationFiles,
        int destructiveCount,
        int potentiallyBreakingCount,
        int nonBreakingCount,
        int informationalCount,
        int unknownCount,
        boolean isComplete,
        Instant analyzedAt,
        List<String> warnings,
        List<String> limitations
) {}

