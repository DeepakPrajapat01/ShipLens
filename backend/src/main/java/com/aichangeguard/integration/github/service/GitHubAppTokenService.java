package com.aichangeguard.integration.github.service;

import com.aichangeguard.integration.github.config.GitHubAppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Generates GitHub App authentication JWTs and manages cached installation tokens.
 *
 * <p>Uses standard Java cryptography (RS256 with PKCS#8 RSA private keys).
 * Never exposes or logs private key material or token strings.</p>
 */
@Service
public class GitHubAppTokenService {

    private static final Logger log = LoggerFactory.getLogger(GitHubAppTokenService.class);

    private final GitHubAppProperties properties;
    private final Map<Long, CachedToken> tokenCache = new ConcurrentHashMap<>();

    public GitHubAppTokenService(GitHubAppProperties properties) {
        this.properties = properties;
    }

    /**
     * Generates a signed RS256 JWT for GitHub App authentication.
     * Valid for 10 minutes with 60-second back-dated issuance for clock drift.
     */
    public String createSignedJwt() {
        String pem = properties.getPrivateKeyPem();
        String appId = properties.getAppId();

        if (pem == null || pem.isBlank() || appId == null || appId.isBlank()) {
            log.warn("GitHub App credentials (privateKeyPem or appId) are not configured.");
            return "";
        }

        try {
            PrivateKey privateKey = parsePrivateKey(pem);
            Instant now = Instant.now();
            long iat = now.minusSeconds(60).getEpochSecond();
            long exp = now.plusSeconds(600).getEpochSecond();

            String headerJson = "{\"alg\":\"RS256\",\"typ\":\"JWT\"}";
            String payloadJson = String.format("{\"iat\":%d,\"exp\":%d,\"iss\":\"%s\"}", iat, exp, appId);

            Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
            String encodedHeader = encoder.encodeToString(headerJson.getBytes(StandardCharsets.UTF_8));
            String encodedPayload = encoder.encodeToString(payloadJson.getBytes(StandardCharsets.UTF_8));
            String signingInput = encodedHeader + "." + encodedPayload;

            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initSign(privateKey);
            signature.update(signingInput.getBytes(StandardCharsets.UTF_8));
            byte[] signatureBytes = signature.sign();

            String encodedSignature = encoder.encodeToString(signatureBytes);
            return signingInput + "." + encodedSignature;
        } catch (Exception e) {
            log.error("Failed to generate GitHub App JWT: {}", e.getMessage());
            throw new IllegalStateException("Failed to generate GitHub App authentication token", e);
        }
    }

    /**
     * Retrieves or caches an installation access token.
     */
    public OptionalCachedToken getCachedInstallationToken(Long installationId) {
        if (installationId == null) {
            return OptionalCachedToken.empty();
        }

        CachedToken cached = tokenCache.get(installationId);
        if (cached != null && cached.isValid()) {
            return OptionalCachedToken.of(cached.token());
        }

        return OptionalCachedToken.empty();
    }

    public void cacheInstallationToken(Long installationId, String token, Instant expiresAt) {
        if (installationId != null && token != null && expiresAt != null) {
            tokenCache.put(installationId, new CachedToken(token, expiresAt));
            log.debug("Cached installation token for installation {}", installationId);
        }
    }

    public void clearCache() {
        tokenCache.clear();
    }

    private PrivateKey parsePrivateKey(String pem) throws Exception {
        String cleaned = pem
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replace("-----BEGIN RSA PRIVATE KEY-----", "")
                .replace("-----END RSA PRIVATE KEY-----", "")
                .replaceAll("\\s+", "");

        byte[] keyBytes = Base64.getDecoder().decode(cleaned);
        PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(keyBytes);
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        return keyFactory.generatePrivate(keySpec);
    }

    private record CachedToken(String token, Instant expiresAt) {
        public boolean isValid() {
            // Buffer 5 minutes before expiration
            return Instant.now().plusSeconds(300).isBefore(expiresAt);
        }
    }

    public record OptionalCachedToken(boolean isPresent, String token) {
        public static OptionalCachedToken empty() {
            return new OptionalCachedToken(false, null);
        }

        public static OptionalCachedToken of(String token) {
            return new OptionalCachedToken(true, token);
        }

        @Override
        public String toString() {
            return isPresent ? "[REDACTED_TOKEN]" : "[NONE]";
        }
    }
}

