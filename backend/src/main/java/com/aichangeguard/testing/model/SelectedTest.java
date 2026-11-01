package com.aichangeguard.testing.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * Representation of an individual test candidate selected for execution.
 *
 * <p>Requirements:
 * <ul>
 *   <li>Every selected test MUST have a traceable reason and evidence path.</li>
 *   <li>Confidence level must be explicitly classified.</li>
 *   <li>Related production components must be identified.</li>
 * </ul>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SelectedTest(
        String testClass,
        String testMethod,
        String testMethodSymbolId,
        String sourceFile,
        int sourceLine,
        List<String> relatedProductionComponents,
        String selectionReason,
        TestSelectionReasonType reasonType,
        TestConfidence confidence,
        List<String> evidencePath
) {
    public String displayName() {
        return testClass + "#" + testMethod;
    }
}

