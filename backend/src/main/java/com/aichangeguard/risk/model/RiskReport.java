package com.aichangeguard.risk.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Complete, structured production risk assessment report for a code change.
 *
 * <p>Presents an explainable risk evaluation aggregating diff analysis, dependency impact,
 * API contracts, database migrations, and isolated test execution results.</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record RiskReport(
        String repositoryPath,
        String baseRevision,
        String targetRevision,
        RiskLevel riskLevel,
        int overallScore,
        AnalysisCompleteness completeness,
        Map<RiskCategory, Integer> scoreByCategory,
        List<NormalizedRiskFinding> findings,
        List<UncertaintyIndicator> uncertaintyIndicators,
        List<Recommendation> recommendations,
        List<String> limitations,
        Instant analyzedAt,
        String modelVersion,
        String disclaimer
) {
    public static final String SAFETY_DISCLAIMER =
            "This risk assessment provides automated decision support based on static and dynamic analysis. " +
            "It cannot guarantee the absence of defects, regressions, or production incidents.";

    public int totalFindingsCount() {
        return findings != null ? findings.size() : 0;
    }

    public boolean isIndeterminate() {
        return riskLevel == RiskLevel.INDETERMINATE;
    }
}

