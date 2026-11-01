package com.aichangeguard.orchestration.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotBlank;

/**
 * Request payload to initiate an end-to-end ChangeGuard analysis pipeline run.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AnalysisRequest(
        @NotBlank(message = "repositoryPath must not be blank")
        String repositoryPath,

        @NotBlank(message = "baseRevision must not be blank")
        String baseRevision,

        @NotBlank(message = "targetRevision must not be blank")
        String targetRevision,

        /** Whether to execute selected tests in an isolated Docker container when permitted. */
        boolean executeTests
) {
    public AnalysisRequest(String repositoryPath, String baseRevision, String targetRevision) {
        this(repositoryPath, baseRevision, targetRevision, false);
    }
}

