package com.aichangeguard.repository.diff.model;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Represents a single line within a unified diff hunk with its associated line numbers.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record DiffLine(
        DiffLineType type,
        String content,
        Integer baseLineNumber,
        Integer targetLineNumber
) {}
