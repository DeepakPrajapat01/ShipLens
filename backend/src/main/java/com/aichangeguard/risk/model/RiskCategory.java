package com.aichangeguard.risk.model;

/**
 * Standard risk categories defined by the ChangeGuard risk architecture.
 */
public enum RiskCategory {
    /** REST endpoint removals, parameter changes, DTO changes, constraint modifications. */
    API_COMPATIBILITY,

    /** Schema migrations, table/column drops, NOT NULL constraints, destructive DML. */
    DATABASE_IMPACT,

    /** Transitive caller blast radius, circular dependencies, deep call trees. */
    DEPENDENCY_IMPACT,

    /** Regressions or test execution errors inside containerized test runs. */
    TEST_FAILURE,

    /** Security vulnerabilities or unsafe constructs detected in changes. */
    SECURITY,

    /** Diff surface, lines modified, high file count, wide package sprawl. */
    CODE_CHANGE_COMPLEXITY,

    /** Missing inputs, parser errors, truncated graphs, unsupported SQL/AST constructs. */
    ANALYSIS_UNCERTAINTY
}

