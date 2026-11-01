package com.aichangeguard.common.error;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(GlobalExceptionHandlerTest.TestExceptionController.class)
class GlobalExceptionHandlerTest {

    @Autowired
    private MockMvc mockMvc;

    @RestController
    @RequestMapping("/test/errors")
    static class TestExceptionController {

        @GetMapping("/not-found")
        public void throwNotFound() {
            throw new ResourceNotFoundException("Repository", "repo-123");
        }

        @GetMapping("/validation")
        public void throwValidation() {
            throw new ValidationException("Commit SHA is invalid");
        }

        @GetMapping("/domain-error")
        public void throwDomainError() {
            throw new ChangeGuardException(ErrorCode.INGESTION_ERROR, "Git checkout bounded limit exceeded");
        }

        @GetMapping("/unhandled")
        public void throwUnhandled() {
            throw new RuntimeException("Simulated unexpected failure");
        }
    }

    @Test
    @DisplayName("ResourceNotFoundException produces 404 with standard ErrorResponse envelope")
    void handleResourceNotFoundException() throws Exception {
        mockMvc.perform(get("/test/errors/not-found"))
                .andExpect(status().isNotFound())
                .andExpect(header().exists("X-Correlation-ID"))
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Repository 'repo-123' not found"))
                .andExpect(jsonPath("$.correlationId").isNotEmpty())
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.details.resourceType").value("Repository"));
    }

    @Test
    @DisplayName("ValidationException produces 400 with standard ErrorResponse envelope")
    void handleValidationException() throws Exception {
        mockMvc.perform(get("/test/errors/validation"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("Commit SHA is invalid"));
    }

    @Test
    @DisplayName("Domain IngestionError produces 422 with standard ErrorResponse envelope")
    void handleDomainIngestionError() throws Exception {
        mockMvc.perform(get("/test/errors/domain-error"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INGESTION_ERROR"))
                .andExpect(jsonPath("$.message").value("Git checkout bounded limit exceeded"));
    }

    @Test
    @DisplayName("Unhandled Exception produces 500 without leaking internal stack trace")
    void handleUnhandledException() throws Exception {
        mockMvc.perform(get("/test/errors/unhandled"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_SERVER_ERROR"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("An unexpected internal error occurred")))
                .andExpect(jsonPath("$.stackTrace").doesNotExist());
    }

    @Test
    @DisplayName("Unsupported HTTP method produces 405 Method Not Allowed")
    void handleMethodNotSupported() throws Exception {
        mockMvc.perform(post("/api/v1/health"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
    }
}
