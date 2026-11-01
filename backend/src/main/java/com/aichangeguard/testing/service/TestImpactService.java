package com.aichangeguard.testing.service;

import com.aichangeguard.dependency.model.DependencyGraph;
import com.aichangeguard.dependency.model.DependencyNode;
import com.aichangeguard.dependency.model.ImpactAnalysisResult;
import com.aichangeguard.dependency.model.ImpactPath;
import com.aichangeguard.dependency.service.ImpactTraversalService;
import com.aichangeguard.repository.diff.model.ChangedFileEntry;
import com.aichangeguard.repository.diff.model.DiffAnalysisResult;
import com.aichangeguard.testing.model.FallbackReason;
import com.aichangeguard.testing.model.SelectedTest;
import com.aichangeguard.testing.model.TestCase;
import com.aichangeguard.testing.model.TestClass;
import com.aichangeguard.testing.model.TestConfidence;
import com.aichangeguard.testing.model.TestSelectionReasonType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Implements the dependency-aware test selection strategy.
 *
 * <p>Associates discovered tests with proposed code changes using evidence in strict order:
 * <ol>
 *   <li>Direct production-code references from test methods.</li>
 *   <li>Resolved method-level dependency paths from call graph traversal.</li>
 *   <li>Class-level dependencies (injected fields, instantiations, imports).</li>
 *   <li>Inheritance and implemented interface relationships.</li>
 *   <li>Changed test files themselves.</li>
 * </ol>
 *
 * <p>Enforces conservative fallback when build configuration changes or evidence is insufficient.</p>
 */
@Service
public class TestImpactService {

    private static final Logger log = LoggerFactory.getLogger(TestImpactService.class);

    private final ImpactTraversalService impactTraversalService;

    public TestImpactService(ImpactTraversalService impactTraversalService) {
        this.impactTraversalService = impactTraversalService;
    }

    /**
     * Result of test selection evaluation.
     */
    public record SelectionResult(
            List<SelectedTest> selectedTests,
            List<TestCase> unselectedTests,
            boolean isFallbackTriggered,
            FallbackReason fallbackReason,
            String fallbackExplanation
    ) {}

    /**
     * Selects tests for the given changed symbols, dependency graph, and discovered test classes.
     */
    public SelectionResult selectTests(List<TestClass> testClasses,
                                       DependencyGraph graph,
                                       List<String> changedSymbols,
                                       DiffAnalysisResult diffResult) {
        List<TestCase> allTestMethods = new ArrayList<>();
        for (TestClass tc : safeList(testClasses)) {
            allTestMethods.addAll(safeList(tc.testMethods()));
        }

        if (allTestMethods.isEmpty()) {
            return new SelectionResult(Collections.emptyList(), Collections.emptyList(), false, FallbackReason.NONE, null);
        }

        // Check if build file (pom.xml) changed in diff -> conservative full suite fallback
        if (isBuildConfigurationChanged(diffResult)) {
            log.info("Build configuration change detected in diff; triggering conservative full-suite fallback");
            List<SelectedTest> fallbackSelected = allTestMethods.stream()
                    .map(tm -> new SelectedTest(
                            tm.testClassSimpleName(),
                            tm.testMethodName(),
                            tm.testMethodSymbolId(),
                            tm.sourceFile(),
                            tm.sourceLine(),
                            List.of("pom.xml"),
                            "Selected due to build configuration change (pom.xml). All tests must run.",
                            TestSelectionReasonType.BUILD_CONFIGURATION_CHANGED,
                            TestConfidence.FALLBACK,
                            List.of("pom.xml", tm.testClassQualifiedName())
                    ))
                    .toList();
            return new SelectionResult(
                    fallbackSelected,
                    Collections.emptyList(),
                    true,
                    FallbackReason.BUILD_CONFIGURATION_CHANGED,
                    "Build configuration changed; all tests selected conservatively."
            );
        }

        // Set of modified test files from diff
        Set<String> modifiedTestFiles = extractModifiedTestFiles(diffResult);

        // Precompute impacted callers from dependency graph for each changed symbol
        Map<String, ImpactAnalysisResult> impactByChangedSymbol = new HashMap<>();
        Set<String> allImpactedCallers = new HashSet<>();

        if (graph != null && changedSymbols != null) {
            for (String changedSym : changedSymbols) {
                ImpactAnalysisResult impact = impactTraversalService.findAffectedCallers(graph, changedSym);
                impactByChangedSymbol.put(changedSym, impact);
                allImpactedCallers.addAll(impact.affectedMethodIds());
            }
        }

        Map<String, SelectedTest> selectedMap = new LinkedHashMap<>();
        Set<String> safeChangedSymbols = new HashSet<>(safeList(changedSymbols));

        // Evaluate each test method against the selection hierarchy
        for (TestClass tc : safeList(testClasses)) {
            for (TestCase tm : safeList(tc.testMethods())) {
                SelectedTest candidate = evaluateCandidate(
                        tm,
                        tc,
                        safeChangedSymbols,
                        allImpactedCallers,
                        impactByChangedSymbol,
                        modifiedTestFiles,
                        graph
                );

                if (candidate != null) {
                    selectedMap.put(candidate.testMethodSymbolId(), candidate);
                }
            }
        }

        List<SelectedTest> selectedTests = new ArrayList<>(selectedMap.values());
        List<TestCase> unselectedTests = allTestMethods.stream()
                .filter(tm -> !selectedMap.containsKey(tm.testMethodSymbolId()))
                .toList();

        // Check if zero tests matched while production code was changed
        boolean fallback = false;
        FallbackReason fallbackReason = FallbackReason.NONE;
        String fallbackExplanation = null;

        if (selectedTests.isEmpty() && !safeChangedSymbols.isEmpty()) {
            fallback = true;
            fallbackReason = FallbackReason.ZERO_TESTS_MATCHED_PRODUCTION_CHANGES;
            fallbackExplanation = "No discovered tests directly or transitively reference changed symbols. Manual or full validation recommended.";
            log.warn("Test selection yielded 0 tests for {} changed symbols; marking zero-matches fallback", safeChangedSymbols.size());
        }

        return new SelectionResult(selectedTests, unselectedTests, fallback, fallbackReason, fallbackExplanation);
    }

    private SelectedTest evaluateCandidate(TestCase tm,
                                           TestClass tc,
                                           Set<String> changedSymbols,
                                           Set<String> allImpactedCallers,
                                           Map<String, ImpactAnalysisResult> impactByChangedSymbol,
                                           Set<String> modifiedTestFiles,
                                           DependencyGraph graph) {

        // 1. Direct changed test file
        if (modifiedTestFiles.contains(tm.sourceFile()) || modifiedTestFiles.contains(extractFileName(tm.sourceFile()))) {
            return new SelectedTest(
                    tm.testClassSimpleName(),
                    tm.testMethodName(),
                    tm.testMethodSymbolId(),
                    tm.sourceFile(),
                    tm.sourceLine(),
                    List.of(tm.sourceFile()),
                    "Test file itself was modified in the proposed change.",
                    TestSelectionReasonType.CHANGED_TEST_FILE,
                    TestConfidence.HIGH,
                    List.of(tm.testMethodSymbolId())
            );
        }

        // 2. Direct production-code reference from test method
        for (String changedSym : changedSymbols) {
            String methodName = extractMemberName(changedSym);
            String className = extractClassName(changedSym);

            if (tm.referencedProductionSymbols().contains(changedSym)
                    || tm.referencedProductionSymbols().contains(methodName)
                    || tm.referencedProductionSymbols().contains(className + "." + methodName)) {
                return new SelectedTest(
                        tm.testClassSimpleName(),
                        tm.testMethodName(),
                        tm.testMethodSymbolId(),
                        tm.sourceFile(),
                        tm.sourceLine(),
                        List.of(changedSym),
                        "Test method directly references changed method: " + changedSym,
                        TestSelectionReasonType.DIRECT_METHOD_CALL,
                        TestConfidence.HIGH,
                        List.of(tm.testMethodSymbolId(), changedSym)
                );
            }
        }

        // 3. Resolved method dependencies (transitive caller path from graph)
        if (graph != null) {
            // Check if test method itself is a node in the graph calling an impacted method
            for (String changedSym : changedSymbols) {
                ImpactAnalysisResult impact = impactByChangedSymbol.get(changedSym);
                if (impact != null) {
                    for (String impactedCaller : impact.affectedMethodIds()) {
                        String callerMethod = extractMemberName(impactedCaller);
                        if (tm.referencedProductionSymbols().contains(impactedCaller)
                                || tm.referencedProductionSymbols().contains(callerMethod)) {
                            // Find matching path
                            List<String> pathDisplay = List.of(tm.testMethodSymbolId(), impactedCaller, changedSym);
                            for (ImpactPath p : impact.impactPaths()) {
                                if (p.nodeIds().contains(impactedCaller)) {
                                    List<String> fullPath = new ArrayList<>();
                                    fullPath.add(tm.testMethodSymbolId());
                                    fullPath.addAll(p.nodeIds());
                                    pathDisplay = fullPath;
                                    break;
                                }
                            }
                            return new SelectedTest(
                                    tm.testClassSimpleName(),
                                    tm.testMethodName(),
                                    tm.testMethodSymbolId(),
                                    tm.sourceFile(),
                                    tm.sourceLine(),
                                    List.of(changedSym),
                                    "Test method transitively invokes changed symbol via: " + impactedCaller,
                                    TestSelectionReasonType.TRANSITIVE_DEPENDENCY,
                                    TestConfidence.MEDIUM,
                                    pathDisplay
                            );
                        }
                    }
                }
            }
        }

        // 4. Class-level dependencies (injected fields, instantiations, imports)
        for (String changedSym : changedSymbols) {
            String className = extractClassName(changedSym);
            if (className != null && !className.isBlank()) {
                if (tc.referencedProductionTypes().contains(className)
                        || tm.referencedProductionTypes().contains(className)) {
                    return new SelectedTest(
                            tm.testClassSimpleName(),
                            tm.testMethodName(),
                            tm.testMethodSymbolId(),
                            tm.sourceFile(),
                            tm.sourceLine(),
                            List.of(className),
                            "Test class references production type containing changed code: " + className,
                            TestSelectionReasonType.DIRECT_CLASS_REFERENCE,
                            TestConfidence.MEDIUM,
                            List.of(tc.qualifiedName(), className, changedSym)
                    );
                }
            }
        }

        // 5. Inheritance relationships (test uses a subclass of changed class)
        if (graph != null) {
            for (String changedSym : changedSymbols) {
                String className = extractClassName(changedSym);
                if (className != null) {
                    DependencyNode classNode = graph.getNode(className).orElse(null);
                    if (classNode != null) {
                        for (String refType : tc.referencedProductionTypes()) {
                            DependencyNode refNode = graph.getNode(refType).orElse(null);
                            if (refNode != null && isSubtypeOf(refNode, className, graph)) {
                                return new SelectedTest(
                                        tm.testClassSimpleName(),
                                        tm.testMethodName(),
                                        tm.testMethodSymbolId(),
                                        tm.sourceFile(),
                                        tm.sourceLine(),
                                        List.of(className),
                                        "Test exercises type '" + refType + "' which inherits from changed class '" + className + "'",
                                        TestSelectionReasonType.INHERITANCE_HIERARCHY,
                                        TestConfidence.LOW,
                                        List.of(tc.qualifiedName(), refType, className)
                                );
                            }
                        }
                    }
                }
            }
        }

        return null;
    }

    private boolean isSubtypeOf(DependencyNode candidate, String targetSuperType, DependencyGraph graph) {
        Set<String> visited = new HashSet<>();
        List<String> queue = new ArrayList<>();
        queue.add(candidate.id());

        while (!queue.isEmpty()) {
            String current = queue.remove(0);
            if (current.equals(targetSuperType) || extractClassName(current).equals(targetSuperType)) {
                return true;
            }
            if (visited.add(current)) {
                for (var edge : graph.getOutgoingEdges(current)) {
                    if (edge.type() == com.aichangeguard.dependency.model.EdgeType.EXTENDS
                            || edge.type() == com.aichangeguard.dependency.model.EdgeType.IMPLEMENTS) {
                        queue.add(edge.targetNodeId());
                    }
                }
            }
        }
        return false;
    }

    private boolean isBuildConfigurationChanged(DiffAnalysisResult diffResult) {
        if (diffResult == null || diffResult.changedFiles() == null) {
            return false;
        }
        return diffResult.changedFiles().stream()
                .anyMatch(f -> {
                    String path = f.effectivePath().toLowerCase();
                    return path.endsWith("pom.xml") || path.endsWith("build.gradle") || path.endsWith("build.gradle.kts");
                });
    }

    private Set<String> extractModifiedTestFiles(DiffAnalysisResult diffResult) {
        if (diffResult == null || diffResult.changedFiles() == null) {
            return Collections.emptySet();
        }
        Set<String> testFiles = new HashSet<>();
        for (ChangedFileEntry entry : diffResult.changedFiles()) {
            String path = entry.effectivePath();
            if (path.contains("src/test/") || path.endsWith("Test.java") || path.endsWith("Tests.java") || path.endsWith("TestCase.java")) {
                testFiles.add(path);
                testFiles.add(extractFileName(path));
            }
        }
        return testFiles;
    }

    private String extractClassName(String symbolId) {
        if (symbolId == null) return "";
        int hashIdx = symbolId.indexOf('#');
        String qualified = hashIdx >= 0 ? symbolId.substring(0, hashIdx) : symbolId;
        int dotIdx = qualified.lastIndexOf('.');
        return dotIdx >= 0 ? qualified.substring(dotIdx + 1) : qualified;
    }

    private String extractMemberName(String symbolId) {
        if (symbolId == null) return "";
        int hashIdx = symbolId.indexOf('#');
        if (hashIdx >= 0) {
            String member = symbolId.substring(hashIdx + 1);
            int parenIdx = member.indexOf('(');
            return parenIdx >= 0 ? member.substring(0, parenIdx) : member;
        }
        return symbolId;
    }

    private String extractFileName(String path) {
        if (path == null) return "";
        int idx = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
        return idx >= 0 ? path.substring(idx + 1) : path;
    }

    private <T> List<T> safeList(List<T> list) {
        return list != null ? list : Collections.emptyList();
    }
}
