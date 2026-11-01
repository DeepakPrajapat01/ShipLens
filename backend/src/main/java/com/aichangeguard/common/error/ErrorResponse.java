package com.aichangeguard.common.error;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.Map;

/**
 * Standardized machine-readable error envelope as defined in API_CONVENTIONS.md.
 * Stack traces, internal file paths, and sensitive secrets are strictly excluded.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(
        String code,
        String message,
        String correlationId,
        Instant timestamp,
        Map<String, Object> details
) {
    public static ErrorResponse of(String code, String message, String correlationId) {
        return new ErrorResponse(code, message, correlationId, Instant.now(), null);
    }

    public static ErrorResponse of(String code, String message, String correlationId, Map<String, Object> details) {
        return new ErrorResponse(code, message, correlationId, Instant.now(), details);
    }
}
