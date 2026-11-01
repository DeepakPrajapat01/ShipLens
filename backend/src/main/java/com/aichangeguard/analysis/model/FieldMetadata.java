package com.aichangeguard.analysis.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;
import java.util.Set;

/**
 * Metadata for a field declaration.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record FieldMetadata(
        String symbolId,
        String name,
        String typeName,
        Set<String> modifiers,
        List<AnnotationMetadata> annotations,
        SourceRange range
) {}
