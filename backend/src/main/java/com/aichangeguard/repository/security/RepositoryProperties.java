package com.aichangeguard.repository.security;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Configuration properties for repository ingestion security controls.
 */
@Component
@ConfigurationProperties(prefix = "changeguard.repository")
public class RepositoryProperties {

    /**
     * Whitelist of permitted base directories. Paths outside these directories are rejected.
     */
    private List<String> allowedBaseDirectories = new ArrayList<>();

    /**
     * Maximum depth for recursive directory traversal to prevent pathological directory depth.
     */
    private int maxDirectoryDepth = 15;

    /**
     * Maximum number of files to inspect to bound resource consumption.
     */
    private int maxFileInspectionCount = 50000;

    public List<String> getAllowedBaseDirectories() {
        return allowedBaseDirectories;
    }

    public void setAllowedBaseDirectories(List<String> allowedBaseDirectories) {
        this.allowedBaseDirectories = allowedBaseDirectories;
    }

    public int getMaxDirectoryDepth() {
        return maxDirectoryDepth;
    }

    public void setMaxDirectoryDepth(int maxDirectoryDepth) {
        this.maxDirectoryDepth = maxDirectoryDepth;
    }

    public int getMaxFileInspectionCount() {
        return maxFileInspectionCount;
    }

    public void setMaxFileInspectionCount(int maxFileInspectionCount) {
        this.maxFileInspectionCount = maxFileInspectionCount;
    }
}
