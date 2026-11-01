package com.aichangeguard.common.api;

import com.aichangeguard.common.model.HealthStatusResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Health check endpoint providing service liveness and runtime metadata.
 */
@RestController
@RequestMapping("/api/v1/health")
public class HealthController {

    private final String applicationName;

    public HealthController(@Value("${spring.application.name:ai-changeguard-backend}") String applicationName) {
        this.applicationName = applicationName;
    }

    @GetMapping
    public ResponseEntity<HealthStatusResponse> getHealth() {
        return ResponseEntity.ok(HealthStatusResponse.up(applicationName, "0.1.0-SNAPSHOT"));
    }
}
