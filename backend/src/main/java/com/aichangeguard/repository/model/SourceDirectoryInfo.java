package com.aichangeguard.repository.model;

import java.util.List;

/**
 * Detailed information about a discovered Java source directory.
 */
public record SourceDirectoryInfo(
        String relativePath,
        String scope, // "main", "test", or "custom"
        int javaFileCount,
        List<String> sampleFiles
) {}
