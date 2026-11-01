package com.aichangeguard.integration.github.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

/**
 * Representation of an asynchronous pull request risk analysis job.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PullRequestAnalysisJob(
        /** Unique identifier for this analysis run. */
        String jobId,

        /** Webhook delivery GUID that triggered this job. */
        String deliveryId,

        /** GitHub App installation ID. */
        Long installationId,

        /** GitHub numeric repository ID. */
        Long repositoryId,

        /** Repository full name (e.g., owner/repo). */
        String repositoryFullName,

        /** Pull request number. */
        int pullRequestNumber,

        /** Pull request action (e.g., opened, synchronize, reopened). */
        String action,

        /** Target branch ref (e.g., refs/heads/main). */
        String baseRef,

        /** Immutable base commit SHA. */
        String baseSha,

        /** Source branch ref (e.g., refs/heads/feature). */
        String headRef,

        /** Immutable head commit SHA. */
        String headSha,

        /** Current job lifecycle state. */
        AnalysisJobState state,

        /** Timestamp when the job was accepted. */
        Instant createdAt,

        /** Timestamp of last state change. */
        Instant updatedAt,

        /** Failure reason if state is FAILED. */
        String failureReason
) {
    public PullRequestAnalysisJob withState(AnalysisJobState newState, String reason) {
        return new PullRequestAnalysisJob(
                jobId, deliveryId, installationId, repositoryId, repositoryFullName,
                pullRequestNumber, action, baseRef, baseSha, headRef, headSha,
                newState, createdAt, Instant.now(), reason
        );
    }
}

