package com.aichangeguard.integration.github.service;

import com.aichangeguard.integration.github.model.GitHubInstallation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class GitHubInstallationManagerTest {

    private GitHubInstallationManager manager;

    @BeforeEach
    void setUp() {
        manager = new GitHubInstallationManager();
    }

    @Test
    @DisplayName("Installation created with all repositories allows any repo")
    void installationCreatedAllRepos() {
        manager.handleInstallationEvent("created", 100L, "my-org", "Organization", "all", List.of());

        assertThat(manager.isRepositoryAccessible(100L, "my-org/backend")).isTrue();
        assertThat(manager.isRepositoryAccessible(100L, "my-org/frontend")).isTrue();
    }

    @Test
    @DisplayName("Installation created with selected repositories restricts access")
    void installationCreatedSelectedRepos() {
        manager.handleInstallationEvent("created", 200L, "my-org", "Organization", "selected", List.of("my-org/allowed-repo"));

        assertThat(manager.isRepositoryAccessible(200L, "my-org/allowed-repo")).isTrue();
        assertThat(manager.isRepositoryAccessible(200L, "my-org/forbidden-repo")).isFalse();
    }

    @Test
    @DisplayName("Suspended installation blocks all repository access")
    void suspendedInstallationBlocksAccess() {
        manager.handleInstallationEvent("created", 300L, "my-org", "Organization", "all", List.of());
        assertThat(manager.isRepositoryAccessible(300L, "my-org/repo")).isTrue();

        manager.handleInstallationEvent("suspend", 300L, "my-org", "Organization", "all", List.of());
        assertThat(manager.isRepositoryAccessible(300L, "my-org/repo")).isFalse();

        manager.handleInstallationEvent("unsuspend", 300L, "my-org", "Organization", "all", List.of());
        assertThat(manager.isRepositoryAccessible(300L, "my-org/repo")).isTrue();
    }

    @Test
    @DisplayName("Deleted installation removes installation completely")
    void deletedInstallationRemovesAccess() {
        manager.handleInstallationEvent("created", 400L, "my-org", "Organization", "all", List.of());
        manager.handleInstallationEvent("deleted", 400L, "my-org", "Organization", "all", List.of());

        assertThat(manager.isRepositoryAccessible(400L, "my-org/repo")).isFalse();
        assertThat(manager.getInstallation(400L)).isEmpty();
    }

    @Test
    @DisplayName("Installation repositories added and removed update permitted list")
    void updatesRepositoryPermissions() {
        manager.handleInstallationEvent("created", 500L, "my-org", "Organization", "selected", List.of("my-org/repo1"));

        manager.handleInstallationRepositoriesEvent("added", 500L, List.of("my-org/repo2"), List.of());
        assertThat(manager.isRepositoryAccessible(500L, "my-org/repo2")).isTrue();

        manager.handleInstallationRepositoriesEvent("removed", 500L, List.of(), List.of("my-org/repo1"));
        assertThat(manager.isRepositoryAccessible(500L, "my-org/repo1")).isFalse();
    }
}

