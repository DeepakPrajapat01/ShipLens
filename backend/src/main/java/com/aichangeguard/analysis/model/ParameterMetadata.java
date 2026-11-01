package com.aichangeguard.analysis.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * Metadata for a method or constructor parameter.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ParameterMetadata(
        String name,
        String typeName,
        List<AnnotationMetadata> annotations,
        boolean isVarargs
) {}
