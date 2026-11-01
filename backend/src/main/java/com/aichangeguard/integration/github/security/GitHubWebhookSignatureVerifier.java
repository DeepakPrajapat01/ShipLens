package com.aichangeguard.integration.github.security;

import com.aichangeguard.common.error.ChangeGuardException;
import com.aichangeguard.common.error.ErrorCode;
import com.aichangeguard.integration.github.config.GitHubAppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Validates GitHub webhook signatures using HMAC-SHA256 with constant-time equality checks.
 *
 * <p>Never trusts unverified webhook payloads. Timing attacks are mitigated using
 * {@link MessageDigest#isEqual(byte[], byte[])}.</p>
 */
@Component
public class GitHubWebhookSignatureVerifier {

    private static final Logger log = LoggerFactory.getLogger(GitHubWebhookSignatureVerifier.class);
    private static final String HMAC_SHA256 = "HmacSHA256";
    private static final String PREFIX = "sha256=";

    private final GitHubAppProperties properties;

    public GitHubWebhookSignatureVerifier(GitHubAppProperties properties) {
        this.properties = properties;
    }

    /**
     * Checks whether the provided signature header is valid for the raw payload.
     *
     * @param payload raw unparsed webhook payload bytes
     * @param signatureHeader value of the X-Hub-Signature-256 header
     * @return true if signature is valid; false otherwise
     */
    public boolean isValid(byte[] payload, String signatureHeader) {
        if (payload == null || signatureHeader == null || !signatureHeader.startsWith(PREFIX)) {
            return false;
        }

        String secret = properties.getWebhookSecret();
        if (secret == null || secret.isBlank()) {
            log.error("GitHub webhook secret is not configured. Webhook rejected.");
            return false;
        }

        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            SecretKeySpec secretKey = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_SHA256);
            mac.init(secretKey);
            byte[] hmacBytes = mac.doFinal(payload);
            String computedSignature = PREFIX + HexFormat.of().formatHex(hmacBytes);

            byte[] expectedBytes = computedSignature.getBytes(StandardCharsets.UTF_8);
            byte[] providedBytes = signatureHeader.getBytes(StandardCharsets.UTF_8);

            return MessageDigest.isEqual(expectedBytes, providedBytes);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            log.error("Cryptographic error computing webhook HMAC signature: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Verifies the webhook signature, throwing an UNAUTHORIZED ChangeGuardException if invalid.
     */
    public void verifyOrThrow(byte[] payload, String signatureHeader) {
        if (!isValid(payload, signatureHeader)) {
            throw new ChangeGuardException(ErrorCode.UNAUTHORIZED, "Invalid or missing GitHub webhook signature");
        }
    }
}

