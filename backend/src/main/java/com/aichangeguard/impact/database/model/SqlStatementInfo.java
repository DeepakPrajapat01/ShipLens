package com.aichangeguard.impact.database.model;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Parsed representation of an individual SQL statement in a migration file.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SqlStatementInfo(
        String rawSql,
        SqlStatementType statementType,
        String targetTable,
        String targetColumn,
        String dataType,
        Boolean nullable,
        String defaultValue,
        String constraintName,
        String indexName,
        boolean isDestructive,
        String destructiveReason,
        String unsupportedReason,
        int lineNumber
) {}

