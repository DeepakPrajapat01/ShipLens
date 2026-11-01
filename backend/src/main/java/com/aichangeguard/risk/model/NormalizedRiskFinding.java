package com.aichangeguard.risk.model;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * A normalized, deduplicated risk finding produced by integrating multiple analysis engines.
 *
 * <p>Every finding carries traceable evidence to a source file, line position, graph path,
 * database migration, or test execution record.</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record NormalizedRiskFinding(
        /** Deterministic hash or fingerprint key used for deduplication. */
        String fingerprint,

        /** Categorization of the finding. */
        RiskCategory category,

        /** Severity assessment of the finding. */
        RiskSeverity severity,

        /** Confidence classification of the supporting evidence. */
        EvidenceConfidence confidence,

        /** Short human-readable summary. */
        String title,

        /** Detailed explanation of the risk mechanism. */
        String description,

        /** Source file path for traceability, or null if repository-level. */
        String sourceFile,

        /** Source line position for traceability, or null if unavailable. */
        Integer sourceLine,

        /** Value in the base revision if applicable. */
        String baseValue,

        /** Value in the target revision if applicable. */
        String targetValue,

        /** Exact supporting evidence reference (e.g. SQL statement, call path, failure trace). */
        String evidenceReference,

        /** Whether the finding is based on deterministic fact rather than heuristic inference. */
        boolean isDeterministic,

        /** Normalized score weight assigned to this finding. */
        int scoreContribution,

        /** Number of duplicate occurrences merged into this finding. */
        int duplicateCount
) {
    public NormalizedRiskFinding withDuplicateCount(int count) {
        return new NormalizedRiskFinding(
                fingerprint, category, severity, confidence, title, description,
                sourceFile, sourceLine, baseValue, targetValue, evidenceReference,
                isDeterministic, scoreContribution, count
        );
    }
}

