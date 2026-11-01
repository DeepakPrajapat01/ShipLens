package com.aichangeguard.integration.github.api;

import com.aichangeguard.common.error.ChangeGuardException;
import com.aichangeguard.common.error.ErrorCode;
import com.aichangeguard.integration.github.model.GitHubWebhookProcessingResult;
import com.aichangeguard.integration.github.model.PullRequestAnalysisJob;
import com.aichangeguard.integration.github.service.GitHubWebhookProcessor;
import com.aichangeguard.integration.github.service.PullRequestJobService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller receiving GitHub App webhook deliveries and providing PR job status.
 */
@RestController
@RequestMapping("/api/v1/integrations/github")
public class GitHubWebhookController {

    private final GitHubWebhookProcessor webhookProcessor;
    private final PullRequestJobService jobService;

    public GitHubWebhookController(
            GitHubWebhookProcessor webhookProcessor,
            PullRequestJobService jobService
    ) {
        this.webhookProcessor = webhookProcessor;
        this.jobService = jobService;
    }

    /**
     * Intake endpoint for GitHub App webhook events.
     *
     * <p>Enforces HMAC-SHA256 signature verification before inspecting payload data.
     * Returns immediately with an asynchronous job identifier without blocking on analysis.</p>
     */
    @PostMapping(value = "/webhook", consumes = "application/json", produces = "application/json")
    public ResponseEntity<GitHubWebhookProcessingResult> handleWebhook(
            @RequestHeader(value = "X-GitHub-Delivery", required = false) String deliveryId,
            @RequestHeader(value = "X-GitHub-Event", required = false) String eventType,
            @RequestHeader(value = "X-Hub-Signature-256", required = false) String signatureHeader,
            @RequestBody(required = false) byte[] rawPayload
    ) {
        if (deliveryId == null || deliveryId.isBlank()) {
            throw new ChangeGuardException(ErrorCode.INVALID_REQUEST, "Missing required X-GitHub-Delivery header.");
        }
        if (eventType == null || eventType.isBlank()) {
            throw new ChangeGuardException(ErrorCode.INVALID_REQUEST, "Missing required X-GitHub-Event header.");
        }
        if (rawPayload == null || rawPayload.length == 0) {
            throw new ChangeGuardException(ErrorCode.INVALID_REQUEST, "Webhook payload cannot be empty.");
        }

        GitHubWebhookProcessingResult result = webhookProcessor.processWebhook(
                deliveryId,
                eventType,
                signatureHeader,
                rawPayload
        );

        HttpStatus httpStatus = switch (result.status()) {
            case ACCEPTED -> HttpStatus.ACCEPTED;
            case IGNORED_DUPLICATE -> HttpStatus.OK;
            case PING_ACKNOWLEDGED -> HttpStatus.OK;
            case UNSUPPORTED_EVENT -> HttpStatus.OK;
            case INACCESSIBLE_REPOSITORY -> HttpStatus.FORBIDDEN;
            case INVALID_PAYLOAD -> HttpStatus.BAD_REQUEST;
        };

        return ResponseEntity.status(httpStatus).body(result);
    }

    /**
     * Retrieves status of a created PR analysis job.
     */
    @GetMapping("/jobs/{jobId}")
    public ResponseEntity<PullRequestAnalysisJob> getJob(@PathVariable String jobId) {
        return jobService.getJob(jobId)
                .map(ResponseEntity::ok)
                .orElseThrow(() -> new ChangeGuardException(
                        ErrorCode.RESOURCE_NOT_FOUND,
                        "Analysis job not found: " + jobId
                ));
    }
}

