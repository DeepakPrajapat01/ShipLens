package com.aichangeguard.testing.model;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Information about a test call or reference that could not be mapped to production symbols deterministically.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record UnresolvedTestMapping(
        String testClass,
        String testMethod,
        String targetInvocation,
        String reason,
        String sourceFile,
        int sourceLine
) {}

