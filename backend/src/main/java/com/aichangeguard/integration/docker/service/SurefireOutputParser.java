package com.aichangeguard.integration.docker.service;

import com.aichangeguard.integration.docker.model.TestExecutionStatus;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses Maven Surefire test execution console output and summary blocks.
 */
@Component
public class SurefireOutputParser {

    private static final Pattern SUREFIRE_SUMMARY_PATTERN = Pattern.compile(
            "Tests run:\\s*([0-9]+),\\s*Failures:\\s*([0-9]+),\\s*Errors:\\s*([0-9]+),\\s*Skipped:\\s*([0-9]+)",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern COMPILATION_ERROR_PATTERN = Pattern.compile(
            "\\[ERROR\\]\\s+(?:COMPILATION ERROR|Compilation failure)",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern FAILURE_LINE_PATTERN = Pattern.compile(
            "\\[ERROR\\]\\s+(.*?<<<\\s*(?:FAILURE|ERROR)!)",
            Pattern.CASE_INSENSITIVE
    );

    public record ParsedSurefireResult(
            int totalTests,
            int passedCount,
            int failedCount,
            int errorCount,
            int skippedCount,
            List<String> failureDetails,
            TestExecutionStatus suggestedStatus
    ) {}

    /**
     * Parses the stdout/stderr log from a Maven test execution.
     */
    public ParsedSurefireResult parse(String output, int exitCode) {
        if (output == null) {
            output = "";
        }

        int total = 0;
        int failures = 0;
        int errors = 0;
        int skipped = 0;
        boolean summaryFound = false;

        Matcher matcher = SUREFIRE_SUMMARY_PATTERN.matcher(output);
        // Take the last summary occurrence (overall summary)
        while (matcher.find()) {
            try {
                total = Integer.parseInt(matcher.group(1));
                failures = Integer.parseInt(matcher.group(2));
                errors = Integer.parseInt(matcher.group(3));
                skipped = Integer.parseInt(matcher.group(4));
                summaryFound = true;
            } catch (NumberFormatException ignored) {}
        }

        List<String> failureDetails = new ArrayList<>();
        Matcher failMatcher = FAILURE_LINE_PATTERN.matcher(output);
        while (failMatcher.find()) {
            failureDetails.add(failMatcher.group(1).trim());
        }

        // Determine status
        TestExecutionStatus status;
        if (COMPILATION_ERROR_PATTERN.matcher(output).find()) {
            status = TestExecutionStatus.ERROR;
            if (failureDetails.isEmpty()) {
                failureDetails.add("Maven compilation failure occurred inside container");
            }
        } else if (exitCode == 0 && failures == 0 && errors == 0) {
            status = TestExecutionStatus.PASSED;
        } else if (failures > 0 || errors > 0) {
            status = failures > 0 ? TestExecutionStatus.FAILED : TestExecutionStatus.ERROR;
        } else if (exitCode != 0) {
            status = TestExecutionStatus.ERROR;
            if (failureDetails.isEmpty()) {
                failureDetails.add("Process exited with non-zero code (" + exitCode + ")");
            }
        } else {
            status = TestExecutionStatus.PASSED;
        }

        int passed = Math.max(0, total - failures - errors - skipped);

        return new ParsedSurefireResult(
                total,
                passed,
                failures,
                errors,
                skipped,
                failureDetails,
                status
        );
    }
}

