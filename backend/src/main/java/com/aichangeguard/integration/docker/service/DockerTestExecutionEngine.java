package com.aichangeguard.integration.docker.service;

import com.aichangeguard.common.error.ValidationException;
import com.aichangeguard.integration.docker.model.DockerContainerConfig;
import com.aichangeguard.integration.docker.model.DockerProperties;
import com.aichangeguard.integration.docker.model.TestExecutionRequest;
import com.aichangeguard.integration.docker.model.TestExecutionResult;
import com.aichangeguard.integration.docker.model.TestExecutionStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Collections;
import java.util.List;

/**
 * Orchestrates isolated Docker-based test execution for Maven/JUnit 5 test suites.
 *
 * <p>Requirements:
 * <ul>
 *   <li>Enforces non-root, restricted capability container execution.</li>
 *   <li>Prevents execution of untrusted repository scripts on the control-plane host.</li>
 *   <li>Enforces strict CPU, memory, PID, and wall-clock timeout limits.</li>
 *   <li>Ensures guaranteed container cleanup after success, failure, or timeout.</li>
 *   <li>Distinguishes application test failures from host infrastructure failures.</li>
 * </ul>
 */
@Service
public class DockerTestExecutionEngine {

    private static final Logger log = LoggerFactory.getLogger(DockerTestExecutionEngine.class);

    private final DockerClient dockerClient;
    private final TestExecutionValidator validator;
    private final MavenCommandGenerator commandGenerator;
    private final SurefireOutputParser surefireParser;
    private final DockerProperties properties;

    public DockerTestExecutionEngine(DockerClient dockerClient,
                                     TestExecutionValidator validator,
                                     MavenCommandGenerator commandGenerator,
                                     SurefireOutputParser surefireParser,
                                     DockerProperties properties) {
        this.dockerClient = dockerClient;
        this.validator = validator;
        this.commandGenerator = commandGenerator;
        this.surefireParser = surefireParser;
        this.properties = properties;
    }

    /**
     * Executes the requested tests inside an isolated Docker container.
     *
     * @param request execution specification
     * @return structured test execution outcome
     */
    public TestExecutionResult executeTests(TestExecutionRequest request) {
        Instant startTime = Instant.now();

        // 1. Validate request
        Path repoPath;
        try {
            repoPath = validator.validate(request);
        } catch (ValidationException e) {
            log.warn("Validation failed for test execution request: {}", e.getMessage());
            return new TestExecutionResult(
                    request != null ? request.jobId() : "unknown",
                    TestExecutionStatus.ERROR,
                    -1, 0, 0, 0, 0, 0, 0L,
                    "", e.getMessage(), null,
                    Collections.emptyList(),
                    List.of(e.getMessage()),
                    null, true, startTime
            );
        }

        // 2. Verify Docker availability
        if (!dockerClient.isDockerAvailable()) {
            log.warn("Docker daemon or CLI is not available on host. Failing job as INFRASTRUCTURE_FAILURE.");
            return new TestExecutionResult(
                    request.jobId(),
                    TestExecutionStatus.INFRASTRUCTURE_FAILURE,
                    -1, 0, 0, 0, 0, 0, 0L,
                    "", "Docker is not installed or the Docker daemon is not responding",
                    null,
                    request.selectedTests() != null ? request.selectedTests() : Collections.emptyList(),
                    Collections.emptyList(),
                    "Docker daemon unavailable on host system",
                    true, startTime
            );
        }

        String containerName = "changeguard-test-" + request.jobId();
        boolean cleanedUp = false;

        try {
            // 3. Generate Maven test command
            List<String> mavenArgs = commandGenerator.generateCommand(
                    request.selectedTests(),
                    request.networkDisabled(),
                    request.additionalMavenArgs()
            );

            // 4. Build container configuration
            DockerContainerConfig config = new DockerContainerConfig(
                    containerName,
                    request.dockerImage(),
                    repoPath.toAbsolutePath().toString(),
                    "/workspace",
                    false, // Writable inside container so Maven can create target/
                    request.cpuLimit(),
                    request.memoryLimit(),
                    properties.getPidsLimit(),
                    request.networkDisabled(),
                    null,
                    mavenArgs,
                    Collections.emptyMap()
            );

            // 5. Run container with timeout
            DockerClient.DockerExecutionOutput output = dockerClient.runContainer(config, request.timeoutSeconds());

            // 6. Evaluate outcome
            if (output.isTimedOut()) {
                return new TestExecutionResult(
                        request.jobId(),
                        TestExecutionStatus.TIMEOUT,
                        -1, 0, 0, 0, 0, 0,
                        output.executionDurationMs(),
                        output.stdout(), output.stderr(),
                        containerName,
                        request.selectedTests(),
                        List.of("Execution timed out after " + request.timeoutSeconds() + " seconds"),
                        null, true, startTime
                );
            }

            // 7. Parse Surefire test output
            String combinedLogs = output.stdout() + "\n" + output.stderr();
            SurefireOutputParser.ParsedSurefireResult parsed = surefireParser.parse(combinedLogs, output.exitCode());

            return new TestExecutionResult(
                    request.jobId(),
                    parsed.suggestedStatus(),
                    output.exitCode(),
                    parsed.totalTests(),
                    parsed.passedCount(),
                    parsed.failedCount(),
                    parsed.errorCount(),
                    parsed.skippedCount(),
                    output.executionDurationMs(),
                    output.stdout(),
                    output.stderr(),
                    containerName,
                    request.selectedTests(),
                    parsed.failureDetails(),
                    null,
                    true,
                    startTime
            );

        } finally {
            // 8. Guaranteed cleanup
            try {
                cleanedUp = dockerClient.stopAndRemoveContainer(containerName);
            } catch (Exception e) {
                log.warn("Error during post-execution cleanup of container '{}': {}", containerName, e.getMessage());
            }
        }
    }
}

