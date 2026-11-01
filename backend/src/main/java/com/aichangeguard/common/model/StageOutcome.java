package com.aichangeguard.common.model;

/**
 * Stage outcome states for the analysis lifecycle as specified in docs/ERROR_HANDLING.md.
 */
public enum StageOutcome {
    PENDING,
    RUNNING,
    SUCCEEDED,
    PARTIAL,
    FAILED,
    SKIPPED,
    CANCELLED,
    TIMED_OUT
}
