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
import com.aichangeguard.risk.model.EvidenceConfidence;
import com.aichangeguard.risk.model.NormalizedRiskFinding;
import com.aichangeguard.risk.model.RiskCategory;
import com.aichangeguard.risk.model.RiskSeverity;
import com.aichangeguard.risk.model.UncertaintyIndicator;
import com.aichangeguard.testing.model.FallbackReason;
import com.aichangeguard.testing.model.TestSelectionReport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class FindingNormalizerTest {

    private FindingNormalizer normalizer;
    private RiskScoringProperties properties;

    @BeforeEach
    void setUp() {
        properties = new RiskScoringProperties();
        normalizer = new FindingNormalizer(properties);
    }

    @Test
    @DisplayName("Normalizes breaking API findings with CRITICAL severity and deterministic confidence")
    void normalizeBreakingApiFinding() {
        ApiChangeFinding apiFinding = new ApiChangeFinding(
                ApiChangeType.ENDPOINT_REMOVED,
                ApiChangeSeverity.BREAKING,
                "Endpoint DELETE /api/v1/orders/{id} was removed",
                "DELETE:/api/v1/orders/{id}",
                null,
                "OrderController",
                "/api/v1/orders/{id}",
                "DELETE",
                null,
                "src/main/java/com/example/OrderController.java",
                45
        );

        ApiImpactReport apiReport = new ApiImpactReport(
                "/repo", "main", "feature",
                Collections.emptyList(), Collections.emptyList(),
                List.of(apiFinding),
                1, 0, 0, 0, 1, 1, true, Instant.now(), Collections.emptyList(), Collections.emptyList()
        );

        FindingNormalizer.NormalizationResult result = normalizer.normalize(
                null, Collections.emptyList(), null, apiReport, null, null, null, null
        );

        assertThat(result.findings()).hasSize(1);
        NormalizedRiskFinding normalized = result.findings().getFirst();
        assertThat(normalized.category()).isEqualTo(RiskCategory.API_COMPATIBILITY);
        assertThat(normalized.severity()).isEqualTo(RiskSeverity.CRITICAL);
        assertThat(normalized.confidence()).isEqualTo(EvidenceConfidence.HIGH);
        assertThat(normalized.isDeterministic()).isTrue();
        assertThat(normalized.scoreContribution()).isEqualTo(35);
        assertThat(normalized.sourceFile()).isEqualTo("src/main/java/com/example/OrderController.java");
        assertThat(normalized.sourceLine()).isEqualTo(45);
    }

    @Test
    @DisplayName("Normalizes destructive database migration findings with CRITICAL severity")
    void normalizeDestructiveDatabaseFinding() {
        DatabaseChangeFinding dbFinding = new DatabaseChangeFinding(
                DatabaseChangeType.TABLE_DROPPED,
                DatabaseChangeSeverity.DESTRUCTIVE,
                EvidenceCategory.MIGRATION_BASED,
                "Drop Table",
                "Table 'customer_tokens' was permanently dropped",
                "customer_tokens",
                null,
                "customer_tokens",
                null,
                "src/main/resources/db/migration/V2__drop.sql",
                12,
                "DROP TABLE customer_tokens;",
                "Ensure data is archived before dropping table."
        );

        DatabaseImpactReport dbReport = new DatabaseImpactReport(
                "/repo", "main", "feature",
                List.of(dbFinding),
                Collections.emptyList(), Collections.emptyList(),
                Collections.emptyList(), Collections.emptyList(),
                Collections.emptyList(),
                1, 0, 0, 0, 0, true, Instant.now(), Collections.emptyList(), Collections.emptyList()
        );

        FindingNormalizer.NormalizationResult result = normalizer.normalize(
                null, Collections.emptyList(), null, null, dbReport, null, null, null
        );

        assertThat(result.findings()).hasSize(1);
        NormalizedRiskFinding normalized = result.findings().getFirst();
        assertThat(normalized.category()).isEqualTo(RiskCategory.DATABASE_IMPACT);
        assertThat(normalized.severity()).isEqualTo(RiskSeverity.CRITICAL);
        assertThat(normalized.scoreContribution()).isEqualTo(35);
        assertThat(normalized.sourceFile()).isEqualTo("src/main/resources/db/migration/V2__drop.sql");
    }

    @Test
    @DisplayName("Deduplicates multiple identical findings without inflating score")
    void deduplicatesFindings() {
        ApiChangeFinding api1 = new ApiChangeFinding(
                ApiChangeType.REQUIRED_PARAMETER_ADDED,
                ApiChangeSeverity.POTENTIALLY_BREAKING,
                "Added required param",
                "GET:/api/users",
                "GET:/api/users",
                "UserController",
                "tenantId",
                null,
                "String",
                "UserController.java",
                20
        );

        ApiChangeFinding api2 = new ApiChangeFinding(
                ApiChangeType.REQUIRED_PARAMETER_ADDED,
                ApiChangeSeverity.BREAKING, // Higher severity duplicate
                "Added required param",
                "GET:/api/users",
                "GET:/api/users",
                "UserController",
                "tenantId",
                null,
                "String",
                "UserController.java",
                20
        );

        ApiImpactReport apiReport = new ApiImpactReport(
                "/repo", "main", "feature",
                Collections.emptyList(), Collections.emptyList(),
                List.of(api1, api2),
                1, 1, 0, 0, 1, 1, true, Instant.now(), Collections.emptyList(), Collections.emptyList()
        );

        FindingNormalizer.NormalizationResult result = normalizer.normalize(
                null, Collections.emptyList(), null, apiReport, null, null, null, null
        );

        // Deduplication retains the higher severity (CRITICAL) and tracks duplicateCount = 2
        assertThat(result.findings()).hasSize(1);
        NormalizedRiskFinding normalized = result.findings().getFirst();
        assertThat(normalized.severity()).isEqualTo(RiskSeverity.CRITICAL);
        assertThat(normalized.duplicateCount()).isEqualTo(2);
        assertThat(normalized.scoreContribution()).isEqualTo(35); // Not inflated to 55!
    }

    @Test
    @DisplayName("Detects Docker infrastructure failure and flags indeterminate uncertainty")
    void flagsDockerInfrastructureFailureAsIndeterminate() {
        TestExecutionResult execResult = new TestExecutionResult(
                "job-1",
                TestExecutionStatus.INFRASTRUCTURE_FAILURE,
                1,
                0, 0, 0, 0, 0,
                100,
                "", "Docker daemon connection failed",
                null,
                Collections.emptyList(),
                Collections.emptyList(),
                "Cannot connect to the Docker daemon at unix:///var/run/docker.sock",
                true,
                Instant.now()
        );

        FindingNormalizer.NormalizationResult result = normalizer.normalize(
                null, Collections.emptyList(), null, null, null, null, execResult, null
        );

        assertThat(result.uncertainties()).anyMatch(u ->
                u.category() == RiskCategory.TEST_FAILURE &&
                u.causesIndeterminate() &&
                u.reason().contains("Cannot connect to the Docker daemon")
        );
    }

    @Test
    @DisplayName("Flags missing git diff as critical uncertainty")
    void flagsMissingDiffAsCriticalUncertainty() {
        FindingNormalizer.NormalizationResult result = normalizer.normalize(
                null, Collections.emptyList(), null, null, null, null, null, null
        );

        assertThat(result.uncertainties()).anyMatch(u ->
                u.category() == RiskCategory.CODE_CHANGE_COMPLEXITY &&
                u.causesIndeterminate()
        );
    }
}
