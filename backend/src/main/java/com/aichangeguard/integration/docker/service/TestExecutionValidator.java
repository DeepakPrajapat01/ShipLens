package com.aichangeguard.integration.docker.service;

import com.aichangeguard.common.error.ValidationException;
import com.aichangeguard.integration.docker.model.DockerProperties;
import com.aichangeguard.integration.docker.model.TestExecutionRequest;
import com.aichangeguard.repository.security.RepositoryPathValidator;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.regex.Pattern;

/**
 * Validates execution requests and parameters before container creation.
 */
@Component
public class TestExecutionValidator {

    private static final Pattern SAFE_JOB_ID_PATTERN = Pattern.compile("^[A-Za-z0-9_-]+$");
    private static final Pattern SAFE_CPU_PATTERN = Pattern.compile("^[0-9]+(?:\\.[0-9]+)?$");
    private static final Pattern SAFE_MEMORY_PATTERN = Pattern.compile("^[0-9]+[mMgG]$");
    private static final Pattern SAFE_IMAGE_PATTERN = Pattern.compile("^[a-zA-Z0-9_.-]+(?:/[a-zA-Z0-9_.-]+)*(?::[a-zA-Z0-9_.-]+)?$");

    private final RepositoryPathValidator pathValidator;
    private final DockerProperties properties;

    public TestExecutionValidator(RepositoryPathValidator pathValidator, DockerProperties properties) {
        this.pathValidator = pathValidator;
        this.properties = properties;
    }

    /**
     * Validates that the request parameters conform to safety limits and format constraints.
     *
     * @param request execution request
     * @return validated Path to the repository
     */
    public Path validate(TestExecutionRequest request) {
        if (request == null) {
            throw new ValidationException("Test execution request cannot be null");
        }

        if (request.jobId() == null || request.jobId().isBlank()) {
            throw new ValidationException("Job ID cannot be null or empty");
        }

        if (!SAFE_JOB_ID_PATTERN.matcher(request.jobId()).matches()) {
            throw new ValidationException("Invalid Job ID format: '" + request.jobId() + "'. Only alphanumeric, dash, and underscore are allowed.");
        }

        Path validatedRepoPath = pathValidator.validate(request.repositoryPath());

        // Validate timeout
        if (request.timeoutSeconds() <= 0) {
            throw new ValidationException("Timeout must be greater than 0 seconds");
        }
        if (request.timeoutSeconds() > properties.getMaxTimeoutSeconds()) {
            throw new ValidationException("Timeout (" + request.timeoutSeconds() + "s) exceeds maximum allowed limit (" + properties.getMaxTimeoutSeconds() + "s)");
        }

        // Validate CPU limit
        if (request.cpuLimit() != null && !SAFE_CPU_PATTERN.matcher(request.cpuLimit()).matches()) {
            throw new ValidationException("Invalid CPU limit format: " + request.cpuLimit());
        }

        // Validate Memory limit
        if (request.memoryLimit() != null && !SAFE_MEMORY_PATTERN.matcher(request.memoryLimit()).matches()) {
            throw new ValidationException("Invalid memory limit format: " + request.memoryLimit() + ". Expected format e.g. '2g' or '512m'.");
        }

        // Validate Docker image name
        if (request.dockerImage() != null && !SAFE_IMAGE_PATTERN.matcher(request.dockerImage()).matches()) {
            throw new ValidationException("Invalid or unsafe Docker image name: " + request.dockerImage());
        }

        return validatedRepoPath;
    }
}

