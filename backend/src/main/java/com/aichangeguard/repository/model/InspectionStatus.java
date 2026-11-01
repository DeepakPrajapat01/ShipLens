package com.aichangeguard.repository.model;

/**
 * Inspection status classifications for ingested repositories.
 */
public enum InspectionStatus {
    /** Valid Java project configured with supported Maven build */
    VALID_JAVA_MAVEN,

    /** Java source files detected, but no supported build system found */
    JAVA_NO_BUILD_SYSTEM,

    /** Maven build file present, but no Java source files discovered */
    MAVEN_NO_JAVA_SOURCES,

    /** Repository contains non-Java codebase */
    NON_JAVA_REPOSITORY,

    /** Directory is completely empty */
    EMPTY_REPOSITORY,

    /** Repository layout or files could not be inspected */
    INVALID_LAYOUT,

    /** Path validation or access error */
    REJECTED
}
