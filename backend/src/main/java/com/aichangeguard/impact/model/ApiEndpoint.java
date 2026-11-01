package com.aichangeguard.impact.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;
import java.util.Map;

/**
 * Normalized representation of a single Spring REST API endpoint.
 *
 * <p>Extracted from Spring MVC annotation metadata (e.g. {@code @GetMapping}, {@code @PostMapping},
 * {@code @RequestMapping}). Used for comparing endpoints between two revisions to detect
 * contract changes.</p>
 *
 * <p>Limitations: This representation is based on syntactic annotation extraction only.
 * Runtime-resolved URL patterns, dynamic path segments from configuration, and
 * framework-generated endpoints are outside the current analysis scope.</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiEndpoint(
        /** Stable identifier: HTTP_METHOD:fullPath (e.g. "GET:/api/v1/users/{id}"). */
        String endpointId,

        /** HTTP method (GET, POST, etc.). */
        HttpMethodType httpMethod,

        /** Full URL path including class-level prefix. */
        String path,

        /** Fully qualified controller class name. */
        String controllerClass,

        /** Handler method name. */
        String methodName,

        /** Handler method symbol ID from the AST analyzer. */
        String methodSymbolId,

        /** Path variables. */
        List<EndpointParameter> pathVariables,

        /** Query parameters annotated with @RequestParam. */
        List<EndpointParameter> requestParameters,

        /** Request body type name, null if none. */
        String requestBodyType,

        /** Whether the request body is required. */
        boolean requestBodyRequired,

        /** Return/response type name. */
        String responseType,

        /** Media types produced (from produces attribute). */
        List<String> producesMediaTypes,

        /** Media types consumed (from consumes attribute). */
        List<String> consumesMediaTypes,

        /** Validation annotations on the handler method and its parameters. */
        List<Map<String, String>> validationConstraints,

        /** Source file path for traceability. */
        String sourceFile,

        /** Source line number of the handler method. */
        int sourceLine
) {
    /**
     * Creates the standard endpoint identifier from HTTP method and path.
     */
    public static String buildEndpointId(HttpMethodType httpMethod, String path) {
        return httpMethod.name() + ":" + path;
    }
}
