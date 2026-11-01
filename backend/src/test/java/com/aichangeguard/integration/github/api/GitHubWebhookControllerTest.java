package com.aichangeguard.integration.github.api;

import com.aichangeguard.integration.github.config.GitHubAppProperties;
import com.aichangeguard.integration.github.model.GitHubInstallation;
import com.aichangeguard.integration.github.security.GitHubWebhookSignatureVerifierTest;
import com.aichangeguard.integration.github.service.GitHubInstallationManager;
import com.aichangeguard.integration.github.service.WebhookDeduplicationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class GitHubWebhookControllerTest {

    private static final String SECRET = "changeguard-test-secret";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private GitHubAppProperties properties;

    @Autowired
    private GitHubInstallationManager installationManager;

    @Autowired
    private WebhookDeduplicationService deduplicationService;

    @BeforeEach
    void setUp() {
        properties.setWebhookSecret(SECRET);
        deduplicationService.clear();
        installationManager.clear();

        // Register default test installation
        installationManager.registerInstallation(new GitHubInstallation(
                9999L, "acme-corp", "Organization", "all", true,
                Set.of(), Instant.now(), Instant.now()
        ));
    }

    @Test
    @DisplayName("Valid signature with PR opened event returns 202 ACCEPTED with created job ID")
    void validPrOpenedReturnsAccepted() throws Exception {
        String payload = """
                {
                  "action": "opened",
                  "number": 10,
                  "pull_request": {
                    "number": 10,
                    "base": {
                      "ref": "main",
                      "sha": "1111111111111111111111111111111111111111"
                    },
                    "head": {
                      "ref": "feature-1",
                      "sha": "2222222222222222222222222222222222222222"
                    }
                  },
                  "repository": {
                    "id": 12345,
                    "full_name": "acme-corp/service"
                  },
                  "installation": {
                    "id": 9999
                  }
                }
                """;

        byte[] body = payload.getBytes(StandardCharsets.UTF_8);
        String signature = GitHubWebhookSignatureVerifierTest.computeSignature(body, SECRET);

        mockMvc.perform(post("/api/v1/integrations/github/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-GitHub-Delivery", "del-pr-opened-1")
                        .header("X-GitHub-Event", "pull_request")
                        .header("X-Hub-Signature-256", signature)
                        .content(body))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("ACCEPTED"))
                .andExpect(jsonPath("$.deliveryId").value("del-pr-opened-1"))
                .andExpect(jsonPath("$.jobId").isNotEmpty());
    }

    @Test
    @DisplayName("Invalid signature is rejected with 401 UNAUTHORIZED")
    void invalidSignatureRejected() throws Exception {
        byte[] body = "{\"action\":\"opened\"}".getBytes(StandardCharsets.UTF_8);

        mockMvc.perform(post("/api/v1/integrations/github/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-GitHub-Delivery", "del-invalid-sig")
                        .header("X-GitHub-Event", "pull_request")
                        .header("X-Hub-Signature-256", "sha256=badbadbadbadbadbadbadbadbadbadbadbadbadbadbadbadbadbadbadbadbadbad")
                        .content(body))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("Missing signature is rejected with 401 UNAUTHORIZED")
    void missingSignatureRejected() throws Exception {
        byte[] body = "{\"action\":\"opened\"}".getBytes(StandardCharsets.UTF_8);

        mockMvc.perform(post("/api/v1/integrations/github/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-GitHub-Delivery", "del-no-sig")
                        .header("X-GitHub-Event", "pull_request")
                        .content(body))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("Duplicate delivery ID is handled idempotently without recreating job")
    void duplicateDeliveryIdIgnored() throws Exception {
        String payload = """
                {
                  "action": "opened",
                  "pull_request": {
                    "number": 11,
                    "base": { "ref": "main", "sha": "1111111111111111111111111111111111111111" },
                    "head": { "ref": "feat", "sha": "3333333333333333333333333333333333333333" }
                  },
                  "repository": { "id": 12345, "full_name": "acme-corp/service" },
                  "installation": { "id": 9999 }
                }
                """;
        byte[] body = payload.getBytes(StandardCharsets.UTF_8);
        String signature = GitHubWebhookSignatureVerifierTest.computeSignature(body, SECRET);

        // First delivery -> ACCEPTED (202)
        mockMvc.perform(post("/api/v1/integrations/github/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-GitHub-Delivery", "del-dup-1")
                        .header("X-GitHub-Event", "pull_request")
                        .header("X-Hub-Signature-256", signature)
                        .content(body))
                .andExpect(status().isAccepted());

        // Duplicate delivery -> OK (200) with IGNORED_DUPLICATE
        mockMvc.perform(post("/api/v1/integrations/github/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-GitHub-Delivery", "del-dup-1")
                        .header("X-GitHub-Event", "pull_request")
                        .header("X-Hub-Signature-256", signature)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IGNORED_DUPLICATE"));
    }

    @Test
    @DisplayName("Unsupported PR actions (e.g. closed, labeled) are acknowledged with 200 OK")
    void unsupportedPrActionAcknowledged() throws Exception {
        byte[] body = "{\"action\":\"closed\"}".getBytes(StandardCharsets.UTF_8);
        String signature = GitHubWebhookSignatureVerifierTest.computeSignature(body, SECRET);

        mockMvc.perform(post("/api/v1/integrations/github/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-GitHub-Delivery", "del-unsupported-action")
                        .header("X-GitHub-Event", "pull_request")
                        .header("X-Hub-Signature-256", signature)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UNSUPPORTED_EVENT"));
    }

    @Test
    @DisplayName("Unsupported event types (e.g. issues, watch) are acknowledged with 200 OK")
    void unsupportedEventTypeAcknowledged() throws Exception {
        byte[] body = "{\"action\":\"opened\"}".getBytes(StandardCharsets.UTF_8);
        String signature = GitHubWebhookSignatureVerifierTest.computeSignature(body, SECRET);

        mockMvc.perform(post("/api/v1/integrations/github/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-GitHub-Delivery", "del-unsupported-event")
                        .header("X-GitHub-Event", "issues")
                        .header("X-Hub-Signature-256", signature)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UNSUPPORTED_EVENT"));
    }

    @Test
    @DisplayName("Ping event is acknowledged with 200 OK PING_ACKNOWLEDGED")
    void pingEventAcknowledged() throws Exception {
        byte[] body = "{\"zen\":\"Responsive is better than fast.\"}".getBytes(StandardCharsets.UTF_8);
        String signature = GitHubWebhookSignatureVerifierTest.computeSignature(body, SECRET);

        mockMvc.perform(post("/api/v1/integrations/github/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-GitHub-Delivery", "del-ping-1")
                        .header("X-GitHub-Event", "ping")
                        .header("X-Hub-Signature-256", signature)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PING_ACKNOWLEDGED"));
    }

    @Test
    @DisplayName("Malformed JSON payload is rejected with 400 BAD_REQUEST INVALID_PAYLOAD")
    void malformedPayloadRejected() throws Exception {
        byte[] body = "not-json-content".getBytes(StandardCharsets.UTF_8);
        String signature = GitHubWebhookSignatureVerifierTest.computeSignature(body, SECRET);

        mockMvc.perform(post("/api/v1/integrations/github/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-GitHub-Delivery", "del-malformed-1")
                        .header("X-GitHub-Event", "pull_request")
                        .header("X-Hub-Signature-256", signature)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("INVALID_PAYLOAD"));
    }

    @Test
    @DisplayName("Malformed commit SHA is rejected with 400 BAD_REQUEST INVALID_PAYLOAD")
    void malformedShaRejected() throws Exception {
        String payload = """
                {
                  "action": "opened",
                  "pull_request": {
                    "number": 12,
                    "base": { "ref": "main", "sha": "invalid-non-40-hex-sha" },
                    "head": { "ref": "feat", "sha": "1111111111111111111111111111111111111111" }
                  },
                  "repository": { "id": 12345, "full_name": "acme-corp/service" },
                  "installation": { "id": 9999 }
                }
                """;
        byte[] body = payload.getBytes(StandardCharsets.UTF_8);
        String signature = GitHubWebhookSignatureVerifierTest.computeSignature(body, SECRET);

        mockMvc.perform(post("/api/v1/integrations/github/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-GitHub-Delivery", "del-bad-sha-1")
                        .header("X-GitHub-Event", "pull_request")
                        .header("X-Hub-Signature-256", signature)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("INVALID_PAYLOAD"));
    }

    @Test
    @DisplayName("Inaccessible repository under installation is rejected with 403 FORBIDDEN")
    void inaccessibleRepositoryForbidden() throws Exception {
        // Register installation 8888 with only access to acme-corp/other
        installationManager.registerInstallation(new GitHubInstallation(
                8888L, "acme-corp", "Organization", "selected", true,
                Set.of("acme-corp/other"), Instant.now(), Instant.now()
        ));

        String payload = """
                {
                  "action": "opened",
                  "pull_request": {
                    "number": 15,
                    "base": { "ref": "main", "sha": "1111111111111111111111111111111111111111" },
                    "head": { "ref": "feat", "sha": "2222222222222222222222222222222222222222" }
                  },
                  "repository": { "id": 99999, "full_name": "acme-corp/secret-repo" },
                  "installation": { "id": 8888 }
                }
                """;
        byte[] body = payload.getBytes(StandardCharsets.UTF_8);
        String signature = GitHubWebhookSignatureVerifierTest.computeSignature(body, SECRET);

        mockMvc.perform(post("/api/v1/integrations/github/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-GitHub-Delivery", "del-inaccessible-1")
                        .header("X-GitHub-Event", "pull_request")
                        .header("X-Hub-Signature-256", signature)
                        .content(body))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value("INACCESSIBLE_REPOSITORY"));
    }

    @Test
    @DisplayName("GET /api/v1/integrations/github/jobs/{jobId} returns created job details")
    void getJobReturnsDetails() throws Exception {
        String payload = """
                {
                  "action": "opened",
                  "pull_request": {
                    "number": 20,
                    "base": { "ref": "main", "sha": "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa" },
                    "head": { "ref": "feat", "sha": "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb" }
                  },
                  "repository": { "id": 12345, "full_name": "acme-corp/service" },
                  "installation": { "id": 9999 }
                }
                """;
        byte[] body = payload.getBytes(StandardCharsets.UTF_8);
        String signature = GitHubWebhookSignatureVerifierTest.computeSignature(body, SECRET);

        String response = mockMvc.perform(post("/api/v1/integrations/github/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-GitHub-Delivery", "del-get-job-1")
                        .header("X-GitHub-Event", "pull_request")
                        .header("X-Hub-Signature-256", signature)
                        .content(body))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();

        // Extract jobId
        String jobId = response.replaceAll(".*\"jobId\":\"([^\"]+)\".*", "$1");

        mockMvc.perform(get("/api/v1/integrations/github/jobs/" + jobId)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobId").value(jobId))
                .andExpect(jsonPath("$.repositoryFullName").value("acme-corp/service"))
                .andExpect(jsonPath("$.pullRequestNumber").value(20))
                .andExpect(jsonPath("$.headSha").value("bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"))
                .andExpect(jsonPath("$.state").value("PENDING"));
    }
}

