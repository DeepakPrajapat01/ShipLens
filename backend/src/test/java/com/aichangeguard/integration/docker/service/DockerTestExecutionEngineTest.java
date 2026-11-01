package com.aichangeguard.integration.docker.service;

import com.aichangeguard.integration.docker.model.DockerContainerConfig;
import com.aichangeguard.integration.docker.model.DockerProperties;
import com.aichangeguard.integration.docker.model.TestExecutionRequest;
import com.aichangeguard.integration.docker.model.TestExecutionResult;
import com.aichangeguard.integration.docker.model.TestExecutionStatus;
import com.aichangeguard.repository.security.RepositoryPathValidator;
import com.aichangeguard.repository.security.RepositoryProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DockerTestExecutionEngineTest {

    private DockerTestExecutionEngine engine;
    private MockDockerClient mockDockerClient;
    private Path allowedDir;

    @BeforeEach
    void setUp(@TempDir Path tempDir) {
        allowedDir = tempDir;
        RepositoryProperties repoProps = new RepositoryProperties();
        repoProps.setAllowedBaseDirectories(List.of(allowedDir.toString()));
        RepositoryPathValidator pathValidator = new RepositoryPathValidator(repoProps);

        DockerProperties dockerProps = new DockerProperties();
        mockDockerClient = new MockDockerClient();
        TestExecutionValidator validator = new TestExecutionValidator(pathValidator, dockerProps);
        MavenCommandGenerator commandGenerator = new MavenCommandGenerator();
        SurefireOutputParser surefireParser = new SurefireOutputParser();

        engine = new DockerTestExecutionEngine(
                mockDockerClient, validator, commandGenerator, surefireParser, dockerProps
        );
    }

    @Test
    @DisplayName("Should execute tests successfully and verify container cleanup")
    void shouldExecuteTestsSuccessfully() {
        mockDockerClient.setDockerAvailable(true);
        mockDockerClient.setNextOutput(new DockerClient.DockerExecutionOutput(
                0,
                "[INFO] Tests run: 4, Failures: 0, Errors: 0, Skipped: 0\n[INFO] BUILD SUCCESS",
                "",
                false,
                1500L
        ));

        TestExecutionRequest request = TestExecutionRequest.builder("job-success", allowedDir.toString())
                .selectedTests(List.of("UserServiceTest#testFind"))
                .build();

        TestExecutionResult result = engine.executeTests(request);

        assertThat(result.status()).isEqualTo(TestExecutionStatus.PASSED);
        assertThat(result.totalTests()).isEqualTo(4);
        assertThat(result.passedCount()).isEqualTo(4);
        assertThat(result.failedCount()).isZero();
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.cleanedUp()).isTrue();
        assertThat(mockDockerClient.getCleanedContainers()).contains("changeguard-test-job-success");
    }

    @Test
    @DisplayName("Should capture test failures and verify container cleanup")
    void shouldCaptureTestFailures() {
        mockDockerClient.setDockerAvailable(true);
        mockDockerClient.setNextOutput(new DockerClient.DockerExecutionOutput(
                1,
                "[ERROR] Tests run: 2, Failures: 1, Errors: 0, Skipped: 0 <<< FAILURE!\n[INFO] BUILD FAILURE",
                "",
                false,
                2000L
        ));

        TestExecutionRequest request = TestExecutionRequest.builder("job-failure", allowedDir.toString())
                .selectedTests(List.of("PaymentTest"))
                .build();

        TestExecutionResult result = engine.executeTests(request);

        assertThat(result.status()).isEqualTo(TestExecutionStatus.FAILED);
        assertThat(result.totalTests()).isEqualTo(2);
        assertThat(result.failedCount()).isEqualTo(1);
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.cleanedUp()).isTrue();
        assertThat(mockDockerClient.getCleanedContainers()).contains("changeguard-test-job-failure");
    }

    @Test
    @DisplayName("Should handle container execution timeout")
    void shouldHandleTimeout() {
        mockDockerClient.setDockerAvailable(true);
        mockDockerClient.setNextOutput(new DockerClient.DockerExecutionOutput(
                -1,
                "[INFO] Running long test...",
                "Execution timed out",
                true, // Timed out
                180000L
        ));

        TestExecutionRequest request = TestExecutionRequest.builder("job-timeout", allowedDir.toString())
                .timeoutSeconds(10)
                .build();

        TestExecutionResult result = engine.executeTests(request);

        assertThat(result.status()).isEqualTo(TestExecutionStatus.TIMEOUT);
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.cleanedUp()).isTrue();
        assertThat(mockDockerClient.getCleanedContainers()).contains("changeguard-test-job-timeout");
    }

    @Test
    @DisplayName("Should classify missing Docker daemon as INFRASTRUCTURE_FAILURE without claiming test failure")
    void shouldClassifyMissingDockerAsInfrastructureFailure() {
        mockDockerClient.setDockerAvailable(false); // Docker daemon is down

        TestExecutionRequest request = TestExecutionRequest.builder("job-infra", allowedDir.toString())
                .selectedTests(List.of("SomeTest"))
                .build();

        TestExecutionResult result = engine.executeTests(request);

        assertThat(result.status()).isEqualTo(TestExecutionStatus.INFRASTRUCTURE_FAILURE);
        assertThat(result.infrastructureError()).contains("Docker daemon unavailable");
        assertThat(result.totalTests()).isZero(); // Did not run tests
        assertThat(result.cleanedUp()).isTrue();
    }

    @Test
    @DisplayName("Should handle invalid project path gracefully")
    void shouldHandleInvalidProjectPath() {
        TestExecutionRequest request = TestExecutionRequest.builder("job-invalid", "/non/existent/path")
                .build();

        TestExecutionResult result = engine.executeTests(request);

        assertThat(result.status()).isEqualTo(TestExecutionStatus.ERROR);
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.cleanedUp()).isTrue();
    }

    /**
     * Test double for {@link DockerClient}.
     */
    private static class MockDockerClient implements DockerClient {
        private boolean dockerAvailable = true;
        private DockerExecutionOutput nextOutput;
        private final List<String> cleanedContainers = new ArrayList<>();

        public void setDockerAvailable(boolean available) {
            this.dockerAvailable = available;
        }

        public void setNextOutput(DockerExecutionOutput output) {
            this.nextOutput = output;
        }

        public List<String> getCleanedContainers() {
            return cleanedContainers;
        }

        @Override
        public boolean isDockerAvailable() {
            return dockerAvailable;
        }

        @Override
        public DockerExecutionOutput runContainer(DockerContainerConfig config, int timeoutSeconds) {
            return nextOutput != null ? nextOutput : new DockerExecutionOutput(0, "", "", false, 100L);
        }

        @Override
        public boolean stopAndRemoveContainer(String containerName) {
            cleanedContainers.add(containerName);
            return true;
        }
    }
}

