package com.aichangeguard.repository.model;

import jakarta.validation.constraints.NotBlank;

/**
 * Request payload to register and inspect a local repository.
 */
public record RepositoryRegistrationRequest(
        @NotBlank(message = "Repository ID must not be blank")
        String repositoryId,

        @NotBlank(message = "Repository path must not be blank")
        String repositoryPath,

        String displayName
) {}
