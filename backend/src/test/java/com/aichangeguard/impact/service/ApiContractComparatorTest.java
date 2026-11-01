package com.aichangeguard.impact.service;

import com.aichangeguard.impact.model.ApiChangeFinding;
import com.aichangeguard.impact.model.ApiChangeSeverity;
import com.aichangeguard.impact.model.ApiChangeType;
import com.aichangeguard.impact.model.ApiEndpoint;
import com.aichangeguard.impact.model.EndpointParameter;
import com.aichangeguard.impact.model.HttpMethodType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link ApiContractComparator}.
 *
 * <p>Validates detection of API contract changes between base and target endpoint lists
 * including endpoint removal/addition, parameter changes, body/response type changes,
 * and validation constraint changes.</p>
 */
class ApiContractComparatorTest {

    private ApiContractComparator comparator;

    @BeforeEach
    void setUp() {
        comparator = new ApiContractComparator();
    }

    // --- Test Data Builders ---

    private ApiEndpoint endpoint(String path, HttpMethodType method, String controller,
                                  List<EndpointParameter> pathVars,
                                  List<EndpointParameter> params,
                                  String requestBody, boolean bodyRequired,
                                  String responseType) {
        String endpointId = ApiEndpoint.buildEndpointId(method, path);
        return new ApiEndpoint(
                endpointId, method, path, controller, "handler", "symbol",
                pathVars, params, requestBody, bodyRequired, responseType,
                Collections.emptyList(), Collections.emptyList(),
                Collections.emptyList(),
                "Controller.java", 10
        );
    }

    private ApiEndpoint simpleEndpoint(String path, HttpMethodType method) {
        return endpoint(path, method, "com.example.Controller",
                Collections.emptyList(), Collections.emptyList(),
                null, false, "String");
    }

    private ApiEndpoint endpointWithParams(String path, HttpMethodType method,
                                            List<EndpointParameter> params) {
        return endpoint(path, method, "com.example.Controller",
                Collections.emptyList(), params,
                null, false, "String");
    }

    private ApiEndpoint endpointWithBody(String path, HttpMethodType method,
                                          String bodyType, boolean required) {
        return endpoint(path, method, "com.example.Controller",
                Collections.emptyList(), Collections.emptyList(),
                bodyType, required, "String");
    }

    private ApiEndpoint endpointWithResponse(String path, HttpMethodType method,
                                              String responseType) {
        return endpoint(path, method, "com.example.Controller",
                Collections.emptyList(), Collections.emptyList(),
                null, false, responseType);
    }

    private ApiEndpoint endpointWithValidation(String path, HttpMethodType method,
                                                List<Map<String, String>> constraints) {
        String endpointId = ApiEndpoint.buildEndpointId(method, path);
        return new ApiEndpoint(
                endpointId, method, path, "com.example.Controller", "handler", "symbol",
                Collections.emptyList(), Collections.emptyList(),
                null, false, "String",
                Collections.emptyList(), Collections.emptyList(),
                constraints,
                "Controller.java", 10
        );
    }

    private EndpointParameter param(String name, String type, boolean required) {
        return new EndpointParameter(name, "RequestParam", type, required, null,
                Collections.emptyList());
    }

    // --- Tests ---

    @Nested
    @DisplayName("Endpoint addition and removal")
    class EndpointAdditionRemoval {

        @Test
        @DisplayName("Should detect removed endpoint as BREAKING")
        void shouldDetectRemovedEndpoint() {
            List<ApiEndpoint> base = List.of(simpleEndpoint("/users", HttpMethodType.GET));
            List<ApiEndpoint> target = Collections.emptyList();

            List<ApiChangeFinding> findings = comparator.compareEndpoints(base, target);

            assertThat(findings).hasSize(1);
            assertThat(findings.getFirst().changeType()).isEqualTo(ApiChangeType.ENDPOINT_REMOVED);
            assertThat(findings.getFirst().severity()).isEqualTo(ApiChangeSeverity.BREAKING);
            assertThat(findings.getFirst().baseEndpointId()).isEqualTo("GET:/users");
        }

        @Test
        @DisplayName("Should detect added endpoint as COMPATIBLE")
        void shouldDetectAddedEndpoint() {
            List<ApiEndpoint> base = Collections.emptyList();
            List<ApiEndpoint> target = List.of(simpleEndpoint("/users", HttpMethodType.POST));

            List<ApiChangeFinding> findings = comparator.compareEndpoints(base, target);

            assertThat(findings).hasSize(1);
            assertThat(findings.getFirst().changeType()).isEqualTo(ApiChangeType.ENDPOINT_ADDED);
            assertThat(findings.getFirst().severity()).isEqualTo(ApiChangeSeverity.COMPATIBLE);
            assertThat(findings.getFirst().targetEndpointId()).isEqualTo("POST:/users");
        }

        @Test
        @DisplayName("Should detect no changes when endpoints are identical")
        void shouldDetectNoChanges() {
            ApiEndpoint ep = simpleEndpoint("/users", HttpMethodType.GET);

            List<ApiChangeFinding> findings = comparator.compareEndpoints(List.of(ep), List.of(ep));

            assertThat(findings).isEmpty();
        }

        @Test
        @DisplayName("Should detect multiple additions and removals")
        void shouldDetectMultipleChanges() {
            List<ApiEndpoint> base = List.of(
                    simpleEndpoint("/users", HttpMethodType.GET),
                    simpleEndpoint("/orders", HttpMethodType.GET)
            );
            List<ApiEndpoint> target = List.of(
                    simpleEndpoint("/users", HttpMethodType.GET),
                    simpleEndpoint("/products", HttpMethodType.GET)
            );

            List<ApiChangeFinding> findings = comparator.compareEndpoints(base, target);

            assertThat(findings).hasSize(2);
            assertThat(findings.stream().map(ApiChangeFinding::changeType))
                    .containsExactlyInAnyOrder(
                            ApiChangeType.ENDPOINT_REMOVED,
                            ApiChangeType.ENDPOINT_ADDED
                    );
        }
    }

    @Nested
    @DisplayName("Parameter changes")
    class ParameterChanges {

        @Test
        @DisplayName("Should detect added required parameter as BREAKING")
        void shouldDetectAddedRequiredParam() {
            List<ApiEndpoint> base = List.of(
                    endpointWithParams("/users", HttpMethodType.GET, Collections.emptyList())
            );
            List<ApiEndpoint> target = List.of(
                    endpointWithParams("/users", HttpMethodType.GET,
                            List.of(param("filter", "String", true)))
            );

            List<ApiChangeFinding> findings = comparator.compareEndpoints(base, target);

            assertThat(findings).hasSize(1);
            assertThat(findings.getFirst().changeType()).isEqualTo(ApiChangeType.REQUIRED_PARAMETER_ADDED);
            assertThat(findings.getFirst().severity()).isEqualTo(ApiChangeSeverity.BREAKING);
        }

        @Test
        @DisplayName("Should detect added optional parameter as COMPATIBLE")
        void shouldDetectAddedOptionalParam() {
            List<ApiEndpoint> base = List.of(
                    endpointWithParams("/users", HttpMethodType.GET, Collections.emptyList())
            );
            List<ApiEndpoint> target = List.of(
                    endpointWithParams("/users", HttpMethodType.GET,
                            List.of(param("page", "int", false)))
            );

            List<ApiChangeFinding> findings = comparator.compareEndpoints(base, target);

            assertThat(findings).hasSize(1);
            assertThat(findings.getFirst().changeType()).isEqualTo(ApiChangeType.OPTIONAL_PARAMETER_ADDED);
            assertThat(findings.getFirst().severity()).isEqualTo(ApiChangeSeverity.COMPATIBLE);
        }

        @Test
        @DisplayName("Should detect removed required parameter as BREAKING")
        void shouldDetectRemovedRequiredParam() {
            List<ApiEndpoint> base = List.of(
                    endpointWithParams("/users", HttpMethodType.GET,
                            List.of(param("name", "String", true)))
            );
            List<ApiEndpoint> target = List.of(
                    endpointWithParams("/users", HttpMethodType.GET, Collections.emptyList())
            );

            List<ApiChangeFinding> findings = comparator.compareEndpoints(base, target);

            assertThat(findings).hasSize(1);
            assertThat(findings.getFirst().changeType()).isEqualTo(ApiChangeType.REQUIRED_PARAMETER_REMOVED);
            assertThat(findings.getFirst().severity()).isEqualTo(ApiChangeSeverity.BREAKING);
        }

        @Test
        @DisplayName("Should detect parameter type change as POTENTIALLY_BREAKING")
        void shouldDetectParamTypeChange() {
            List<ApiEndpoint> base = List.of(
                    endpointWithParams("/users", HttpMethodType.GET,
                            List.of(param("id", "Long", true)))
            );
            List<ApiEndpoint> target = List.of(
                    endpointWithParams("/users", HttpMethodType.GET,
                            List.of(param("id", "String", true)))
            );

            List<ApiChangeFinding> findings = comparator.compareEndpoints(base, target);

            assertThat(findings).anySatisfy(f -> {
                assertThat(f.changeType()).isEqualTo(ApiChangeType.PARAMETER_TYPE_CHANGED);
                assertThat(f.severity()).isEqualTo(ApiChangeSeverity.POTENTIALLY_BREAKING);
                assertThat(f.baseValue()).isEqualTo("Long");
                assertThat(f.targetValue()).isEqualTo("String");
            });
        }

        @Test
        @DisplayName("Should detect parameter required status change to required as BREAKING")
        void shouldDetectParamBecameRequired() {
            List<ApiEndpoint> base = List.of(
                    endpointWithParams("/users", HttpMethodType.GET,
                            List.of(param("filter", "String", false)))
            );
            List<ApiEndpoint> target = List.of(
                    endpointWithParams("/users", HttpMethodType.GET,
                            List.of(param("filter", "String", true)))
            );

            List<ApiChangeFinding> findings = comparator.compareEndpoints(base, target);

            assertThat(findings).anySatisfy(f -> {
                assertThat(f.severity()).isEqualTo(ApiChangeSeverity.BREAKING);
                assertThat(f.baseValue()).isEqualTo("optional");
                assertThat(f.targetValue()).isEqualTo("required");
            });
        }
    }

    @Nested
    @DisplayName("Request body changes")
    class RequestBodyChanges {

        @Test
        @DisplayName("Should detect request body type change as POTENTIALLY_BREAKING")
        void shouldDetectBodyTypeChange() {
            List<ApiEndpoint> base = List.of(
                    endpointWithBody("/users", HttpMethodType.POST, "CreateUserRequest", true)
            );
            List<ApiEndpoint> target = List.of(
                    endpointWithBody("/users", HttpMethodType.POST, "CreateUserRequestV2", true)
            );

            List<ApiChangeFinding> findings = comparator.compareEndpoints(base, target);

            assertThat(findings).anySatisfy(f -> {
                assertThat(f.changeType()).isEqualTo(ApiChangeType.REQUEST_BODY_CHANGED);
                assertThat(f.severity()).isEqualTo(ApiChangeSeverity.POTENTIALLY_BREAKING);
            });
        }

        @Test
        @DisplayName("Should detect added request body as BREAKING")
        void shouldDetectAddedBody() {
            List<ApiEndpoint> base = List.of(
                    endpointWithBody("/users", HttpMethodType.POST, null, false)
            );
            List<ApiEndpoint> target = List.of(
                    endpointWithBody("/users", HttpMethodType.POST, "UserRequest", true)
            );

            List<ApiChangeFinding> findings = comparator.compareEndpoints(base, target);

            assertThat(findings).anySatisfy(f -> {
                assertThat(f.changeType()).isEqualTo(ApiChangeType.REQUEST_BODY_CHANGED);
                assertThat(f.severity()).isEqualTo(ApiChangeSeverity.BREAKING);
            });
        }

        @Test
        @DisplayName("Should detect removed request body as BREAKING")
        void shouldDetectRemovedBody() {
            List<ApiEndpoint> base = List.of(
                    endpointWithBody("/users", HttpMethodType.POST, "UserRequest", true)
            );
            List<ApiEndpoint> target = List.of(
                    endpointWithBody("/users", HttpMethodType.POST, null, false)
            );

            List<ApiChangeFinding> findings = comparator.compareEndpoints(base, target);

            assertThat(findings).anySatisfy(f -> {
                assertThat(f.changeType()).isEqualTo(ApiChangeType.REQUEST_BODY_CHANGED);
                assertThat(f.severity()).isEqualTo(ApiChangeSeverity.BREAKING);
            });
        }
    }

    @Nested
    @DisplayName("Response type changes")
    class ResponseTypeChanges {

        @Test
        @DisplayName("Should detect response type change as POTENTIALLY_BREAKING")
        void shouldDetectResponseTypeChange() {
            List<ApiEndpoint> base = List.of(
                    endpointWithResponse("/users", HttpMethodType.GET, "List<UserDto>")
            );
            List<ApiEndpoint> target = List.of(
                    endpointWithResponse("/users", HttpMethodType.GET, "Page<UserDto>")
            );

            List<ApiChangeFinding> findings = comparator.compareEndpoints(base, target);

            assertThat(findings).hasSize(1);
            assertThat(findings.getFirst().changeType()).isEqualTo(ApiChangeType.RESPONSE_TYPE_CHANGED);
            assertThat(findings.getFirst().severity()).isEqualTo(ApiChangeSeverity.POTENTIALLY_BREAKING);
            assertThat(findings.getFirst().baseValue()).isEqualTo("List<UserDto>");
            assertThat(findings.getFirst().targetValue()).isEqualTo("Page<UserDto>");
        }

        @Test
        @DisplayName("Should not report when response type is unchanged")
        void shouldNotReportUnchangedResponse() {
            List<ApiEndpoint> base = List.of(
                    endpointWithResponse("/users", HttpMethodType.GET, "List<UserDto>")
            );
            List<ApiEndpoint> target = List.of(
                    endpointWithResponse("/users", HttpMethodType.GET, "List<UserDto>")
            );

            List<ApiChangeFinding> findings = comparator.compareEndpoints(base, target);

            assertThat(findings).isEmpty();
        }
    }

    @Nested
    @DisplayName("Validation constraint changes")
    class ValidationChanges {

        @Test
        @DisplayName("Should detect validation constraint changes")
        void shouldDetectValidationChanges() {
            List<ApiEndpoint> base = List.of(
                    endpointWithValidation("/users", HttpMethodType.POST,
                            List.of(Map.of("annotation", "NotNull")))
            );
            List<ApiEndpoint> target = List.of(
                    endpointWithValidation("/users", HttpMethodType.POST,
                            List.of(Map.of("annotation", "NotNull"),
                                    Map.of("annotation", "Size", "min", "1", "max", "100")))
            );

            List<ApiChangeFinding> findings = comparator.compareEndpoints(base, target);

            assertThat(findings).anySatisfy(f ->
                    assertThat(f.changeType()).isEqualTo(ApiChangeType.VALIDATION_CONSTRAINT_CHANGED)
            );
        }
    }

    @Nested
    @DisplayName("Edge cases")
    class EdgeCases {

        @Test
        @DisplayName("Should handle null lists")
        void shouldHandleNullLists() {
            List<ApiChangeFinding> findings = comparator.compareEndpoints(null, null);
            assertThat(findings).isEmpty();
        }

        @Test
        @DisplayName("Should handle empty lists")
        void shouldHandleEmptyLists() {
            List<ApiChangeFinding> findings = comparator.compareEndpoints(
                    Collections.emptyList(), Collections.emptyList()
            );
            assertThat(findings).isEmpty();
        }

        @Test
        @DisplayName("Should handle complete endpoint replacement")
        void shouldHandleCompleteReplacement() {
            List<ApiEndpoint> base = List.of(
                    simpleEndpoint("/v1/users", HttpMethodType.GET),
                    simpleEndpoint("/v1/orders", HttpMethodType.GET)
            );
            List<ApiEndpoint> target = List.of(
                    simpleEndpoint("/v2/users", HttpMethodType.GET),
                    simpleEndpoint("/v2/orders", HttpMethodType.GET)
            );

            List<ApiChangeFinding> findings = comparator.compareEndpoints(base, target);

            // Should see 2 removals and 2 additions
            long removals = findings.stream()
                    .filter(f -> f.changeType() == ApiChangeType.ENDPOINT_REMOVED).count();
            long additions = findings.stream()
                    .filter(f -> f.changeType() == ApiChangeType.ENDPOINT_ADDED).count();

            assertThat(removals).isEqualTo(2);
            assertThat(additions).isEqualTo(2);
        }
    }
}
