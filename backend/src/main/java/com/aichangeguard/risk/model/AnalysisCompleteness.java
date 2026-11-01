package com.aichangeguard.risk.model;

/**
 * Indicates how completely the code change was analyzed across all analytical dimensions.
 */
public enum AnalysisCompleteness {
    /** All required analysis modules ran to completion with deterministic evidence. */
    COMPLETE,

    /** Most analysis modules succeeded, but non-critical fallbacks or heuristics were used. */
    PARTIAL,

    /** Critical analysis failed, was skipped, or suffered infrastructure errors. */
    INSUFFICIENT
}

