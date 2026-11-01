package com.aichangeguard.integration.github.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration properties for GitHub App authentication and webhook handling.
 */
@Component
@ConfigurationProperties(prefix = "changeguard.github")
public class GitHubAppProperties {

    /** GitHub App ID assigned by GitHub. */
    private String appId = "";

    /** Webhook secret used to verify X-Hub-Signature-256 headers. */
    private String webhookSecret = "";

    /** PKCS#8 RSA private key in PEM format used to sign GitHub App JWTs. */
    private String privateKeyPem = "";

    /** GitHub REST API base URL. Defaults to public GitHub API. */
    private String apiBaseUrl = "https://api.github.com";

    /** Minimum remaining GitHub API calls before rate-limit throttling kicks in. */
    private int rateLimitThreshold = 50;

    /** HTTP connection timeout in milliseconds. */
    private int connectTimeoutMs = 5000;

    /** HTTP read timeout in milliseconds. */
    private int readTimeoutMs = 10000;

    public String getAppId() {
        return appId;
    }

    public void setAppId(String appId) {
        this.appId = appId;
    }

    public String getWebhookSecret() {
        return webhookSecret;
    }

    public void setWebhookSecret(String webhookSecret) {
        this.webhookSecret = webhookSecret;
    }

    public String getPrivateKeyPem() {
        return privateKeyPem;
    }

    public void setPrivateKeyPem(String privateKeyPem) {
        this.privateKeyPem = privateKeyPem;
    }

    public String getApiBaseUrl() {
        return apiBaseUrl;
    }

    public void setApiBaseUrl(String apiBaseUrl) {
        this.apiBaseUrl = apiBaseUrl;
    }

    public int getRateLimitThreshold() {
        return rateLimitThreshold;
    }

    public void setRateLimitThreshold(int rateLimitThreshold) {
        this.rateLimitThreshold = rateLimitThreshold;
    }

    public int getConnectTimeoutMs() {
        return connectTimeoutMs;
    }

    public void setConnectTimeoutMs(int connectTimeoutMs) {
        this.connectTimeoutMs = connectTimeoutMs;
    }

    public int getReadTimeoutMs() {
        return readTimeoutMs;
    }

    public void setReadTimeoutMs(int readTimeoutMs) {
        this.readTimeoutMs = readTimeoutMs;
    }
}

