package com.aichangeguard.impact.database.model;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Normalized representation of a column mapping extracted from a JPA entity field.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record JpaColumn(
        String fieldName,
        String columnName,
        String javaType,
        String columnDefinition,
        boolean nullable,
        boolean unique,
        boolean isPrimaryKey,
        Integer length,
        int sourceLine
) {}

