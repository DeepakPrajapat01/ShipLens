package com.aichangeguard.impact.service;

import com.aichangeguard.analysis.model.AnnotationMetadata;
import com.aichangeguard.analysis.model.MethodMetadata;
import com.aichangeguard.analysis.model.ParameterMetadata;
import com.aichangeguard.analysis.model.ParsedFileMetadata;
import com.aichangeguard.analysis.model.SourceAnalysisReport;
import com.aichangeguard.analysis.model.TypeKind;
import com.aichangeguard.analysis.model.TypeMetadata;
import com.aichangeguard.impact.model.ApiEndpoint;
import com.aichangeguard.impact.model.EndpointParameter;
import com.aichangeguard.impact.model.HttpMethodType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Extracts normalized REST API endpoint representations from parsed Java source metadata.
 *
 * <p>Discovers Spring MVC controllers and their handler method mappings based on annotation
 * metadata extracted by the AST Analyzer. Supports {@code @RestController}, {@code @Controller},
 * {@code @RequestMapping}, and the composed mapping annotations ({@code @GetMapping},
 * {@code @PostMapping}, {@code @PutMapping}, {@code @DeleteMapping}, {@code @PatchMapping}).</p>
 *
 * <h3>Limitations</h3>
 * <ul>
 *   <li>Annotation attribute values are extracted syntactically; expressions, constants,
 *       and SpEL are not evaluated.</li>
 *   <li>Runtime-registered endpoints (e.g., RouterFunction, programmatic registration)
 *       are not detected.</li>
 *   <li>Inherited controller mappings from superclasses are not resolved across files.</li>
 *   <li>Type resolution for request/response DTOs uses simple names from the AST,
 *       which may be ambiguous.</li>
 * </ul>
 */
@Service
public class RestEndpointExtractor {

    private static final Logger log = LoggerFactory.getLogger(RestEndpointExtractor.class);

    /** Spring MVC controller annotations that mark a class as a REST controller. */
    private static final Set<String> CONTROLLER_ANNOTATIONS = Set.of(
            "RestController", "Controller"
    );

    /** Mapping annotations and their associated HTTP methods. */
    private static final Map<String, HttpMethodType> MAPPING_ANNOTATIONS;
    static {
        Map<String, HttpMethodType> m = new HashMap<>();
        m.put("GetMapping", HttpMethodType.GET);
        m.put("PostMapping", HttpMethodType.POST);
        m.put("PutMapping", HttpMethodType.PUT);
        m.put("DeleteMapping", HttpMethodType.DELETE);
        m.put("PatchMapping", HttpMethodType.PATCH);
        m.put("RequestMapping", null); // HTTP method determined from annotation attributes
        MAPPING_ANNOTATIONS = Collections.unmodifiableMap(m);
    }

    /** Parameter source annotations. */
    private static final Set<String> PARAM_SOURCE_ANNOTATIONS = Set.of(
            "RequestParam", "PathVariable", "RequestHeader", "MatrixVariable"
    );

    /** Validation constraint annotations (jakarta.validation + Spring). */
    private static final Set<String> VALIDATION_ANNOTATIONS = Set.of(
            "NotNull", "NotBlank", "NotEmpty", "Size", "Min", "Max",
            "Pattern", "Email", "Positive", "PositiveOrZero", "Negative", "NegativeOrZero",
            "Past", "PastOrPresent", "Future", "FutureOrPresent", "Digits",
            "DecimalMin", "DecimalMax", "Valid", "Validated"
    );

    /**
     * Extracts all REST API endpoints from a source analysis report.
     *
     * @param report the parsed source analysis report
     * @return list of normalized API endpoints discovered
     */
    public List<ApiEndpoint> extractEndpoints(SourceAnalysisReport report) {
        if (report == null || report.parsedFiles() == null) {
            return Collections.emptyList();
        }

        List<ApiEndpoint> endpoints = new ArrayList<>();

        for (ParsedFileMetadata file : report.parsedFiles()) {
            if (!file.isSuccessful() || file.types() == null) {
                continue;
            }
            for (TypeMetadata type : file.types()) {
                endpoints.addAll(extractFromType(type, file.relativePath()));
            }
        }

        log.info("Extracted {} REST API endpoints from {} parsed files",
                endpoints.size(), report.totalFilesSuccessful());
        return endpoints;
    }

    /**
     * Extracts endpoints from a single type declaration, including nested types.
     */
    private List<ApiEndpoint> extractFromType(TypeMetadata type, String sourceFile) {
        List<ApiEndpoint> endpoints = new ArrayList<>();

        if (isController(type)) {
            String classLevelPath = extractClassLevelPath(type);
            for (MethodMetadata method : safeList(type.methods())) {
                List<ApiEndpoint> methodEndpoints = extractFromMethod(
                        method, type.qualifiedName(), classLevelPath, sourceFile
                );
                endpoints.addAll(methodEndpoints);
            }
        }

        // Check nested types
        for (TypeMetadata nested : safeList(type.nestedTypes())) {
            endpoints.addAll(extractFromType(nested, sourceFile));
        }

        return endpoints;
    }

    /**
     * Checks if a type is a Spring MVC controller.
     */
    private boolean isController(TypeMetadata type) {
        if (type.kind() != TypeKind.CLASS) {
            return false;
        }
        return safeList(type.annotations()).stream()
                .anyMatch(a -> CONTROLLER_ANNOTATIONS.contains(a.name()));
    }

    /**
     * Extracts the class-level @RequestMapping path, if any.
     */
    private String extractClassLevelPath(TypeMetadata type) {
        for (AnnotationMetadata annotation : safeList(type.annotations())) {
            if ("RequestMapping".equals(annotation.name())) {
                return extractPathFromAnnotation(annotation);
            }
        }
        return "";
    }

    /**
     * Extracts endpoints from a handler method.
     * A method can map to multiple endpoints if @RequestMapping specifies multiple HTTP methods.
     */
    private List<ApiEndpoint> extractFromMethod(MethodMetadata method, String controllerClass,
                                                 String classLevelPath, String sourceFile) {
        List<ApiEndpoint> endpoints = new ArrayList<>();

        for (AnnotationMetadata annotation : safeList(method.annotations())) {
            HttpMethodType mappedHttpMethod = MAPPING_ANNOTATIONS.get(annotation.name());

            if (mappedHttpMethod == null && !MAPPING_ANNOTATIONS.containsKey(annotation.name())) {
                // Not a mapping annotation
                continue;
            }

            String methodPath = extractPathFromAnnotation(annotation);
            String fullPath = normalizePath(classLevelPath, methodPath);

            List<HttpMethodType> httpMethods;
            if (mappedHttpMethod != null) {
                // Composed mapping like @GetMapping
                httpMethods = List.of(mappedHttpMethod);
            } else {
                // @RequestMapping — check for explicit method attribute
                httpMethods = extractHttpMethodsFromRequestMapping(annotation);
            }

            // Extract parameters, request body, response type
            List<EndpointParameter> pathVariables = new ArrayList<>();
            List<EndpointParameter> requestParameters = new ArrayList<>();
            String requestBodyType = null;
            boolean requestBodyRequired = true;
            List<Map<String, String>> validationConstraints = new ArrayList<>();

            for (ParameterMetadata param : safeList(method.parameters())) {
                EndpointParameter extracted = extractEndpointParameter(param);
                if (extracted != null) {
                    if ("PathVariable".equals(extracted.source())) {
                        pathVariables.add(extracted);
                    } else {
                        requestParameters.add(extracted);
                    }
                } else if (isRequestBody(param)) {
                    requestBodyType = param.typeName();
                    requestBodyRequired = isRequestBodyRequired(param);
                }

                // Collect validation constraints from the parameter
                validationConstraints.addAll(extractValidationConstraints(param));
            }

            // Also collect method-level validation annotations
            for (AnnotationMetadata methodAnno : safeList(method.annotations())) {
                if (VALIDATION_ANNOTATIONS.contains(methodAnno.name())) {
                    Map<String, String> constraint = new HashMap<>();
                    constraint.put("annotation", methodAnno.name());
                    if (methodAnno.attributes() != null) {
                        constraint.putAll(methodAnno.attributes());
                    }
                    validationConstraints.add(constraint);
                }
            }

            String responseType = method.returnTypeName();

            // Extract produces/consumes
            List<String> produces = extractMediaTypes(annotation, "produces");
            List<String> consumes = extractMediaTypes(annotation, "consumes");

            int sourceLine = method.range() != null && method.range().begin() != null
                    ? method.range().begin().line() : 0;

            for (HttpMethodType httpMethod : httpMethods) {
                String endpointId = ApiEndpoint.buildEndpointId(httpMethod, fullPath);
                endpoints.add(new ApiEndpoint(
                        endpointId,
                        httpMethod,
                        fullPath,
                        controllerClass,
                        method.name(),
                        method.symbolId(),
                        pathVariables,
                        requestParameters,
                        requestBodyType,
                        requestBodyRequired,
                        responseType,
                        produces,
                        consumes,
                        validationConstraints,
                        sourceFile,
                        sourceLine
                ));
            }
        }

        return endpoints;
    }

    /**
     * Extracts the path from a mapping annotation.
     * Checks for "value" or "path" attributes.
     */
    private String extractPathFromAnnotation(AnnotationMetadata annotation) {
        if (annotation.attributes() == null) {
            return "";
        }
        String path = annotation.attributes().get("value");
        if (path == null) {
            path = annotation.attributes().get("path");
        }
        if (path == null) {
            return "";
        }
        return cleanAnnotationValue(path);
    }

    /**
     * Extracts HTTP methods from a @RequestMapping annotation's "method" attribute.
     */
    private List<HttpMethodType> extractHttpMethodsFromRequestMapping(AnnotationMetadata annotation) {
        if (annotation.attributes() == null) {
            // No method specified — defaults to all methods, treated as UNKNOWN
            return List.of(HttpMethodType.UNKNOWN);
        }
        String methodAttr = annotation.attributes().get("method");
        if (methodAttr == null || methodAttr.isBlank()) {
            return List.of(HttpMethodType.UNKNOWN);
        }

        List<HttpMethodType> methods = new ArrayList<>();
        String cleaned = cleanAnnotationValue(methodAttr);

        for (String part : cleaned.split(",")) {
            String trimmed = part.trim()
                    .replace("RequestMethod.", "")
                    .replace("{", "")
                    .replace("}", "")
                    .trim();
            try {
                methods.add(HttpMethodType.valueOf(trimmed.toUpperCase()));
            } catch (IllegalArgumentException e) {
                log.warn("Unrecognized HTTP method in @RequestMapping: '{}'", trimmed);
                methods.add(HttpMethodType.UNKNOWN);
            }
        }

        return methods.isEmpty() ? List.of(HttpMethodType.UNKNOWN) : methods;
    }

    /**
     * Extracts a method parameter as an EndpointParameter, or null if it's not a request param/path variable.
     */
    private EndpointParameter extractEndpointParameter(ParameterMetadata param) {
        for (AnnotationMetadata annotation : safeList(param.annotations())) {
            if (PARAM_SOURCE_ANNOTATIONS.contains(annotation.name())) {
                String paramName = extractParamName(annotation, param.name());
                boolean required = isParameterRequired(annotation);
                String defaultValue = extractDefaultValue(annotation);
                List<Map<String, String>> constraints = extractValidationConstraints(param);

                return new EndpointParameter(
                        paramName,
                        annotation.name(),
                        param.typeName(),
                        required,
                        defaultValue,
                        constraints
                );
            }
        }
        return null;
    }

    /**
     * Checks if a parameter has a @RequestBody annotation.
     */
    private boolean isRequestBody(ParameterMetadata param) {
        return safeList(param.annotations()).stream()
                .anyMatch(a -> "RequestBody".equals(a.name()));
    }

    /**
     * Checks if a @RequestBody annotation marks the body as required.
     */
    private boolean isRequestBodyRequired(ParameterMetadata param) {
        for (AnnotationMetadata annotation : safeList(param.annotations())) {
            if ("RequestBody".equals(annotation.name()) && annotation.attributes() != null) {
                String required = annotation.attributes().get("required");
                if ("false".equalsIgnoreCase(required)) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Extracts the parameter name from the annotation or falls back to the method parameter name.
     */
    private String extractParamName(AnnotationMetadata annotation, String fallback) {
        if (annotation.attributes() != null) {
            String name = annotation.attributes().get("name");
            if (name != null && !name.isBlank()) {
                return cleanAnnotationValue(name);
            }
            String value = annotation.attributes().get("value");
            if (value != null && !value.isBlank()) {
                return cleanAnnotationValue(value);
            }
        }
        return fallback;
    }

    /**
     * Determines whether a parameter annotation marks the parameter as required.
     */
    private boolean isParameterRequired(AnnotationMetadata annotation) {
        if (annotation.attributes() == null) {
            // @PathVariable is required by default, @RequestParam is required by default
            return true;
        }
        String required = annotation.attributes().get("required");
        if (required != null) {
            return !"false".equalsIgnoreCase(required);
        }
        // If a defaultValue is present, the parameter is effectively optional
        String defaultValue = annotation.attributes().get("defaultValue");
        return defaultValue == null || defaultValue.isBlank();
    }

    /**
     * Extracts the defaultValue from a parameter annotation, if present.
     */
    private String extractDefaultValue(AnnotationMetadata annotation) {
        if (annotation.attributes() == null) {
            return null;
        }
        String value = annotation.attributes().get("defaultValue");
        if (value != null && !value.isBlank()) {
            return cleanAnnotationValue(value);
        }
        return null;
    }

    /**
     * Extracts validation constraint annotations from a method parameter.
     */
    private List<Map<String, String>> extractValidationConstraints(ParameterMetadata param) {
        List<Map<String, String>> constraints = new ArrayList<>();
        for (AnnotationMetadata annotation : safeList(param.annotations())) {
            if (VALIDATION_ANNOTATIONS.contains(annotation.name())) {
                Map<String, String> constraint = new HashMap<>();
                constraint.put("annotation", annotation.name());
                if (annotation.attributes() != null) {
                    constraint.putAll(annotation.attributes());
                }
                constraints.add(constraint);
            }
        }
        return constraints;
    }

    /**
     * Extracts media type lists from the produces or consumes annotation attribute.
     */
    private List<String> extractMediaTypes(AnnotationMetadata annotation, String attributeName) {
        if (annotation.attributes() == null) {
            return Collections.emptyList();
        }
        String value = annotation.attributes().get(attributeName);
        if (value == null || value.isBlank()) {
            return Collections.emptyList();
        }

        String cleaned = cleanAnnotationValue(value)
                .replace("{", "")
                .replace("}", "");

        List<String> mediaTypes = new ArrayList<>();
        for (String part : cleaned.split(",")) {
            String trimmed = part.trim()
                    .replace("MediaType.", "")
                    .replace("\"", "");
            if (!trimmed.isBlank()) {
                mediaTypes.add(trimmed);
            }
        }
        return mediaTypes;
    }

    /**
     * Normalizes a combined class-level and method-level URL path.
     */
    private String normalizePath(String classPath, String methodPath) {
        String cp = classPath == null ? "" : classPath.trim();
        String mp = methodPath == null ? "" : methodPath.trim();

        if (cp.isEmpty() && mp.isEmpty()) {
            return "/";
        }

        // Ensure class path starts with /
        if (!cp.isEmpty() && !cp.startsWith("/")) {
            cp = "/" + cp;
        }
        // Remove trailing slash from class path
        if (cp.endsWith("/")) {
            cp = cp.substring(0, cp.length() - 1);
        }

        // Ensure method path starts with /
        if (!mp.isEmpty() && !mp.startsWith("/")) {
            mp = "/" + mp;
        }

        String combined = cp + mp;
        return combined.isEmpty() ? "/" : combined;
    }

    /**
     * Cleans annotation values by removing surrounding quotes and whitespace.
     */
    private String cleanAnnotationValue(String value) {
        if (value == null) {
            return "";
        }
        return value.trim()
                .replaceAll("^\"+|\"+$", "")   // Remove surrounding double quotes
                .replaceAll("^'+|'+$", "")     // Remove surrounding single quotes
                .trim();
    }

    private <T> List<T> safeList(List<T> list) {
        return list != null ? list : Collections.emptyList();
    }
}
