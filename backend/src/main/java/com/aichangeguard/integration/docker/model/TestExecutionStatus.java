package com.aichangeguard.integration.docker.model;

/**
 * Status of an isolated test execution job in a Docker container.
 *
 * <p>Crucial distinction rule: Infrastructure failures (e.g. Docker daemon unavailable,
 * container launch failure, OOM on host) are NEVER classified as application test failures.</p>
 */
public enum TestExecutionStatus {
    /** All selected tests executed and passed successfully. */
    PASSED,

    /** Tests executed, but one or more assertions or test methods failed. */
    FAILED,

    /** Build or runtime error occurred inside the container (e.g. compilation failure). */
    ERROR,

    /** Container execution exceeded the wall-clock timeout and was terminated. */
    TIMEOUT,

    /** Execution was explicitly cancelled by the control plane. */
    CANCELLED,

    /** Host or container infrastructure failure (Docker missing, daemon down, image pull failed). */
    INFRASTRUCTURE_FAILURE
}

