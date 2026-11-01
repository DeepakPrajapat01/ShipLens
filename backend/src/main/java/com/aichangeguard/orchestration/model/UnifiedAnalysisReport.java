package com.aichangeguard.orchestration.model;

import com.aichangeguard.risk.model.NormalizedRiskFinding;
import com.aichangeguard.risk.model.Recommendation;
import com.aichangeguard.risk.model.RiskLevel;
import com.aichangeguard.risk.model.RiskReport;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.Collections;
import java.util.List;

/**
 * Unified, client-facing comprehensive deliverable combining all analysis insights.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record UnifiedAnalysisReport(
        String runId,
        String repositoryPath,
        String baseRevision,
        String targetRevision,
        AnalysisRunStatus status,
        RiskLevel riskLevel,
        int overallScore,
        int filesChanged,
        int linesAdded,
        int linesDeleted,
        int selectedTestsCount,
        boolean testExecutionAttempted,
        boolean testExecutionPassed,
        List<NormalizedRiskFinding> findings,
        List<Recommendation> recommendations,
        List<String> limitations,
        List<String> warnings,
        Instant completedAt,
        String disclaimer
) {
    public static UnifiedAnalysisReport from(AnalysisRun run) {
        RiskReport risk = run.riskReport();

        RiskLevel level = risk != null ? risk.riskLevel() : RiskLevel.INDETERMINATE;
        int score = risk != null ? risk.overallScore() : -1;
        List<NormalizedRiskFinding> findings = risk != null ? risk.findings() : Collections.emptyList();
        List<Recommendation> recommendations = risk != null ? risk.recommendations() : Collections.emptyList();
        List<String> limitations = risk != null ? risk.limitations() : Collections.emptyList();

        int filesChanged = run.diffResult() != null ? run.diffResult().totalFilesChanged() : 0;
        int linesAdded = run.diffResult() != null ? run.diffResult().totalLinesAdded() : 0;
        int linesDeleted = run.diffResult() != null ? run.diffResult().totalLinesDeleted() : 0;

        int selectedTests = run.testSelectionReport() != null ? run.testSelectionReport().selectedCount() : 0;
        boolean execAttempted = run.testExecutionResult() != null;
        boolean execPassed = run.testExecutionResult() != null && run.testExecutionResult().isSuccess();

        return new UnifiedAnalysisReport(
                run.runId(),
                run.repositoryPath(),
                run.baseRevision(),
                run.targetRevision(),
                run.status(),
                level,
                score,
                filesChanged,
                linesAdded,
                linesDeleted,
                selectedTests,
                execAttempted,
                execPassed,
                findings,
                recommendations,
                limitations,
                run.warnings() != null ? run.warnings() : Collections.emptyList(),
                run.completedAt(),
                RiskReport.SAFETY_DISCLAIMER
        );
    }
}

