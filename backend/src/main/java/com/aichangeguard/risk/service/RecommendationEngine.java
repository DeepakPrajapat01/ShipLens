package com.aichangeguard.risk.service;

import com.aichangeguard.risk.model.NormalizedRiskFinding;
import com.aichangeguard.risk.model.Recommendation;
import com.aichangeguard.risk.model.RecommendationPriority;
import com.aichangeguard.risk.model.RiskCategory;
import com.aichangeguard.risk.model.RiskLevel;
import com.aichangeguard.risk.model.RiskSeverity;
import com.aichangeguard.risk.model.UncertaintyIndicator;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Generates prioritized, evidence-linked recommendations based on risk findings.
 */
@Service
public class RecommendationEngine {

    public List<Recommendation> generateRecommendations(
            RiskLevel riskLevel,
            List<NormalizedRiskFinding> findings,
            List<UncertaintyIndicator> uncertainties
    ) {
        List<Recommendation> recommendations = new ArrayList<>();

        if (findings == null) {
            findings = Collections.emptyList();
        }
        if (uncertainties == null) {
            uncertainties = Collections.emptyList();
        }

        // 1. Check for Indeterminate / Infrastructure Blockers
        if (riskLevel == RiskLevel.INDETERMINATE) {
            List<String> blockers = uncertainties.stream()
                    .filter(UncertaintyIndicator::causesIndeterminate)
                    .map(UncertaintyIndicator::reason)
                    .toList();

            recommendations.add(new Recommendation(
                    RecommendationPriority.BLOCKING,
                    "Resolve analysis blockers and rerun assessment",
                    "Assessment is INDETERMINATE because critical inputs or isolated execution failed: " +
                            String.join("; ", blockers),
                    blockers
            ));
        }

        // 2. Check for Test Failures
        List<NormalizedRiskFinding> testFailures = findings.stream()
                .filter(f -> f.category() == RiskCategory.TEST_FAILURE && f.severity() == RiskSeverity.CRITICAL)
                .toList();
        if (!testFailures.isEmpty()) {
            List<String> targets = testFailures.stream().map(NormalizedRiskFinding::evidenceReference).toList();
            recommendations.add(new Recommendation(
                    RecommendationPriority.BLOCKING,
                    "Resolve failing automated regression tests before deployment",
                    "Isolated container tests failed. Deploying this change will introduce known regressions into production.",
                    targets
            ));
        }

        // 3. Check for Destructive Database Migrations
        List<NormalizedRiskFinding> destructiveDb = findings.stream()
                .filter(f -> f.category() == RiskCategory.DATABASE_IMPACT && f.severity() == RiskSeverity.CRITICAL)
                .toList();
        if (!destructiveDb.isEmpty()) {
            List<String> targets = destructiveDb.stream().map(NormalizedRiskFinding::evidenceReference).toList();
            recommendations.add(new Recommendation(
                    RecommendationPriority.BLOCKING,
                    "Review destructive database migrations and verify backup/rollback procedures",
                    "Migration statements contain DROP TABLE, DROP COLUMN, or TRUNCATE commands that cause irreversible data loss.",
                    targets
            ));
        }

        // 4. Check for Breaking API Contracts
        List<NormalizedRiskFinding> breakingApi = findings.stream()
                .filter(f -> f.category() == RiskCategory.API_COMPATIBILITY && f.severity() == RiskSeverity.CRITICAL)
                .toList();
        if (!breakingApi.isEmpty()) {
            List<String> endpoints = breakingApi.stream().map(NormalizedRiskFinding::evidenceReference).toList();
            recommendations.add(new Recommendation(
                    RecommendationPriority.HIGH,
                    "Coordinate breaking REST API changes with client teams",
                    "Endpoints or required parameters were removed or modified, which will break existing API clients.",
                    endpoints
            ));
        }

        // 5. Check for Potentially Breaking Schema/API Changes
        List<NormalizedRiskFinding> potentiallyBreaking = findings.stream()
                .filter(f -> (f.category() == RiskCategory.DATABASE_IMPACT || f.category() == RiskCategory.API_COMPATIBILITY)
                        && f.severity() == RiskSeverity.HIGH)
                .toList();
        if (!potentiallyBreaking.isEmpty()) {
            List<String> items = potentiallyBreaking.stream().map(NormalizedRiskFinding::title).toList();
            recommendations.add(new Recommendation(
                    RecommendationPriority.HIGH,
                    "Verify backward-compatibility of schema evolutions and new constraints",
                    "Schema modifications (e.g. NOT NULL constraints or type alterations) may fail on existing production datasets.",
                    items
            ));
        }

        // 6. Check for High Dependency Blast Radius
        List<NormalizedRiskFinding> highBlastRadius = findings.stream()
                .filter(f -> f.category() == RiskCategory.DEPENDENCY_IMPACT && f.severity() == RiskSeverity.HIGH)
                .toList();
        if (!highBlastRadius.isEmpty()) {
            List<String> symbols = highBlastRadius.stream().map(NormalizedRiskFinding::evidenceReference).toList();
            recommendations.add(new Recommendation(
                    RecommendationPriority.MEDIUM,
                    "Perform extended end-to-end integration testing across affected callers",
                    "Changed methods have extensive caller blast radius spanning multiple modules.",
                    symbols
            ));
        }

        // 7. Check for Code Change Complexity
        boolean highComplexity = findings.stream()
                .anyMatch(f -> f.category() == RiskCategory.CODE_CHANGE_COMPLEXITY && f.severity() == RiskSeverity.HIGH);
        if (highComplexity) {
            recommendations.add(new Recommendation(
                    RecommendationPriority.MEDIUM,
                    "Decompose large change into smaller, incremental pull requests",
                    "High change volume across numerous files significantly increases cognitive load and latent defect risk.",
                    Collections.emptyList()
            ));
        }

        // 8. Low Risk Fallback Recommendation
        if (recommendations.isEmpty() && riskLevel == RiskLevel.LOW) {
            recommendations.add(new Recommendation(
                    RecommendationPriority.LOW,
                    "Proceed with standard automated deployment pipeline",
                    "No breaking API changes, destructive migrations, or regression test failures were detected.",
                    Collections.emptyList()
            ));
        }

        return recommendations;
    }
}

