package com.aichangeguard.integration.docker.service;

import com.aichangeguard.integration.docker.model.TestExecutionStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SurefireOutputParserTest {

    private SurefireOutputParser parser;

    @BeforeEach
    void setUp() {
        parser = new SurefireOutputParser();
    }

    @Test
    @DisplayName("Should parse successful test run output")
    void shouldParseSuccessfulOutput() {
        String log = """
                [INFO] Running com.example.UserServiceTest
                [INFO] Tests run: 5, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.12 s
                [INFO] Results:
                [INFO]
                [INFO] Tests run: 5, Failures: 0, Errors: 0, Skipped: 0
                [INFO] BUILD SUCCESS
                """;

        SurefireOutputParser.ParsedSurefireResult result = parser.parse(log, 0);

        assertThat(result.suggestedStatus()).isEqualTo(TestExecutionStatus.PASSED);
        assertThat(result.totalTests()).isEqualTo(5);
        assertThat(result.passedCount()).isEqualTo(5);
        assertThat(result.failedCount()).isZero();
        assertThat(result.errorCount()).isZero();
        assertThat(result.failureDetails()).isEmpty();
    }

    @Test
    @DisplayName("Should parse failed test output and extract details")
    void shouldParseFailedOutput() {
        String log = """
                [INFO] Running com.example.OrderTest
                [ERROR] Tests run: 3, Failures: 1, Errors: 0, Skipped: 0, Time elapsed: 0.05 s <<< FAILURE! -- in com.example.OrderTest
                [ERROR] com.example.OrderTest.testOrderFailed <<< FAILURE!
                [INFO] Results:
                [INFO]
                [INFO] Tests run: 3, Failures: 1, Errors: 0, Skipped: 0
                [INFO] BUILD FAILURE
                """;

        SurefireOutputParser.ParsedSurefireResult result = parser.parse(log, 1);

        assertThat(result.suggestedStatus()).isEqualTo(TestExecutionStatus.FAILED);
        assertThat(result.totalTests()).isEqualTo(3);
        assertThat(result.passedCount()).isEqualTo(2);
        assertThat(result.failedCount()).isEqualTo(1);
        assertThat(result.failureDetails()).isNotEmpty();
    }

    @Test
    @DisplayName("Should detect Maven compilation errors as ERROR status")
    void shouldDetectCompilationErrors() {
        String log = """
                [INFO] --- compiler:3.13.0:testCompile (default-testCompile) @ test-app ---
                [ERROR] COMPILATION ERROR :
                [ERROR] /src/test/java/com/example/BrokenTest.java:[15,20] cannot find symbol
                [INFO] BUILD FAILURE
                """;

        SurefireOutputParser.ParsedSurefireResult result = parser.parse(log, 1);

        assertThat(result.suggestedStatus()).isEqualTo(TestExecutionStatus.ERROR);
        assertThat(result.totalTests()).isZero();
    }
}

