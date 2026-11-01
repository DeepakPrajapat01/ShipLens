package com.aichangeguard.testing.model;

/**
 * Categorization of the evidence type that triggered the selection of a test.
 */
public enum TestSelectionReasonType {
    /** Test method directly invokes the changed method or constructor. */
    DIRECT_METHOD_CALL,

    /** Test method invokes a method that transitively calls the changed method. */
    TRANSITIVE_DEPENDENCY,

    /** Test class references, instantiates, or injects the changed production class. */
    DIRECT_CLASS_REFERENCE,

    /** Test exercises a subclass or interface implementation of the changed type. */
    INHERITANCE_HIERARCHY,

    /** The test file itself was added or modified in the proposed change. */
    CHANGED_TEST_FILE,

    /** Selected conservatively because build configuration (e.g. pom.xml) changed. */
    BUILD_CONFIGURATION_CHANGED,

    /** Selected conservatively because graph completeness or parser coverage was insufficient. */
    FALLBACK_INCOMPLETE_GRAPH,

    /** Selected conservatively because zero tests matched the changed production code. */
    FALLBACK_ZERO_MATCHES
}

