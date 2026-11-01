package com.aichangeguard.configuration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CorrelationIdFilterTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("Generates a new Correlation ID when request header is absent")
    void generatesCorrelationIdWhenMissing() throws Exception {
        mockMvc.perform(get("/api/v1/health"))
                .andExpect(status().isOk())
                .andExpect(header().exists("X-Correlation-ID"));
    }

    @Test
    @DisplayName("Preserves and propagates the provided Correlation ID from incoming request")
    void preservesExistingCorrelationId() throws Exception {
        String existingId = "client-trace-12345";
        mockMvc.perform(get("/api/v1/health")
                        .header("X-Correlation-ID", existingId))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Correlation-ID", existingId));
    }
}
