package com.aichangeguard.dependency.model;

import com.aichangeguard.analysis.model.SourceRange;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Map;

/**
 * Node in the method-level and type-level dependency graph.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record DependencyNode(
        String id,
        NodeKind kind,
        String qualifiedName,
        String simpleName,
        String parentTypeId,
        SourceRange sourceRange,
        Map<String, Object> metadata
) {}
