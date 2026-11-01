package com.aichangeguard.risk.config;

import com.aichangeguard.risk.model.EvidenceConfidence;
import com.aichangeguard.risk.model.RiskCategory;
import com.aichangeguard.risk.model.RiskSeverity;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.Map;

/**
 * Configurable thresholds, weights, and category caps for the Risk Assessment Engine.
 */
@Component
@ConfigurationProperties(prefix = "changeguard.risk")
public class RiskScoringProperties {

    /** Model version identifier for reproducibility and traceability. */
    private String modelVersion = "1.0.0";

    /** Upper bound inclusive score for LOW risk level (default: 29). */
    private int lowMaxThreshold = 29;

    /** Upper bound inclusive score for MODERATE risk level (default: 69). Score >= 70 is HIGH. */
    private int moderateMaxThreshold = 69;

    /** Maximum points any single category can contribute to the overall score. */
    private Map<RiskCategory, Integer> categoryCaps = new EnumMap<>(RiskCategory.class);

    /** Score weights for severity levels. */
    private Map<RiskSeverity, Integer> severityWeights = new EnumMap<>(RiskSeverity.class);

    /** Multipliers for confidence levels. */
    private Map<EvidenceConfidence, Double> confidenceFactors = new EnumMap<>(EvidenceConfidence.class);

    public RiskScoringProperties() {
        // Initialize default category caps
        categoryCaps.put(RiskCategory.API_COMPATIBILITY, 40);
        categoryCaps.put(RiskCategory.DATABASE_IMPACT, 40);
        categoryCaps.put(RiskCategory.TEST_FAILURE, 50);
        categoryCaps.put(RiskCategory.DEPENDENCY_IMPACT, 25);
        categoryCaps.put(RiskCategory.CODE_CHANGE_COMPLEXITY, 20);
        categoryCaps.put(RiskCategory.SECURITY, 40);
        categoryCaps.put(RiskCategory.ANALYSIS_UNCERTAINTY, 30);

        // Initialize default severity weights
        severityWeights.put(RiskSeverity.CRITICAL, 35);
        severityWeights.put(RiskSeverity.HIGH, 20);
        severityWeights.put(RiskSeverity.MEDIUM, 10);
        severityWeights.put(RiskSeverity.LOW, 5);
        severityWeights.put(RiskSeverity.INFO, 0);

        // Initialize default confidence factors
        confidenceFactors.put(EvidenceConfidence.HIGH, 1.0);
        confidenceFactors.put(EvidenceConfidence.MEDIUM, 0.75);
        confidenceFactors.put(EvidenceConfidence.LOW, 0.5);
        confidenceFactors.put(EvidenceConfidence.UNKNOWN, 0.25);
    }

    public String getModelVersion() {
        return modelVersion;
    }

    public void setModelVersion(String modelVersion) {
        this.modelVersion = modelVersion;
    }

    public int getLowMaxThreshold() {
        return lowMaxThreshold;
    }

    public void setLowMaxThreshold(int lowMaxThreshold) {
        this.lowMaxThreshold = lowMaxThreshold;
    }

    public int getModerateMaxThreshold() {
        return moderateMaxThreshold;
    }

    public void setModerateMaxThreshold(int moderateMaxThreshold) {
        this.moderateMaxThreshold = moderateMaxThreshold;
    }

    public Map<RiskCategory, Integer> getCategoryCaps() {
        return categoryCaps;
    }

    public void setCategoryCaps(Map<RiskCategory, Integer> categoryCaps) {
        this.categoryCaps = categoryCaps;
    }

    public Map<RiskSeverity, Integer> getSeverityWeights() {
        return severityWeights;
    }

    public void setSeverityWeights(Map<RiskSeverity, Integer> severityWeights) {
        this.severityWeights = severityWeights;
    }

    public Map<EvidenceConfidence, Double> getConfidenceFactors() {
        return confidenceFactors;
    }

    public void setConfidenceFactors(Map<EvidenceConfidence, Double> confidenceFactors) {
        this.confidenceFactors = confidenceFactors;
    }

    public int getCategoryCap(RiskCategory category) {
        return categoryCaps.getOrDefault(category, 30);
    }

    public int getSeverityWeight(RiskSeverity severity) {
        return severityWeights.getOrDefault(severity, 10);
    }

    public double getConfidenceFactor(EvidenceConfidence confidence) {
        return confidenceFactors.getOrDefault(confidence, 1.0);
    }
}

