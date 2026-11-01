package com.aichangeguard.impact.service;

import com.aichangeguard.analysis.model.AnnotationMetadata;
import com.aichangeguard.analysis.model.MethodMetadata;
import com.aichangeguard.analysis.model.ParameterMetadata;
import com.aichangeguard.analysis.model.ParsedFileMetadata;
import com.aichangeguard.analysis.model.SourceAnalysisReport;
import com.aichangeguard.analysis.model.SourceRange;
import com.aichangeguard.analysis.model.TypeKind;
import com.aichangeguard.analysis.model.TypeMetadata;
import com.aichangeguard.impact.model.ApiEndpoint;
import com.aichangeguard.impact.model.HttpMethodType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link RestEndpointExtractor}.
 *
 * <p>Validates REST endpoint discovery from parsed Java source metadata including
 * controller detection, path composition, parameter extraction, and annotation handling.</p>
 */
class RestEndpointExtractorTest {

    private RestEndpointExtractor extractor;

    @BeforeEach
    void setUp() {
        extractor = new RestEndpointExtractor();
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
                "com.example.TestController#" + name + "()",
                name, returnType, params,
                Set.of("public"), annotations,
                Collections.emptyList(), false, false,
                SourceRange.of(10, 1, 20, 1),
                Collections.emptyList()
        );
    }

    private TypeMetadata controllerType(String name, List<AnnotationMetadata> classAnnotations,
                                         List<MethodMetadata> methods) {
        return new TypeMetadata(
                "com.example." + name,
                "com.example." + name,
                name,
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
                files, Collections.emptyList(), Instant.now(), Collections.emptyList()
        );
    }

    private ParsedFileMetadata file(List<TypeMetadata> types) {
        return new ParsedFileMetadata(
                "src/main/java/com/example/TestController.java",
                "com.example",
                Collections.emptyList(),
                types,
                Collections.emptyList(),
                true,
                SourceRange.UNKNOWN
        );
    }

    // --- Tests ---

    @Nested
    @DisplayName("Controller detection")
    class ControllerDetection {

        @Test
        @DisplayName("Should detect @RestController annotated class")
        void shouldDetectRestController() {
            TypeMetadata type = controllerType("UserController",
                    List.of(
                            annotation("RestController"),
                            annotation("RequestMapping", Map.of("value", "\"/api/users\""))
                    ),
                    List.of(method("getUsers", "List<User>",
                            Collections.emptyList(),
                            List.of(annotation("GetMapping"))
                    ))
            );

            List<ApiEndpoint> endpoints = extractor.extractEndpoints(report(List.of(file(List.of(type)))));

            assertThat(endpoints).hasSize(1);
            assertThat(endpoints.getFirst().httpMethod()).isEqualTo(HttpMethodType.GET);
            assertThat(endpoints.getFirst().path()).isEqualTo("/api/users");
        }

        @Test
        @DisplayName("Should detect @Controller annotated class")
        void shouldDetectController() {
            TypeMetadata type = controllerType("PageController",
                    List.of(annotation("Controller")),
                    List.of(method("home", "String",
                            Collections.emptyList(),
                            List.of(annotation("GetMapping", Map.of("value", "\"/home\"")))
                    ))
            );

            List<ApiEndpoint> endpoints = extractor.extractEndpoints(report(List.of(file(List.of(type)))));

            assertThat(endpoints).hasSize(1);
            assertThat(endpoints.getFirst().path()).isEqualTo("/home");
        }

        @Test
        @DisplayName("Should skip non-controller classes")
        void shouldSkipNonControllerClasses() {
            TypeMetadata type = controllerType("UserService",
                    List.of(annotation("Service")),
                    List.of(method("findAll", "List<User>",
                            Collections.emptyList(),
                            List.of(annotation("GetMapping"))
                    ))
            );

            List<ApiEndpoint> endpoints = extractor.extractEndpoints(report(List.of(file(List.of(type)))));

            assertThat(endpoints).isEmpty();
        }

        @Test
        @DisplayName("Should skip interfaces even with annotations")
        void shouldSkipInterfaces() {
            TypeMetadata type = new TypeMetadata(
                    "com.example.UserApi", "com.example.UserApi", "UserApi",
                    TypeKind.INTERFACE,
                    Set.of("public"),
                    List.of(annotation("RestController")),
                    Collections.emptyList(), Collections.emptyList(),
                    Collections.emptyList(), Collections.emptyList(),
                    List.of(method("get", "User", Collections.emptyList(),
                            List.of(annotation("GetMapping", Map.of("value", "\"/users\""))))),
                    Collections.emptyList(), SourceRange.UNKNOWN
            );

            List<ApiEndpoint> endpoints = extractor.extractEndpoints(report(List.of(file(List.of(type)))));

            assertThat(endpoints).isEmpty();
        }
    }

    @Nested
    @DisplayName("Path composition")
    class PathComposition {

        @Test
        @DisplayName("Should combine class-level and method-level paths")
        void shouldCombinePaths() {
            TypeMetadata type = controllerType("UserController",
                    List.of(
                            annotation("RestController"),
                            annotation("RequestMapping", Map.of("value", "\"/api/v1\""))
                    ),
                    List.of(method("getUser", "User",
                            Collections.emptyList(),
                            List.of(annotation("GetMapping", Map.of("value", "\"/users/{id}\"")))
                    ))
            );

            List<ApiEndpoint> endpoints = extractor.extractEndpoints(report(List.of(file(List.of(type)))));

            assertThat(endpoints).hasSize(1);
            assertThat(endpoints.getFirst().path()).isEqualTo("/api/v1/users/{id}");
        }

        @Test
        @DisplayName("Should handle missing class-level path")
        void shouldHandleMissingClassPath() {
            TypeMetadata type = controllerType("SimpleController",
                    List.of(annotation("RestController")),
                    List.of(method("health", "String",
                            Collections.emptyList(),
                            List.of(annotation("GetMapping", Map.of("value", "\"/health\"")))
                    ))
            );

            List<ApiEndpoint> endpoints = extractor.extractEndpoints(report(List.of(file(List.of(type)))));

            assertThat(endpoints).hasSize(1);
            assertThat(endpoints.getFirst().path()).isEqualTo("/health");
        }

        @Test
        @DisplayName("Should handle missing method-level path")
        void shouldHandleMissingMethodPath() {
            TypeMetadata type = controllerType("RootController",
                    List.of(
                            annotation("RestController"),
                            annotation("RequestMapping", Map.of("value", "\"/api\""))
                    ),
                    List.of(method("root", "String",
                            Collections.emptyList(),
                            List.of(annotation("GetMapping"))
                    ))
            );

            List<ApiEndpoint> endpoints = extractor.extractEndpoints(report(List.of(file(List.of(type)))));

            assertThat(endpoints).hasSize(1);
            assertThat(endpoints.getFirst().path()).isEqualTo("/api");
        }

        @Test
        @DisplayName("Should normalize path with trailing/leading slashes")
        void shouldNormalizeSlashes() {
            TypeMetadata type = controllerType("SlashController",
                    List.of(
                            annotation("RestController"),
                            annotation("RequestMapping", Map.of("value", "\"/api/\""))
                    ),
                    List.of(method("get", "String",
                            Collections.emptyList(),
                            List.of(annotation("GetMapping", Map.of("value", "\"/items\"")))
                    ))
            );

            List<ApiEndpoint> endpoints = extractor.extractEndpoints(report(List.of(file(List.of(type)))));

            assertThat(endpoints).hasSize(1);
            assertThat(endpoints.getFirst().path()).isEqualTo("/api/items");
        }
    }

    @Nested
    @DisplayName("HTTP method extraction")
    class HttpMethodExtraction {

        @Test
        @DisplayName("Should extract GET from @GetMapping")
        void shouldExtractGet() {
            TypeMetadata type = controllerType("Ctrl",
                    List.of(annotation("RestController")),
                    List.of(method("m", "void", Collections.emptyList(),
                            List.of(annotation("GetMapping", Map.of("value", "\"/test\""))))));

            List<ApiEndpoint> endpoints = extractor.extractEndpoints(report(List.of(file(List.of(type)))));
            assertThat(endpoints.getFirst().httpMethod()).isEqualTo(HttpMethodType.GET);
        }

        @Test
        @DisplayName("Should extract POST from @PostMapping")
        void shouldExtractPost() {
            TypeMetadata type = controllerType("Ctrl",
                    List.of(annotation("RestController")),
                    List.of(method("m", "void", Collections.emptyList(),
                            List.of(annotation("PostMapping", Map.of("value", "\"/test\""))))));

            List<ApiEndpoint> endpoints = extractor.extractEndpoints(report(List.of(file(List.of(type)))));
            assertThat(endpoints.getFirst().httpMethod()).isEqualTo(HttpMethodType.POST);
        }

        @Test
        @DisplayName("Should extract PUT from @PutMapping")
        void shouldExtractPut() {
            TypeMetadata type = controllerType("Ctrl",
                    List.of(annotation("RestController")),
                    List.of(method("m", "void", Collections.emptyList(),
                            List.of(annotation("PutMapping", Map.of("value", "\"/test\""))))));

            List<ApiEndpoint> endpoints = extractor.extractEndpoints(report(List.of(file(List.of(type)))));
            assertThat(endpoints.getFirst().httpMethod()).isEqualTo(HttpMethodType.PUT);
        }

        @Test
        @DisplayName("Should extract DELETE from @DeleteMapping")
        void shouldExtractDelete() {
            TypeMetadata type = controllerType("Ctrl",
                    List.of(annotation("RestController")),
                    List.of(method("m", "void", Collections.emptyList(),
                            List.of(annotation("DeleteMapping", Map.of("value", "\"/test\""))))));

            List<ApiEndpoint> endpoints = extractor.extractEndpoints(report(List.of(file(List.of(type)))));
            assertThat(endpoints.getFirst().httpMethod()).isEqualTo(HttpMethodType.DELETE);
        }

        @Test
        @DisplayName("Should extract PATCH from @PatchMapping")
        void shouldExtractPatch() {
            TypeMetadata type = controllerType("Ctrl",
                    List.of(annotation("RestController")),
                    List.of(method("m", "void", Collections.emptyList(),
                            List.of(annotation("PatchMapping", Map.of("value", "\"/test\""))))));

            List<ApiEndpoint> endpoints = extractor.extractEndpoints(report(List.of(file(List.of(type)))));
            assertThat(endpoints.getFirst().httpMethod()).isEqualTo(HttpMethodType.PATCH);
        }

        @Test
        @DisplayName("Should extract HTTP method from @RequestMapping method attribute")
        void shouldExtractFromRequestMapping() {
            TypeMetadata type = controllerType("Ctrl",
                    List.of(annotation("RestController")),
                    List.of(method("m", "void", Collections.emptyList(),
                            List.of(annotation("RequestMapping",
                                    Map.of("value", "\"/test\"", "method", "RequestMethod.GET"))))));

            List<ApiEndpoint> endpoints = extractor.extractEndpoints(report(List.of(file(List.of(type)))));
            assertThat(endpoints).hasSize(1);
            assertThat(endpoints.getFirst().httpMethod()).isEqualTo(HttpMethodType.GET);
        }

        @Test
        @DisplayName("Should default to UNKNOWN when @RequestMapping has no method")
        void shouldDefaultToUnknown() {
            TypeMetadata type = controllerType("Ctrl",
                    List.of(annotation("RestController")),
                    List.of(method("m", "void", Collections.emptyList(),
                            List.of(annotation("RequestMapping", Map.of("value", "\"/test\""))))));

            List<ApiEndpoint> endpoints = extractor.extractEndpoints(report(List.of(file(List.of(type)))));
            assertThat(endpoints).hasSize(1);
            assertThat(endpoints.getFirst().httpMethod()).isEqualTo(HttpMethodType.UNKNOWN);
        }
    }

    @Nested
    @DisplayName("Parameter extraction")
    class ParameterExtraction {

        @Test
        @DisplayName("Should extract @RequestParam parameters")
        void shouldExtractRequestParam() {
            ParameterMetadata param = parameter("name", "String",
                    List.of(annotation("RequestParam", Map.of("value", "\"name\""))));

            TypeMetadata type = controllerType("Ctrl",
                    List.of(annotation("RestController")),
                    List.of(method("search", "List<User>", List.of(param),
                            List.of(annotation("GetMapping", Map.of("value", "\"/search\""))))));

            List<ApiEndpoint> endpoints = extractor.extractEndpoints(report(List.of(file(List.of(type)))));

            assertThat(endpoints).hasSize(1);
            assertThat(endpoints.getFirst().requestParameters()).hasSize(1);
            assertThat(endpoints.getFirst().requestParameters().getFirst().name()).isEqualTo("name");
            assertThat(endpoints.getFirst().requestParameters().getFirst().source()).isEqualTo("RequestParam");
            assertThat(endpoints.getFirst().requestParameters().getFirst().typeName()).isEqualTo("String");
        }

        @Test
        @DisplayName("Should extract @PathVariable parameters")
        void shouldExtractPathVariable() {
            ParameterMetadata param = parameter("id", "Long",
                    List.of(annotation("PathVariable")));

            TypeMetadata type = controllerType("Ctrl",
                    List.of(annotation("RestController")),
                    List.of(method("getById", "User", List.of(param),
                            List.of(annotation("GetMapping", Map.of("value", "\"/{id}\""))))));

            List<ApiEndpoint> endpoints = extractor.extractEndpoints(report(List.of(file(List.of(type)))));

            assertThat(endpoints).hasSize(1);
            assertThat(endpoints.getFirst().pathVariables()).hasSize(1);
            assertThat(endpoints.getFirst().pathVariables().getFirst().name()).isEqualTo("id");
            assertThat(endpoints.getFirst().pathVariables().getFirst().source()).isEqualTo("PathVariable");
        }

        @Test
        @DisplayName("Should extract @RequestBody type")
        void shouldExtractRequestBody() {
            ParameterMetadata param = parameter("body", "CreateUserRequest",
                    List.of(annotation("RequestBody")));

            TypeMetadata type = controllerType("Ctrl",
                    List.of(annotation("RestController")),
                    List.of(method("create", "User", List.of(param),
                            List.of(annotation("PostMapping", Map.of("value", "\"/users\""))))));

            List<ApiEndpoint> endpoints = extractor.extractEndpoints(report(List.of(file(List.of(type)))));

            assertThat(endpoints).hasSize(1);
            assertThat(endpoints.getFirst().requestBodyType()).isEqualTo("CreateUserRequest");
            assertThat(endpoints.getFirst().requestBodyRequired()).isTrue();
        }

        @Test
        @DisplayName("Should detect optional request body")
        void shouldDetectOptionalRequestBody() {
            ParameterMetadata param = parameter("body", "UpdateRequest",
                    List.of(annotation("RequestBody", Map.of("required", "false"))));

            TypeMetadata type = controllerType("Ctrl",
                    List.of(annotation("RestController")),
                    List.of(method("update", "User", List.of(param),
                            List.of(annotation("PatchMapping", Map.of("value", "\"/users/{id}\""))))));

            List<ApiEndpoint> endpoints = extractor.extractEndpoints(report(List.of(file(List.of(type)))));

            assertThat(endpoints).hasSize(1);
            assertThat(endpoints.getFirst().requestBodyRequired()).isFalse();
        }

        @Test
        @DisplayName("Should extract optional parameter with defaultValue")
        void shouldExtractOptionalParamWithDefault() {
            ParameterMetadata param = parameter("page", "int",
                    List.of(annotation("RequestParam",
                            Map.of("value", "\"page\"", "defaultValue", "\"0\""))));

            TypeMetadata type = controllerType("Ctrl",
                    List.of(annotation("RestController")),
                    List.of(method("list", "Page<User>", List.of(param),
                            List.of(annotation("GetMapping", Map.of("value", "\"/users\""))))));

            List<ApiEndpoint> endpoints = extractor.extractEndpoints(report(List.of(file(List.of(type)))));

            assertThat(endpoints).hasSize(1);
            assertThat(endpoints.getFirst().requestParameters().getFirst().required()).isFalse();
            assertThat(endpoints.getFirst().requestParameters().getFirst().defaultValue()).isEqualTo("0");
        }
    }

    @Nested
    @DisplayName("Validation constraint extraction")
    class ValidationExtraction {

        @Test
        @DisplayName("Should extract validation annotations from parameters")
        void shouldExtractValidationAnnotations() {
            ParameterMetadata param = parameter("email", "String",
                    List.of(
                            annotation("RequestParam"),
                            annotation("NotBlank"),
                            annotation("Email")
                    ));

            TypeMetadata type = controllerType("Ctrl",
                    List.of(annotation("RestController")),
                    List.of(method("findByEmail", "User", List.of(param),
                            List.of(annotation("GetMapping", Map.of("value", "\"/find\""))))));

            List<ApiEndpoint> endpoints = extractor.extractEndpoints(report(List.of(file(List.of(type)))));

            assertThat(endpoints).hasSize(1);
            assertThat(endpoints.getFirst().validationConstraints()).hasSizeGreaterThanOrEqualTo(2);
        }
    }

    @Nested
    @DisplayName("Edge cases")
    class EdgeCases {

        @Test
        @DisplayName("Should handle null report")
        void shouldHandleNullReport() {
            List<ApiEndpoint> endpoints = extractor.extractEndpoints(null);
            assertThat(endpoints).isEmpty();
        }

        @Test
        @DisplayName("Should handle empty report")
        void shouldHandleEmptyReport() {
            SourceAnalysisReport emptyReport = new SourceAnalysisReport(
                    "/repo", 0, 0, 0, 0.0,
                    Collections.emptyList(), Collections.emptyList(),
                    Instant.now(), Collections.emptyList()
            );

            List<ApiEndpoint> endpoints = extractor.extractEndpoints(emptyReport);
            assertThat(endpoints).isEmpty();
        }

        @Test
        @DisplayName("Should skip failed files")
        void shouldSkipFailedFiles() {
            ParsedFileMetadata failedFile = new ParsedFileMetadata(
                    "broken.java", "", Collections.emptyList(),
                    Collections.emptyList(), Collections.emptyList(),
                    false, SourceRange.UNKNOWN
            );

            List<ApiEndpoint> endpoints = extractor.extractEndpoints(report(List.of(failedFile)));
            assertThat(endpoints).isEmpty();
        }

        @Test
        @DisplayName("Should skip methods without mapping annotations")
        void shouldSkipNonMappedMethods() {
            TypeMetadata type = controllerType("Ctrl",
                    List.of(annotation("RestController")),
                    List.of(method("helperMethod", "void", Collections.emptyList(),
                            List.of(annotation("Transactional")))));

            List<ApiEndpoint> endpoints = extractor.extractEndpoints(report(List.of(file(List.of(type)))));
            assertThat(endpoints).isEmpty();
        }

        @Test
        @DisplayName("Should generate stable endpoint IDs")
        void shouldGenerateStableEndpointIds() {
            TypeMetadata type = controllerType("Ctrl",
                    List.of(annotation("RestController")),
                    List.of(method("get", "User", Collections.emptyList(),
                            List.of(annotation("GetMapping", Map.of("value", "\"/users/{id}\""))))));

            List<ApiEndpoint> endpoints = extractor.extractEndpoints(report(List.of(file(List.of(type)))));

            assertThat(endpoints).hasSize(1);
            assertThat(endpoints.getFirst().endpointId()).isEqualTo("GET:/users/{id}");
        }

        @Test
        @DisplayName("Should extract endpoints from multiple controllers")
        void shouldHandleMultipleControllers() {
            TypeMetadata type1 = controllerType("UserController",
                    List.of(annotation("RestController"),
                            annotation("RequestMapping", Map.of("value", "\"/users\""))),
                    List.of(method("list", "List<User>", Collections.emptyList(),
                            List.of(annotation("GetMapping")))));

            TypeMetadata type2 = controllerType("OrderController",
                    List.of(annotation("RestController"),
                            annotation("RequestMapping", Map.of("value", "\"/orders\""))),
                    List.of(method("list", "List<Order>", Collections.emptyList(),
                            List.of(annotation("GetMapping")))));

            ParsedFileMetadata f1 = new ParsedFileMetadata(
                    "UserController.java", "com.example", Collections.emptyList(),
                    List.of(type1), Collections.emptyList(), true, SourceRange.UNKNOWN);
            ParsedFileMetadata f2 = new ParsedFileMetadata(
                    "OrderController.java", "com.example", Collections.emptyList(),
                    List.of(type2), Collections.emptyList(), true, SourceRange.UNKNOWN);

            List<ApiEndpoint> endpoints = extractor.extractEndpoints(report(List.of(f1, f2)));

            assertThat(endpoints).hasSize(2);
            assertThat(endpoints.stream().map(ApiEndpoint::path))
                    .containsExactlyInAnyOrder("/users", "/orders");
        }
    }

    @Nested
    @DisplayName("Media type extraction")
    class MediaTypeExtraction {

        @Test
        @DisplayName("Should extract produces media types")
        void shouldExtractProduces() {
            TypeMetadata type = controllerType("Ctrl",
                    List.of(annotation("RestController")),
                    List.of(method("get", "User", Collections.emptyList(),
                            List.of(annotation("GetMapping",
                                    Map.of("value", "\"/test\"",
                                            "produces", "\"application/json\""))))));

            List<ApiEndpoint> endpoints = extractor.extractEndpoints(report(List.of(file(List.of(type)))));

            assertThat(endpoints).hasSize(1);
            assertThat(endpoints.getFirst().producesMediaTypes()).contains("application/json");
        }

        @Test
        @DisplayName("Should extract consumes media types")
        void shouldExtractConsumes() {
            TypeMetadata type = controllerType("Ctrl",
                    List.of(annotation("RestController")),
                    List.of(method("create", "User", Collections.emptyList(),
                            List.of(annotation("PostMapping",
                                    Map.of("value", "\"/test\"",
                                            "consumes", "\"application/json\""))))));

            List<ApiEndpoint> endpoints = extractor.extractEndpoints(report(List.of(file(List.of(type)))));

            assertThat(endpoints).hasSize(1);
            assertThat(endpoints.getFirst().consumesMediaTypes()).contains("application/json");
        }
    }
}
