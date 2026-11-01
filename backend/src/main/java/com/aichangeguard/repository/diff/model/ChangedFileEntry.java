package com.aichangeguard.repository.diff.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * Structured change metadata for an individual file in a Git diff.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ChangedFileEntry(
        FileChangeType changeType,
        String oldPath,
        String newPath,
        boolean isJavaFile,
        boolean isBinary,
        int linesAdded,
        int linesDeleted,
        List<DiffHunk> hunks,
        List<MethodCandidateHint> candidateMethods
) {
    public String effectivePath() {
        return newPath != null ? newPath : oldPath;
    }
}
