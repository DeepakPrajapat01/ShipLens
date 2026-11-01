package com.aichangeguard.risk.model;

/**
 * High-level production risk categorization.
 *
 * <p>Directly implements the documented risk levels in {@code RISK_ASSESSMENT_MODEL.md}:
 * <ul>
 *   <li>{@link #LOW}: Minor, backward-compatible, low-complexity changes with high verification confidence.</li>
 *   <li>{@link #MODERATE}: Medium complexity or notable surface changes requiring standard review and verification.</li>
 *   <li>{@link #HIGH}: Breaking API changes, destructive database operations, failed tests, or widespread blast radius.</li>
 *   <li>{@link #INDETERMINATE}: Analysis is missing, incomplete, or truncated, preventing a reliable directional assessment.</li>
 * </ul>
 */
public enum RiskLevel {
    LOW,
    MODERATE,
    HIGH,
    INDETERMINATE
}

