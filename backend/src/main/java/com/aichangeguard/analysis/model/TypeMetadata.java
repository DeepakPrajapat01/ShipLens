package com.aichangeguard.analysis.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;
import java.util.Set;

/**
 * Metadata for a Java type declaration (class, interface, enum, record, annotation).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TypeMetadata(
        String symbolId,
        String qualifiedName,
        String simpleName,
        TypeKind kind,
        Set<String> modifiers,
        List<AnnotationMetadata> annotations,
        List<String> extendedTypes,
        List<String> implementedInterfaces,
        List<FieldMetadata> fields,
        List<MethodMetadata> constructors,
        List<MethodMetadata> methods,
        List<TypeMetadata> nestedTypes,
        SourceRange range
) {}
