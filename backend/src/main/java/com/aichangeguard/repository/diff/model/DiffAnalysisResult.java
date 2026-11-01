package com.aichangeguard.repository.diff.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;

/**
 * Complete structured analysis report for a Git diff.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record DiffAnalysisResult(
        String repositoryPath,
        String baseRevision,
        String targetRevision,
        String baseSha,
        String targetSha,
        List<ChangedFileEntry> changedFiles,
        int totalFilesChanged,
        int totalJavaFilesChanged,
        int totalLinesAdded,
        int totalLinesDeleted,
        boolean isClean,
        boolean isMergeCommit,
        Instant analyzedAt,
        List<String> warnings,
        List<String> limitations
) {}
