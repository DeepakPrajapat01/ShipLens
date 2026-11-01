package com.aichangeguard.integration.github.service;

import com.aichangeguard.integration.github.model.AnalysisJobState;
import com.aichangeguard.integration.github.model.PullRequestAnalysisJob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service managing the lifecycle, storage, and retrieval of pull request analysis jobs.
 */
@Service
public class PullRequestJobService {

    private static final Logger log = LoggerFactory.getLogger(PullRequestJobService.class);

    private final Map<String, PullRequestAnalysisJob> jobs = new ConcurrentHashMap<>();
    private final WebhookDeduplicationService deduplicationService;

    public PullRequestJobService(WebhookDeduplicationService deduplicationService) {
        this.deduplicationService = deduplicationService;
    }

    /**
     * Creates a new pending analysis job and registers it for deduplication.
     */
    public PullRequestAnalysisJob createJob(
            String deliveryId,
            Long installationId,
            Long repositoryId,
            String repositoryFullName,
            int pullRequestNumber,
            String action,
            String baseRef,
            String baseSha,
            String headRef,
            String headSha
    ) {
        String jobId = "job-" + UUID.randomUUID();
        Instant now = Instant.now();

        PullRequestAnalysisJob job = new PullRequestAnalysisJob(
                jobId,
                deliveryId,
                installationId,
                repositoryId,
                repositoryFullName,
                pullRequestNumber,
                action,
                baseRef,
                baseSha,
                headRef,
                headSha,
                AnalysisJobState.PENDING,
                now,
                now,
                null
        );

        jobs.put(jobId, job);
        deduplicationService.registerJob(job);

        log.info("Created pull request analysis job {} for {} PR #{} [head: {}]",
                jobId, repositoryFullName, pullRequestNumber, headSha);

        return job;
    }

    public Optional<PullRequestAnalysisJob> getJob(String jobId) {
        return Optional.ofNullable(jobs.get(jobId));
    }

    public void updateJobState(String jobId, AnalysisJobState state, String reason) {
        jobs.computeIfPresent(jobId, (id, current) -> {
            PullRequestAnalysisJob updated = current.withState(state, reason);
            deduplicationService.registerJob(updated);
            log.info("Updated job {} state to {}: {}", jobId, state, reason != null ? reason : "");
            return updated;
        });
    }

    public void clear() {
        jobs.clear();
    }
}

