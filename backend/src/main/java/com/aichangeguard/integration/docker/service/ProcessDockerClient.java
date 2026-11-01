package com.aichangeguard.integration.docker.service;

import com.aichangeguard.integration.docker.model.DockerContainerConfig;
import com.aichangeguard.integration.docker.model.DockerProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Standard implementation of {@link DockerClient} using discrete arguments with {@link ProcessBuilder}.
 *
 * <p>Strictly avoids shell string interpolation (e.g. bash -c) to eliminate command injection risk.</p>
 */
@Component
public class ProcessDockerClient implements DockerClient {

    private static final Logger log = LoggerFactory.getLogger(ProcessDockerClient.class);

    private final DockerProperties properties;

    public ProcessDockerClient(DockerProperties properties) {
        this.properties = properties;
    }

    @Override
    public boolean isDockerAvailable() {
        try {
            Process process = new ProcessBuilder("docker", "version", "--format", "{{.Server.Version}}")
                    .redirectErrorStream(true)
                    .start();
            boolean finished = process.waitFor(5, TimeUnit.SECONDS);
            return finished && process.exitValue() == 0;
        } catch (Exception e) {
            log.debug("Docker CLI or daemon is not available: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public DockerExecutionOutput runContainer(DockerContainerConfig config, int timeoutSeconds) {
        List<String> command = new ArrayList<>();
        command.add("docker");
        command.add("run");

        // Named container for verified cleanup
        command.add("--name");
        command.add(config.containerName());

        // Resource & Security Limits per DOCKER_EXECUTION.md
        command.add("--cpus=" + config.cpuLimit());
        command.add("--memory=" + config.memoryLimit());
        command.add("--pids-limit=" + config.pidsLimit());
        command.add("--security-opt=no-new-privileges");
        command.add("--cap-drop=ALL");

        // Network isolation
        if (config.networkDisabled()) {
            command.add("--network=none");
        }

        // Non-root execution if specified
        if (config.user() != null && !config.user().isBlank()) {
            command.add("--user=" + config.user());
        }

        // Mount host workspace
        String mountOption = config.readOnlyMount() ? ":ro" : "";
        command.add("-v");
        command.add(config.hostWorkspacePath() + ":" + config.containerWorkspacePath() + mountOption);

        // Working directory
        command.add("-w");
        command.add(config.containerWorkspacePath());

        // Image
        command.add(config.image());

        // Command arguments inside container
        command.addAll(config.commandArgs());

        long startTime = System.currentTimeMillis();
        ProcessBuilder pb = new ProcessBuilder(command);

        Process process = null;
        try {
            log.info("Launching Docker container '{}' with image '{}' and timeout {}s",
                    config.containerName(), config.image(), timeoutSeconds);

            process = pb.start();

            // Asynchronously collect bounded stdout and stderr
            StreamCollector stdoutCollector = new StreamCollector(process.getInputStream(), properties.getMaxLogSizeBytes());
            StreamCollector stderrCollector = new StreamCollector(process.getErrorStream(), properties.getMaxLogSizeBytes());
            Thread stdoutThread = new Thread(stdoutCollector, "docker-stdout-" + config.containerName());
            Thread stderrThread = new Thread(stderrCollector, "docker-stderr-" + config.containerName());
            stdoutThread.start();
            stderrThread.start();

            boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            long duration = System.currentTimeMillis() - startTime;

            if (!finished) {
                log.warn("Docker container '{}' exceeded timeout of {}s. Forcibly terminating.",
                        config.containerName(), timeoutSeconds);
                stopAndRemoveContainer(config.containerName());
                process.destroyForcibly();
                return new DockerExecutionOutput(-1, stdoutCollector.getOutput(), "Execution timed out after " + timeoutSeconds + "s", true, duration);
            }

            stdoutThread.join(2000);
            stderrThread.join(2000);

            return new DockerExecutionOutput(process.exitValue(), stdoutCollector.getOutput(), stderrCollector.getOutput(), false, duration);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Container execution interrupted for '{}'", config.containerName());
            stopAndRemoveContainer(config.containerName());
            return new DockerExecutionOutput(-1, "", "Execution cancelled/interrupted", false, System.currentTimeMillis() - startTime);
        } catch (Exception e) {
            log.error("Failed to run Docker container '{}': {}", config.containerName(), e.getMessage());
            return new DockerExecutionOutput(-1, "", "Container execution failed: " + e.getMessage(), false, System.currentTimeMillis() - startTime);
        }
    }

    @Override
    public boolean stopAndRemoveContainer(String containerName) {
        if (containerName == null || containerName.isBlank()) {
            return true;
        }
        try {
            Process process = new ProcessBuilder("docker", "rm", "-f", containerName)
                    .redirectErrorStream(true)
                    .start();
            boolean finished = process.waitFor(10, TimeUnit.SECONDS);
            boolean success = finished && process.exitValue() == 0;
            if (success) {
                log.info("Successfully cleaned up container '{}'", containerName);
            } else {
                log.warn("Failed or timed out cleaning container '{}'", containerName);
            }
            return success;
        } catch (Exception e) {
            log.warn("Exception during container removal for '{}': {}", containerName, e.getMessage());
            return false;
        }
    }

    private static class StreamCollector implements Runnable {
        private final java.io.InputStream inputStream;
        private final int maxBytes;
        private final StringBuilder buffer = new StringBuilder();

        StreamCollector(java.io.InputStream inputStream, int maxBytes) {
            this.inputStream = inputStream;
            this.maxBytes = maxBytes;
        }

        @Override
        public void run() {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
                char[] charBuf = new char[1024];
                int read;
                int totalRead = 0;
                while ((read = reader.read(charBuf)) != -1) {
                    if (totalRead + read <= maxBytes) {
                        buffer.append(charBuf, 0, read);
                        totalRead += read;
                    } else if (totalRead < maxBytes) {
                        int remaining = maxBytes - totalRead;
                        buffer.append(charBuf, 0, remaining);
                        buffer.append("\n[OUTPUT TRUNCATED: Max log limit exceeded]\n");
                        totalRead = maxBytes;
                    }
                }
            } catch (Exception ignored) {}
        }

        public String getOutput() {
            return buffer.toString();
        }
    }
}

