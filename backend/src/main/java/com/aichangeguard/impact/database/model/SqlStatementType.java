package com.aichangeguard.impact.database.model;

/**
 * Categorization of SQL statements discovered in migration scripts.
 */
public enum SqlStatementType {
    CREATE_TABLE,
    DROP_TABLE,
    ALTER_TABLE_ADD_COLUMN,
    ALTER_TABLE_DROP_COLUMN,
    ALTER_TABLE_ALTER_COLUMN_TYPE,
    ALTER_TABLE_SET_NOT_NULL,
    ALTER_TABLE_DROP_NOT_NULL,
    ALTER_TABLE_ADD_CONSTRAINT,
    ALTER_TABLE_DROP_CONSTRAINT,
    ALTER_TABLE_RENAME_COLUMN,
    ALTER_TABLE_RENAME_TABLE,
    CREATE_INDEX,
    DROP_INDEX,
    TRUNCATE_TABLE,
    DELETE_DML,
    UPDATE_DML,
    INSERT_DML,
    OTHER_DDL,
    UNSUPPORTED
}

