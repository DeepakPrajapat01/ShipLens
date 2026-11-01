package com.aichangeguard.repository.security;

import com.aichangeguard.common.error.ValidationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

/**
 * Validates repository paths against security boundaries:
 * - Confirms path existence and directory type
 * - Enforces allowed base directory whitelist
 * - Prevents path traversal and unsafe symlink escapes
 */
@Component
public class RepositoryPathValidator {

    private static final Logger log = LoggerFactory.getLogger(RepositoryPathValidator.class);
    private final RepositoryProperties properties;

    public RepositoryPathValidator(RepositoryProperties properties) {
        this.properties = properties;
    }

    /**
     * Validates and returns the canonical real path of a target repository.
     *
     * @param rawPath User-supplied path string
     * @return Validated canonical Path
     * @throws ValidationException if the path violates security constraints
     */
    public Path validate(String rawPath) {
        if (!StringUtils.hasText(rawPath)) {
            throw new ValidationException("Repository path cannot be null or empty");
        }

        Path candidatePath;
        try {
            candidatePath = Paths.get(rawPath);
        } catch (InvalidPathException e) {
            throw new ValidationException("Invalid path syntax: " + rawPath,
                    Map.of("rawPath", rawPath, "reason", e.getMessage()));
        }

        if (!Files.exists(candidatePath)) {
            throw new ValidationException("Repository path does not exist: " + rawPath,
                    Map.of("rawPath", rawPath));
        }

        if (!Files.isDirectory(candidatePath)) {
            throw new ValidationException("Repository path must be a directory: " + rawPath,
                    Map.of("rawPath", rawPath));
        }

        if (!Files.isReadable(candidatePath)) {
            throw new ValidationException("Repository path is not readable: " + rawPath,
                    Map.of("rawPath", rawPath));
        }

        Path realPath;
        try {
            realPath = candidatePath.toRealPath();
        } catch (IOException e) {
            throw new ValidationException("Failed to resolve canonical path for: " + rawPath,
                    Map.of("rawPath", rawPath, "error", e.getMessage()));
        }

        // Enforce allowed base directory whitelist if configured
        List<String> allowedBases = properties.getAllowedBaseDirectories();
        if (allowedBases != null && !allowedBases.isEmpty()) {
            boolean allowed = false;
            for (String allowedBaseStr : allowedBases) {
                if (!StringUtils.hasText(allowedBaseStr)) {
                    continue;
                }
                try {
                    Path allowedBasePath = Paths.get(allowedBaseStr);
                    if (Files.exists(allowedBasePath)) {
                        Path realAllowedBase = allowedBasePath.toRealPath();
                        if (realPath.startsWith(realAllowedBase)) {
                            allowed = true;
                            break;
                        }
                    }
                } catch (Exception ex) {
                    log.warn("Error resolving allowed base directory '{}': {}", allowedBaseStr, ex.getMessage());
                }
            }

            if (!allowed) {
                log.warn("Rejected path outside allowed base directories: {}", realPath);
                throw new ValidationException("Repository path is outside the permitted directories",
                        Map.of("path", realPath.toString(), "allowedBases", allowedBases));
            }
        }

        return realPath;
    }
}
