package com.aichangeguard.integration.docker.model;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration properties for Docker test execution.
 */
@Component
@ConfigurationProperties(prefix = "changeguard.docker")
public class DockerProperties {

    private boolean enabled = true;
    private String image = "maven:3.9-eclipse-temurin-21";
    private int defaultTimeoutSeconds = 180;
    private int maxTimeoutSeconds = 600;
    private String defaultCpuLimit = "2.0";
    private String defaultMemoryLimit = "2g";
    private String maxMemoryLimit = "4g";
    private boolean networkDisabled = true;
    private int maxLogSizeBytes = 512000; // 500 KB
    private int pidsLimit = 256;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getImage() {
        return image;
    }

    public void setImage(String image) {
        this.image = image;
    }

    public int getDefaultTimeoutSeconds() {
        return defaultTimeoutSeconds;
    }

    public void setDefaultTimeoutSeconds(int defaultTimeoutSeconds) {
        this.defaultTimeoutSeconds = defaultTimeoutSeconds;
    }

    public int getMaxTimeoutSeconds() {
        return maxTimeoutSeconds;
    }

    public void setMaxTimeoutSeconds(int maxTimeoutSeconds) {
        this.maxTimeoutSeconds = maxTimeoutSeconds;
    }

    public String getDefaultCpuLimit() {
        return defaultCpuLimit;
    }

    public void setDefaultCpuLimit(String defaultCpuLimit) {
        this.defaultCpuLimit = defaultCpuLimit;
    }

    public String getDefaultMemoryLimit() {
        return defaultMemoryLimit;
    }

    public void setDefaultMemoryLimit(String defaultMemoryLimit) {
        this.defaultMemoryLimit = defaultMemoryLimit;
    }

    public String getMaxMemoryLimit() {
        return maxMemoryLimit;
    }

    public void setMaxMemoryLimit(String maxMemoryLimit) {
        this.maxMemoryLimit = maxMemoryLimit;
    }

    public boolean isNetworkDisabled() {
        return networkDisabled;
    }

    public void setNetworkDisabled(boolean networkDisabled) {
        this.networkDisabled = networkDisabled;
    }

    public int getMaxLogSizeBytes() {
        return maxLogSizeBytes;
    }

    public void setMaxLogSizeBytes(int maxLogSizeBytes) {
        this.maxLogSizeBytes = maxLogSizeBytes;
    }

    public int getPidsLimit() {
        return pidsLimit;
    }

    public void setPidsLimit(int pidsLimit) {
        this.pidsLimit = pidsLimit;
    }
}

