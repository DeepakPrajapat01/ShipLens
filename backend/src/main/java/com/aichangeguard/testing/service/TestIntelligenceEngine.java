package com.aichangeguard.testing.service;

import com.aichangeguard.analysis.model.SourceAnalysisReport;
import com.aichangeguard.dependency.model.DependencyGraph;
import com.aichangeguard.repository.diff.model.DiffAnalysisResult;
import com.aichangeguard.testing.model.FallbackReason;
import com.aichangeguard.testing.model.SelectedTest;
import com.aichangeguard.testing.model.TestCase;
import com.aichangeguard.testing.model.TestClass;
import com.aichangeguard.testing.model.TestSelectionReport;
import com.aichangeguard.testing.model.UnresolvedTestMapping;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Main entry point and orchestrator for the Test Intelligence Engine.
 *
 * <p>Integrates test discovery, production-symbol mapping, dependency graph traversal,
 * and conservative fallback policies into an explainable {@link TestSelectionReport}.</p>
 */
@Service
public class TestIntelligenceEngine {

    private static final Logger log = LoggerFactory.getLogger(TestIntelligenceEngine.class);

    private final TestDiscoveryService discoveryService;
    private final TestImpactService impactService;

    public TestIntelligenceEngine(TestDiscoveryService discoveryService,
                                  TestImpactService impactService) {
        this.discoveryService = discoveryService;
        this.impactService = impactService;
    }

    /**
     * Executes dependency-aware test selection for the given inputs.
     *
     * @param repositoryPath local path to the repository
     * @param baseRevision base commit or branch
     * @param targetRevision target commit or branch
     * @param sourceReport parsed source code (production and/or test files)
     * @param dependencyGraph production dependency graph
     * @param changedSymbols list of modified/added symbol IDs
     * @param diffResult Git diff analysis result (optional)
     * @return structured test selection report
     */
    public TestSelectionReport selectTests(String repositoryPath,
                                          String baseRevision,
                                          String targetRevision,
                                          SourceAnalysisReport sourceReport,
                                          DependencyGraph dependencyGraph,
                                          List<String> changedSymbols,
                                          DiffAnalysisResult diffResult) {
        log.info("Starting test selection for '{}': {} -> {} with {} changed symbols",
                repositoryPath, baseRevision, targetRevision,
                changedSymbols != null ? changedSymbols.size() : 0);

        List<String> warnings = new ArrayList<>();

        // 1. Discover all test classes and methods
        List<TestClass> testClasses = Collections.emptyList();
        try {
            testClasses = discoveryService.discoverTestClasses(sourceReport);
        } catch (Exception e) {
            log.error("Failed to discover tests: {}", e.getMessage(), e);
            warnings.add("Test discovery failed: " + e.getMessage());
        }

        int totalTestClasses = testClasses.size();
        int totalTestMethods = testClasses.stream()
                .mapToInt(TestClass::testCount)
                .sum();

        // 2. Discover unresolved test mappings
        List<UnresolvedTestMapping> unresolvedMappings = Collections.emptyList();
        try {
            unresolvedMappings = discoveryService.findUnresolvedMappings(testClasses);
        } catch (Exception e) {
            log.error("Failed to find unresolved mappings: {}", e.getMessage(), e);
        }

        // 3. Select impacted tests
        TestImpactService.SelectionResult selectionResult;
        try {
            selectionResult = impactService.selectTests(testClasses, dependencyGraph, changedSymbols, diffResult);
        } catch (Exception e) {
            log.error("Failed during test selection: {}", e.getMessage(), e);
            warnings.add("Test selection failed: " + e.getMessage());
            selectionResult = new TestImpactService.SelectionResult(
                    Collections.emptyList(), Collections.emptyList(), true, FallbackReason.PARSER_FAILURE_IN_CHANGED_FILES, e.getMessage()
            );
        }

        List<String> safeChangedSymbols = changedSymbols != null ? changedSymbols : Collections.emptyList();
        List<String> limitations = buildLimitations();

        TestSelectionReport report = new TestSelectionReport(
                repositoryPath,
                baseRevision,
                targetRevision,
                totalTestMethods,
                totalTestClasses,
                selectionResult.selectedTests(),
                selectionResult.unselectedTests(),
                unresolvedMappings,
                selectionResult.isFallbackTriggered(),
                selectionResult.fallbackReason(),
                selectionResult.fallbackExplanation(),
                safeChangedSymbols,
                Instant.now(),
                warnings,
                limitations
        );

        log.info("Test selection complete: {}/{} tests selected (fallback: {})",
                report.selectedCount(), report.totalDiscoveredTests(), report.isFallbackTriggered());

        return report;
    }

    private List<String> buildLimitations() {
        return List.of(
                "Test selection identifies candidate tests using static call graph edges and class references; runtime reflection, SpEL, and dynamic dispatch may cause missed coverage.",
                "Textual symbol references in tests do not constitute proof of runtime execution or assertion coverage.",
                "When dependency information is incomplete or build configuration files (e.g. pom.xml) are modified, conservative fallback is triggered.",
                "Tests with external environment dependencies, containers, or test profiles not discoverable statically may require manual selection.",
                "This engine does not claim completeness; unselected tests are not proven to be unaffected."
        );
    }
}

