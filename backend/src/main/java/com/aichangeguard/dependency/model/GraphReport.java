package com.aichangeguard.dependency.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;

/**
 * Summary report describing dependency graph construction metrics, coverage, and limitations.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record GraphReport(
        int totalNodes,
        int totalTypeNodes,
        int totalMethodNodes,
        int totalEdges,
        int resolvedCallEdges,
        int unresolvedInvocationsCount,
        int cyclesDetected,
        Instant generatedAt,
        List<String> limitations
) {}
