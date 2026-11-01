package com.aichangeguard.repository.model;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Immutable metadata extracted from repository build descriptors (e.g. pom.xml).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ProjectMetadata(
        String name,
        String groupId,
        String artifactId,
        String version,
        String packaging,
        String javaVersion
) {
    public static ProjectMetadata empty() {
        return new ProjectMetadata(null, null, null, null, null, null);
    }
}
