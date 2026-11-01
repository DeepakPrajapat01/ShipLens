package com.aichangeguard.common.model;

import java.time.Instant;

/**
 * Health check status response record.
 */
public record HealthStatusResponse(
        String status,
        String service,
        String version,
        Instant timestamp
) {
    public static HealthStatusResponse up(String service, String version) {
        return new HealthStatusResponse("UP", service, version, Instant.now());
    }
}
