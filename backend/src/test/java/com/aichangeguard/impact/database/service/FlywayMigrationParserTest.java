package com.aichangeguard.impact.database.service;

import com.aichangeguard.impact.database.model.FlywayMigrationFile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FlywayMigrationParserTest {

    private FlywayMigrationParser parser;

    @BeforeEach
    void setUp() {
        parser = new FlywayMigrationParser(new SqlStatementParser());
    }

    @Test
    @DisplayName("Should parse standard versioned migration")
    void shouldParseVersionedMigration() {
        String sql = "CREATE TABLE users (id BIGINT PRIMARY KEY);";
        FlywayMigrationFile file = parser.parseContent("db/migration/V1__init_schema.sql", sql);

        assertThat(file.isValidFormat()).isTrue();
        assertThat(file.migrationType()).isEqualTo("VERSIONED");
        assertThat(file.version()).isEqualTo("1");
        assertThat(file.description()).isEqualTo("init schema");
        assertThat(file.totalStatements()).isEqualTo(1);
    }

    @Test
    @DisplayName("Should parse dotted versioned migration")
    void shouldParseDottedVersionedMigration() {
        String sql = "ALTER TABLE users ADD COLUMN age INT;";
        FlywayMigrationFile file = parser.parseContent("db/migration/V2.1.3__add_age.sql", sql);

        assertThat(file.isValidFormat()).isTrue();
        assertThat(file.version()).isEqualTo("2.1.3");
        assertThat(file.description()).isEqualTo("add age");
    }

    @Test
    @DisplayName("Should parse undo migration")
    void shouldParseUndoMigration() {
        String sql = "DROP TABLE users;";
        FlywayMigrationFile file = parser.parseContent("db/migration/U1__drop_users.sql", sql);

        assertThat(file.isValidFormat()).isTrue();
        assertThat(file.migrationType()).isEqualTo("UNDO");
        assertThat(file.version()).isEqualTo("1");
    }

    @Test
    @DisplayName("Should parse repeatable migration")
    void shouldParseRepeatableMigration() {
        String sql = "CREATE OR REPLACE VIEW user_summary AS SELECT id FROM users;";
        FlywayMigrationFile file = parser.parseContent("db/migration/R__user_summary_view.sql", sql);

        assertThat(file.isValidFormat()).isTrue();
        assertThat(file.migrationType()).isEqualTo("REPEATABLE");
        assertThat(file.description()).isEqualTo("user summary view");
    }

    @Test
    @DisplayName("Should reject invalid Flyway naming format")
    void shouldRejectInvalidNamingFormat() {
        FlywayMigrationFile file = parser.parseContent("db/migration/patch_script.sql", "SELECT 1;");

        assertThat(file.isValidFormat()).isFalse();
        assertThat(file.migrationType()).isEqualTo("INVALID_FORMAT");
        assertThat(file.parseError()).contains("does not match Flyway naming convention");
    }
}

