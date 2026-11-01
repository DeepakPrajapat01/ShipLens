package com.aichangeguard.risk.service;

import com.aichangeguard.risk.config.RiskScoringProperties;
import com.aichangeguard.risk.model.AnalysisCompleteness;
import com.aichangeguard.risk.model.EvidenceConfidence;
import com.aichangeguard.risk.model.NormalizedRiskFinding;
import com.aichangeguard.risk.model.RiskCategory;
import com.aichangeguard.risk.model.RiskLevel;
import com.aichangeguard.risk.model.RiskSeverity;
import com.aichangeguard.risk.model.UncertaintyIndicator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RiskScorerTest {

    private RiskScorer scorer;
    private RiskScoringProperties properties;

    @BeforeEach
    void setUp() {
        properties = new RiskScoringProperties();
        scorer = new RiskScorer(properties);
    }

    @Test
    @DisplayName("Calculates LOW risk when total score is within low threshold")
    void calculatesLowRisk() {
        NormalizedRiskFinding finding = new NormalizedRiskFinding(
                "API:LOW",
                RiskCategory.API_COMPATIBILITY,
                RiskSeverity.LOW,
                EvidenceConfidence.HIGH,
                "Compatible API addition",
                "New optional parameter",
                "Controller.java",
                10,
                null,
                "param",
                "GET /api/items",
                true,
                5,
                1
        );

        RiskScorer.ScoringResult result = scorer.score(List.of(finding), Collections.emptyList());

        assertThat(result.riskLevel()).isEqualTo(RiskLevel.LOW);
        assertThat(result.overallScore()).isEqualTo(5);
        assertThat(result.completeness()).isEqualTo(AnalysisCompleteness.COMPLETE);
    }

    @Test
    @DisplayName("Calculates MODERATE risk when score is between 30 and 69")
    void calculatesModerateRisk() {
        NormalizedRiskFinding finding1 = new NormalizedRiskFinding(
                "API:BREAKING",
                RiskCategory.API_COMPATIBILITY,
                RiskSeverity.CRITICAL,
                EvidenceConfidence.HIGH,
                "Breaking API change",
                "Removed endpoint",
                "Controller.java",
                25,
                null,
                null,
                "DELETE /api/items",
                true,
                35,
                1
        );

        RiskScorer.ScoringResult result = scorer.score(List.of(finding1), Collections.emptyList());

        assertThat(result.riskLevel()).isEqualTo(RiskLevel.MODERATE);
        assertThat(result.overallScore()).isEqualTo(35);
    }

    @Test
    @DisplayName("Calculates HIGH risk when multiple high-severity findings exceed threshold 70")
    void calculatesHighRisk() {
        NormalizedRiskFinding f1 = new NormalizedRiskFinding(
                "API:BREAKING",
                RiskCategory.API_COMPATIBILITY,
                RiskSeverity.CRITICAL,
                EvidenceConfidence.HIGH,
                "Breaking API",
                "Removed endpoint",
                "Controller.java",
                20, null, null, "DELETE", true, 35, 1
        );

        NormalizedRiskFinding f2 = new NormalizedRiskFinding(
                "TEST:FAIL",
                RiskCategory.TEST_FAILURE,
                RiskSeverity.CRITICAL,
                EvidenceConfidence.HIGH,
                "Test failure",
                "Regression failure",
                "Test.java",
                15, null, null, "assert failed", true, 40, 1
        );

        RiskScorer.ScoringResult result = scorer.score(List.of(f1, f2), Collections.emptyList());

        assertThat(result.riskLevel()).isEqualTo(RiskLevel.HIGH);
        assertThat(result.overallScore()).isEqualTo(75);
    }

    @Test
    @DisplayName("Applies category caps so a single category cannot saturate score")
    void appliesCategoryCaps() {
        // 5 breaking API changes, each 35 points = 175 points
        List<NormalizedRiskFinding> findings = List.of(
                new NormalizedRiskFinding("A1", RiskCategory.API_COMPATIBILITY, RiskSeverity.CRITICAL, EvidenceConfidence.HIGH, "T", "D", null, null, null, null, "E", true, 35, 1),
                new NormalizedRiskFinding("A2", RiskCategory.API_COMPATIBILITY, RiskSeverity.CRITICAL, EvidenceConfidence.HIGH, "T", "D", null, null, null, null, "E", true, 35, 1),
                new NormalizedRiskFinding("A3", RiskCategory.API_COMPATIBILITY, RiskSeverity.CRITICAL, EvidenceConfidence.HIGH, "T", "D", null, null, null, null, "E", true, 35, 1),
                new NormalizedRiskFinding("A4", RiskCategory.API_COMPATIBILITY, RiskSeverity.CRITICAL, EvidenceConfidence.HIGH, "T", "D", null, null, null, null, "E", true, 35, 1),
                new NormalizedRiskFinding("A5", RiskCategory.API_COMPATIBILITY, RiskSeverity.CRITICAL, EvidenceConfidence.HIGH, "T", "D", null, null, null, null, "E", true, 35, 1)
        );

        RiskScorer.ScoringResult result = scorer.score(findings, Collections.emptyList());

        // API cap is 40!
        assertThat(result.categoryScores().get(RiskCategory.API_COMPATIBILITY)).isEqualTo(40);
        assertThat(result.overallScore()).isEqualTo(40);
        assertThat(result.riskLevel()).isEqualTo(RiskLevel.MODERATE); // Capped at 40, does not artificially reach HIGH (70)
    }

    @Test
    @DisplayName("Forces INDETERMINATE level when critical uncertainty indicator is present")
    void forcesIndeterminateWhenCriticalUncertaintyPresent() {
        UncertaintyIndicator indicator = new UncertaintyIndicator(
                RiskCategory.TEST_FAILURE,
                "Docker socket unavailable",
                "Cannot execute container tests",
                true // critical blocker
        );

        RiskScorer.ScoringResult result = scorer.score(Collections.emptyList(), List.of(indicator));

        assertThat(result.riskLevel()).isEqualTo(RiskLevel.INDETERMINATE);
        assertThat(result.overallScore()).isEqualTo(-1);
        assertThat(result.completeness()).isEqualTo(AnalysisCompleteness.INSUFFICIENT);
    }
}

