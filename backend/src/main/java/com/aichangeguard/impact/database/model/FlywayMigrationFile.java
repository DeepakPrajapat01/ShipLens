package com.aichangeguard.impact.database.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * Metadata and statements for a Flyway migration file.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record FlywayMigrationFile(
        String relativePath,
        String fileName,
        String version,
        String description,
        String migrationType, // VERSIONED, UNDO, REPEATABLE, INVALID_FORMAT
        List<SqlStatementInfo> statements,
        boolean isValidFormat,
        String parseError,
        int totalStatements
) {}

