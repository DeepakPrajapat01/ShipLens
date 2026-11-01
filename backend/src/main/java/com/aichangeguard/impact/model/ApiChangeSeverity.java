package com.aichangeguard.impact.model;

/**
 * Severity classification for API endpoint changes.
 *
 * <p>Aligned with the risk assessment model in docs/RISK_ASSESSMENT_MODEL.md.</p>
 */
public enum ApiChangeSeverity {
    /**
     * Change that breaks backward compatibility for existing API consumers.
     * Examples: removed endpoint, changed required parameter, incompatible type change.
     */
    BREAKING,

    /**
     * Change that could break backward compatibility depending on consumer usage patterns.
     * Examples: added required parameter with no default, response field type change.
     */
    POTENTIALLY_BREAKING,

    /**
     * Change that is backward-compatible and additive.
     * Examples: new endpoint, new optional parameter, additional response field.
     */
    COMPATIBLE,

    /**
     * Change whose compatibility impact could not be determined.
     * Examples: unsupported annotation patterns, unresolved type references.
     */
    UNKNOWN
}
