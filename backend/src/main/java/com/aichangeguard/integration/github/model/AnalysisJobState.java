package com.aichangeguard.integration.github.model;

/**
 * Lifecycle state of a pull request analysis job.
 */
public enum AnalysisJobState {
    /** Job created and queued for asynchronous execution. */
    PENDING,

    /** Pipeline analysis actively executing. */
    RUNNING,

    /** Analysis completed successfully and risk report generated. */
    COMPLETED,

    /** Analysis failed due to an unrecoverable error. */
    FAILED,

    /** Analysis cancelled before or during execution. */
    CANCELLED
}

