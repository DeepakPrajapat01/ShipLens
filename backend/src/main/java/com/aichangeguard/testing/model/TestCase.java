package com.aichangeguard.testing.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;
import java.util.Set;

/**
 * Normalized representation of a discovered test method (JUnit 5, etc.).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TestCase(
        String testClassQualifiedName,
        String testClassSimpleName,
        String testMethodName,
        String testMethodSymbolId,
        String sourceFile,
        int sourceLine,
        List<String> annotations,
        boolean isParameterized,
        Set<String> referencedProductionSymbols,
        Set<String> referencedProductionTypes
) {
    public String displayName() {
        return testClassSimpleName + "#" + testMethodName;
    }
}

