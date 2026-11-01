package com.aichangeguard.dependency.service;

import com.aichangeguard.analysis.model.ParsedFileMetadata;
import com.aichangeguard.analysis.service.JavaSourceParser;
import com.aichangeguard.dependency.model.DependencyEdge;
import com.aichangeguard.dependency.model.DependencyGraph;
import com.aichangeguard.dependency.model.DependencyNode;
import com.aichangeguard.dependency.model.EdgeType;
import com.aichangeguard.dependency.model.GraphReport;
import com.aichangeguard.dependency.model.ImpactAnalysisResult;
import com.aichangeguard.dependency.model.NodeKind;
import com.aichangeguard.dependency.model.ResolutionStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DependencyGraphEngineTest {

    private JavaSourceParser parser;
    private DependencyGraphBuilder graphBuilder;
    private ImpactTraversalService traversalService;

    @BeforeEach
    void setUp() {
        parser = new JavaSourceParser();
        graphBuilder = new DependencyGraphBuilder();
        traversalService = new ImpactTraversalService();
    }

    @Test
    @DisplayName("Direct method calls: resolves internal same-class invocation and identifies direct caller")
    void testDirectMethodCalls() {
        String code = """
                package com.sample;

                public class OrderService {
                    public void placeOrder(String id) {
                        validateOrder(id);
                    }

                    public void validateOrder(String id) {
                        System.out.println(id);
                    }
                }
                """;

        ParsedFileMetadata parsed = parser.parseSource("OrderService.java", code);
        DependencyGraph graph = graphBuilder.buildGraph(List.of(parsed));

        String callerId = "com.sample.OrderService#placeOrder(String)";
        String targetId = "com.sample.OrderService#validateOrder(String)";

        assertThat(graph.getNode(callerId)).isPresent();
        assertThat(graph.getNode(targetId)).isPresent();

        List<DependencyEdge> outgoing = graph.getOutgoingEdges(callerId);
        assertThat(outgoing).anyMatch(e -> e.targetNodeId().equals(targetId) && e.type() == EdgeType.CALLS);

        // Reverse impact lookup: changing validateOrder should directly affect placeOrder
        ImpactAnalysisResult impact = traversalService.findAffectedCallers(graph, targetId);
        assertThat(impact.affectedMethodIds()).containsExactly(callerId);
        assertThat(impact.impactPaths()).hasSize(1);
        assertThat(impact.impactPaths().get(0).nodeIds()).containsExactly(callerId, targetId);
    }

    @Test
    @DisplayName("Multi-level dependencies: A -> B -> C traversal identifies all transitive callers")
    void testMultiLevelDependencies() {
        String code = """
                package com.sample;

                public class WorkflowService {
                    public void start() {
                        stepOne();
                    }

                    public void stepOne() {
                        stepTwo();
                    }

                    public void stepTwo() {
                        finalizeWorkflow();
                    }

                    public void finalizeWorkflow() {
                        System.out.println("Done");
                    }
                }
                """;

        ParsedFileMetadata parsed = parser.parseSource("WorkflowService.java", code);
        DependencyGraph graph = graphBuilder.buildGraph(List.of(parsed));

        String leafId = "com.sample.WorkflowService#finalizeWorkflow()";
        String stepTwoId = "com.sample.WorkflowService#stepTwo()";
        String stepOneId = "com.sample.WorkflowService#stepOne()";
        String startId = "com.sample.WorkflowService#start()";

        ImpactAnalysisResult impact = traversalService.findAffectedCallers(graph, leafId);

        // Transitive callers
        assertThat(impact.affectedMethodIds()).containsExactlyInAnyOrder(stepTwoId, stepOneId, startId);
        assertThat(impact.maxDepthReached()).isEqualTo(3);
    }

    @Test
    @DisplayName("Circular dependencies: A -> B -> A terminates safely without infinite loops")
    void testCircularDependencies() {
        String code = """
                package com.sample;

                public class CycleService {
                    public void methodA() {
                        methodB();
                    }

                    public void methodB() {
                        methodA();
                    }
                }
                """;

        ParsedFileMetadata parsed = parser.parseSource("CycleService.java", code);
        DependencyGraph graph = graphBuilder.buildGraph(List.of(parsed));

        assertThat(graph.getCycleCount()).isGreaterThanOrEqualTo(1);

        String methodA = "com.sample.CycleService#methodA()";
        String methodB = "com.sample.CycleService#methodB()";

        // Reverse traversal must complete cleanly without StackOverflowError
        ImpactAnalysisResult impact = traversalService.findAffectedCallers(graph, methodA);

        assertThat(impact.affectedMethodIds()).contains(methodB);
        assertThat(impact.warnings()).anyMatch(w -> w.contains("Encountered"));
    }

    @Test
    @DisplayName("Overloaded methods: distinct nodes and call edges for overloads")
    void testOverloadedMethods() {
        String code = """
                package com.sample;

                public class CalculatorService {
                    public void runInt() {
                        compute(10, 20);
                    }

                    public void runString() {
                        compute("data");
                    }

                    public int compute(int a, int b) {
                        return a + b;
                    }

                    public int compute(String text) {
                        return text.length();
                    }
                }
                """;

        ParsedFileMetadata parsed = parser.parseSource("CalculatorService.java", code);
        DependencyGraph graph = graphBuilder.buildGraph(List.of(parsed));

        String computeInt = "com.sample.CalculatorService#compute(int,int)";
        String computeStr = "com.sample.CalculatorService#compute(String)";
        String runInt = "com.sample.CalculatorService#runInt()";
        String runString = "com.sample.CalculatorService#runString()";

        assertThat(graph.getNode(computeInt)).isPresent();
        assertThat(graph.getNode(computeStr)).isPresent();

        // compute(int, int) changed -> affects runInt(), but NOT runString()
        ImpactAnalysisResult intImpact = traversalService.findAffectedCallers(graph, computeInt);
        assertThat(intImpact.affectedMethodIds()).contains(runInt);
        assertThat(intImpact.affectedMethodIds()).doesNotContain(runString);

        // compute(String) changed -> affects runString(), but NOT runInt()
        ImpactAnalysisResult strImpact = traversalService.findAffectedCallers(graph, computeStr);
        assertThat(strImpact.affectedMethodIds()).contains(runString);
        assertThat(strImpact.affectedMethodIds()).doesNotContain(runInt);
    }

    @Test
    @DisplayName("Inheritance: resolves calls to inherited superclass methods")
    void testInheritanceHierarchyCalls() {
        String baseCode = """
                package com.sample;

                public class BaseHandler {
                    public void handleEvent(String event) {
                        System.out.println(event);
                    }
                }
                """;

        String childCode = """
                package com.sample;

                public class SpecificHandler extends BaseHandler {
                    public void onEvent() {
                        handleEvent("test-event");
                    }
                }
                """;

        ParsedFileMetadata baseMeta = parser.parseSource("BaseHandler.java", baseCode);
        ParsedFileMetadata childMeta = parser.parseSource("SpecificHandler.java", childCode);
        DependencyGraph graph = graphBuilder.buildGraph(List.of(baseMeta, childMeta));

        String baseMethod = "com.sample.BaseHandler#handleEvent(String)";
        String childMethod = "com.sample.SpecificHandler#onEvent()";

        // Verify class hierarchy edge
        List<DependencyEdge> classEdges = graph.getOutgoingEdges("com.sample.SpecificHandler");
        assertThat(classEdges).anyMatch(e -> e.targetNodeId().equals("com.sample.BaseHandler") && e.type() == EdgeType.EXTENDS);

        // Verify inherited call resolution
        List<DependencyEdge> childMethodEdges = graph.getOutgoingEdges(childMethod);
        assertThat(childMethodEdges).anyMatch(e -> e.targetNodeId().equals(baseMethod) && e.type() == EdgeType.CALLS);

        // Impact analysis: modifying BaseHandler#handleEvent affects SpecificHandler#onEvent
        ImpactAnalysisResult impact = traversalService.findAffectedCallers(graph, baseMethod);
        assertThat(impact.affectedMethodIds()).contains(childMethod);
    }

    @Test
    @DisplayName("Unresolved calls: external library calls are recorded as unresolved without fabricating nodes")
    void testUnresolvedCallsRecordedExplicitly() {
        String code = """
                package com.sample;

                public class ExternalCaller {
                    public void execute() {
                        System.out.println("External call");
                        Math.max(1, 2);
                    }
                }
                """;

        ParsedFileMetadata parsed = parser.parseSource("ExternalCaller.java", code);
        DependencyGraph graph = graphBuilder.buildGraph(List.of(parsed));

        assertThat(graph.getUnresolvedInvocations()).isNotEmpty();
        assertThat(graph.getUnresolvedInvocations())
                .extracting("invokedName")
                .contains("println", "max");

        // Ensure no fabricated target nodes were added for external library calls
        assertThat(graph.getNode("System.out.println")).isEmpty();
        assertThat(graph.getNode("Math.max")).isEmpty();
    }

    @Test
    @DisplayName("Graph report accurately summarizes metrics, counts, and limitations")
    void testGraphReportGeneration() {
        String code = """
                package com.sample;

                public class SimpleApp {
                    private int counter;

                    public void doAction() {
                        helper();
                    }

                    private void helper() {}
                }
                """;

        ParsedFileMetadata parsed = parser.parseSource("SimpleApp.java", code);
        DependencyGraph graph = graphBuilder.buildGraph(List.of(parsed));
        GraphReport report = graph.generateReport();

        assertThat(report.totalNodes()).isGreaterThan(0);
        assertThat(report.totalTypeNodes()).isEqualTo(1);
        assertThat(report.totalMethodNodes()).isGreaterThanOrEqualTo(2);
        assertThat(report.resolvedCallEdges()).isEqualTo(1);
        assertThat(report.limitations()).isNotEmpty();
    }
}
