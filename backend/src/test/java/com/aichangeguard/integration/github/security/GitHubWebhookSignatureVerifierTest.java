package com.aichangeguard.integration.github.security;

import com.aichangeguard.common.error.ChangeGuardException;
import com.aichangeguard.common.error.ErrorCode;
import com.aichangeguard.integration.github.config.GitHubAppProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class GitHubWebhookSignatureVerifierTest {

    private static final String SECRET = "top-secret-webhook-key-12345";
    private GitHubAppProperties properties;
    private GitHubWebhookSignatureVerifier verifier;

    @BeforeEach
    void setUp() {
        properties = new GitHubAppProperties();
        properties.setWebhookSecret(SECRET);
        verifier = new GitHubWebhookSignatureVerifier(properties);
    }

    @Test
    @DisplayName("Valid signature is accepted")
    void validSignatureAccepted() {
        byte[] payload = "{\"action\":\"opened\"}".getBytes(StandardCharsets.UTF_8);
        String signature = computeSignature(payload, SECRET);

        assertThat(verifier.isValid(payload, signature)).isTrue();
        verifier.verifyOrThrow(payload, signature); // does not throw
    }

    @Test
    @DisplayName("Tampered payload with valid signature is rejected")
    void tamperedPayloadRejected() {
        byte[] payload = "{\"action\":\"opened\"}".getBytes(StandardCharsets.UTF_8);
        String signature = computeSignature(payload, SECRET);

        byte[] tamperedPayload = "{\"action\":\"closed\"}".getBytes(StandardCharsets.UTF_8);

        assertThat(verifier.isValid(tamperedPayload, signature)).isFalse();
        assertThatThrownBy(() -> verifier.verifyOrThrow(tamperedPayload, signature))
                .isInstanceOf(ChangeGuardException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.UNAUTHORIZED);
    }

    @Test
    @DisplayName("Invalid signature computed with wrong secret is rejected")
    void wrongSecretRejected() {
        byte[] payload = "{\"action\":\"opened\"}".getBytes(StandardCharsets.UTF_8);
        String wrongSignature = computeSignature(payload, "wrong-secret");

        assertThat(verifier.isValid(payload, wrongSignature)).isFalse();
        assertThatThrownBy(() -> verifier.verifyOrThrow(payload, wrongSignature))
                .isInstanceOf(ChangeGuardException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.UNAUTHORIZED);
    }

    @Test
    @DisplayName("Missing or malformed signature header is rejected")
    void missingOrMalformedSignatureRejected() {
        byte[] payload = "{\"action\":\"opened\"}".getBytes(StandardCharsets.UTF_8);

        assertThat(verifier.isValid(payload, null)).isFalse();
        assertThat(verifier.isValid(payload, "")).isFalse();
        assertThat(verifier.isValid(payload, "invalid_prefix_hex")).isFalse();
        assertThat(verifier.isValid(null, "sha256=1234")).isFalse();
    }

    @Test
    @DisplayName("Unconfigured webhook secret fails verification safely")
    void unconfiguredSecretFails() {
        properties.setWebhookSecret("");
        byte[] payload = "{\"action\":\"opened\"}".getBytes(StandardCharsets.UTF_8);

        assertThat(verifier.isValid(payload, "sha256=abcdef1234567890")).isFalse();
    }

    public static String computeSignature(byte[] payload, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return "sha256=" + HexFormat.of().formatHex(mac.doFinal(payload));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
