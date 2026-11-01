package com.aichangeguard.analysis.model;

/**
 * 1-based source line and column position.
 */
public record SourcePosition(int line, int column) {
    public static final SourcePosition UNKNOWN = new SourcePosition(0, 0);
}
