package com.aichangeguard.integration.docker.service;

import com.aichangeguard.common.error.ValidationException;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Generates discrete Maven command arguments for containerized test execution.
 *
 * <p>Enforces strict injection guards so arbitrary shell strings cannot be smuggled
 * via test names or parameters.</p>
 */
@Component
public class MavenCommandGenerator {

    private static final Pattern SAFE_TEST_PATTERN = Pattern.compile("^[A-Za-z0-9_$.*#]+$");

    /**
     * Generates a safe, un-interpolated command argument list for Maven test execution.
     *
     * @param selectedTests list of test class/method patterns (e.g. UserServiceTest, UserServiceTest#testFind)
     * @param networkDisabled whether offline mode should be enforced
     * @param additionalArgs safe additional flags (e.g. -pl module)
     * @return list of discrete command arguments
     */
    public List<String> generateCommand(List<String> selectedTests, boolean networkDisabled, List<String> additionalArgs) {
        List<String> args = new ArrayList<>();
        args.add("mvn");
        args.add("-B");
        args.add("--no-transfer-progress");

        if (networkDisabled) {
            args.add("-o"); // offline mode
        }

        args.add("test");
        args.add("-Dsurefire.failIfNoSpecifiedTests=false");

        if (selectedTests != null && !selectedTests.isEmpty()) {
            validateTestPatterns(selectedTests);
            String testParam = String.join(",", selectedTests);
            args.add("-Dtest=" + testParam);
        }

        if (additionalArgs != null) {
            for (String extra : additionalArgs) {
                validateSafeArg(extra);
                args.add(extra);
            }
        }

        return args;
    }

    private void validateTestPatterns(List<String> selectedTests) {
        for (String test : selectedTests) {
            if (test == null || test.isBlank()) {
                continue;
            }
            if (!SAFE_TEST_PATTERN.matcher(test.trim()).matches()) {
                throw new ValidationException("Invalid or unsafe test identifier: '" + test + "'. Only alphanumeric, '.', '#', '_', '$', '*' are permitted.");
            }
        }
    }

    private void validateSafeArg(String arg) {
        if (arg == null || arg.isBlank()) {
            return;
        }
        if (arg.contains(";") || arg.contains("&") || arg.contains("|")
                || arg.contains("`") || arg.contains("$") || arg.contains(">")
                || arg.contains("<") || arg.contains("\n") || arg.contains("\r")) {
            throw new ValidationException("Unsafe characters detected in Maven arguments: " + arg);
        }
    }
}

