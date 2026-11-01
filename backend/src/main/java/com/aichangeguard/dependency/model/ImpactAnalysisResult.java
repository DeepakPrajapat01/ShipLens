package com.aichangeguard.dependency.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;
import java.util.Set;

/**
 * Result of traversing the reverse dependency graph from a changed method symbol.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ImpactAnalysisResult(
        String changedSymbolId,
        Set<String> affectedMethodIds,
        List<ImpactPath> impactPaths,
        int maxDepthReached,
        int cyclesEncountered,
        List<String> warnings
) {}
