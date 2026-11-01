package com.aichangeguard.risk.service;

import com.aichangeguard.dependency.model.GraphReport;
import com.aichangeguard.dependency.model.ImpactAnalysisResult;
import com.aichangeguard.impact.database.model.DatabaseChangeFinding;
import com.aichangeguard.impact.database.model.DatabaseChangeSeverity;
import com.aichangeguard.impact.database.model.DatabaseChangeType;
import com.aichangeguard.impact.database.model.DatabaseImpactReport;
import com.aichangeguard.impact.database.model.EvidenceCategory;
import com.aichangeguard.impact.model.ApiChangeFinding;
import com.aichangeguard.impact.model.ApiChangeSeverity;
import com.aichangeguard.impact.model.ApiChangeType;
import com.aichangeguard.impact.model.ApiImpactReport;
import com.aichangeguard.integration.docker.model.TestExecutionResult;
import com.aichangeguard.integration.docker.model.TestExecutionStatus;
import com.aichangeguard.repository.diff.model.ChangedFileEntry;
import com.aichangeguard.repository.diff.model.DiffAnalysisResult;
import com.aichangeguard.repository.diff.model.FileChangeType;
import com.aichangeguard.risk.config.RiskScoringProperties;
import com.aichangeguard.risk.model.AnalysisCompleteness;
import com.aichangeguard.risk.model.RecommendationPriority;
import com.aichangeguard.risk.model.RiskLevel;
import com.aichangeguard.risk.model.RiskReport;
import com.aichangeguard.testing.model.TestSelectionReport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ProductionRiskAssessmentEngineTest {

    private ProductionRiskAssessmentEngine engine;

    @BeforeEach
    void setUp() {
        RiskScoringProperties properties = new RiskScoringProperties();
        FindingNormalizer normalizer = new FindingNormalizer(properties);
        RiskScorer scorer = new RiskScorer(properties);
        RecommendationEngine recommendationEngine = new RecommendationEngine();
        engine = new ProductionRiskAssessmentEngine(normalizer, scorer, recommendationEngine, properties);
    }

    @Test
    @DisplayName("Evaluates clean additive change as LOW risk with complete status")
    void evaluatesLowRiskChange() {
        DiffAnalysisResult diff = new DiffAnalysisResult(
                "/repo", "main", "feature-1", "abc", "def",
                List.of(new ChangedFileEntry(FileChangeType.MODIFIED, "src/main/Order.java", "src/main/Order.java", true, false, 5, 0, Collections.emptyList(), Collections.emptyList())),
                1, 1, 5, 0, true, false, Instant.now(), Collections.emptyList(), Collections.emptyList()
        );

        ApiChangeFinding apiFinding = new ApiChangeFinding(
                ApiChangeType.ENDPOINT_ADDED,
                ApiChangeSeverity.COMPATIBLE,
                "New endpoint added",
                null, "GET:/api/v1/orders/summary",
                "OrderController", "/api/v1/orders/summary",
                null, "GET",
                "OrderController.java", 30
        );
        ApiImpactReport apiReport = new ApiImpactReport(
                "/repo", "main", "feature-1",
                Collections.emptyList(), Collections.emptyList(),
                List.of(apiFinding),
                0, 0, 1, 0, 1, 1, true, Instant.now(), Collections.emptyList(), Collections.emptyList()
        );

        TestExecutionResult execResult = new TestExecutionResult(
                "job-1", TestExecutionStatus.PASSED, 0, 5, 5, 0, 0, 0, 1200,
                "OK", "", "cid-1", List.of("OrderTest"), Collections.emptyList(), null, true, Instant.now()
        );

        RiskReport report = engine.assessRisk(
                diff, Collections.emptyList(), null, apiReport, null, null, execResult, null
        );

        assertThat(report.riskLevel()).isEqualTo(RiskLevel.LOW);
        assertThat(report.overallScore()).isLessThanOrEqualTo(29);
        assertThat(report.isIndeterminate()).isFalse();
        assertThat(report.disclaimer()).isEqualTo(RiskReport.SAFETY_DISCLAIMER);
        assertThat(report.recommendations()).anyMatch(r -> r.priority() == RecommendationPriority.LOW);
    }

    @Test
    @DisplayName("Evaluates breaking changes and failing tests as HIGH risk with blocking recommendations")
    void evaluatesHighRiskChange() {
        DiffAnalysisResult diff = new DiffAnalysisResult(
                "/repo", "main", "feature-2", "abc", "def",
                List.of(new ChangedFileEntry(FileChangeType.MODIFIED, "src/main/Order.java", "src/main/Order.java", true, false, 200, 100, Collections.emptyList(), Collections.emptyList())),
                5, 5, 200, 100, true, false, Instant.now(), Collections.emptyList(), Collections.emptyList()
        );

        ApiChangeFinding apiFinding = new ApiChangeFinding(
                ApiChangeType.ENDPOINT_REMOVED,
                ApiChangeSeverity.BREAKING,
                "Deleted endpoint",
                "DELETE:/api/v1/orders", null,
                "OrderController", "/api/v1/orders",
                "DELETE", null,
                "OrderController.java", 50
        );
        ApiImpactReport apiReport = new ApiImpactReport(
                "/repo", "main", "feature-2",
                Collections.emptyList(), Collections.emptyList(),
                List.of(apiFinding),
                1, 0, 0, 0, 1, 1, true, Instant.now(), Collections.emptyList(), Collections.emptyList()
        );

        DatabaseChangeFinding dbFinding = new DatabaseChangeFinding(
                DatabaseChangeType.TABLE_DROPPED,
                DatabaseChangeSeverity.DESTRUCTIVE,
                EvidenceCategory.MIGRATION_BASED,
                "Drop table",
                "Dropped orders table",
                "orders", null, "orders", null,
                "V2__drop.sql", 1, "DROP TABLE orders;", null
        );
        DatabaseImpactReport dbReport = new DatabaseImpactReport(
                "/repo", "main", "feature-2",
                List.of(dbFinding),
                Collections.emptyList(), Collections.emptyList(),
                Collections.emptyList(), Collections.emptyList(),
                Collections.emptyList(),
                1, 0, 0, 0, 0, true, Instant.now(), Collections.emptyList(), Collections.emptyList()
        );

        TestExecutionResult execResult = new TestExecutionResult(
                "job-2", TestExecutionStatus.FAILED, 1, 10, 8, 2, 0, 0, 4500,
                "", "OrderTest failed", "cid-2", List.of("OrderTest"), List.of("OrderTest#testDelete: 404 not found"), null, true, Instant.now()
        );

        RiskReport report = engine.assessRisk(
                diff, Collections.emptyList(), null, apiReport, dbReport, null, execResult, null
        );

        assertThat(report.riskLevel()).isEqualTo(RiskLevel.HIGH);
        assertThat(report.overallScore()).isGreaterThanOrEqualTo(70);
        assertThat(report.recommendations()).anyMatch(r ->
                r.priority() == RecommendationPriority.BLOCKING &&
                r.action().contains("Resolve failing automated regression tests")
        );
        assertThat(report.recommendations()).anyMatch(r ->
                r.priority() == RecommendationPriority.BLOCKING &&
                r.action().contains("destructive database migrations")
        );
    }

    @Test
    @DisplayName("Evaluates infrastructure failure as INDETERMINATE with INSUFFICIENT completeness")
    void evaluatesInfrastructureFailureAsIndeterminate() {
        DiffAnalysisResult diff = new DiffAnalysisResult(
                "/repo", "main", "feature-3", "abc", "def",
                List.of(new ChangedFileEntry(FileChangeType.MODIFIED, "src/main/Order.java", "src/main/Order.java", true, false, 2, 1, Collections.emptyList(), Collections.emptyList())),
                1, 1, 2, 1, true, false, Instant.now(), Collections.emptyList(), Collections.emptyList()
        );

        TestExecutionResult execResult = new TestExecutionResult(
                "job-3", TestExecutionStatus.INFRASTRUCTURE_FAILURE, 1, 0, 0, 0, 0, 0, 200,
                "", "Docker daemon connection failed", null, Collections.emptyList(), Collections.emptyList(),
                "Docker error: daemon not reachable", true, Instant.now()
        );

        RiskReport report = engine.assessRisk(
                diff, Collections.emptyList(), null, null, null, null, execResult, null
        );

        assertThat(report.riskLevel()).isEqualTo(RiskLevel.INDETERMINATE);
        assertThat(report.overallScore()).isEqualTo(-1);
        assertThat(report.completeness()).isEqualTo(AnalysisCompleteness.INSUFFICIENT);
        assertThat(report.recommendations()).anyMatch(r ->
                r.priority() == RecommendationPriority.BLOCKING &&
                r.action().contains("Resolve analysis blockers")
        );
    }
}
