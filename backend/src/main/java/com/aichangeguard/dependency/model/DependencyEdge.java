package com.aichangeguard.dependency.model;

import com.aichangeguard.analysis.model.SourceRange;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Directed edge representing a dependency between two code elements.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record DependencyEdge(
        String sourceNodeId,
        String targetNodeId,
        EdgeType type,
        ResolutionStatus resolutionStatus,
        String evidence,
        SourceRange callSite
) {}
