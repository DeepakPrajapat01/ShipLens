package com.aichangeguard.impact.database.service;

import com.aichangeguard.impact.database.model.SqlStatementInfo;
import com.aichangeguard.impact.database.model.SqlStatementType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SqlStatementParserTest {

    private SqlStatementParser parser;

    @BeforeEach
    void setUp() {
        parser = new SqlStatementParser();
    }

    @Nested
    @DisplayName("DDL statement parsing")
    class DdlParsingTests {

        @Test
        @DisplayName("Should parse CREATE TABLE")
        void shouldParseCreateTable() {
            String sql = "CREATE TABLE users (id BIGINT PRIMARY KEY, name VARCHAR(255));";
            List<SqlStatementInfo> stmts = parser.parseScript(sql);

            assertThat(stmts).hasSize(1);
            SqlStatementInfo s = stmts.getFirst();
            assertThat(s.statementType()).isEqualTo(SqlStatementType.CREATE_TABLE);
            assertThat(s.targetTable()).isEqualTo("users");
            assertThat(s.isDestructive()).isFalse();
        }

        @Test
        @DisplayName("Should detect DROP TABLE as destructive")
        void shouldDetectDropTableAsDestructive() {
            String sql = "DROP TABLE IF EXISTS old_orders CASCADE;";
            List<SqlStatementInfo> stmts = parser.parseScript(sql);

            assertThat(stmts).hasSize(1);
            SqlStatementInfo s = stmts.getFirst();
            assertThat(s.statementType()).isEqualTo(SqlStatementType.DROP_TABLE);
            assertThat(s.targetTable()).isEqualTo("old_orders");
            assertThat(s.isDestructive()).isTrue();
            assertThat(s.destructiveReason()).contains("Table dropped");
        }

        @Test
        @DisplayName("Should detect TRUNCATE as destructive")
        void shouldDetectTruncateAsDestructive() {
            String sql = "TRUNCATE TABLE session_cache;";
            List<SqlStatementInfo> stmts = parser.parseScript(sql);

            assertThat(stmts).hasSize(1);
            SqlStatementInfo s = stmts.getFirst();
            assertThat(s.statementType()).isEqualTo(SqlStatementType.TRUNCATE_TABLE);
            assertThat(s.targetTable()).isEqualTo("session_cache");
            assertThat(s.isDestructive()).isTrue();
        }

        @Test
        @DisplayName("Should parse ALTER TABLE ADD COLUMN with NOT NULL without DEFAULT as potentially breaking")
        void shouldParseAddColumnNotNullWithoutDefault() {
            String sql = "ALTER TABLE users ADD COLUMN phone VARCHAR(20) NOT NULL;";
            List<SqlStatementInfo> stmts = parser.parseScript(sql);

            assertThat(stmts).hasSize(1);
            SqlStatementInfo s = stmts.getFirst();
            assertThat(s.statementType()).isEqualTo(SqlStatementType.ALTER_TABLE_ADD_COLUMN);
            assertThat(s.targetTable()).isEqualTo("users");
            assertThat(s.targetColumn()).isEqualTo("phone");
            assertThat(s.nullable()).isFalse();
            assertThat(s.defaultValue()).isNull();
            assertThat(s.isDestructive()).isTrue(); // Marked breaking
        }

        @Test
        @DisplayName("Should parse ALTER TABLE ADD COLUMN with DEFAULT as non-breaking")
        void shouldParseAddColumnWithDefault() {
            String sql = "ALTER TABLE users ADD COLUMN active BOOLEAN NOT NULL DEFAULT true;";
            List<SqlStatementInfo> stmts = parser.parseScript(sql);

            assertThat(stmts).hasSize(1);
            SqlStatementInfo s = stmts.getFirst();
            assertThat(s.statementType()).isEqualTo(SqlStatementType.ALTER_TABLE_ADD_COLUMN);
            assertThat(s.targetTable()).isEqualTo("users");
            assertThat(s.targetColumn()).isEqualTo("active");
            assertThat(s.nullable()).isFalse();
            assertThat(s.defaultValue()).isEqualTo("true");
            assertThat(s.isDestructive()).isFalse();
        }

        @Test
        @DisplayName("Should parse ALTER TABLE DROP COLUMN as destructive")
        void shouldParseDropColumn() {
            String sql = "ALTER TABLE users DROP COLUMN legacy_hash;";
            List<SqlStatementInfo> stmts = parser.parseScript(sql);

            assertThat(stmts).hasSize(1);
            SqlStatementInfo s = stmts.getFirst();
            assertThat(s.statementType()).isEqualTo(SqlStatementType.ALTER_TABLE_DROP_COLUMN);
            assertThat(s.targetTable()).isEqualTo("users");
            assertThat(s.targetColumn()).isEqualTo("legacy_hash");
            assertThat(s.isDestructive()).isTrue();
        }

        @Test
        @DisplayName("Should parse ALTER TABLE ALTER COLUMN TYPE")
        void shouldParseAlterColumnType() {
            String sql = "ALTER TABLE orders ALTER COLUMN amount TYPE NUMERIC(12,2);";
            List<SqlStatementInfo> stmts = parser.parseScript(sql);

            assertThat(stmts).hasSize(1);
            SqlStatementInfo s = stmts.getFirst();
            assertThat(s.statementType()).isEqualTo(SqlStatementType.ALTER_TABLE_ALTER_COLUMN_TYPE);
            assertThat(s.targetTable()).isEqualTo("orders");
            assertThat(s.targetColumn()).isEqualTo("amount");
            assertThat(s.dataType()).isEqualTo("NUMERIC(12,2)");
        }

        @Test
        @DisplayName("Should parse SET NOT NULL and DROP NOT NULL")
        void shouldParseSetAndDropNotNull() {
            String sql = "ALTER TABLE users ALTER COLUMN email SET NOT NULL;\n" +
                         "ALTER TABLE users ALTER COLUMN nickname DROP NOT NULL;";
            List<SqlStatementInfo> stmts = parser.parseScript(sql);

            assertThat(stmts).hasSize(2);
            assertThat(stmts.get(0).statementType()).isEqualTo(SqlStatementType.ALTER_TABLE_SET_NOT_NULL);
            assertThat(stmts.get(0).targetColumn()).isEqualTo("email");
            assertThat(stmts.get(0).isDestructive()).isTrue();

            assertThat(stmts.get(1).statementType()).isEqualTo(SqlStatementType.ALTER_TABLE_DROP_NOT_NULL);
            assertThat(stmts.get(1).targetColumn()).isEqualTo("nickname");
            assertThat(stmts.get(1).isDestructive()).isFalse();
        }

        @Test
        @DisplayName("Should parse CREATE INDEX and DROP INDEX")
        void shouldParseIndexOperations() {
            String sql = "CREATE INDEX idx_user_email ON users (email);\n" +
                         "DROP INDEX idx_old;";
            List<SqlStatementInfo> stmts = parser.parseScript(sql);

            assertThat(stmts).hasSize(2);
            assertThat(stmts.get(0).statementType()).isEqualTo(SqlStatementType.CREATE_INDEX);
            assertThat(stmts.get(0).indexName()).isEqualTo("idx_user_email");
            assertThat(stmts.get(0).targetTable()).isEqualTo("users");

            assertThat(stmts.get(1).statementType()).isEqualTo(SqlStatementType.DROP_INDEX);
            assertThat(stmts.get(1).indexName()).isEqualTo("idx_old");
        }

        @Test
        @DisplayName("Should parse column rename and table rename as destructive")
        void shouldParseRenameOperations() {
            String sql = "ALTER TABLE users RENAME COLUMN user_name TO username;\n" +
                         "ALTER TABLE accounts RENAME TO customer_accounts;";
            List<SqlStatementInfo> stmts = parser.parseScript(sql);

            assertThat(stmts).hasSize(2);
            assertThat(stmts.get(0).statementType()).isEqualTo(SqlStatementType.ALTER_TABLE_RENAME_COLUMN);
            assertThat(stmts.get(0).targetColumn()).isEqualTo("user_name");
            assertThat(stmts.get(0).defaultValue()).isEqualTo("username");
            assertThat(stmts.get(0).isDestructive()).isTrue();

            assertThat(stmts.get(1).statementType()).isEqualTo(SqlStatementType.ALTER_TABLE_RENAME_TABLE);
            assertThat(stmts.get(1).targetTable()).isEqualTo("accounts");
            assertThat(stmts.get(1).defaultValue()).isEqualTo("customer_accounts");
            assertThat(stmts.get(1).isDestructive()).isTrue();
        }
    }

    @Nested
    @DisplayName("DML statement parsing")
    class DmlParsingTests {

        @Test
        @DisplayName("Should detect unconditional DELETE as destructive")
        void shouldDetectUnconditionalDelete() {
            String sql = "DELETE FROM audit_logs;";
            List<SqlStatementInfo> stmts = parser.parseScript(sql);

            assertThat(stmts).hasSize(1);
            SqlStatementInfo s = stmts.getFirst();
            assertThat(s.statementType()).isEqualTo(SqlStatementType.DELETE_DML);
            assertThat(s.isDestructive()).isTrue();
        }

        @Test
        @DisplayName("Should parse conditional DELETE as non-destructive")
        void shouldParseConditionalDelete() {
            String sql = "DELETE FROM audit_logs WHERE created_at < NOW() - INTERVAL '30 days';";
            List<SqlStatementInfo> stmts = parser.parseScript(sql);

            assertThat(stmts).hasSize(1);
            SqlStatementInfo s = stmts.getFirst();
            assertThat(s.statementType()).isEqualTo(SqlStatementType.DELETE_DML);
            assertThat(s.isDestructive()).isFalse();
        }
    }

    @Nested
    @DisplayName("Unsupported SQL handling")
    class UnsupportedSqlTests {

        @Test
        @DisplayName("Should flag procedural block (DO $$) as UNSUPPORTED")
        void shouldFlagProceduralBlockAsUnsupported() {
            String sql = "DO $$ BEGIN RAISE NOTICE 'test'; END $$;";
            List<SqlStatementInfo> stmts = parser.parseScript(sql);

            assertThat(stmts).hasSize(1);
            assertThat(stmts.getFirst().statementType()).isEqualTo(SqlStatementType.UNSUPPORTED);
            assertThat(stmts.getFirst().unsupportedReason()).contains("Unsupported");
        }

        @Test
        @DisplayName("Should flag stored procedure as UNSUPPORTED")
        void shouldFlagStoredProcedureAsUnsupported() {
            String sql = "CREATE OR REPLACE FUNCTION audit_func() RETURNS trigger AS $$ BEGIN RETURN NEW; END; $$ LANGUAGE plpgsql;";
            List<SqlStatementInfo> stmts = parser.parseScript(sql);

            assertThat(stmts).hasSize(1);
            assertThat(stmts.getFirst().statementType()).isEqualTo(SqlStatementType.UNSUPPORTED);
        }
    }

    @Nested
    @DisplayName("Comments and line numbering")
    class CommentAndLineNumberTests {

        @Test
        @DisplayName("Should ignore comments and preserve statement line numbers")
        void shouldIgnoreCommentsAndPreserveLines() {
            String sql = "-- Migration initial\n" +
                         "/* multi-line\n" +
                         "   comment */\n" +
                         "CREATE TABLE users (id INT);\n" +
                         "\n" +
                         "-- next statement\n" +
                         "DROP TABLE old_data;";

            List<SqlStatementInfo> stmts = parser.parseScript(sql);

            assertThat(stmts).hasSize(2);
            assertThat(stmts.get(0).targetTable()).isEqualTo("users");
            assertThat(stmts.get(0).lineNumber()).isEqualTo(4);

            assertThat(stmts.get(1).targetTable()).isEqualTo("old_data");
            assertThat(stmts.get(1).lineNumber()).isEqualTo(7);
        }
    }
}

