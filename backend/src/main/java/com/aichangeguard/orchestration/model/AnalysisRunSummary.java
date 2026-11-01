package com.aichangeguard.orchestration.model;

import com.aichangeguard.risk.model.RiskLevel;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

/**
 * Lightweight overview of an analysis run for listing history.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AnalysisRunSummary(
        String runId,
        String repositoryPath,
        String baseRevision,
        String targetRevision,
        AnalysisRunStatus status,
        AnalysisStage stage,
        int progressPercent,
        RiskLevel riskLevel,
        int overallScore,
        Instant createdAt,
        Instant completedAt,
        String errorMessage
) {
    public static AnalysisRunSummary from(AnalysisRun run) {
        RiskLevel level = run.riskReport() != null ? run.riskReport().riskLevel() : null;
        int score = run.riskReport() != null ? run.riskReport().overallScore() : -1;
        return new AnalysisRunSummary(
                run.runId(),
                run.repositoryPath(),
                run.baseRevision(),
                run.targetRevision(),
                run.status(),
                run.stage(),
                run.progressPercent(),
                level,
                score,
                run.createdAt(),
                run.completedAt(),
                run.errorMessage()
        );
    }
}

