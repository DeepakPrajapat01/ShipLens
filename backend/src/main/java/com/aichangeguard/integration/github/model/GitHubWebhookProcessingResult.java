package com.aichangeguard.integration.github.model;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Result returned by the webhook intake processor.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record GitHubWebhookProcessingResult(
        Status status,
        String deliveryId,
        String jobId,
        String message
) {
    public enum Status {
        /** Analysis job successfully created and scheduled. */
        ACCEPTED,

        /** Delivery or active job already exists; duplicate safely ignored. */
        IGNORED_DUPLICATE,

        /** Event or PR action not configured for analysis (e.g. PR closed, comment, label). */
        UNSUPPORTED_EVENT,

        /** Ping event successfully acknowledged. */
        PING_ACKNOWLEDGED,

        /** Repository is not authorized under an active GitHub App installation. */
        INACCESSIBLE_REPOSITORY,

        /** Webhook payload was syntactically malformed or missing required fields. */
        INVALID_PAYLOAD
    }

    public static GitHubWebhookProcessingResult accepted(String deliveryId, String jobId, String message) {
        return new GitHubWebhookProcessingResult(Status.ACCEPTED, deliveryId, jobId, message);
    }

    public static GitHubWebhookProcessingResult duplicate(String deliveryId, String jobId, String message) {
        return new GitHubWebhookProcessingResult(Status.IGNORED_DUPLICATE, deliveryId, jobId, message);
    }

    public static GitHubWebhookProcessingResult unsupported(String deliveryId, String message) {
        return new GitHubWebhookProcessingResult(Status.UNSUPPORTED_EVENT, deliveryId, null, message);
    }

    public static GitHubWebhookProcessingResult ping(String deliveryId, String message) {
        return new GitHubWebhookProcessingResult(Status.PING_ACKNOWLEDGED, deliveryId, null, message);
    }

    public static GitHubWebhookProcessingResult inaccessible(String deliveryId, String message) {
        return new GitHubWebhookProcessingResult(Status.INACCESSIBLE_REPOSITORY, deliveryId, null, message);
    }

    public static GitHubWebhookProcessingResult invalid(String deliveryId, String message) {
        return new GitHubWebhookProcessingResult(Status.INVALID_PAYLOAD, deliveryId, null, message);
    }
}

