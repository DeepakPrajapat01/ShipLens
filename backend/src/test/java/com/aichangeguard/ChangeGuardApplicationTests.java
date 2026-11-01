package com.aichangeguard;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class ChangeGuardApplicationTests {

    @Test
    @DisplayName("Spring ApplicationContext loads successfully with default configuration")
    void contextLoads() {
        // Verifies the application context starts up without misconfiguration
    }
}
