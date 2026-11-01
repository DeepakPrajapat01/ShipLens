package com.aichangeguard.integration.docker.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;
import java.util.Map;

/**
 * Low-level configuration for launching an isolated Docker container.
 *
 * <p>Enforces security constraints defined in {@code DOCKER_EXECUTION.md}:
 * <ul>
 *   <li>Non-root execution where supported.</li>
 *   <li>No privileged mode and capability dropping.</li>
 *   <li>No Docker socket mount or access to host sensitive dirs.</li>
 *   <li>Read-only source mount or controlled disposable workspace.</li>
 *   <li>Strict CPU, memory, and PID limits.</li>
 *   <li>Disabled network by default.</li>
 * </ul>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record DockerContainerConfig(
        String containerName,
        String image,
        String hostWorkspacePath,
        String containerWorkspacePath,
        boolean readOnlyMount,
        String cpuLimit,
        String memoryLimit,
        int pidsLimit,
        boolean networkDisabled,
        String user,
        List<String> commandArgs,
        Map<String, String> environmentVariables
) {}

