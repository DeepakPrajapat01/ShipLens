package com.aichangeguard.analysis.model;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Diagnostic event captured during parsing (e.g. syntax errors, unsupported constructs).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ParsingDiagnostic(
        String filePath,
        String severity, // "ERROR", "WARNING", "INFO"
        String message,
        SourcePosition position
) {}
