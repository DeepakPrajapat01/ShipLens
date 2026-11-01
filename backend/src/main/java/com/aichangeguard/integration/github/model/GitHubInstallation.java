package com.aichangeguard.integration.github.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.Set;

/**
 * Tracks a tenant's GitHub App installation and repository authorizations.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record GitHubInstallation(
        Long installationId,
        String accountLogin,
        String accountType,
        String repositorySelection, // "all" or "selected"
        boolean active,
        Set<String> permittedRepositories, // e.g. ["owner/repo1", "owner/repo2"]
        Instant createdAt,
        Instant updatedAt
) {
    public boolean allowsRepository(String repositoryFullName) {
        if (!active) {
            return false;
        }
        if ("all".equalsIgnoreCase(repositorySelection)) {
            return true;
        }
        return permittedRepositories != null && permittedRepositories.contains(repositoryFullName);
    }
}

