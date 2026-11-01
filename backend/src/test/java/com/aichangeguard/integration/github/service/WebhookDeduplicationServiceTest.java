package com.aichangeguard.integration.github.service;

import com.aichangeguard.integration.github.model.AnalysisJobState;
import com.aichangeguard.integration.github.model.PullRequestAnalysisJob;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class WebhookDeduplicationServiceTest {

    private WebhookDeduplicationService deduplicationService;

    @BeforeEach
    void setUp() {
        deduplicationService = new WebhookDeduplicationService();
    }

    @Test
    @DisplayName("Identifies duplicate delivery IDs idempotently")
    void recordsDeliveryIfNew() {
        String deliveryId = "guid-12345-67890";

        assertThat(deduplicationService.recordDeliveryIfNew(deliveryId)).isTrue();
        // Second delivery with same ID must be rejected as duplicate
        assertThat(deduplicationService.recordDeliveryIfNew(deliveryId)).isFalse();
    }

    @Test
    @DisplayName("Finds active analysis job for identical repository, PR number, and head SHA")
    void findsActiveJob() {
        Long repoId = 101L;
        int prNumber = 42;
        String headSha = "abcdef1234567890abcdef1234567890abcdef12";

        PullRequestAnalysisJob job = new PullRequestAnalysisJob(
                "job-1", "del-1", 1L, repoId, "owner/repo", prNumber, "opened",
                "refs/heads/main", "1111111111111111111111111111111111111111",
                "refs/heads/feat", headSha, AnalysisJobState.PENDING, Instant.now(), Instant.now(), null
        );

        deduplicationService.registerJob(job);

        Optional<PullRequestAnalysisJob> active = deduplicationService.findActiveJob(repoId, prNumber, headSha);
        assertThat(active).isPresent();
        assertThat(active.get().jobId()).isEqualTo("job-1");
    }

    @Test
    @DisplayName("Completed or failed jobs are not returned as active jobs")
    void completedJobsNotActive() {
        Long repoId = 102L;
        int prNumber = 50;
        String headSha = "1234567890abcdef1234567890abcdef12345678";

        PullRequestAnalysisJob job = new PullRequestAnalysisJob(
                "job-2", "del-2", 1L, repoId, "owner/repo", prNumber, "opened",
                "refs/heads/main", "0000000000000000000000000000000000000000",
                "refs/heads/feat", headSha, AnalysisJobState.COMPLETED, Instant.now(), Instant.now(), null
        );

        deduplicationService.registerJob(job);

        Optional<PullRequestAnalysisJob> active = deduplicationService.findActiveJob(repoId, prNumber, headSha);
        assertThat(active).isEmpty();
    }
}

