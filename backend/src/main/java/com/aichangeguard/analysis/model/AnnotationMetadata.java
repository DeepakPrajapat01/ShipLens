package com.aichangeguard.analysis.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Map;

/**
 * Metadata for a Java annotation declaration or usage.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AnnotationMetadata(
        String name,
        Map<String, String> attributes,
        SourceRange range
) {}
