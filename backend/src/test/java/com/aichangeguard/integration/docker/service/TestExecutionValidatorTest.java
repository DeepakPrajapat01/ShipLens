package com.aichangeguard.integration.docker.service;

import com.aichangeguard.common.error.ValidationException;
import com.aichangeguard.integration.docker.model.DockerProperties;
import com.aichangeguard.integration.docker.model.TestExecutionRequest;
import com.aichangeguard.repository.security.RepositoryPathValidator;
import com.aichangeguard.repository.security.RepositoryProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestExecutionValidatorTest {

    private TestExecutionValidator validator;
    private Path allowedDir;

    @BeforeEach
    void setUp(@TempDir Path tempDir) {
        allowedDir = tempDir;
        RepositoryProperties repoProps = new RepositoryProperties();
        repoProps.setAllowedBaseDirectories(List.of(allowedDir.toString()));
        RepositoryPathValidator pathValidator = new RepositoryPathValidator(repoProps);

        DockerProperties dockerProps = new DockerProperties();
        dockerProps.setMaxTimeoutSeconds(600);

        validator = new TestExecutionValidator(pathValidator, dockerProps);
    }

    @Test
    @DisplayName("Should validate valid execution request successfully")
    void shouldValidateValidRequest() {
        TestExecutionRequest request = TestExecutionRequest.builder("job-123", allowedDir.toString())
                .timeoutSeconds(120)
                .cpuLimit("2.0")
                .memoryLimit("2g")
                .dockerImage("maven:3.9-eclipse-temurin-21")
                .build();

        Path path = validator.validate(request);
        assertThat(path).isEqualTo(allowedDir);
    }

    @Test
    @DisplayName("Should reject invalid job ID with special characters")
    void shouldRejectInvalidJobId() {
        TestExecutionRequest request = TestExecutionRequest.builder("job 123; rm", allowedDir.toString())
                .build();

        assertThatThrownBy(() -> validator.validate(request))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Invalid Job ID format");
    }

    @Test
    @DisplayName("Should reject timeout exceeding maximum limit")
    void shouldRejectExcessiveTimeout() {
        TestExecutionRequest request = TestExecutionRequest.builder("job-1", allowedDir.toString())
                .timeoutSeconds(1000) // exceeds 600
                .build();

        assertThatThrownBy(() -> validator.validate(request))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("exceeds maximum allowed limit");
    }

    @Test
    @DisplayName("Should reject invalid memory format")
    void shouldRejectInvalidMemoryFormat() {
        TestExecutionRequest request = TestExecutionRequest.builder("job-1", allowedDir.toString())
                .memoryLimit("unlimited")
                .build();

        assertThatThrownBy(() -> validator.validate(request))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Invalid memory limit format");
    }
}

