package com.aichangeguard.impact.service;

import com.aichangeguard.impact.model.ApiChangeFinding;
import com.aichangeguard.impact.model.ApiChangeSeverity;
import com.aichangeguard.impact.model.ApiChangeType;
import com.aichangeguard.impact.model.ApiEndpoint;
import com.aichangeguard.impact.model.EndpointParameter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Compares two sets of REST API endpoints (base and target revision) to detect contract changes.
 *
 * <p>Produces a list of {@link ApiChangeFinding} instances, each classified with a
 * {@link ApiChangeSeverity} indicating the potential impact on API consumers.</p>
 *
 * <h3>Comparison strategy</h3>
 * <ol>
 *   <li>Endpoints are matched by their stable endpoint ID (HTTP_METHOD:path).</li>
 *   <li>Endpoints in base but not target → ENDPOINT_REMOVED (BREAKING).</li>
 *   <li>Endpoints in target but not base → ENDPOINT_ADDED (COMPATIBLE).</li>
 *   <li>Matched endpoints are compared for parameter, body, response, and constraint changes.</li>
 * </ol>
 *
 * <h3>Limitations</h3>
 * <ul>
 *   <li>Endpoint matching uses syntactic path comparison; runtime path resolution
 *       (e.g., SpEL, property placeholders) is not evaluated.</li>
 *   <li>DTO structural comparison is limited to type name changes; field-level DTO
 *       comparison is not yet implemented.</li>
 *   <li>Renamed endpoints are reported as removed + added, not as a rename operation.</li>
 * </ul>
 */
@Service
public class ApiContractComparator {

    private static final Logger log = LoggerFactory.getLogger(ApiContractComparator.class);

    /**
     * Compares base and target endpoint lists and produces change findings.
     *
     * @param baseEndpoints endpoints from the base revision
     * @param targetEndpoints endpoints from the target revision
     * @return list of detected API contract changes
     */
    public List<ApiChangeFinding> compareEndpoints(List<ApiEndpoint> baseEndpoints,
                                                    List<ApiEndpoint> targetEndpoints) {
        List<ApiChangeFinding> findings = new ArrayList<>();

        Map<String, ApiEndpoint> baseMap = indexById(safeList(baseEndpoints));
        Map<String, ApiEndpoint> targetMap = indexById(safeList(targetEndpoints));

        Set<String> allIds = new HashSet<>();
        allIds.addAll(baseMap.keySet());
        allIds.addAll(targetMap.keySet());

        for (String endpointId : allIds) {
            ApiEndpoint base = baseMap.get(endpointId);
            ApiEndpoint target = targetMap.get(endpointId);

            if (base != null && target == null) {
                // Endpoint removed
                findings.add(new ApiChangeFinding(
                        ApiChangeType.ENDPOINT_REMOVED,
                        ApiChangeSeverity.BREAKING,
                        "Endpoint removed: " + endpointId,
                        endpointId,
                        null,
                        base.controllerClass(),
                        endpointId,
                        base.httpMethod() + " " + base.path(),
                        null,
                        base.sourceFile(),
                        base.sourceLine()
                ));
            } else if (base == null && target != null) {
                // Endpoint added
                findings.add(new ApiChangeFinding(
                        ApiChangeType.ENDPOINT_ADDED,
                        ApiChangeSeverity.COMPATIBLE,
                        "New endpoint added: " + endpointId,
                        null,
                        endpointId,
                        target.controllerClass(),
                        endpointId,
                        null,
                        target.httpMethod() + " " + target.path(),
                        target.sourceFile(),
                        target.sourceLine()
                ));
            } else {
                // Both exist — compare details
                findings.addAll(compareMatchedEndpoints(base, target));
            }
        }

        log.info("API comparison produced {} findings ({} breaking, {} potentially breaking, {} compatible, {} unknown)",
                findings.size(),
                findings.stream().filter(f -> f.severity() == ApiChangeSeverity.BREAKING).count(),
                findings.stream().filter(f -> f.severity() == ApiChangeSeverity.POTENTIALLY_BREAKING).count(),
                findings.stream().filter(f -> f.severity() == ApiChangeSeverity.COMPATIBLE).count(),
                findings.stream().filter(f -> f.severity() == ApiChangeSeverity.UNKNOWN).count()
        );

        return findings;
    }

    /**
     * Compares two matched endpoints for detailed changes.
     */
    private List<ApiChangeFinding> compareMatchedEndpoints(ApiEndpoint base, ApiEndpoint target) {
        List<ApiChangeFinding> findings = new ArrayList<>();

        // Compare request parameters
        findings.addAll(compareParameters(base, target,
                safeList(base.requestParameters()), safeList(target.requestParameters())));

        // Compare path variables
        findings.addAll(comparePathVariables(base, target));

        // Compare request body
        findings.addAll(compareRequestBody(base, target));

        // Compare response type
        findings.addAll(compareResponseType(base, target));

        // Compare validation constraints
        findings.addAll(compareValidationConstraints(base, target));

        // Compare media types
        findings.addAll(compareMediaTypes(base, target, "produces",
                safeList(base.producesMediaTypes()), safeList(target.producesMediaTypes())));
        findings.addAll(compareMediaTypes(base, target, "consumes",
                safeList(base.consumesMediaTypes()), safeList(target.consumesMediaTypes())));

        return findings;
    }

    /**
     * Compares request parameters between two endpoint revisions.
     */
    private List<ApiChangeFinding> compareParameters(ApiEndpoint base, ApiEndpoint target,
                                                      List<EndpointParameter> baseParams,
                                                      List<EndpointParameter> targetParams) {
        List<ApiChangeFinding> findings = new ArrayList<>();
        Map<String, EndpointParameter> baseMap = indexParamsByName(baseParams);
        Map<String, EndpointParameter> targetMap = indexParamsByName(targetParams);

        Set<String> allNames = new HashSet<>();
        allNames.addAll(baseMap.keySet());
        allNames.addAll(targetMap.keySet());

        for (String paramName : allNames) {
            EndpointParameter bp = baseMap.get(paramName);
            EndpointParameter tp = targetMap.get(paramName);

            if (bp != null && tp == null) {
                // Parameter removed
                ApiChangeSeverity severity = bp.required()
                        ? ApiChangeSeverity.BREAKING
                        : ApiChangeSeverity.COMPATIBLE;
                ApiChangeType changeType = bp.required()
                        ? ApiChangeType.REQUIRED_PARAMETER_REMOVED
                        : ApiChangeType.OPTIONAL_PARAMETER_REMOVED;
                findings.add(new ApiChangeFinding(
                        changeType, severity,
                        "Parameter '" + paramName + "' removed from " + base.endpointId(),
                        base.endpointId(), target.endpointId(),
                        target.controllerClass(), paramName,
                        bp.typeName(), null,
                        target.sourceFile(), target.sourceLine()
                ));
            } else if (bp == null && tp != null) {
                // Parameter added
                ApiChangeSeverity severity = tp.required()
                        ? ApiChangeSeverity.BREAKING
                        : ApiChangeSeverity.COMPATIBLE;
                ApiChangeType changeType = tp.required()
                        ? ApiChangeType.REQUIRED_PARAMETER_ADDED
                        : ApiChangeType.OPTIONAL_PARAMETER_ADDED;
                findings.add(new ApiChangeFinding(
                        changeType, severity,
                        "Parameter '" + paramName + "' added to " + target.endpointId(),
                        base.endpointId(), target.endpointId(),
                        target.controllerClass(), paramName,
                        null, tp.typeName(),
                        target.sourceFile(), target.sourceLine()
                ));
            } else {
                // Parameter exists in both — compare type
                if (!Objects.equals(bp.typeName(), tp.typeName())) {
                    findings.add(new ApiChangeFinding(
                            ApiChangeType.PARAMETER_TYPE_CHANGED,
                            ApiChangeSeverity.POTENTIALLY_BREAKING,
                            "Parameter '" + paramName + "' type changed in " + base.endpointId(),
                            base.endpointId(), target.endpointId(),
                            target.controllerClass(), paramName,
                            bp.typeName(), tp.typeName(),
                            target.sourceFile(), target.sourceLine()
                    ));
                }

                // Check if required status changed
                if (bp.required() != tp.required()) {
                    if (tp.required() && !bp.required()) {
                        // Made required — breaking
                        findings.add(new ApiChangeFinding(
                                ApiChangeType.REQUIRED_PARAMETER_ADDED,
                                ApiChangeSeverity.BREAKING,
                                "Parameter '" + paramName + "' changed from optional to required in " + target.endpointId(),
                                base.endpointId(), target.endpointId(),
                                target.controllerClass(), paramName,
                                "optional", "required",
                                target.sourceFile(), target.sourceLine()
                        ));
                    } else {
                        // Made optional — compatible
                        findings.add(new ApiChangeFinding(
                                ApiChangeType.OPTIONAL_PARAMETER_ADDED,
                                ApiChangeSeverity.COMPATIBLE,
                                "Parameter '" + paramName + "' changed from required to optional in " + target.endpointId(),
                                base.endpointId(), target.endpointId(),
                                target.controllerClass(), paramName,
                                "required", "optional",
                                target.sourceFile(), target.sourceLine()
                        ));
                    }
                }
            }
        }

        return findings;
    }

    /**
     * Compares path variables between endpoints.
     */
    private List<ApiChangeFinding> comparePathVariables(ApiEndpoint base, ApiEndpoint target) {
        return compareParameters(base, target,
                safeList(base.pathVariables()), safeList(target.pathVariables()));
    }

    /**
     * Compares request body type between endpoints.
     */
    private List<ApiChangeFinding> compareRequestBody(ApiEndpoint base, ApiEndpoint target) {
        List<ApiChangeFinding> findings = new ArrayList<>();

        String baseBody = base.requestBodyType();
        String targetBody = target.requestBodyType();

        if (!Objects.equals(baseBody, targetBody)) {
            ApiChangeSeverity severity;
            String description;

            if (baseBody == null && targetBody != null) {
                severity = ApiChangeSeverity.BREAKING;
                description = "Request body added to " + target.endpointId() + ": " + targetBody;
            } else if (baseBody != null && targetBody == null) {
                severity = ApiChangeSeverity.BREAKING;
                description = "Request body removed from " + base.endpointId() + ": " + baseBody;
            } else {
                severity = ApiChangeSeverity.POTENTIALLY_BREAKING;
                description = "Request body type changed in " + base.endpointId() +
                        ": " + baseBody + " → " + targetBody;
            }

            findings.add(new ApiChangeFinding(
                    ApiChangeType.REQUEST_BODY_CHANGED, severity, description,
                    base.endpointId(), target.endpointId(),
                    target.controllerClass(), "requestBody",
                    baseBody, targetBody,
                    target.sourceFile(), target.sourceLine()
            ));
        }

        // Check if required status changed
        if (base.requestBodyRequired() != target.requestBodyRequired()
                && baseBody != null && targetBody != null) {
            String reqChange = base.requestBodyRequired() ? "required → optional" : "optional → required";
            ApiChangeSeverity severity = target.requestBodyRequired()
                    ? ApiChangeSeverity.BREAKING
                    : ApiChangeSeverity.COMPATIBLE;
            findings.add(new ApiChangeFinding(
                    ApiChangeType.REQUEST_BODY_CHANGED, severity,
                    "Request body requirement changed in " + target.endpointId() + ": " + reqChange,
                    base.endpointId(), target.endpointId(),
                    target.controllerClass(), "requestBody.required",
                    String.valueOf(base.requestBodyRequired()),
                    String.valueOf(target.requestBodyRequired()),
                    target.sourceFile(), target.sourceLine()
            ));
        }

        return findings;
    }

    /**
     * Compares response types between endpoints.
     */
    private List<ApiChangeFinding> compareResponseType(ApiEndpoint base, ApiEndpoint target) {
        List<ApiChangeFinding> findings = new ArrayList<>();

        if (!Objects.equals(base.responseType(), target.responseType())) {
            findings.add(new ApiChangeFinding(
                    ApiChangeType.RESPONSE_TYPE_CHANGED,
                    ApiChangeSeverity.POTENTIALLY_BREAKING,
                    "Response type changed in " + base.endpointId() +
                            ": " + base.responseType() + " → " + target.responseType(),
                    base.endpointId(), target.endpointId(),
                    target.controllerClass(), "responseType",
                    base.responseType(), target.responseType(),
                    target.sourceFile(), target.sourceLine()
            ));
        }

        return findings;
    }

    /**
     * Compares validation constraints between endpoints.
     */
    private List<ApiChangeFinding> compareValidationConstraints(ApiEndpoint base, ApiEndpoint target) {
        List<ApiChangeFinding> findings = new ArrayList<>();

        Set<String> baseConstraints = constraintKeys(safeList(base.validationConstraints()));
        Set<String> targetConstraints = constraintKeys(safeList(target.validationConstraints()));

        if (!baseConstraints.equals(targetConstraints)) {
            // Determine severity based on what changed
            Set<String> added = new HashSet<>(targetConstraints);
            added.removeAll(baseConstraints);

            Set<String> removed = new HashSet<>(baseConstraints);
            removed.removeAll(targetConstraints);

            ApiChangeSeverity severity;
            if (!removed.isEmpty()) {
                // Removing validation constraints could break clients that rely on server-side validation
                severity = ApiChangeSeverity.POTENTIALLY_BREAKING;
            } else {
                // Adding new constraints may reject previously valid requests
                severity = ApiChangeSeverity.POTENTIALLY_BREAKING;
            }

            String description = "Validation constraints changed in " + base.endpointId();
            if (!added.isEmpty()) {
                description += "; added: " + added;
            }
            if (!removed.isEmpty()) {
                description += "; removed: " + removed;
            }

            findings.add(new ApiChangeFinding(
                    ApiChangeType.VALIDATION_CONSTRAINT_CHANGED,
                    severity, description,
                    base.endpointId(), target.endpointId(),
                    target.controllerClass(), "validationConstraints",
                    baseConstraints.toString(), targetConstraints.toString(),
                    target.sourceFile(), target.sourceLine()
            ));
        }

        return findings;
    }

    /**
     * Compares media type attributes (produces/consumes).
     */
    private List<ApiChangeFinding> compareMediaTypes(ApiEndpoint base, ApiEndpoint target,
                                                      String attributeName,
                                                      List<String> baseTypes, List<String> targetTypes) {
        List<ApiChangeFinding> findings = new ArrayList<>();

        if (!new HashSet<>(baseTypes).equals(new HashSet<>(targetTypes))) {
            findings.add(new ApiChangeFinding(
                    ApiChangeType.ENDPOINT_ATTRIBUTE_CHANGED,
                    ApiChangeSeverity.POTENTIALLY_BREAKING,
                    attributeName + " media types changed in " + base.endpointId() +
                            ": " + baseTypes + " → " + targetTypes,
                    base.endpointId(), target.endpointId(),
                    target.controllerClass(), attributeName,
                    baseTypes.toString(), targetTypes.toString(),
                    target.sourceFile(), target.sourceLine()
            ));
        }

        return findings;
    }

    /**
     * Indexes endpoints by their endpoint ID.
     * If duplicate IDs exist (should not normally happen), logs a warning.
     */
    private Map<String, ApiEndpoint> indexById(List<ApiEndpoint> endpoints) {
        Map<String, ApiEndpoint> map = new HashMap<>();
        for (ApiEndpoint endpoint : endpoints) {
            if (map.containsKey(endpoint.endpointId())) {
                log.warn("Duplicate endpoint ID detected: {}. Only the last occurrence will be compared.",
                        endpoint.endpointId());
            }
            map.put(endpoint.endpointId(), endpoint);
        }
        return map;
    }

    /**
     * Indexes parameters by name.
     */
    private Map<String, EndpointParameter> indexParamsByName(List<EndpointParameter> params) {
        Map<String, EndpointParameter> map = new HashMap<>();
        for (EndpointParameter param : params) {
            map.put(param.name(), param);
        }
        return map;
    }

    /**
     * Produces stable string keys from validation constraint maps for set comparison.
     */
    private Set<String> constraintKeys(List<Map<String, String>> constraints) {
        return constraints.stream()
                .map(c -> {
                    String annotation = c.getOrDefault("annotation", "");
                    Map<String, String> attrs = new HashMap<>(c);
                    attrs.remove("annotation");
                    return annotation + (attrs.isEmpty() ? "" : attrs.toString());
                })
                .collect(Collectors.toSet());
    }

    private <T> List<T> safeList(List<T> list) {
        return list != null ? list : Collections.emptyList();
    }
}
