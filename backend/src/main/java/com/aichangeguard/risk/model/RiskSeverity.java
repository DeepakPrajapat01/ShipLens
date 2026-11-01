package com.aichangeguard.risk.model;

/**
 * Standard severity levels for normalized risk findings.
 */
public enum RiskSeverity {
    /**
     * Critical risk factor that indicates likely production disruption or severe defect
     * (e.g., breaking API removal, destructive DROP TABLE migration, test failure in affected component).
     */
    CRITICAL,

    /**
     * High risk factor requiring manual verification or caution
     * (e.g., potentially breaking schema/API change, container execution error, wide blast radius).
     */
    HIGH,

    /**
     * Moderate risk factor with contained impact
     * (e.g., large code change complexity, circular dependency, unselected affected tests).
     */
    MEDIUM,

    /**
     * Low risk factor with minor impact
     * (e.g., compatible additive change, minor dependency expansion).
     */
    LOW,

    /**
     * Informational finding with no immediate risk implication.
     */
    INFO
}

