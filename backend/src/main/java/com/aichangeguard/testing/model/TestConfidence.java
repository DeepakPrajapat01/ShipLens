package com.aichangeguard.testing.model;

/**
 * Confidence classification for test selection.
 *
 * <p>Reflects the strength of evidence linking a candidate test to a proposed code change:
 * <ul>
 *   <li>{@link #HIGH}: Direct method-level invocation or modified test file.</li>
 *   <li>{@link #MEDIUM}: Resolved transitive call chain or direct class-level reference.</li>
 *   <li>{@link #LOW}: Distant transitive dependency or inheritance hierarchy inference.</li>
 *   <li>{@link #FALLBACK}: Selected conservatively due to incomplete dependency evidence or build/config changes.</li>
 *   <li>{@link #UNKNOWN}: Association is ambiguous or cannot be verified statically.</li>
 * </ul>
 */
public enum TestConfidence {
    /** Direct method-level call or test file modification. */
    HIGH,

    /** Resolved transitive call chain or direct class dependency. */
    MEDIUM,

    /** Inheritance or deep call chain association. */
    LOW,

    /** Conservative fallback due to analysis gaps or build-level changes. */
    FALLBACK,

    /** Unresolved or ambiguous association. */
    UNKNOWN
}

