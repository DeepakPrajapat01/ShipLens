package com.aichangeguard.impact.database.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * Representation of a unique constraint declared on a JPA @Table or @Column.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record JpaUniqueConstraint(
        String name,
        List<String> columnNames,
        int sourceLine
) {}

