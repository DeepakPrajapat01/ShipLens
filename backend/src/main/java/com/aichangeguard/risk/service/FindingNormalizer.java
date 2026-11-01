package com.aichangeguard.risk.service;

import com.aichangeguard.dependency.model.GraphReport;
import com.aichangeguard.dependency.model.ImpactAnalysisResult;
import com.aichangeguard.impact.database.model.DatabaseChangeFinding;
import com.aichangeguard.impact.database.model.DatabaseImpactReport;
import com.aichangeguard.impact.model.ApiChangeFinding;
import com.aichangeguard.impact.model.ApiImpactReport;
import com.aichangeguard.integration.docker.model.TestExecutionResult;
import com.aichangeguard.integration.docker.model.TestExecutionStatus;
import com.aichangeguard.repository.diff.model.DiffAnalysisResult;
import com.aichangeguard.risk.config.RiskScoringProperties;
import com.aichangeguard.risk.model.EvidenceConfidence;
import com.aichangeguard.risk.model.NormalizedRiskFinding;
import com.aichangeguard.risk.model.RiskCategory;
import com.aichangeguard.risk.model.RiskSeverity;
import com.aichangeguard.risk.model.UncertaintyIndicator;
import com.aichangeguard.testing.model.TestSelectionReport;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Normalizes, validates, and deduplicates findings from heterogeneous analysis engines.
 */
@Service
public class FindingNormalizer {

    private final RiskScoringProperties properties;

    public FindingNormalizer(RiskScoringProperties properties) {
        this.properties = properties;
    }

    /**
     * Normalizes all input reports into deduplicated risk findings and uncertainty indicators.
     */
    public NormalizationResult normalize(
            DiffAnalysisResult diffResult,
            List<ImpactAnalysisResult> impactResults,
            GraphReport graphReport,
            ApiImpactReport apiReport,
            DatabaseImpactReport dbReport,
            TestSelectionReport testSelectionReport,
            TestExecutionResult testExecutionResult,
            List<NormalizedRiskFinding> securityFindings
    ) {
        Map<String, NormalizedRiskFinding> deduplicatedMap = new LinkedHashMap<>();
        List<UncertaintyIndicator> uncertainties = new ArrayList<>();

        // 1. API Compatibility
        normalizeApiFindings(apiReport, deduplicatedMap, uncertainties);

        // 2. Database Impact
        normalizeDatabaseFindings(dbReport, deduplicatedMap, uncertainties);

        // 3. Diff Analysis / Code Change Complexity
        normalizeDiffFindings(diffResult, deduplicatedMap, uncertainties);

        // 4. Dependency Blast Radius & Cycles
        normalizeDependencyFindings(impactResults, graphReport, deduplicatedMap, uncertainties);

        // 5. Test Selection & Mapping Quality
        normalizeTestSelectionFindings(testSelectionReport, deduplicatedMap, uncertainties);

        // 6. Test Execution Results
        normalizeTestExecutionFindings(testExecutionResult, deduplicatedMap, uncertainties);

        // 7. Security Findings (if provided)
        if (securityFindings != null) {
            for (NormalizedRiskFinding finding : securityFindings) {
                mergeFinding(deduplicatedMap, finding);
            }
        }

        return new NormalizationResult(new ArrayList<>(deduplicatedMap.values()), uncertainties);
    }

    private void normalizeApiFindings(
            ApiImpactReport apiReport,
            Map<String, NormalizedRiskFinding> map,
            List<UncertaintyIndicator> uncertainties
    ) {
        if (apiReport == null) {
            uncertainties.add(new UncertaintyIndicator(
                    RiskCategory.API_COMPATIBILITY,
                    "REST API impact analysis was not provided",
                    "Potential API contract breakages cannot be ruled out.",
                    false
            ));
            return;
        }

        if (!apiReport.isComplete()) {
            uncertainties.add(new UncertaintyIndicator(
                    RiskCategory.API_COMPATIBILITY,
                    "REST API impact analysis was incomplete",
                    "Some controller or endpoint changes may not have been evaluated.",
                    false
            ));
        }

        if (apiReport.findings() == null) {
            return;
        }

        for (ApiChangeFinding finding : apiReport.findings()) {
            RiskSeverity severity;
            EvidenceConfidence confidence = EvidenceConfidence.HIGH;

            switch (finding.severity()) {
                case BREAKING -> severity = RiskSeverity.CRITICAL;
                case POTENTIALLY_BREAKING -> severity = RiskSeverity.HIGH;
                case COMPATIBLE -> severity = RiskSeverity.LOW;
                case UNKNOWN -> {
                    severity = RiskSeverity.MEDIUM;
                    confidence = EvidenceConfidence.LOW;
                }
                default -> severity = RiskSeverity.INFO;
            }

            int score = calculateScore(severity, confidence);
            String target = finding.targetEndpointId() != null ? finding.targetEndpointId() : finding.baseEndpointId();
            String fingerprint = "API:" + (finding.controllerClass() != null ? finding.controllerClass() : "Controller")
                    + ":" + (target != null ? target : "")
                    + ":" + (finding.changedElement() != null ? finding.changedElement() : "")
                    + ":" + finding.changeType();

            NormalizedRiskFinding normalized = new NormalizedRiskFinding(
                    fingerprint,
                    RiskCategory.API_COMPATIBILITY,
                    severity,
                    confidence,
                    "API Change: " + finding.changeType(),
                    finding.description(),
                    finding.sourceFile(),
                    finding.sourceLine() > 0 ? finding.sourceLine() : null,
                    finding.baseValue(),
                    finding.targetValue(),
                    "Endpoint: " + target + ", Element: " + finding.changedElement(),
                    confidence.isDeterministic(),
                    score,
                    1
            );

            mergeFinding(map, normalized);
        }
    }

    private void normalizeDatabaseFindings(
            DatabaseImpactReport dbReport,
            Map<String, NormalizedRiskFinding> map,
            List<UncertaintyIndicator> uncertainties
    ) {
        if (dbReport == null) {
            uncertainties.add(new UncertaintyIndicator(
                    RiskCategory.DATABASE_IMPACT,
                    "Database impact analysis was not provided",
                    "Database schema migrations or entity incompatibilities cannot be ruled out.",
                    false
            ));
            return;
        }

        if (!dbReport.isComplete()) {
            uncertainties.add(new UncertaintyIndicator(
                    RiskCategory.DATABASE_IMPACT,
                    "Database impact analysis was incomplete",
                    "Some Flyway migrations or entity mappings could not be fully analyzed.",
                    false
            ));
        }

        if (dbReport.findings() == null) {
            return;
        }

        for (DatabaseChangeFinding finding : dbReport.findings()) {
            RiskSeverity severity;
            EvidenceConfidence confidence;

            switch (finding.severity()) {
                case DESTRUCTIVE -> {
                    severity = RiskSeverity.CRITICAL;
                    confidence = EvidenceConfidence.HIGH;
                }
                case POTENTIALLY_BREAKING -> {
                    severity = RiskSeverity.HIGH;
                    confidence = EvidenceConfidence.HIGH;
                }
                case NON_BREAKING -> {
                    severity = RiskSeverity.LOW;
                    confidence = EvidenceConfidence.HIGH;
                }
                case INFORMATIONAL -> {
                    severity = RiskSeverity.INFO;
                    confidence = EvidenceConfidence.HIGH;
                }
                case UNKNOWN -> {
                    severity = RiskSeverity.MEDIUM;
                    confidence = EvidenceConfidence.LOW;
                }
                default -> {
                    severity = RiskSeverity.INFO;
                    confidence = EvidenceConfidence.UNKNOWN;
                }
            }

            int score = calculateScore(severity, confidence);
            String table = finding.affectedTable() != null ? finding.affectedTable() : "SCHEMA";
            String col = finding.affectedColumn() != null ? finding.affectedColumn() : "";
            String fingerprint = "DB:" + table + ":" + col + ":" + finding.changeType();

            NormalizedRiskFinding normalized = new NormalizedRiskFinding(
                    fingerprint,
                    RiskCategory.DATABASE_IMPACT,
                    severity,
                    confidence,
                    finding.title() != null ? finding.title() : ("Database Change: " + finding.changeType()),
                    finding.description(),
                    finding.sourceFile(),
                    finding.sourceLine() > 0 ? finding.sourceLine() : null,
                    finding.baseValue(),
                    finding.targetValue(),
                    finding.rawEvidence() != null ? finding.rawEvidence() : ("Table: " + table + ", Column: " + col),
                    confidence.isDeterministic(),
                    score,
                    1
            );

            mergeFinding(map, normalized);
        }
    }

    private void normalizeDiffFindings(
            DiffAnalysisResult diffResult,
            Map<String, NormalizedRiskFinding> map,
            List<UncertaintyIndicator> uncertainties
    ) {
        if (diffResult == null) {
            uncertainties.add(new UncertaintyIndicator(
                    RiskCategory.CODE_CHANGE_COMPLEXITY,
                    "Git diff analysis was not provided",
                    "Code change size and surface area could not be determined.",
                    true // Critical missing input!
            ));
            return;
        }

        int totalLinesChanged = diffResult.totalLinesAdded() + diffResult.totalLinesDeleted();

        if (totalLinesChanged > 1000 || diffResult.totalFilesChanged() > 30) {
            RiskSeverity severity = RiskSeverity.HIGH;
            int score = calculateScore(severity, EvidenceConfidence.HIGH);
            NormalizedRiskFinding finding = new NormalizedRiskFinding(
                    "DIFF:HIGH_COMPLEXITY",
                    RiskCategory.CODE_CHANGE_COMPLEXITY,
                    severity,
                    EvidenceConfidence.HIGH,
                    "Very Large Code Surface Area",
                    String.format("Change modifies %d files with %d lines changed (+%d/-%d). High risk of unpredicted side-effects.",
                            diffResult.totalFilesChanged(), totalLinesChanged, diffResult.totalLinesAdded(), diffResult.totalLinesDeleted()),
                    null,
                    null,
                    null,
                    null,
                    "Files: " + diffResult.totalFilesChanged() + ", Lines: " + totalLinesChanged,
                    true,
                    score,
                    1
            );
            mergeFinding(map, finding);
        } else if (totalLinesChanged > 300 || diffResult.totalFilesChanged() > 10) {
            RiskSeverity severity = RiskSeverity.MEDIUM;
            int score = calculateScore(severity, EvidenceConfidence.HIGH);
            NormalizedRiskFinding finding = new NormalizedRiskFinding(
                    "DIFF:MODERATE_COMPLEXITY",
                    RiskCategory.CODE_CHANGE_COMPLEXITY,
                    severity,
                    EvidenceConfidence.HIGH,
                    "Moderate Code Surface Area",
                    String.format("Change modifies %d files with %d lines changed (+%d/-%d).",
                            diffResult.totalFilesChanged(), totalLinesChanged, diffResult.totalLinesAdded(), diffResult.totalLinesDeleted()),
                    null,
                    null,
                    null,
                    null,
                    "Files: " + diffResult.totalFilesChanged() + ", Lines: " + totalLinesChanged,
                    true,
                    score,
                    1
            );
            mergeFinding(map, finding);
        }

        if (diffResult.warnings() != null && !diffResult.warnings().isEmpty()) {
            for (String warning : diffResult.warnings()) {
                uncertainties.add(new UncertaintyIndicator(
                        RiskCategory.CODE_CHANGE_COMPLEXITY,
                        "Git diff warning: " + warning,
                        "Diff analysis may have missed some changed hunks or binary files.",
                        false
                ));
            }
        }
    }

    private void normalizeDependencyFindings(
            List<ImpactAnalysisResult> impactResults,
            GraphReport graphReport,
            Map<String, NormalizedRiskFinding> map,
            List<UncertaintyIndicator> uncertainties
    ) {
        if (graphReport != null && graphReport.unresolvedInvocationsCount() > 0) {
            uncertainties.add(new UncertaintyIndicator(
                    RiskCategory.ANALYSIS_UNCERTAINTY,
                    "Dependency graph contains " + graphReport.unresolvedInvocationsCount() + " unresolved invocations",
                    "Dynamic dispatch, reflection, or external library calls could not be resolved statically.",
                    false
            ));
        }

        if (impactResults == null || impactResults.isEmpty()) {
            return;
        }

        for (ImpactAnalysisResult impact : impactResults) {
            int callerCount = impact.affectedMethodIds() != null ? impact.affectedMethodIds().size() : 0;
            if (callerCount >= 10) {
                RiskSeverity severity = RiskSeverity.HIGH;
                int score = calculateScore(severity, EvidenceConfidence.HIGH);
                NormalizedRiskFinding finding = new NormalizedRiskFinding(
                        "DEP:BLAST_RADIUS:" + impact.changedSymbolId(),
                        RiskCategory.DEPENDENCY_IMPACT,
                        severity,
                        EvidenceConfidence.HIGH,
                        "Large Blast Radius for Changed Symbol",
                        String.format("Symbol '%s' directly or transitively affects %d callers across depth %d.",
                                impact.changedSymbolId(), callerCount, impact.maxDepthReached()),
                        null,
                        null,
                        null,
                        null,
                        "Affected callers: " + callerCount + ", Max depth: " + impact.maxDepthReached(),
                        true,
                        score,
                        1
                );
                mergeFinding(map, finding);
            } else if (callerCount >= 3) {
                RiskSeverity severity = RiskSeverity.MEDIUM;
                int score = calculateScore(severity, EvidenceConfidence.HIGH);
                NormalizedRiskFinding finding = new NormalizedRiskFinding(
                        "DEP:BLAST_RADIUS:" + impact.changedSymbolId(),
                        RiskCategory.DEPENDENCY_IMPACT,
                        severity,
                        EvidenceConfidence.HIGH,
                        "Moderate Blast Radius for Changed Symbol",
                        String.format("Symbol '%s' affects %d callers across depth %d.",
                                impact.changedSymbolId(), callerCount, impact.maxDepthReached()),
                        null,
                        null,
                        null,
                        null,
                        "Affected callers: " + callerCount,
                        true,
                        score,
                        1
                );
                mergeFinding(map, finding);
            }

            if (impact.cyclesEncountered() > 0) {
                RiskSeverity severity = RiskSeverity.MEDIUM;
                int score = calculateScore(severity, EvidenceConfidence.HIGH);
                NormalizedRiskFinding finding = new NormalizedRiskFinding(
                        "DEP:CYCLE:" + impact.changedSymbolId(),
                        RiskCategory.DEPENDENCY_IMPACT,
                        severity,
                        EvidenceConfidence.HIGH,
                        "Cyclic Dependency Encountered in Call Path",
                        String.format("Impact traversal for '%s' encountered %d cycles. Call graph has recursion or mutual dependencies.",
                                impact.changedSymbolId(), impact.cyclesEncountered()),
                        null,
                        null,
                        null,
                        null,
                        "Cycles: " + impact.cyclesEncountered(),
                        true,
                        score,
                        1
                );
                mergeFinding(map, finding);
            }
        }
    }

    private void normalizeTestSelectionFindings(
            TestSelectionReport testSelectionReport,
            Map<String, NormalizedRiskFinding> map,
            List<UncertaintyIndicator> uncertainties
    ) {
        if (testSelectionReport == null) {
            uncertainties.add(new UncertaintyIndicator(
                    RiskCategory.ANALYSIS_UNCERTAINTY,
                    "Test selection report was not provided",
                    "Automated test relevance could not be verified.",
                    false
            ));
            return;
        }

        if (testSelectionReport.isFallbackTriggered()) {
            uncertainties.add(new UncertaintyIndicator(
                    RiskCategory.ANALYSIS_UNCERTAINTY,
                    "Test selection fallback triggered: " + testSelectionReport.fallbackReason(),
                    testSelectionReport.fallbackExplanation() != null ?
                            testSelectionReport.fallbackExplanation() : "All discovered tests selected due to mapping ambiguity.",
                    false
            ));
        }

        if (testSelectionReport.unresolvedCount() > 0) {
            uncertainties.add(new UncertaintyIndicator(
                    RiskCategory.ANALYSIS_UNCERTAINTY,
                    testSelectionReport.unresolvedCount() + " tests could not be mapped to production symbols",
                    "Some tests reference unknown or unparsed production classes.",
                    false
            ));
        }
    }

    private void normalizeTestExecutionFindings(
            TestExecutionResult execResult,
            Map<String, NormalizedRiskFinding> map,
            List<UncertaintyIndicator> uncertainties
    ) {
        if (execResult == null) {
            uncertainties.add(new UncertaintyIndicator(
                    RiskCategory.TEST_FAILURE,
                    "Containerized test execution results were not provided",
                    "Dynamic runtime verification has not been performed.",
                    false
            ));
            return;
        }

        if (execResult.status() == TestExecutionStatus.INFRASTRUCTURE_FAILURE) {
            uncertainties.add(new UncertaintyIndicator(
                    RiskCategory.TEST_FAILURE,
                    "Test container infrastructure failure: " + execResult.infrastructureError(),
                    "Tests could not execute due to Docker environment issues. Actual test pass/fail state is unknown.",
                    true // Forces INDETERMINATE!
            ));
            return;
        }

        if (execResult.status() == TestExecutionStatus.FAILED) {
            int score = calculateScore(RiskSeverity.CRITICAL, EvidenceConfidence.HIGH);
            String failureSummary = (execResult.failureDetails() != null && !execResult.failureDetails().isEmpty())
                    ? String.join("; ", execResult.failureDetails())
                    : execResult.failedCount() + " test(s) failed";

            NormalizedRiskFinding finding = new NormalizedRiskFinding(
                    "TEST:REGRESSION_FAILURE",
                    RiskCategory.TEST_FAILURE,
                    RiskSeverity.CRITICAL,
                    EvidenceConfidence.HIGH,
                    "Automated Regression Test Failure",
                    String.format("Isolated Docker test execution failed: %d failed, %d errors out of %d total tests.",
                            execResult.failedCount(), execResult.errorCount(), execResult.totalTests()),
                    null,
                    null,
                    null,
                    null,
                    failureSummary,
                    true,
                    score,
                    1
            );
            mergeFinding(map, finding);
        } else if (execResult.status() == TestExecutionStatus.ERROR) {
            int score = calculateScore(RiskSeverity.CRITICAL, EvidenceConfidence.HIGH);
            NormalizedRiskFinding finding = new NormalizedRiskFinding(
                    "TEST:EXECUTION_ERROR",
                    RiskCategory.TEST_FAILURE,
                    RiskSeverity.CRITICAL,
                    EvidenceConfidence.HIGH,
                    "Test Execution Compilation or Runtime Error",
                    "Maven test execution terminated with build or execution error in container.",
                    null,
                    null,
                    null,
                    null,
                    "Exit code: " + execResult.exitCode(),
                    true,
                    score,
                    1
            );
            mergeFinding(map, finding);
        } else if (execResult.status() == TestExecutionStatus.TIMEOUT) {
            int score = calculateScore(RiskSeverity.HIGH, EvidenceConfidence.HIGH);
            NormalizedRiskFinding finding = new NormalizedRiskFinding(
                    "TEST:TIMEOUT",
                    RiskCategory.TEST_FAILURE,
                    RiskSeverity.HIGH,
                    EvidenceConfidence.HIGH,
                    "Test Execution Timeout Exceeded",
                    "Isolated test execution exceeded container timeout limit and was killed.",
                    null,
                    null,
                    null,
                    null,
                    "Duration: " + execResult.durationMs() + "ms",
                    true,
                    score,
                    1
            );
            mergeFinding(map, finding);
        }
    }

    private void mergeFinding(Map<String, NormalizedRiskFinding> map, NormalizedRiskFinding finding) {
        String fp = finding.fingerprint();
        if (map.containsKey(fp)) {
            NormalizedRiskFinding existing = map.get(fp);
            // Deduplicate: Keep finding with higher severity, increment duplicateCount
            if (finding.severity().ordinal() < existing.severity().ordinal()) { // CRITICAL (0) < HIGH (1) < MEDIUM (2)...
                map.put(fp, finding.withDuplicateCount(existing.duplicateCount() + 1));
            } else {
                map.put(fp, existing.withDuplicateCount(existing.duplicateCount() + 1));
            }
        } else {
            map.put(fp, finding);
        }
    }

    private int calculateScore(RiskSeverity severity, EvidenceConfidence confidence) {
        int weight = properties.getSeverityWeight(severity);
        double factor = properties.getConfidenceFactor(confidence);
        return (int) Math.round(weight * factor);
    }

    public record NormalizationResult(
            List<NormalizedRiskFinding> findings,
            List<UncertaintyIndicator> uncertainties
    ) {}
}

