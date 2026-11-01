package com.aichangeguard.impact.service;

import com.aichangeguard.analysis.model.SourceAnalysisReport;
import com.aichangeguard.impact.model.ApiChangeFinding;
import com.aichangeguard.impact.model.ApiChangeSeverity;
import com.aichangeguard.impact.model.ApiEndpoint;
import com.aichangeguard.impact.model.ApiImpactReport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Orchestrates REST API impact analysis between two repository revisions.
 *
 * <p>Coordinates endpoint extraction (via {@link RestEndpointExtractor}) and
 * contract comparison (via {@link ApiContractComparator}) to produce a complete
 * {@link ApiImpactReport}.</p>
 *
 * <h3>Analysis pipeline</h3>
 * <ol>
 *   <li>Extract API endpoints from base revision parsed metadata.</li>
 *   <li>Extract API endpoints from target revision parsed metadata.</li>
 *   <li>Compare base and target endpoints for contract changes.</li>
 *   <li>Aggregate statistics and limitations into the report.</li>
 * </ol>
 *
 * <h3>Evidence integrity</h3>
 * <p>Every report includes mandatory limitations to prevent false confidence.
 * Missing evidence is not proof of safety.</p>
 */
@Service
public class ApiImpactAnalyzer {

    private static final Logger log = LoggerFactory.getLogger(ApiImpactAnalyzer.class);

    private final RestEndpointExtractor endpointExtractor;
    private final ApiContractComparator contractComparator;

    public ApiImpactAnalyzer(RestEndpointExtractor endpointExtractor,
                              ApiContractComparator contractComparator) {
        this.endpointExtractor = endpointExtractor;
        this.contractComparator = contractComparator;
    }

    /**
     * Performs API impact analysis between two revisions.
     *
     * @param repositoryPath path to the repository being analyzed
     * @param baseRevision base revision identifier
     * @param targetRevision target revision identifier
     * @param baseReport parsed source analysis from the base revision
     * @param targetReport parsed source analysis from the target revision
     * @return complete API impact analysis report
     */
    public ApiImpactReport analyze(String repositoryPath,
                                    String baseRevision,
                                    String targetRevision,
                                    SourceAnalysisReport baseReport,
                                    SourceAnalysisReport targetReport) {
        log.info("Starting API impact analysis for repository '{}': {} → {}",
                repositoryPath, baseRevision, targetRevision);

        List<String> warnings = new ArrayList<>();

        // Step 1: Extract endpoints from both revisions
        List<ApiEndpoint> baseEndpoints;
        List<ApiEndpoint> targetEndpoints;

        try {
            baseEndpoints = endpointExtractor.extractEndpoints(baseReport);
        } catch (Exception e) {
            log.error("Failed to extract endpoints from base revision: {}", e.getMessage(), e);
            warnings.add("Base revision endpoint extraction failed: " + e.getMessage());
            baseEndpoints = List.of();
        }

        try {
            targetEndpoints = endpointExtractor.extractEndpoints(targetReport);
        } catch (Exception e) {
            log.error("Failed to extract endpoints from target revision: {}", e.getMessage(), e);
            warnings.add("Target revision endpoint extraction failed: " + e.getMessage());
            targetEndpoints = List.of();
        }

        // Step 2: Compare endpoints
        List<ApiChangeFinding> findings;
        try {
            findings = contractComparator.compareEndpoints(baseEndpoints, targetEndpoints);
        } catch (Exception e) {
            log.error("Failed to compare API endpoints: {}", e.getMessage(), e);
            warnings.add("Endpoint comparison failed: " + e.getMessage());
            findings = List.of();
        }

        // Step 3: Compute statistics
        int breakingCount = countBySeverity(findings, ApiChangeSeverity.BREAKING);
        int potentiallyBreakingCount = countBySeverity(findings, ApiChangeSeverity.POTENTIALLY_BREAKING);
        int compatibleCount = countBySeverity(findings, ApiChangeSeverity.COMPATIBLE);
        int unknownCount = countBySeverity(findings, ApiChangeSeverity.UNKNOWN);

        int baseControllerCount = countDistinctControllers(baseEndpoints);
        int targetControllerCount = countDistinctControllers(targetEndpoints);

        boolean isComplete = warnings.isEmpty();

        // Step 4: Build report with mandatory limitations
        List<String> limitations = buildLimitations();

        ApiImpactReport report = new ApiImpactReport(
                repositoryPath,
                baseRevision,
                targetRevision,
                baseEndpoints,
                targetEndpoints,
                findings,
                breakingCount,
                potentiallyBreakingCount,
                compatibleCount,
                unknownCount,
                baseControllerCount,
                targetControllerCount,
                isComplete,
                Instant.now(),
                warnings,
                limitations
        );

        log.info("API impact analysis complete: {} base endpoints, {} target endpoints, " +
                        "{} findings ({} breaking, {} potentially breaking)",
                baseEndpoints.size(), targetEndpoints.size(),
                findings.size(), breakingCount, potentiallyBreakingCount);

        return report;
    }

    private int countBySeverity(List<ApiChangeFinding> findings, ApiChangeSeverity severity) {
        return (int) findings.stream()
                .filter(f -> f.severity() == severity)
                .count();
    }

    private int countDistinctControllers(List<ApiEndpoint> endpoints) {
        Set<String> controllers = endpoints.stream()
                .map(ApiEndpoint::controllerClass)
                .collect(Collectors.toSet());
        return controllers.size();
    }

    /**
     * Builds the mandatory limitations list.
     * Every report must include these to prevent false confidence, per AGENTS.md and
     * CODE_ANALYSIS_DESIGN.md requirements.
     */
    private List<String> buildLimitations() {
        return List.of(
                "Endpoint extraction is based on syntactic annotation analysis. "
                        + "Runtime-resolved paths, SpEL expressions, and property placeholders are not evaluated.",
                "DTO comparison is limited to type name matching. "
                        + "Field-level changes within request/response DTOs are not yet detected.",
                "Renamed endpoints are reported as removed + added, not as a single rename operation.",
                "Endpoints registered programmatically (e.g., RouterFunction, WebFlux) are not discovered.",
                "Inherited controller mappings from superclasses are not resolved across files.",
                "This analysis does not claim completeness. Missing findings are not evidence of contract safety.",
                "Validation constraint comparison is based on annotation names and attributes. "
                        + "Custom validators and runtime validation behavior are outside analysis scope."
        );
    }
}
