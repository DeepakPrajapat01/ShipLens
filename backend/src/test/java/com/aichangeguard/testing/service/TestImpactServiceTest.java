package com.aichangeguard.testing.service;

import com.aichangeguard.dependency.model.DependencyEdge;
import com.aichangeguard.dependency.model.DependencyGraph;
import com.aichangeguard.dependency.model.DependencyNode;
import com.aichangeguard.dependency.model.EdgeType;
import com.aichangeguard.dependency.model.NodeKind;
import com.aichangeguard.dependency.model.ResolutionStatus;
import com.aichangeguard.dependency.service.ImpactTraversalService;
import com.aichangeguard.repository.diff.model.ChangedFileEntry;
import com.aichangeguard.repository.diff.model.DiffAnalysisResult;
import com.aichangeguard.repository.diff.model.FileChangeType;
import com.aichangeguard.testing.model.FallbackReason;
import com.aichangeguard.testing.model.SelectedTest;
import com.aichangeguard.testing.model.TestCase;
import com.aichangeguard.testing.model.TestClass;
import com.aichangeguard.testing.model.TestConfidence;
import com.aichangeguard.testing.model.TestSelectionReasonType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class TestImpactServiceTest {

    private TestImpactService impactService;

    @BeforeEach
    void setUp() {
        impactService = new TestImpactService(new ImpactTraversalService());
    }

    private TestCase testCase(String className, String methodName, Set<String> referencedSymbols, Set<String> referencedTypes) {
        return new TestCase(
                "com.example." + className, className, methodName,
                "com.example." + className + "#" + methodName + "()",
                "src/test/java/com/example/" + className + ".java", 15,
                List.of("Test"), false, referencedSymbols, referencedTypes
        );
    }

    private TestClass testClass(String className, List<TestCase> methods, Set<String> referencedTypes) {
        return new TestClass(
                "com.example." + className, className,
                "src/test/java/com/example/" + className + ".java", 1,
                List.of(), methods, referencedTypes
        );
    }

    private DependencyGraph buildGraph(Map<String, DependencyNode> nodes,
                                       Map<String, List<DependencyEdge>> outgoing) {
        Map<String, List<DependencyEdge>> incoming = new HashMap<>();
        for (var entry : outgoing.entrySet()) {
            for (var edge : entry.getValue()) {
                incoming.computeIfAbsent(edge.targetNodeId(), k -> new java.util.ArrayList<>()).add(edge);
            }
        }
        return new DependencyGraph(nodes, outgoing, incoming, Collections.emptyList(), 0);
    }

    @Nested
    @DisplayName("Selection hierarchy tests")
    class SelectionHierarchyTests {

        @Test
        @DisplayName("Should select test with HIGH confidence for direct method call")
        void shouldSelectForDirectMethodCall() {
            String changedSymbol = "com.example.UserService#findById(Long)";

            TestCase tc = testCase("UserServiceTest", "testFindById",
                    Set.of("findById", "UserService.findById"), Set.of("UserService"));
            TestClass tClass = testClass("UserServiceTest", List.of(tc), Set.of("UserService"));

            TestImpactService.SelectionResult result = impactService.selectTests(
                    List.of(tClass), null, List.of(changedSymbol), null
            );

            assertThat(result.selectedTests()).hasSize(1);
            SelectedTest selected = result.selectedTests().getFirst();
            assertThat(selected.confidence()).isEqualTo(TestConfidence.HIGH);
            assertThat(selected.reasonType()).isEqualTo(TestSelectionReasonType.DIRECT_METHOD_CALL);
            assertThat(selected.testMethod()).isEqualTo("testFindById");
            assertThat(result.unselectedTests()).isEmpty();
            assertThat(result.isFallbackTriggered()).isFalse();
        }

        @Test
        @DisplayName("Should select test with MEDIUM confidence for transitive dependency")
        void shouldSelectForTransitiveDependency() {
            String changedSymbol = "com.example.UserDao#queryById(Long)";
            String intermediateCaller = "com.example.UserService#getUser(Long)";

            // Service calls Dao
            DependencyNode daoNode = new DependencyNode(changedSymbol, NodeKind.METHOD, changedSymbol, "queryById", "com.example.UserDao", com.aichangeguard.analysis.model.SourceRange.UNKNOWN, Collections.emptyMap());
            DependencyNode serviceNode = new DependencyNode(intermediateCaller, NodeKind.METHOD, intermediateCaller, "getUser", "com.example.UserService", com.aichangeguard.analysis.model.SourceRange.UNKNOWN, Collections.emptyMap());

            DependencyEdge edge = new DependencyEdge(
                    intermediateCaller, changedSymbol, EdgeType.CALLS, ResolutionStatus.RESOLVED, "direct", null
            );

            DependencyGraph graph = buildGraph(
                    Map.of(changedSymbol, daoNode, intermediateCaller, serviceNode),
                    Map.of(intermediateCaller, List.of(edge))
            );

            // Test calls UserService.getUser
            TestCase tc = testCase("UserControllerTest", "testEndpoint",
                    Set.of("getUser", intermediateCaller), Set.of("UserService"));
            TestClass tClass = testClass("UserControllerTest", List.of(tc), Set.of("UserService"));

            TestImpactService.SelectionResult result = impactService.selectTests(
                    List.of(tClass), graph, List.of(changedSymbol), null
            );

            assertThat(result.selectedTests()).hasSize(1);
            SelectedTest selected = result.selectedTests().getFirst();
            assertThat(selected.confidence()).isEqualTo(TestConfidence.MEDIUM);
            assertThat(selected.reasonType()).isEqualTo(TestSelectionReasonType.TRANSITIVE_DEPENDENCY);
            assertThat(selected.evidencePath()).contains(intermediateCaller);
        }

        @Test
        @DisplayName("Should select test with MEDIUM confidence for class-level reference")
        void shouldSelectForClassLevelReference() {
            String changedSymbol = "com.example.OrderService#calculateDiscount(Order)";

            // Test references OrderService in field, but doesn't explicitly call calculateDiscount
            TestCase tc = testCase("OrderServiceTest", "testOtherFeature",
                    Set.of("saveOrder"), Set.of("OrderService"));
            TestClass tClass = testClass("OrderServiceTest", List.of(tc), Set.of("OrderService"));

            TestImpactService.SelectionResult result = impactService.selectTests(
                    List.of(tClass), null, List.of(changedSymbol), null
            );

            assertThat(result.selectedTests()).hasSize(1);
            SelectedTest selected = result.selectedTests().getFirst();
            assertThat(selected.confidence()).isEqualTo(TestConfidence.MEDIUM);
            assertThat(selected.reasonType()).isEqualTo(TestSelectionReasonType.DIRECT_CLASS_REFERENCE);
            assertThat(selected.relatedProductionComponents()).contains("OrderService");
        }

        @Test
        @DisplayName("Should select test with LOW confidence for inheritance relationship")
        void shouldSelectForInheritanceRelationship() {
            String superType = "BaseService";
            String subType = "SpecialService";
            String changedSymbol = "com.example.BaseService#baseMethod()";

            DependencyNode superNode = new DependencyNode(superType, NodeKind.TYPE, superType, superType, null, com.aichangeguard.analysis.model.SourceRange.UNKNOWN, Collections.emptyMap());
            DependencyNode subNode = new DependencyNode(subType, NodeKind.TYPE, subType, subType, null, com.aichangeguard.analysis.model.SourceRange.UNKNOWN, Collections.emptyMap());

            DependencyEdge inheritEdge = new DependencyEdge(
                    subType, superType, EdgeType.EXTENDS, ResolutionStatus.RESOLVED, "extends", null
            );

            DependencyGraph graph = buildGraph(
                    Map.of(superType, superNode, subType, subNode),
                    Map.of(subType, List.of(inheritEdge))
            );

            TestCase tc = testCase("SpecialServiceTest", "testSpecial", Set.of("doSpecial"), Set.of("SpecialService"));
            TestClass tClass = testClass("SpecialServiceTest", List.of(tc), Set.of("SpecialService"));

            TestImpactService.SelectionResult result = impactService.selectTests(
                    List.of(tClass), graph, List.of(changedSymbol), null
            );

            assertThat(result.selectedTests()).hasSize(1);
            SelectedTest selected = result.selectedTests().getFirst();
            assertThat(selected.confidence()).isEqualTo(TestConfidence.LOW);
            assertThat(selected.reasonType()).isEqualTo(TestSelectionReasonType.INHERITANCE_HIERARCHY);
        }

        @Test
        @DisplayName("Should select test with HIGH confidence if test file itself is changed in diff")
        void shouldSelectForModifiedTestFile() {
            TestCase tc = testCase("PaymentTest", "testPayment", Set.of("unrelated"), Set.of("Unrelated"));
            TestClass tClass = testClass("PaymentTest", List.of(tc), Set.of("Unrelated"));

            ChangedFileEntry changedTestFile = new ChangedFileEntry(
                    FileChangeType.MODIFIED,
                    "src/test/java/com/example/PaymentTest.java",
                    "src/test/java/com/example/PaymentTest.java",
                    true, false, 5, 2, Collections.emptyList(), Collections.emptyList()
            );

            DiffAnalysisResult diff = new DiffAnalysisResult(
                    "/repo", "base", "target", "sha1", "sha2",
                    List.of(changedTestFile), 1, 1, 5, 2, false, false,
                    Instant.now(), Collections.emptyList(), Collections.emptyList()
            );

            TestImpactService.SelectionResult result = impactService.selectTests(
                    List.of(tClass), null, Collections.emptyList(), diff
            );

            assertThat(result.selectedTests()).hasSize(1);
            SelectedTest selected = result.selectedTests().getFirst();
            assertThat(selected.confidence()).isEqualTo(TestConfidence.HIGH);
            assertThat(selected.reasonType()).isEqualTo(TestSelectionReasonType.CHANGED_TEST_FILE);
        }
    }

    @Nested
    @DisplayName("Fallback policy tests")
    class FallbackPolicyTests {

        @Test
        @DisplayName("Should trigger full-suite fallback when pom.xml changes")
        void shouldFallbackWhenPomXmlChanges() {
            TestCase tc1 = testCase("A_Test", "test1", Set.of(), Set.of());
            TestCase tc2 = testCase("B_Test", "test2", Set.of(), Set.of());

            TestClass tcClassA = testClass("A_Test", List.of(tc1), Set.of());
            TestClass tcClassB = testClass("B_Test", List.of(tc2), Set.of());

            ChangedFileEntry pomChange = new ChangedFileEntry(
                    FileChangeType.MODIFIED, "pom.xml", "pom.xml",
                    false, false, 2, 1, Collections.emptyList(), Collections.emptyList()
            );

            DiffAnalysisResult diff = new DiffAnalysisResult(
                    "/repo", "base", "target", "sha1", "sha2",
                    List.of(pomChange), 1, 0, 2, 1, false, false,
                    Instant.now(), Collections.emptyList(), Collections.emptyList()
            );

            TestImpactService.SelectionResult result = impactService.selectTests(
                    List.of(tcClassA, tcClassB), null, List.of("com.example.SomeService#m()"), diff
            );

            assertThat(result.isFallbackTriggered()).isTrue();
            assertThat(result.fallbackReason()).isEqualTo(FallbackReason.BUILD_CONFIGURATION_CHANGED);
            assertThat(result.selectedTests()).hasSize(2); // All tests selected
            assertThat(result.selectedTests().getFirst().confidence()).isEqualTo(TestConfidence.FALLBACK);
        }

        @Test
        @DisplayName("Should flag zero matches fallback when production code changes but no tests match")
        void shouldFlagZeroMatchesFallback() {
            TestCase tc = testCase("IsolatedTest", "testIsolated", Set.of("isolatedCall"), Set.of("IsolatedClass"));
            TestClass tClass = testClass("IsolatedTest", List.of(tc), Set.of("IsolatedClass"));

            TestImpactService.SelectionResult result = impactService.selectTests(
                    List.of(tClass), null, List.of("com.example.UnrelatedService#unrelatedMethod()"), null
            );

            assertThat(result.selectedTests()).isEmpty();
            assertThat(result.unselectedTests()).hasSize(1);
            assertThat(result.isFallbackTriggered()).isTrue();
            assertThat(result.fallbackReason()).isEqualTo(FallbackReason.ZERO_TESTS_MATCHED_PRODUCTION_CHANGES);
        }

        @Test
        @DisplayName("Should handle empty test suite gracefully")
        void shouldHandleEmptyTestSuite() {
            TestImpactService.SelectionResult result = impactService.selectTests(
                    Collections.emptyList(), null, List.of("someSymbol"), null
            );

            assertThat(result.selectedTests()).isEmpty();
            assertThat(result.unselectedTests()).isEmpty();
            assertThat(result.isFallbackTriggered()).isFalse();
        }
    }
}
