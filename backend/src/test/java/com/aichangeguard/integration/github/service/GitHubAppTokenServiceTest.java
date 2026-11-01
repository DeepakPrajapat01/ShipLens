package com.aichangeguard.integration.github.service;

import com.aichangeguard.integration.github.config.GitHubAppProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Instant;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

class GitHubAppTokenServiceTest {

    private GitHubAppProperties properties;
    private GitHubAppTokenService tokenService;

    @BeforeEach
    void setUp() throws Exception {
        properties = new GitHubAppProperties();
        properties.setAppId("123456");

        // Generate dynamic 2048-bit RSA key for test execution
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048);
        KeyPair kp = kpg.generateKeyPair();
        String pem = "-----BEGIN PRIVATE KEY-----\n" +
                Base64.getMimeEncoder(64, new byte[]{'\n'}).encodeToString(kp.getPrivate().getEncoded()) +
                "\n-----END PRIVATE KEY-----";
        properties.setPrivateKeyPem(pem);

        tokenService = new GitHubAppTokenService(properties);
    }

    @Test
    @DisplayName("Generates valid RS256 JWT with three base64url segments")
    void generatesValidJwt() {
        String jwt = tokenService.createSignedJwt();

        assertThat(jwt).isNotBlank();
        String[] parts = jwt.split("\\.");
        assertThat(parts).hasSize(3);

        String header = new String(Base64.getUrlDecoder().decode(parts[0]));
        assertThat(header).contains("\"alg\":\"RS256\"");

        String payload = new String(Base64.getUrlDecoder().decode(parts[1]));
        assertThat(payload).contains("\"iss\":\"123456\"");
    }

    @Test
    @DisplayName("Caches installation token until near expiry")
    void cachesInstallationToken() {
        Long installationId = 789L;
        tokenService.cacheInstallationToken(installationId, "ghs_test_token_12345", Instant.now().plusSeconds(3600));

        GitHubAppTokenService.OptionalCachedToken token = tokenService.getCachedInstallationToken(installationId);
        assertThat(token.isPresent()).isTrue();
        assertThat(token.token()).isEqualTo("ghs_test_token_12345");
        assertThat(token.toString()).isEqualTo("[REDACTED_TOKEN]"); // Redacted!
    }

    @Test
    @DisplayName("Expired or near-expiry token is not returned")
    void expiredTokenNotReturned() {
        Long installationId = 790L;
        // Expires in 60 seconds (inside the 300s safety buffer)
        tokenService.cacheInstallationToken(installationId, "ghs_expiring_token", Instant.now().plusSeconds(60));

        GitHubAppTokenService.OptionalCachedToken token = tokenService.getCachedInstallationToken(installationId);
        assertThat(token.isPresent()).isFalse();
    }
}

