package com.aichangeguard.integration.docker.service;

import com.aichangeguard.integration.docker.model.DockerContainerConfig;

/**
 * Interface defining Docker container operations for isolated test execution.
 *
 * <p>Decouples the test execution engine from direct OS process execution,
 * allowing full unit testability without requiring a running Docker daemon.</p>
 */
public interface DockerClient {

    /**
     * Checks if the Docker CLI and daemon are operational.
     */
    boolean isDockerAvailable();

    /**
     * Executes an isolated container according to the given configuration with a strict timeout.
     */
    DockerExecutionOutput runContainer(DockerContainerConfig config, int timeoutSeconds);

    /**
     * Forcibly stops and removes a container by name, ensuring verified cleanup.
     */
    boolean stopAndRemoveContainer(String containerName);

    /**
     * Output produced by a Docker container execution run.
     */
    record DockerExecutionOutput(
            int exitCode,
            String stdout,
            String stderr,
            boolean isTimedOut,
            long executionDurationMs
    ) {}
}

