package com.aichangeguard.repository.diff.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * Represents a contiguous hunk of changes extracted from unified diff output.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record DiffHunk(
        String header,
        String contextHeader,
        int baseStartLine,
        int baseLineCount,
        int targetStartLine,
        int targetLineCount,
        List<DiffLine> lines
) {}
