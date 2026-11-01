package com.aichangeguard.analysis.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;
import java.util.Set;

/**
 * Metadata for a method or constructor declaration.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record MethodMetadata(
        String symbolId,
        String name,
        String returnTypeName,
        List<ParameterMetadata> parameters,
        Set<String> modifiers,
        List<AnnotationMetadata> annotations,
        List<String> thrownExceptions,
        boolean isConstructor,
        boolean isAbstract,
        SourceRange range,
        List<MethodInvocationMetadata> invocations
) {}
