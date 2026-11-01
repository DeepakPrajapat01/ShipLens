package com.aichangeguard.impact.service;

import com.aichangeguard.analysis.model.AnnotationMetadata;
import com.aichangeguard.analysis.model.MethodMetadata;
import com.aichangeguard.analysis.model.ParameterMetadata;
import com.aichangeguard.analysis.model.ParsedFileMetadata;
import com.aichangeguard.analysis.model.SourceAnalysisReport;
import com.aichangeguard.analysis.model.SourceRange;
import com.aichangeguard.analysis.model.TypeKind;
import com.aichangeguard.analysis.model.TypeMetadata;
import com.aichangeguard.impact.model.ApiChangeSeverity;
import com.aichangeguard.impact.model.ApiImpactReport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link ApiImpactAnalyzer}.
 *
 * <p>Validates end-to-end orchestration of REST API impact analysis
 * including endpoint extraction, contract comparison, report generation,
 * and mandatory limitation disclosure.</p>
 */
class ApiImpactAnalyzerTest {

    private ApiImpactAnalyzer analyzer;

    @BeforeEach
    void setUp() {
        analyzer = new ApiImpactAnalyzer(
                new RestEndpointExtractor(),
                new ApiContractComparator()
        );
    }

    // --- Test Data Builders ---

    private AnnotationMetadata annotation(String name) {
        return new AnnotationMetadata(name, Collections.emptyMap(), SourceRange.UNKNOWN);
    }

    private AnnotationMetadata annotation(String name, Map<String, String> attrs) {
        return new AnnotationMetadata(name, attrs, SourceRange.UNKNOWN);
    }

    private ParameterMetadata parameter(String name, String type, List<AnnotationMetadata> annotations) {
        return new ParameterMetadata(name, type, annotations, false);
    }

    private MethodMetadata method(String name, String returnType,
                                   List<ParameterMetadata> params,
                                   List<AnnotationMetadata> annotations) {
        return new MethodMetadata(
                "com.example.Controller#" + name + "()",
                name, returnType, params,
                Set.of("public"), annotations,
                Collections.emptyList(), false, false,
                SourceRange.of(10, 1, 20, 1),
                Collections.emptyList()
        );
    }

    private TypeMetadata controllerType(String className, String basePath,
                                         List<MethodMetadata> methods) {
        List<AnnotationMetadata> classAnnotations;
        if (basePath != null && !basePath.isEmpty()) {
            classAnnotations = List.of(
                    annotation("RestController"),
                    annotation("RequestMapping", Map.of("value", "\"" + basePath + "\""))
            );
        } else {
            classAnnotations = List.of(annotation("RestController"));
        }

        return new TypeMetadata(
                "com.example." + className,
                "com.example." + className,
                className,
                TypeKind.CLASS,
                Set.of("public"),
                classAnnotations,
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                methods,
                Collections.emptyList(),
                SourceRange.of(1, 1, 50, 1)
        );
    }

    private SourceAnalysisReport report(List<ParsedFileMetadata> files) {
        return new SourceAnalysisReport(
                "/repo", files.size(), files.size(), 0, 100.0,
                files, Collections.emptyList(),
                java.time.Instant.now(), Collections.emptyList()
        );
    }

    private ParsedFileMetadata file(String path, List<TypeMetadata> types) {
        return new ParsedFileMetadata(
                path, "com.example",
                Collections.emptyList(), types,
                Collections.emptyList(), true, SourceRange.UNKNOWN
        );
    }

    // --- Tests ---

    @Nested
    @DisplayName("End-to-end analysis")
    class EndToEndAnalysis {

        @Test
        @DisplayName("Should produce report with no findings when APIs are identical")
        void shouldProduceNoFindingsForIdenticalApis() {
            TypeMetadata controller = controllerType("UserController", "/api/users",
                    List.of(method("getAll", "List<User>", Collections.emptyList(),
                            List.of(annotation("GetMapping")))));

            SourceAnalysisReport baseReport = report(List.of(
                    file("UserController.java", List.of(controller))));
            SourceAnalysisReport targetReport = report(List.of(
                    file("UserController.java", List.of(controller))));

            ApiImpactReport result = analyzer.analyze(
                    "/repo", "abc123", "def456", baseReport, targetReport);

            assertThat(result.findings()).isEmpty();
            assertThat(result.breakingChangeCount()).isZero();
            assertThat(result.potentiallyBreakingChangeCount()).isZero();
            assertThat(result.compatibleChangeCount()).isZero();
            assertThat(result.baseEndpoints()).hasSize(1);
            assertThat(result.targetEndpoints()).hasSize(1);
            assertThat(result.isComplete()).isTrue();
        }

        @Test
        @DisplayName("Should detect removed endpoint")
        void shouldDetectRemovedEndpoint() {
            TypeMetadata baseController = controllerType("UserController", "/api/users",
                    List.of(
                            method("getAll", "List<User>", Collections.emptyList(),
                                    List.of(annotation("GetMapping"))),
                            method("getById", "User", Collections.emptyList(),
                                    List.of(annotation("GetMapping", Map.of("value", "\"/{id}\""))))
                    ));

            TypeMetadata targetController = controllerType("UserController", "/api/users",
                    List.of(
                            method("getAll", "List<User>", Collections.emptyList(),
                                    List.of(annotation("GetMapping")))
                    ));

            ApiImpactReport result = analyzer.analyze(
                    "/repo", "abc123", "def456",
                    report(List.of(file("UserController.java", List.of(baseController)))),
                    report(List.of(file("UserController.java", List.of(targetController)))));

            assertThat(result.breakingChangeCount()).isEqualTo(1);
            assertThat(result.findings()).anyMatch(f ->
                    f.severity() == ApiChangeSeverity.BREAKING
                            && f.baseEndpointId() != null
                            && f.baseEndpointId().contains("/api/users/{id}"));
        }

        @Test
        @DisplayName("Should detect added endpoint")
        void shouldDetectAddedEndpoint() {
            TypeMetadata baseController = controllerType("UserController", "/api/users",
                    List.of(method("getAll", "List<User>", Collections.emptyList(),
                            List.of(annotation("GetMapping")))));

            TypeMetadata targetController = controllerType("UserController", "/api/users",
                    List.of(
                            method("getAll", "List<User>", Collections.emptyList(),
                                    List.of(annotation("GetMapping"))),
                            method("create", "User",
                                    List.of(parameter("body", "CreateUserRequest",
                                            List.of(annotation("RequestBody")))),
                                    List.of(annotation("PostMapping")))
                    ));

            ApiImpactReport result = analyzer.analyze(
                    "/repo", "abc123", "def456",
                    report(List.of(file("UserController.java", List.of(baseController)))),
                    report(List.of(file("UserController.java", List.of(targetController)))));

            assertThat(result.compatibleChangeCount()).isEqualTo(1);
            assertThat(result.findings()).anyMatch(f ->
                    f.severity() == ApiChangeSeverity.COMPATIBLE
                            && f.targetEndpointId() != null
                            && f.targetEndpointId().contains("POST:/api/users"));
        }

        @Test
        @DisplayName("Should detect breaking parameter change")
        void shouldDetectBreakingParameterChange() {
            MethodMetadata baseMethod = method("search", "List<User>",
                    Collections.emptyList(),
                    List.of(annotation("GetMapping", Map.of("value", "\"/search\""))));

            MethodMetadata targetMethod = method("search", "List<User>",
                    List.of(parameter("query", "String",
                            List.of(annotation("RequestParam")))),
                    List.of(annotation("GetMapping", Map.of("value", "\"/search\""))));

            ApiImpactReport result = analyzer.analyze(
                    "/repo", "abc123", "def456",
                    report(List.of(file("UserController.java",
                            List.of(controllerType("UserController", "/api/users",
                                    List.of(baseMethod)))))),
                    report(List.of(file("UserController.java",
                            List.of(controllerType("UserController", "/api/users",
                                    List.of(targetMethod)))))));

            assertThat(result.breakingChangeCount()).isGreaterThanOrEqualTo(1);
        }

        @Test
        @DisplayName("Should detect response type change")
        void shouldDetectResponseTypeChange() {
            MethodMetadata baseMethod = method("getAll", "List<User>",
                    Collections.emptyList(),
                    List.of(annotation("GetMapping")));

            MethodMetadata targetMethod = method("getAll", "Page<User>",
                    Collections.emptyList(),
                    List.of(annotation("GetMapping")));

            ApiImpactReport result = analyzer.analyze(
                    "/repo", "abc123", "def456",
                    report(List.of(file("UserController.java",
                            List.of(controllerType("UserController", "/api/users",
                                    List.of(baseMethod)))))),
                    report(List.of(file("UserController.java",
                            List.of(controllerType("UserController", "/api/users",
                                    List.of(targetMethod)))))));

            assertThat(result.potentiallyBreakingChangeCount()).isGreaterThanOrEqualTo(1);
            assertThat(result.findings()).anyMatch(f ->
                    f.baseValue() != null && f.baseValue().contains("List<User>")
                            && f.targetValue() != null && f.targetValue().contains("Page<User>"));
        }
    }

    @Nested
    @DisplayName("Report metadata")
    class ReportMetadata {

        @Test
        @DisplayName("Should include repository path and revisions")
        void shouldIncludeRepoMetadata() {
            SourceAnalysisReport emptyReport = report(Collections.emptyList());

            ApiImpactReport result = analyzer.analyze(
                    "/my/repo", "base-rev", "target-rev", emptyReport, emptyReport);

            assertThat(result.repositoryPath()).isEqualTo("/my/repo");
            assertThat(result.baseRevision()).isEqualTo("base-rev");
            assertThat(result.targetRevision()).isEqualTo("target-rev");
        }

        @Test
        @DisplayName("Should include analysis timestamp")
        void shouldIncludeTimestamp() {
            SourceAnalysisReport emptyReport = report(Collections.emptyList());

            ApiImpactReport result = analyzer.analyze(
                    "/repo", "a", "b", emptyReport, emptyReport);

            assertThat(result.analyzedAt()).isNotNull();
        }

        @Test
        @DisplayName("Should always include mandatory limitations")
        void shouldAlwaysIncludeLimitations() {
            SourceAnalysisReport emptyReport = report(Collections.emptyList());

            ApiImpactReport result = analyzer.analyze(
                    "/repo", "a", "b", emptyReport, emptyReport);

            assertThat(result.limitations()).isNotEmpty();
            assertThat(result.limitations().size()).isGreaterThanOrEqualTo(5);
            // Verify key limitations are present
            assertThat(result.limitations()).anyMatch(l -> l.contains("syntactic"));
            assertThat(result.limitations()).anyMatch(l -> l.contains("completeness"));
        }

        @Test
        @DisplayName("Should count controllers in base and target")
        void shouldCountControllers() {
            TypeMetadata c1 = controllerType("UserController", "/users",
                    List.of(method("get", "User", Collections.emptyList(),
                            List.of(annotation("GetMapping")))));
            TypeMetadata c2 = controllerType("OrderController", "/orders",
                    List.of(method("get", "Order", Collections.emptyList(),
                            List.of(annotation("GetMapping")))));

            SourceAnalysisReport baseReport = report(List.of(
                    file("UserController.java", List.of(c1)),
                    file("OrderController.java", List.of(c2))));
            SourceAnalysisReport targetReport = report(List.of(
                    file("UserController.java", List.of(c1))));

            ApiImpactReport result = analyzer.analyze(
                    "/repo", "a", "b", baseReport, targetReport);

            assertThat(result.baseControllerCount()).isEqualTo(2);
            assertThat(result.targetControllerCount()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("Error handling")
    class ErrorHandling {

        @Test
        @DisplayName("Should handle null base report gracefully")
        void shouldHandleNullBaseReport() {
            SourceAnalysisReport targetReport = report(Collections.emptyList());

            ApiImpactReport result = analyzer.analyze(
                    "/repo", "a", "b", null, targetReport);

            assertThat(result).isNotNull();
            assertThat(result.baseEndpoints()).isEmpty();
            assertThat(result.isComplete()).isTrue();
        }

        @Test
        @DisplayName("Should handle null target report gracefully")
        void shouldHandleNullTargetReport() {
            SourceAnalysisReport baseReport = report(Collections.emptyList());

            ApiImpactReport result = analyzer.analyze(
                    "/repo", "a", "b", baseReport, null);

            assertThat(result).isNotNull();
            assertThat(result.targetEndpoints()).isEmpty();
            assertThat(result.isComplete()).isTrue();
        }

        @Test
        @DisplayName("Should handle both reports being null")
        void shouldHandleBothNullReports() {
            ApiImpactReport result = analyzer.analyze(
                    "/repo", "a", "b", null, null);

            assertThat(result).isNotNull();
            assertThat(result.baseEndpoints()).isEmpty();
            assertThat(result.targetEndpoints()).isEmpty();
            assertThat(result.findings()).isEmpty();
            assertThat(result.limitations()).isNotEmpty();
        }
    }

    @Nested
    @DisplayName("Multi-controller analysis")
    class MultiControllerAnalysis {

        @Test
        @DisplayName("Should analyze endpoints across multiple controllers")
        void shouldAnalyzeAcrossControllers() {
            TypeMetadata userCtrl = controllerType("UserController", "/api/users",
                    List.of(method("getAll", "List<User>", Collections.emptyList(),
                            List.of(annotation("GetMapping")))));
            TypeMetadata orderCtrl = controllerType("OrderController", "/api/orders",
                    List.of(method("getAll", "List<Order>", Collections.emptyList(),
                            List.of(annotation("GetMapping")))));

            // Base has both, target removes OrderController
            SourceAnalysisReport baseReport = report(List.of(
                    file("UserController.java", List.of(userCtrl)),
                    file("OrderController.java", List.of(orderCtrl))));
            SourceAnalysisReport targetReport = report(List.of(
                    file("UserController.java", List.of(userCtrl))));

            ApiImpactReport result = analyzer.analyze(
                    "/repo", "a", "b", baseReport, targetReport);

            assertThat(result.baseEndpoints()).hasSize(2);
            assertThat(result.targetEndpoints()).hasSize(1);
            assertThat(result.breakingChangeCount()).isEqualTo(1);
            assertThat(result.findings()).anyMatch(f ->
                    f.baseEndpointId() != null && f.baseEndpointId().contains("/api/orders"));
        }

        @Test
        @DisplayName("Should handle complete API version migration")
        void shouldHandleVersionMigration() {
            TypeMetadata v1 = controllerType("UserControllerV1", "/api/v1/users",
                    List.of(method("getAll", "List<User>", Collections.emptyList(),
                            List.of(annotation("GetMapping")))));
            TypeMetadata v2 = controllerType("UserControllerV2", "/api/v2/users",
                    List.of(method("getAll", "Page<User>", Collections.emptyList(),
                            List.of(annotation("GetMapping")))));

            SourceAnalysisReport baseReport = report(List.of(
                    file("UserControllerV1.java", List.of(v1))));
            SourceAnalysisReport targetReport = report(List.of(
                    file("UserControllerV2.java", List.of(v2))));

            ApiImpactReport result = analyzer.analyze(
                    "/repo", "a", "b", baseReport, targetReport);

            // Should see removal of v1 endpoint and addition of v2 endpoint
            assertThat(result.breakingChangeCount()).isGreaterThanOrEqualTo(1);
            assertThat(result.compatibleChangeCount()).isGreaterThanOrEqualTo(1);
        }
    }
}

