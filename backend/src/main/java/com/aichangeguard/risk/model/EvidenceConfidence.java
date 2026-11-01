package com.aichangeguard.risk.model;

/**
 * Confidence level of the evidence supporting a risk finding.
 *
 * <p>Preserves the distinction between deterministic facts (e.g., AST parse, exact diff, Surefire test failure)
 * and heuristic inferences (e.g., regex-based SQL parsing, unresolved reflection fallback).</p>
 */
public enum EvidenceConfidence {
    /** Deterministically verified from source code, AST, or exact execution output. */
    HIGH,

    /** Statistically or heuristically inferred with high probability. */
    MEDIUM,

    /** Inferred from fallback rules, ambiguous mapping, or incomplete context. */
    LOW,

    /** Unknown or unverified confidence. */
    UNKNOWN;

    public boolean isDeterministic() {
        return this == HIGH;
    }
}

