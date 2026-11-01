package com.aichangeguard.impact.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;
import java.util.Map;

/**
 * Represents a single parameter (query, path, header, or matrix) on a REST endpoint.
 *
 * <p>Captures the parameter's name, source annotation, type, and validation constraints
 * so that two revisions can be compared for contract-breaking changes.</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record EndpointParameter(
        /** Parameter name as declared in the annotation or method signature. */
        String name,

        /** The Spring annotation source: RequestParam, PathVariable, RequestHeader, MatrixVariable. */
        String source,

        /** Java type name (may be unresolved simple name). */
        String typeName,

        /** Whether the parameter is required (defaultValue absent and required=true or not explicitly optional). */
        boolean required,

        /** Default value if declared, null otherwise. */
        String defaultValue,

        /** Validation annotation names and attributes on this parameter. */
        List<Map<String, String>> validationConstraints
) {}
