package com.aichangeguard.dependency.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * Traceable path through the dependency graph showing how a changed symbol reaches an impacted caller.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ImpactPath(
        List<String> nodeIds,
        List<DependencyEdge> edges,
        int depth
) {}
