package com.aichangeguard.integration.github.service;

import com.aichangeguard.integration.github.model.GitHubInstallation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages GitHub App installation lifecycle and repository authorizations.
 */
@Service
public class GitHubInstallationManager {

    private static final Logger log = LoggerFactory.getLogger(GitHubInstallationManager.class);

    private final Map<Long, GitHubInstallation> installations = new ConcurrentHashMap<>();

    /**
     * Checks if a repository is accessible and authorized under an active installation.
     */
    public boolean isRepositoryAccessible(Long installationId, String repositoryFullName) {
        if (installationId == null || repositoryFullName == null) {
            return false;
        }

        GitHubInstallation installation = installations.get(installationId);
        if (installation == null) {
            // In development or when installation is registered dynamically:
            // if no installation is recorded yet, check if strict checking is disabled, or return false.
            return false;
        }

        return installation.allowsRepository(repositoryFullName);
    }

    /**
     * Registers or updates an installation record.
     */
    public void registerInstallation(GitHubInstallation installation) {
        if (installation != null && installation.installationId() != null) {
            installations.put(installation.installationId(), installation);
            log.info("Registered GitHub App installation {} for account '{}'",
                    installation.installationId(), installation.accountLogin());
        }
    }

    public Optional<GitHubInstallation> getInstallation(Long installationId) {
        return Optional.ofNullable(installations.get(installationId));
    }

    /**
     * Handles lifecycle updates from GitHub 'installation' webhook events.
     */
    public void handleInstallationEvent(
            String action,
            Long installationId,
            String accountLogin,
            String accountType,
            String repoSelection,
            List<String> repositories
    ) {
        if (installationId == null) {
            return;
        }

        Instant now = Instant.now();
        switch (action.toLowerCase()) {
            case "created" -> {
                Set<String> repos = repositories != null ? new HashSet<>(repositories) : Collections.emptySet();
                GitHubInstallation installation = new GitHubInstallation(
                        installationId, accountLogin, accountType, repoSelection, true, repos, now, now
                );
                installations.put(installationId, installation);
                log.info("GitHub App installed: id={}, account={}", installationId, accountLogin);
            }
            case "deleted" -> {
                installations.remove(installationId);
                log.info("GitHub App uninstalled: id={}, account={}", installationId, accountLogin);
            }
            case "suspend" -> {
                GitHubInstallation existing = installations.get(installationId);
                if (existing != null) {
                    installations.put(installationId, new GitHubInstallation(
                            existing.installationId(), existing.accountLogin(), existing.accountType(),
                            existing.repositorySelection(), false, existing.permittedRepositories(),
                            existing.createdAt(), now
                    ));
                    log.info("GitHub App installation suspended: id={}", installationId);
                }
            }
            case "unsuspend" -> {
                GitHubInstallation existing = installations.get(installationId);
                if (existing != null) {
                    installations.put(installationId, new GitHubInstallation(
                            existing.installationId(), existing.accountLogin(), existing.accountType(),
                            existing.repositorySelection(), true, existing.permittedRepositories(),
                            existing.createdAt(), now
                    ));
                    log.info("GitHub App installation unsuspended: id={}", installationId);
                }
            }
            default -> log.debug("Unhandled installation action: {}", action);
        }
    }

    /**
     * Handles repository permission updates from 'installation_repositories' events.
     */
    public void handleInstallationRepositoriesEvent(
            String action,
            Long installationId,
            List<String> added,
            List<String> removed
    ) {
        if (installationId == null) {
            return;
        }

        GitHubInstallation existing = installations.get(installationId);
        if (existing == null) {
            return;
        }

        Set<String> updatedRepos = new HashSet<>(existing.permittedRepositories());
        if (added != null) {
            updatedRepos.addAll(added);
        }
        if (removed != null) {
            updatedRepos.removeAll(removed);
        }

        installations.put(installationId, new GitHubInstallation(
                existing.installationId(), existing.accountLogin(), existing.accountType(),
                existing.repositorySelection(), existing.active(), updatedRepos,
                existing.createdAt(), Instant.now()
        ));
        log.info("Updated repository permissions for installation {}: +{} repos, -{} repos",
                installationId, added != null ? added.size() : 0, removed != null ? removed.size() : 0);
    }

    public void clear() {
        installations.clear();
    }
}

