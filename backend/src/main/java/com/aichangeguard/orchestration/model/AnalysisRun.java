package com.aichangeguard.orchestration.model;

import com.aichangeguard.analysis.model.SourceAnalysisReport;
import com.aichangeguard.dependency.model.GraphReport;
import com.aichangeguard.impact.database.model.DatabaseImpactReport;
import com.aichangeguard.impact.model.ApiImpactReport;
import com.aichangeguard.integration.docker.model.TestExecutionResult;
import com.aichangeguard.repository.diff.model.DiffAnalysisResult;
import com.aichangeguard.repository.model.RepositoryInspectionReport;
import com.aichangeguard.risk.model.RiskReport;
import com.aichangeguard.testing.model.TestSelectionReport;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.Collections;
import java.util.List;

/**
 * Complete immutable representation of an analysis run lifecycle and all intermediate outputs.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AnalysisRun(
        String runId,
        String repositoryPath,
        String baseRevision,
        String targetRevision,
        AnalysisRunStatus status,
        AnalysisStage stage,
        int progressPercent,
        Instant createdAt,
        Instant startedAt,
        Instant completedAt,
        String errorMessage,
        List<String> warnings,
        RepositoryInspectionReport inspectionReport,
        DiffAnalysisResult diffResult,
        SourceAnalysisReport sourceReport,
        GraphReport graphReport,
        ApiImpactReport apiReport,
        DatabaseImpactReport dbReport,
        TestSelectionReport testSelectionReport,
        TestExecutionResult testExecutionResult,
        RiskReport riskReport
) {
    public static AnalysisRun pending(String runId, AnalysisRequest request) {
        Instant now = Instant.now();
        return new AnalysisRun(
                runId,
                request.repositoryPath(),
                request.baseRevision(),
                request.targetRevision(),
                AnalysisRunStatus.PENDING,
                AnalysisStage.INITIALIZING,
                0,
                now,
                null,
                null,
                null,
                Collections.emptyList(),
                null, null, null, null, null, null, null, null, null
        );
    }

    public AnalysisRun withProgress(AnalysisStage newStage, int progress) {
        return new AnalysisRun(
                runId, repositoryPath, baseRevision, targetRevision,
                AnalysisRunStatus.RUNNING, newStage, progress,
                createdAt, startedAt != null ? startedAt : Instant.now(),
                null, errorMessage, warnings,
                inspectionReport, diffResult, sourceReport, graphReport,
                apiReport, dbReport, testSelectionReport, testExecutionResult, riskReport
        );
    }

    public AnalysisRun withFailure(String error) {
        Instant now = Instant.now();
        return new AnalysisRun(
                runId, repositoryPath, baseRevision, targetRevision,
                AnalysisRunStatus.FAILED, AnalysisStage.FAILED, 100,
                createdAt, startedAt != null ? startedAt : now,
                now, error, warnings,
                inspectionReport, diffResult, sourceReport, graphReport,
                apiReport, dbReport, testSelectionReport, testExecutionResult, riskReport
        );
    }

    public AnalysisRun withCompletedReports(
            List<String> updatedWarnings,
            RepositoryInspectionReport inspectionReport,
            DiffAnalysisResult diffResult,
            SourceAnalysisReport sourceReport,
            GraphReport graphReport,
            ApiImpactReport apiReport,
            DatabaseImpactReport dbReport,
            TestSelectionReport testSelectionReport,
            TestExecutionResult testExecutionResult,
            RiskReport riskReport
    ) {
        Instant now = Instant.now();
        return new AnalysisRun(
                runId, repositoryPath, baseRevision, targetRevision,
                AnalysisRunStatus.COMPLETED, AnalysisStage.COMPLETED, 100,
                createdAt, startedAt != null ? startedAt : now,
                now, null, updatedWarnings != null ? updatedWarnings : Collections.emptyList(),
                inspectionReport, diffResult, sourceReport, graphReport,
                apiReport, dbReport, testSelectionReport, testExecutionResult, riskReport
        );
    }
}

