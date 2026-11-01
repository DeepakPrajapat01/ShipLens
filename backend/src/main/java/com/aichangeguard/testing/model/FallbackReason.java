package com.aichangeguard.testing.model;

/**
 * Indicates why conservative fallback test selection was triggered.
 */
public enum FallbackReason {
    NONE,
    BUILD_CONFIGURATION_CHANGED,
    PARSER_FAILURE_IN_CHANGED_FILES,
    INCOMPLETE_GRAPH_COVERAGE,
    ZERO_TESTS_MATCHED_PRODUCTION_CHANGES,
    MANUAL_FULL_SUITE_REQUESTED
}

