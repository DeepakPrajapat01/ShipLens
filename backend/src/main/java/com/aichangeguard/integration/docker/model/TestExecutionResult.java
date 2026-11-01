package com.aichangeguard.integration.docker.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;

/**
 * Result of an isolated Docker test execution run.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TestExecutionResult(
        String jobId,
        TestExecutionStatus status,
        int exitCode,
        int totalTests,
        int passedCount,
        int failedCount,
        int errorCount,
        int skippedCount,
        long durationMs,
        String stdoutLog,
        String stderrLog,
        String containerId,
        List<String> executedTests,
        List<String> failureDetails,
        String infrastructureError,
        boolean cleanedUp,
        Instant executedAt
) {
    public boolean isSuccess() {
        return status == TestExecutionStatus.PASSED;
    }
}

