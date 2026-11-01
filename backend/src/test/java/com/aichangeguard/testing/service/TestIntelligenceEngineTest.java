package com.aichangeguard.testing.service;

import com.aichangeguard.analysis.model.AnnotationMetadata;
import com.aichangeguard.analysis.model.MethodInvocationMetadata;
import com.aichangeguard.analysis.model.MethodMetadata;
import com.aichangeguard.analysis.model.ParsedFileMetadata;
import com.aichangeguard.analysis.model.SourceAnalysisReport;
import com.aichangeguard.analysis.model.SourceRange;
import com.aichangeguard.analysis.model.TypeKind;
import com.aichangeguard.analysis.model.TypeMetadata;
import com.aichangeguard.dependency.model.DependencyEdge;
import com.aichangeguard.dependency.model.DependencyGraph;
import com.aichangeguard.dependency.model.DependencyNode;
import com.aichangeguard.dependency.model.EdgeType;
import com.aichangeguard.dependency.model.NodeKind;
import com.aichangeguard.dependency.model.ResolutionStatus;
import com.aichangeguard.dependency.service.ImpactTraversalService;
import com.aichangeguard.testing.model.SelectedTest;
import com.aichangeguard.testing.model.TestConfidence;
import com.aichangeguard.testing.model.TestSelectionReasonType;
import com.aichangeguard.testing.model.TestSelectionReport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class TestIntelligenceEngineTest {

    private TestIntelligenceEngine engine;

    @BeforeEach
    void setUp() {
        TestDiscoveryService discoveryService = new TestDiscoveryService();
        TestImpactService impactService = new TestImpactService(new ImpactTraversalService());
        engine = new TestIntelligenceEngine(discoveryService, impactService);
    }

    private DependencyGraph buildGraph(Map<String, DependencyNode> nodes, Map<String, List<DependencyEdge>> outgoing) {
        Map<String, List<DependencyEdge>> incoming = new HashMap<>();
        for (var entry : outgoing.entrySet()) {
            for (var edge : entry.getValue()) {
                incoming.computeIfAbsent(edge.targetNodeId(), k -> new java.util.ArrayList<>()).add(edge);
            }
        }
        return new DependencyGraph(nodes, outgoing, incoming, Collections.emptyList(), 0);
    }

    @Test
    @DisplayName("Should run complete test selection with direct and unselected tests")
    void shouldRunCompleteTestSelection() {
        // Test 1 calls UserService.saveUser -> Should be selected!
        MethodInvocationMetadata inv1 = MethodInvocationMetadata.syntactic(
                "userService", "saveUser", 1, SourceRange.UNKNOWN
        );
        MethodMetadata testMethod1 = new MethodMetadata(
                "com.example.UserServiceTest#testSave()",
                "testSave", "void", Collections.emptyList(), Set.of("public"),
                List.of(new AnnotationMetadata("Test", Collections.emptyMap(), SourceRange.UNKNOWN)),
                Collections.emptyList(), false, false, SourceRange.of(10, 1, 15, 1), List.of(inv1)
        );

        // Test 2 calls OrderService.calculateTotal -> Not changed -> Unselected!
        MethodInvocationMetadata inv2 = MethodInvocationMetadata.syntactic(
                "orderService", "calculateTotal", 0, SourceRange.UNKNOWN
        );
        MethodMetadata testMethod2 = new MethodMetadata(
                "com.example.OrderServiceTest#testCalculate()",
                "testCalculate", "void", Collections.emptyList(), Set.of("public"),
                List.of(new AnnotationMetadata("Test", Collections.emptyMap(), SourceRange.UNKNOWN)),
                Collections.emptyList(), false, false, SourceRange.of(20, 1, 25, 1), List.of(inv2)
        );

        TypeMetadata type1 = new TypeMetadata(
                "com.example.UserServiceTest", "com.example.UserServiceTest", "UserServiceTest",
                TypeKind.CLASS, Set.of("public"), Collections.emptyList(),
                Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
                Collections.emptyList(), List.of(testMethod1), Collections.emptyList(), SourceRange.UNKNOWN
        );

        TypeMetadata type2 = new TypeMetadata(
                "com.example.OrderServiceTest", "com.example.OrderServiceTest", "OrderServiceTest",
                TypeKind.CLASS, Set.of("public"), Collections.emptyList(),
                Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
                Collections.emptyList(), List.of(testMethod2), Collections.emptyList(), SourceRange.UNKNOWN
        );

        ParsedFileMetadata file1 = new ParsedFileMetadata(
                "src/test/java/com/example/UserServiceTest.java", "com.example",
                List.of("com.example.UserService"), List.of(type1), Collections.emptyList(), true, SourceRange.UNKNOWN
        );
        ParsedFileMetadata file2 = new ParsedFileMetadata(
                "src/test/java/com/example/OrderServiceTest.java", "com.example",
                List.of("com.example.OrderService"), List.of(type2), Collections.emptyList(), true, SourceRange.UNKNOWN
        );

        SourceAnalysisReport testReport = new SourceAnalysisReport(
                "/repo", 2, 2, 0, 100.0,
                List.of(file1, file2), Collections.emptyList(), Instant.now(), Collections.emptyList()
        );

        String changedSymbol = "com.example.UserService#saveUser(User)";
        DependencyNode node = new DependencyNode(changedSymbol, NodeKind.METHOD, changedSymbol, "saveUser", "com.example.UserService", SourceRange.UNKNOWN, Collections.emptyMap());
        DependencyGraph graph = buildGraph(Map.of(changedSymbol, node), Collections.emptyMap());

        TestSelectionReport report = engine.selectTests(
                "/repo", "base-sha", "target-sha",
                testReport, graph, List.of(changedSymbol), null
        );

        assertThat(report).isNotNull();
        assertThat(report.totalDiscoveredTests()).isEqualTo(2);
        assertThat(report.totalDiscoveredTestClasses()).isEqualTo(2);
        assertThat(report.selectedCount()).isEqualTo(1);
        assertThat(report.unselectedCount()).isEqualTo(1);
        assertThat(report.isFallbackTriggered()).isFalse();

        SelectedTest selected = report.selectedTests().getFirst();
        assertThat(selected.testMethod()).isEqualTo("testSave");
        assertThat(selected.confidence()).isEqualTo(TestConfidence.HIGH);
        assertThat(selected.reasonType()).isEqualTo(TestSelectionReasonType.DIRECT_METHOD_CALL);

        // Mandatory limitations must be present
        assertThat(report.limitations()).isNotEmpty();
        assertThat(report.limitations().size()).isGreaterThanOrEqualTo(5);
        assertThat(report.limitations()).anyMatch(l -> l.contains("completeness"));
        assertThat(report.limitations()).anyMatch(l -> l.contains("conservative fallback"));
    }

    @Test
    @DisplayName("Should handle null reports gracefully")
    void shouldHandleNullReports() {
        TestSelectionReport report = engine.selectTests(
                "/repo", "base", "target", null, null, null, null
        );

        assertThat(report).isNotNull();
        assertThat(report.totalDiscoveredTests()).isZero();
        assertThat(report.selectedCount()).isZero();
        assertThat(report.unselectedCount()).isZero();
        assertThat(report.limitations()).isNotEmpty();
    }
}
