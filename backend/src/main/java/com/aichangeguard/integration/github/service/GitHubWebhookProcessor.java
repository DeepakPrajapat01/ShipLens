package com.aichangeguard.integration.github.service;

import com.aichangeguard.integration.github.model.GitHubWebhookProcessingResult;
import com.aichangeguard.integration.github.model.PullRequestAnalysisJob;
import com.aichangeguard.integration.github.security.GitHubWebhookSignatureVerifier;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Core processor for ingested GitHub webhook events.
 *
 * <p>Validates signatures, enforces idempotent deduplication, validates commit SHAs,
 * handles installation state transitions, and creates asynchronous PR analysis jobs.</p>
 */
@Service
public class GitHubWebhookProcessor {

    private static final Logger log = LoggerFactory.getLogger(GitHubWebhookProcessor.class);
    private static final Pattern SHA_PATTERN = Pattern.compile("^[0-9a-fA-F]{40}$");
    private static final Set<String> SUPPORTED_PR_ACTIONS = Set.of("opened", "synchronize", "reopened");

    private final GitHubWebhookSignatureVerifier signatureVerifier;
    private final WebhookDeduplicationService deduplicationService;
    private final GitHubInstallationManager installationManager;
    private final PullRequestJobService jobService;
    private final ObjectMapper objectMapper;

    public GitHubWebhookProcessor(
            GitHubWebhookSignatureVerifier signatureVerifier,
            WebhookDeduplicationService deduplicationService,
            GitHubInstallationManager installationManager,
            PullRequestJobService jobService,
            ObjectMapper objectMapper
    ) {
        this.signatureVerifier = signatureVerifier;
        this.deduplicationService = deduplicationService;
        this.installationManager = installationManager;
        this.jobService = jobService;
        this.objectMapper = objectMapper;
    }

    /**
     * Processes an incoming raw webhook event.
     */
    public GitHubWebhookProcessingResult processWebhook(
            String deliveryId,
            String eventType,
            String signatureHeader,
            byte[] rawPayload
    ) {
        // 1. Mandatory signature verification FIRST before payload is trusted
        signatureVerifier.verifyOrThrow(rawPayload, signatureHeader);

        // 2. Delivery ID deduplication check
        if (!deduplicationService.recordDeliveryIfNew(deliveryId)) {
            log.info("Duplicate delivery ID received and ignored: {}", deliveryId);
            return GitHubWebhookProcessingResult.duplicate(deliveryId, null, "Duplicate webhook delivery ignored.");
        }

        // 3. Parse JSON payload
        JsonNode root;
        try {
            root = objectMapper.readTree(rawPayload);
        } catch (IOException e) {
            log.warn("Malformed JSON in webhook delivery {}: {}", deliveryId, e.getMessage());
            return GitHubWebhookProcessingResult.invalid(deliveryId, "Malformed JSON payload: " + e.getMessage());
        }

        if (root == null || !root.isObject()) {
            return GitHubWebhookProcessingResult.invalid(deliveryId, "Empty or non-object webhook payload.");
        }

        // 4. Route by Event Type
        if ("ping".equalsIgnoreCase(eventType)) {
            return GitHubWebhookProcessingResult.ping(deliveryId, "Ping event successfully received and acknowledged.");
        }

        if ("installation".equalsIgnoreCase(eventType)) {
            return processInstallationEvent(deliveryId, root);
        }

        if ("installation_repositories".equalsIgnoreCase(eventType)) {
            return processInstallationRepositoriesEvent(deliveryId, root);
        }

        if ("pull_request".equalsIgnoreCase(eventType)) {
            return processPullRequestEvent(deliveryId, root);
        }

        log.debug("Acknowledged unsupported GitHub event type '{}' (delivery: {})", eventType, deliveryId);
        return GitHubWebhookProcessingResult.unsupported(deliveryId, "Unsupported event type: " + eventType);
    }

    private GitHubWebhookProcessingResult processPullRequestEvent(String deliveryId, JsonNode root) {
        String action = root.path("action").asText(null);
        if (action == null || !SUPPORTED_PR_ACTIONS.contains(action.toLowerCase())) {
            log.debug("Ignored unsupported pull_request action '{}' for delivery {}", action, deliveryId);
            return GitHubWebhookProcessingResult.unsupported(
                    deliveryId, "Unsupported pull_request action: " + (action != null ? action : "null")
            );
        }

        JsonNode prNode = root.path("pull_request");
        JsonNode repoNode = root.path("repository");
        JsonNode installationNode = root.path("installation");

        if (prNode.isMissingNode() || repoNode.isMissingNode()) {
            return GitHubWebhookProcessingResult.invalid(
                    deliveryId, "Missing 'pull_request' or 'repository' payload section."
            );
        }

        int prNumber = prNode.path("number").asInt(root.path("number").asInt(0));
        Long repoId = repoNode.path("id").isNumber() ? repoNode.path("id").asLong() : null;
        String repoFullName = repoNode.path("full_name").asText(null);
        Long installationId = installationNode.path("id").isNumber() ? installationNode.path("id").asLong() : null;

        String baseRef = prNode.path("base").path("ref").asText("");
        String baseSha = prNode.path("base").path("sha").asText("");
        String headRef = prNode.path("head").path("ref").asText("");
        String headSha = prNode.path("head").path("sha").asText("");

        // Validate Commit SHAs
        if (!isValidSha(baseSha) || !isValidSha(headSha)) {
            log.warn("Invalid commit SHAs in PR payload (base: '{}', head: '{}') for delivery {}",
                    baseSha, headSha, deliveryId);
            return GitHubWebhookProcessingResult.invalid(
                    deliveryId, "Malformed base or head commit SHA."
            );
        }

        // Validate Repository Authorization / Accessibility
        if (installationId != null && !installationManager.isRepositoryAccessible(installationId, repoFullName)) {
            // Check if installation exists at all
            if (installationManager.getInstallation(installationId).isPresent()) {
                log.warn("Repository '{}' is not accessible under installation {}", repoFullName, installationId);
                return GitHubWebhookProcessingResult.inaccessible(
                        deliveryId, "Repository '" + repoFullName + "' is inaccessible or unauthorized."
                );
            }
        }

        // Idempotency: check if an active job already exists for this (repo, pr, headSha)
        Optional<PullRequestAnalysisJob> existingJob = deduplicationService.findActiveJob(repoId, prNumber, headSha);
        if (existingJob.isPresent()) {
            String existingJobId = existingJob.get().jobId();
            log.info("Reusing existing active analysis job {} for {} PR #{} [head: {}]",
                    existingJobId, repoFullName, prNumber, headSha);
            return GitHubWebhookProcessingResult.duplicate(
                    deliveryId, existingJobId, "Active analysis job already exists for head SHA."
            );
        }

        // Create new asynchronous analysis job
        PullRequestAnalysisJob job = jobService.createJob(
                deliveryId,
                installationId,
                repoId,
                repoFullName,
                prNumber,
                action,
                baseRef,
                baseSha,
                headRef,
                headSha
        );

        return GitHubWebhookProcessingResult.accepted(
                deliveryId, job.jobId(), "Pull request analysis job created successfully."
        );
    }

    private GitHubWebhookProcessingResult processInstallationEvent(String deliveryId, JsonNode root) {
        String action = root.path("action").asText("");
        JsonNode instNode = root.path("installation");
        Long installationId = instNode.path("id").isNumber() ? instNode.path("id").asLong() : null;
        String accountLogin = instNode.path("account").path("login").asText("");
        String accountType = instNode.path("account").path("type").asText("User");
        String repoSelection = instNode.path("repository_selection").asText("all");

        List<String> repos = new ArrayList<>();
        JsonNode reposNode = root.path("repositories");
        if (reposNode.isArray()) {
            for (JsonNode r : reposNode) {
                repos.add(r.path("full_name").asText());
            }
        }

        installationManager.handleInstallationEvent(
                action, installationId, accountLogin, accountType, repoSelection, repos
        );

        return GitHubWebhookProcessingResult.accepted(
                deliveryId, null, "Installation lifecycle event processed: " + action
        );
    }

    private GitHubWebhookProcessingResult processInstallationRepositoriesEvent(String deliveryId, JsonNode root) {
        String action = root.path("action").asText("");
        JsonNode instNode = root.path("installation");
        Long installationId = instNode.path("id").isNumber() ? instNode.path("id").asLong() : null;

        List<String> added = new ArrayList<>();
        JsonNode addedNode = root.path("repositories_added");
        if (addedNode.isArray()) {
            for (JsonNode r : addedNode) {
                added.add(r.path("full_name").asText());
            }
        }

        List<String> removed = new ArrayList<>();
        JsonNode removedNode = root.path("repositories_removed");
        if (removedNode.isArray()) {
            for (JsonNode r : removedNode) {
                removed.add(r.path("full_name").asText());
            }
        }

        installationManager.handleInstallationRepositoriesEvent(action, installationId, added, removed);

        return GitHubWebhookProcessingResult.accepted(
                deliveryId, null, "Installation repositories updated: " + action
        );
    }

    private boolean isValidSha(String sha) {
        return sha != null && SHA_PATTERN.matcher(sha).matches();
    }
}

