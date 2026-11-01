package com.aichangeguard.risk.service;

import com.aichangeguard.dependency.model.GraphReport;
import com.aichangeguard.dependency.model.ImpactAnalysisResult;
import com.aichangeguard.impact.database.model.DatabaseImpactReport;
import com.aichangeguard.impact.model.ApiImpactReport;
import com.aichangeguard.integration.docker.model.TestExecutionResult;
import com.aichangeguard.repository.diff.model.DiffAnalysisResult;
import com.aichangeguard.risk.config.RiskScoringProperties;
import com.aichangeguard.risk.model.NormalizedRiskFinding;
import com.aichangeguard.risk.model.Recommendation;
import com.aichangeguard.risk.model.RiskReport;
import com.aichangeguard.risk.model.UncertaintyIndicator;
import com.aichangeguard.testing.model.TestSelectionReport;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Main coordinator service for the ChangeGuard Production Risk Assessment Engine.
 *
 * <p>Aggregates structured reports from all analysis engines into an explainable,
 * evidence-backed risk report.</p>
 */
@Service
public class ProductionRiskAssessmentEngine {

    private final FindingNormalizer normalizer;
    private final RiskScorer scorer;
    private final RecommendationEngine recommendationEngine;
    private final RiskScoringProperties properties;

    public ProductionRiskAssessmentEngine(
            FindingNormalizer normalizer,
            RiskScorer scorer,
            RecommendationEngine recommendationEngine,
            RiskScoringProperties properties
    ) {
        this.normalizer = normalizer;
        this.scorer = scorer;
        this.recommendationEngine = recommendationEngine;
        this.properties = properties;
    }

    /**
     * Executes end-to-end risk assessment over all analysis inputs.
     */
    public RiskReport assessRisk(
            DiffAnalysisResult diffResult,
            List<ImpactAnalysisResult> impactResults,
            GraphReport graphReport,
            ApiImpactReport apiReport,
            DatabaseImpactReport dbReport,
            TestSelectionReport testSelectionReport,
            TestExecutionResult testExecutionResult,
            List<NormalizedRiskFinding> securityFindings
    ) {
        // 1. Normalize and deduplicate findings
        FindingNormalizer.NormalizationResult normalization = normalizer.normalize(
                diffResult,
                impactResults,
                graphReport,
                apiReport,
                dbReport,
                testSelectionReport,
                testExecutionResult,
                securityFindings
        );

        List<NormalizedRiskFinding> findings = normalization.findings();
        List<UncertaintyIndicator> uncertainties = normalization.uncertainties();

        // 2. Score findings and determine RiskLevel
        RiskScorer.ScoringResult scoringResult = scorer.score(findings, uncertainties);

        // 3. Generate Actionable Recommendations
        List<Recommendation> recommendations = recommendationEngine.generateRecommendations(
                scoringResult.riskLevel(),
                findings,
                uncertainties
        );

        // 4. Collect Analysis Limitations across all subsystems
        List<String> combinedLimitations = collectLimitations(
                diffResult,
                graphReport,
                apiReport,
                dbReport,
                testSelectionReport,
                testExecutionResult
        );

        String repositoryPath = diffResult != null ? diffResult.repositoryPath() : "unknown";
        String baseRevision = diffResult != null ? diffResult.baseRevision() : "unknown";
        String targetRevision = diffResult != null ? diffResult.targetRevision() : "unknown";

        return new RiskReport(
                repositoryPath,
                baseRevision,
                targetRevision,
                scoringResult.riskLevel(),
                scoringResult.overallScore(),
                scoringResult.completeness(),
                scoringResult.categoryScores(),
                findings,
                uncertainties,
                recommendations,
                combinedLimitations,
                Instant.now(),
                properties.getModelVersion(),
                RiskReport.SAFETY_DISCLAIMER
        );
    }

    private List<String> collectLimitations(
            DiffAnalysisResult diffResult,
            GraphReport graphReport,
            ApiImpactReport apiReport,
            DatabaseImpactReport dbReport,
            TestSelectionReport testSelectionReport,
            TestExecutionResult testExecutionResult
    ) {
        List<String> limitations = new ArrayList<>();

        if (diffResult != null && diffResult.limitations() != null) {
            limitations.addAll(diffResult.limitations());
        }
        if (graphReport != null && graphReport.limitations() != null) {
            limitations.addAll(graphReport.limitations());
        }
        if (apiReport != null && apiReport.limitations() != null) {
            limitations.addAll(apiReport.limitations());
        }
        if (dbReport != null && dbReport.limitations() != null) {
            limitations.addAll(dbReport.limitations());
        }
        if (testSelectionReport != null && testSelectionReport.limitations() != null) {
            limitations.addAll(testSelectionReport.limitations());
        }
        if (testExecutionResult != null && testExecutionResult.infrastructureError() != null) {
            limitations.add("Docker infrastructure limitation: " + testExecutionResult.infrastructureError());
        }

        return limitations.isEmpty() ? Collections.emptyList() : Collections.unmodifiableList(limitations);
    }
}

