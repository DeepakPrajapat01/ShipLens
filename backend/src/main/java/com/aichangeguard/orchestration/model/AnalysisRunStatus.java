package com.aichangeguard.orchestration.model;

/**
 * Status of an analysis pipeline run.
 */
public enum AnalysisRunStatus {
    /** Run request accepted and queued. */
    PENDING,

    /** Pipeline is actively executing stages. */
    RUNNING,

    /** All pipeline stages completed and risk report generated. */
    COMPLETED,

    /** Pipeline encountered an unrecoverable failure. */
    FAILED,

    /** Pipeline was explicitly cancelled. */
    CANCELLED
}

