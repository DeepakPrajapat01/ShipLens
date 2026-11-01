package com.aichangeguard.analysis.model;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Enclosing source code range with begin and end positions.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SourceRange(SourcePosition begin, SourcePosition end) {
    public static final SourceRange UNKNOWN = new SourceRange(SourcePosition.UNKNOWN, SourcePosition.UNKNOWN);

    public static SourceRange of(int beginLine, int beginCol, int endLine, int endCol) {
        return new SourceRange(new SourcePosition(beginLine, beginCol), new SourcePosition(endLine, endCol));
    }
}
