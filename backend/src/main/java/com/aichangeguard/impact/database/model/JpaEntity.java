package com.aichangeguard.impact.database.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * Normalized representation of a JPA @Entity declaration.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record JpaEntity(
        String entityName,
        String qualifiedName,
        String tableName,
        String schemaName,
        boolean isTableExplicit,
        List<String> primaryKeyFieldNames,
        List<JpaColumn> columns,
        List<JpaRelationship> relationships,
        List<JpaUniqueConstraint> uniqueConstraints,
        String sourceFile,
        int sourceLine
) {
    /**
     * Resolves a column by its column name (case-insensitive) or field name.
     */
    public JpaColumn findColumn(String name) {
        if (name == null || columns == null) {
            return null;
        }
        for (JpaColumn col : columns) {
            if (name.equalsIgnoreCase(col.columnName()) || name.equalsIgnoreCase(col.fieldName())) {
                return col;
            }
        }
        return null;
    }
}

