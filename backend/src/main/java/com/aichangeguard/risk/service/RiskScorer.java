package com.aichangeguard.risk.service;

import com.aichangeguard.risk.config.RiskScoringProperties;
import com.aichangeguard.risk.model.AnalysisCompleteness;
import com.aichangeguard.risk.model.NormalizedRiskFinding;
import com.aichangeguard.risk.model.RiskCategory;
import com.aichangeguard.risk.model.RiskLevel;
import com.aichangeguard.risk.model.RiskSeverity;
import com.aichangeguard.risk.model.UncertaintyIndicator;
import org.springframework.stereotype.Service;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Calculates deterministic risk scores, applies category caps, and classifies overall risk level.
 */
@Service
public class RiskScorer {

    private final RiskScoringProperties properties;

    public RiskScorer(RiskScoringProperties properties) {
        this.properties = properties;
    }

    /**
     * Computes the scoring breakdown and evaluates final RiskLevel.
     */
    public ScoringResult score(
            List<NormalizedRiskFinding> findings,
            List<UncertaintyIndicator> uncertainties
    ) {
        Map<RiskCategory, Integer> categoryScores = new EnumMap<>(RiskCategory.class);
        for (RiskCategory category : RiskCategory.values()) {
            categoryScores.put(category, 0);
        }

        // 1. Sum up scores per category
        if (findings != null) {
            for (NormalizedRiskFinding finding : findings) {
                int current = categoryScores.get(finding.category());
                categoryScores.put(finding.category(), current + finding.scoreContribution());
            }
        }

        // 2. Apply category caps
        int totalScore = 0;
        for (RiskCategory category : RiskCategory.values()) {
            int raw = categoryScores.get(category);
            int cap = properties.getCategoryCap(category);
            int capped = Math.min(raw, cap);
            categoryScores.put(category, capped);
            totalScore += capped;
        }

        // Overall score bounded between 0 and 100
        int boundedOverallScore = Math.min(100, Math.max(0, totalScore));

        // 3. Determine Analysis Completeness
        AnalysisCompleteness completeness = evaluateCompleteness(uncertainties);

        // 4. Check for Indeterminate Triggers
        boolean indeterminate = false;
        if (uncertainties != null) {
            for (UncertaintyIndicator ui : uncertainties) {
                if (ui.causesIndeterminate()) {
                    indeterminate = true;
                    break;
                }
            }
        }

        if (completeness == AnalysisCompleteness.INSUFFICIENT) {
            indeterminate = true;
        }

        RiskLevel level;
        int finalScore;

        if (indeterminate) {
            level = RiskLevel.INDETERMINATE;
            finalScore = -1; // Explicit indicator of indeterminate numeric evaluation
        } else {
            finalScore = boundedOverallScore;
            if (finalScore >= properties.getModerateMaxThreshold() + 1) { // >= 70
                level = RiskLevel.HIGH;
            } else if (finalScore >= properties.getLowMaxThreshold() + 1) { // 30 - 69
                level = RiskLevel.MODERATE;
            } else { // 0 - 29
                level = RiskLevel.LOW;
            }
        }

        return new ScoringResult(level, finalScore, completeness, categoryScores);
    }

    private AnalysisCompleteness evaluateCompleteness(List<UncertaintyIndicator> uncertainties) {
        if (uncertainties == null || uncertainties.isEmpty()) {
            return AnalysisCompleteness.COMPLETE;
        }

        boolean hasCriticalMissing = uncertainties.stream().anyMatch(UncertaintyIndicator::causesIndeterminate);
        if (hasCriticalMissing) {
            return AnalysisCompleteness.INSUFFICIENT;
        }

        return AnalysisCompleteness.PARTIAL;
    }

    public record ScoringResult(
            RiskLevel riskLevel,
            int overallScore,
            AnalysisCompleteness completeness,
            Map<RiskCategory, Integer> categoryScores
    ) {}
}

