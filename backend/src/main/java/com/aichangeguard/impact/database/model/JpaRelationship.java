package com.aichangeguard.impact.database.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * Normalized representation of an entity relationship extracted from JPA annotations.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record JpaRelationship(
        String fieldName,
        String targetEntity,
        String relationshipType, // ONE_TO_ONE, ONE_TO_MANY, MANY_TO_ONE, MANY_TO_MANY
        String joinColumn,
        String mappedBy,
        String fetchType,
        List<String> cascadeTypes,
        int sourceLine
) {}

