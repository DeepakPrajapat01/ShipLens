package com.aichangeguard.analysis.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;

/**
 * Aggregated report representing Java source parsing results and coverage across a repository.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SourceAnalysisReport(
        String repositoryPath,
        int totalFilesAttempted,
        int totalFilesSuccessful,
        int totalFilesFailed,
        double parserCoveragePercent,
        List<ParsedFileMetadata> parsedFiles,
        List<ParsingDiagnostic> diagnostics,
        Instant analyzedAt,
        List<String> limitations
) {}
