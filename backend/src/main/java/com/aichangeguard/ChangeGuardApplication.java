package com.aichangeguard;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * AI ChangeGuard Backend Application.
 * Entry point for the Spring Boot control plane and analysis engine.
 */
@SpringBootApplication
public class ChangeGuardApplication {

    private static final Logger log = LoggerFactory.getLogger(ChangeGuardApplication.class);

    public static void main(String[] args) {
        log.info("Starting AI ChangeGuard Backend...");
        SpringApplication.run(ChangeGuardApplication.class, args);
        log.info("AI ChangeGuard Backend initialized successfully.");
    }
}
