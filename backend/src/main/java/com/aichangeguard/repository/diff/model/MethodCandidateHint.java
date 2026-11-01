package com.aichangeguard.repository.diff.model;

/**
 * Heuristic identification of a changed method candidate derived from diff context headers.
 * Note: Decision support hint only; full semantic call-graph requires AST parsing.
 */
public record MethodCandidateHint(
        String candidateName,
        String signatureHint,
        int targetLineNumber,
        String source
) {}
