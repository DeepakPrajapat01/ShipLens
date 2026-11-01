package com.aichangeguard.dependency.model;

/**
 * Resolution confidence classification for a dependency relationship.
 */
public enum ResolutionStatus {
    /** Confirmed via exact deterministic AST symbol matching */
    RESOLVED,

    /** Inferred via heuristic naming or candidate proximity */
    HEURISTIC,

    /** Call exists syntactically but target cannot be resolved without classpath / symbol solver */
    UNRESOLVED
}
