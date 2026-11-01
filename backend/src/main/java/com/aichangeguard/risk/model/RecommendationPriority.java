package com.aichangeguard.risk.model;

/**
 * Priority of an action recommended by the Risk Assessment Engine.
 */
public enum RecommendationPriority {
    /** Deployment should be blocked until this issue is resolved. */
    BLOCKING,

    /** High priority remediation recommended prior to release. */
    HIGH,

    /** Moderate priority suggestion or best-practice caution. */
    MEDIUM,

    /** Low priority or informational guidance. */
    LOW
}

