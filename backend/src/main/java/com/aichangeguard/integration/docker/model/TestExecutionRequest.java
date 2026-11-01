package com.aichangeguard.integration.docker.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * Execution request specification for running selected tests inside an isolated Docker container.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TestExecutionRequest(
        String jobId,
        String repositoryPath,
        String revisionSha,
        List<String> selectedTests,
        int timeoutSeconds,
        String cpuLimit,
        String memoryLimit,
        boolean networkDisabled,
        String dockerImage,
        List<String> additionalMavenArgs
) {
    public static final String DEFAULT_IMAGE = "maven:3.9-eclipse-temurin-21";
    public static final int DEFAULT_TIMEOUT_SECONDS = 180;
    public static final String DEFAULT_CPU_LIMIT = "2.0";
    public static final String DEFAULT_MEMORY_LIMIT = "2g";

    public static Builder builder(String jobId, String repositoryPath) {
        return new Builder(jobId, repositoryPath);
    }

    public static class Builder {
        private final String jobId;
        private final String repositoryPath;
        private String revisionSha;
        private List<String> selectedTests = List.of();
        private int timeoutSeconds = DEFAULT_TIMEOUT_SECONDS;
        private String cpuLimit = DEFAULT_CPU_LIMIT;
        private String memoryLimit = DEFAULT_MEMORY_LIMIT;
        private boolean networkDisabled = true;
        private String dockerImage = DEFAULT_IMAGE;
        private List<String> additionalMavenArgs = List.of();

        public Builder(String jobId, String repositoryPath) {
            this.jobId = jobId;
            this.repositoryPath = repositoryPath;
        }

        public Builder revisionSha(String revisionSha) {
            this.revisionSha = revisionSha;
            return this;
        }

        public Builder selectedTests(List<String> selectedTests) {
            this.selectedTests = selectedTests != null ? selectedTests : List.of();
            return this;
        }

        public Builder timeoutSeconds(int timeoutSeconds) {
            this.timeoutSeconds = timeoutSeconds;
            return this;
        }

        public Builder cpuLimit(String cpuLimit) {
            this.cpuLimit = cpuLimit;
            return this;
        }

        public Builder memoryLimit(String memoryLimit) {
            this.memoryLimit = memoryLimit;
            return this;
        }

        public Builder networkDisabled(boolean networkDisabled) {
            this.networkDisabled = networkDisabled;
            return this;
        }

        public Builder dockerImage(String dockerImage) {
            this.dockerImage = dockerImage != null ? dockerImage : DEFAULT_IMAGE;
            return this;
        }

        public Builder additionalMavenArgs(List<String> args) {
            this.additionalMavenArgs = args != null ? args : List.of();
            return this;
        }

        public TestExecutionRequest build() {
            return new TestExecutionRequest(
                    jobId, repositoryPath, revisionSha, selectedTests,
                    timeoutSeconds, cpuLimit, memoryLimit, networkDisabled,
                    dockerImage, additionalMavenArgs
            );
        }
    }
}

