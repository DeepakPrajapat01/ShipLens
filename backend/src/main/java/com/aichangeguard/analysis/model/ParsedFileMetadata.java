package com.aichangeguard.analysis.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * Structured code metadata extracted from a single Java source file.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ParsedFileMetadata(
        String relativePath,
        String packageName,
        List<String> imports,
        List<TypeMetadata> types,
        List<ParsingDiagnostic> diagnostics,
        boolean isSuccessful,
        SourceRange range
) {}
