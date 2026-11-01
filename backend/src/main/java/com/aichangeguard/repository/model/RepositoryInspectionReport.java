package com.aichangeguard.repository.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;

/**
 * Structured inspection report for a registered local repository.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record RepositoryInspectionReport(
        String repositoryId,
        String repositoryPath,
        String projectName,
        BuildSystem buildSystem,
        InspectionStatus inspectionStatus,
        ProjectMetadata metadata,
        List<SourceDirectoryInfo> sourceDirectories,
        int totalJavaFiles,
        List<String> projectStructure,
        Instant inspectedAt,
        List<String> limitations,
        List<String> warnings
) {}
