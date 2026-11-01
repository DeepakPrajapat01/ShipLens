package com.aichangeguard.testing.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;
import java.util.Set;

/**
 * Normalized representation of a discovered test class containing test methods.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TestClass(
        String qualifiedName,
        String simpleName,
        String sourceFile,
        int sourceLine,
        List<String> annotations,
        List<TestCase> testMethods,
        Set<String> referencedProductionTypes
) {
    public int testCount() {
        return testMethods != null ? testMethods.size() : 0;
    }
}

