package com.aichangeguard.impact.database.model;

/**
 * Severity classification for database and schema changes.
 *
 * <p>Aligned with ChangeGuard production risk guidelines:
 * <ul>
 *   <li>{@link #DESTRUCTIVE}: Data loss or irreversible schema destruction (e.g. DROP TABLE, DROP COLUMN, TRUNCATE).</li>
 *   <li>{@link #POTENTIALLY_BREAKING}: Potential runtime failure or migration failure (e.g. adding NOT NULL without DEFAULT, altering column type, adding unique constraint to existing data, removed queries).</li>
 *   <li>{@link #NON_BREAKING}: Safe backward-compatible additions (e.g. CREATE TABLE, nullable ADD COLUMN, ADD INDEX).</li>
 *   <li>{@link #INFORMATIONAL}: Benign metadata or informational schema notes.</li>
 *   <li>{@link #UNKNOWN}: Unsupported SQL syntax, dynamic SQL, or ambiguous constructs requiring human inspection.</li>
 * </ul>
 */
public enum DatabaseChangeSeverity {
    /** Data loss or irreversible schema destruction. */
    DESTRUCTIVE,

    /** Risk of migration failure or runtime query errors for existing data. */
    POTENTIALLY_BREAKING,

    /** Backward-compatible schema evolution. */
    NON_BREAKING,

    /** Informational changes with negligible risk. */
    INFORMATIONAL,

    /** Unsupported constructs or ambiguous statements where risk cannot be proven deterministically. */
    UNKNOWN
}

