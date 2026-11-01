package com.aichangeguard.impact.database.model;

/**
 * Distinguishes deterministic migration-derived evidence from application-level entity inferences.
 *
 * <p>Requirements:
 * <ul>
 *   <li>{@link #MIGRATION_BASED}: Directly parsed from Flyway SQL scripts. High confidence schema truth.</li>
 *   <li>{@link #ENTITY_INFERRED}: Extracted from JPA annotations in Java source. Represents application expectation,
 *       which does NOT guarantee deployed database state.</li>
 *   <li>{@link #REPOSITORY_QUERY}: Extracted from Spring Data repository query methods and annotations.</li>
 *   <li>{@link #MIGRATION_METADATA}: Discovered from Flyway version naming, file existence, and order checks.</li>
 * </ul>
 */
public enum EvidenceCategory {
    /** Deterministic schema evidence parsed from SQL migration files. */
    MIGRATION_BASED,

    /** Inferred mapping from Java JPA entity annotations. Not a guarantee of database schema. */
    ENTITY_INFERRED,

    /** Query-level evidence extracted from Spring Data repository definitions. */
    REPOSITORY_QUERY,

    /** Structural or versioning metadata from Flyway migration files. */
    MIGRATION_METADATA
}

