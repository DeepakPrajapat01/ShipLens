package com.aichangeguard.repository.diff.model;

import jakarta.validation.constraints.NotBlank;

/**
 * Request payload to analyze a diff between two Git revisions in a local repository.
 */
public record DiffAnalysisRequest(
        @NotBlank(message = "Repository path cannot be blank")
        String repositoryPath,

        @NotBlank(message = "Base revision cannot be blank")
        String baseRevision,

        @NotBlank(message = "Target revision cannot be blank")
        String targetRevision
) {}
