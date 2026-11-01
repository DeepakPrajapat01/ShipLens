package com.aichangeguard.integration.github.service;

import com.aichangeguard.integration.github.model.AnalysisJobState;
import com.aichangeguard.integration.github.model.PullRequestAnalysisJob;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe deduplication service for GitHub webhook deliveries and analysis jobs.
 *
 * <p>Ensures idempotent delivery handling and avoids duplicate pipeline execution
 * when duplicate webhook deliveries or identical commit pushes are received.</p>
 */
@Service
public class WebhookDeduplicationService {

    private static final int MAX_CACHE_SIZE = 10_000;
    private static final Duration DELIVERY_EXPIRY = Duration.ofHours(24);

    // deliveryId -> receivedAt
    private final Map<String, Instant> processedDeliveries = new ConcurrentHashMap<>();

    // compositeKey (repoId:prNumber:headSha) -> PullRequestAnalysisJob
    private final Map<String, PullRequestAnalysisJob> activeJobs = new ConcurrentHashMap<>();

    /**
     * Atomically records a delivery ID.
     *
     * @param deliveryId GitHub delivery GUID from X-GitHub-Delivery header
     * @return true if the delivery is new; false if it was already processed
     */
    public boolean recordDeliveryIfNew(String deliveryId) {
        if (deliveryId == null || deliveryId.isBlank()) {
            return false;
        }

        pruneIfNecessary();

        Instant now = Instant.now();
        Instant existing = processedDeliveries.putIfAbsent(deliveryId, now);
        return existing == null;
    }

    /**
     * Checks if an active job already exists for the given repository, pull request, and head SHA.
     */
    public Optional<PullRequestAnalysisJob> findActiveJob(Long repositoryId, int pullRequestNumber, String headSha) {
        if (repositoryId == null || headSha == null) {
            return Optional.empty();
        }

        String key = buildJobKey(repositoryId, pullRequestNumber, headSha);
        PullRequestAnalysisJob job = activeJobs.get(key);
        if (job != null && (job.state() == AnalysisJobState.PENDING || job.state() == AnalysisJobState.RUNNING)) {
            return Optional.of(job);
        }
        return Optional.empty();
    }

    /**
     * Registers an analysis job for idempotency tracking.
     */
    public void registerJob(PullRequestAnalysisJob job) {
        if (job != null && job.repositoryId() != null && job.headSha() != null) {
            String key = buildJobKey(job.repositoryId(), job.pullRequestNumber(), job.headSha());
            activeJobs.put(key, job);
        }
    }

    public void clear() {
        processedDeliveries.clear();
        activeJobs.clear();
    }

    private String buildJobKey(Long repositoryId, int pullRequestNumber, String headSha) {
        return repositoryId + ":" + pullRequestNumber + ":" + headSha.toLowerCase();
    }

    private void pruneIfNecessary() {
        if (processedDeliveries.size() > MAX_CACHE_SIZE) {
            Instant threshold = Instant.now().minus(DELIVERY_EXPIRY);
            processedDeliveries.entrySet().removeIf(entry -> entry.getValue().isBefore(threshold));
        }
    }
}

