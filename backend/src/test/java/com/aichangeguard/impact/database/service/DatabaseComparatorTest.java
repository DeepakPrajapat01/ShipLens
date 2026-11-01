package com.aichangeguard.impact.database.service;

import com.aichangeguard.impact.database.model.DatabaseChangeFinding;
import com.aichangeguard.impact.database.model.DatabaseChangeSeverity;
import com.aichangeguard.impact.database.model.DatabaseChangeType;
import com.aichangeguard.impact.database.model.EvidenceCategory;
import com.aichangeguard.impact.database.model.FlywayMigrationFile;
import com.aichangeguard.impact.database.model.JpaColumn;
import com.aichangeguard.impact.database.model.JpaEntity;
import com.aichangeguard.impact.database.model.RepositoryMethod;
import com.aichangeguard.impact.database.model.SpringDataRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DatabaseComparatorTest {

    private DatabaseComparator comparator;
    private FlywayMigrationParser migrationParser;

    @BeforeEach
    void setUp() {
        comparator = new DatabaseComparator();
        migrationParser = new FlywayMigrationParser(new SqlStatementParser());
    }

    private JpaEntity entity(String name, String table, List<JpaColumn> columns) {
        return new JpaEntity(
                name, "com.example." + name, table, null, true,
                List.of("id"), columns, Collections.emptyList(), Collections.emptyList(),
                "User.java", 1
        );
    }

    private JpaColumn col(String name, String type, boolean nullable) {
        return new JpaColumn(name, name, type, null, nullable, false, "id".equals(name), null, 10);
    }

    @Nested
    @DisplayName("Entity comparison tests")
    class EntityComparisonTests {

        @Test
        @DisplayName("Should detect entity added as NON_BREAKING and removed as POTENTIALLY_BREAKING")
        void shouldDetectEntityAddAndRemove() {
            JpaEntity baseEntity = entity("OldEntity", "old_table", List.of(col("id", "Long", false)));
            JpaEntity targetEntity = entity("NewEntity", "new_table", List.of(col("id", "Long", false)));

            List<DatabaseChangeFinding> findings = comparator.compareEntities(
                    List.of(baseEntity), List.of(targetEntity)
            );

            assertThat(findings).hasSize(2);
            assertThat(findings).anyMatch(f -> f.changeType() == DatabaseChangeType.ENTITY_REMOVED
                    && f.severity() == DatabaseChangeSeverity.POTENTIALLY_BREAKING
                    && f.evidenceCategory() == EvidenceCategory.ENTITY_INFERRED);

            assertThat(findings).anyMatch(f -> f.changeType() == DatabaseChangeType.ENTITY_ADDED
                    && f.severity() == DatabaseChangeSeverity.NON_BREAKING);
        }

        @Test
        @DisplayName("Should detect column type change and nullability change")
        void shouldDetectColumnTypeAndNullabilityChange() {
            JpaEntity base = entity("User", "users", List.of(
                    col("id", "Long", false),
                    col("age", "Integer", true)
            ));
            JpaEntity target = entity("User", "users", List.of(
                    col("id", "Long", false),
                    col("age", "Long", false) // Type changed and became non-nullable
            ));

            List<DatabaseChangeFinding> findings = comparator.compareEntities(List.of(base), List.of(target));

            assertThat(findings).hasSize(2);
            assertThat(findings).anyMatch(f -> f.changeType() == DatabaseChangeType.ENTITY_COLUMN_TYPE_CHANGED
                    && f.severity() == DatabaseChangeSeverity.POTENTIALLY_BREAKING);

            assertThat(findings).anyMatch(f -> f.changeType() == DatabaseChangeType.ENTITY_COLUMN_NULLABILITY_CHANGED
                    && f.severity() == DatabaseChangeSeverity.POTENTIALLY_BREAKING);
        }
    }

    @Nested
    @DisplayName("Spring Data repository comparison tests")
    class RepositoryComparisonTests {

        @Test
        @DisplayName("Should detect query string changes and nativeQuery changes")
        void shouldDetectQueryStringChanges() {
            RepositoryMethod baseMethod = new RepositoryMethod(
                    "findUsers", "List<User>", Collections.emptyList(), "JPQL_QUERY",
                    "SELECT u FROM User u WHERE u.active = true", false, false, 10
            );
            RepositoryMethod targetMethod = new RepositoryMethod(
                    "findUsers", "List<User>", Collections.emptyList(), "NATIVE_QUERY",
                    "SELECT * FROM users WHERE active = 1", true, false, 10
            );

            SpringDataRepository base = new SpringDataRepository(
                    "UserRepository", "com.example.UserRepository", "User", "Long",
                    List.of(baseMethod), "UserRepository.java", 1
            );
            SpringDataRepository target = new SpringDataRepository(
                    "UserRepository", "com.example.UserRepository", "User", "Long",
                    List.of(targetMethod), "UserRepository.java", 1
            );

            List<DatabaseChangeFinding> findings = comparator.compareRepositories(List.of(base), List.of(target));

            assertThat(findings).hasSize(2);
            assertThat(findings).anyMatch(f -> f.changeType() == DatabaseChangeType.REPOSITORY_QUERY_CHANGED
                    && f.severity() == DatabaseChangeSeverity.POTENTIALLY_BREAKING
                    && f.evidenceCategory() == EvidenceCategory.REPOSITORY_QUERY);

            assertThat(findings).anyMatch(f -> f.changeType() == DatabaseChangeType.REPOSITORY_NATIVE_QUERY_FLAG_CHANGED
                    && f.severity() == DatabaseChangeSeverity.POTENTIALLY_BREAKING);
        }
    }

    @Nested
    @DisplayName("Flyway migration evaluation tests")
    class MigrationEvaluationTests {

        @Test
        @DisplayName("Should classify DROP TABLE and TRUNCATE as DESTRUCTIVE")
        void shouldClassifyDestructiveSql() {
            String sql = "DROP TABLE audit_logs;\nTRUNCATE TABLE session_store;";
            FlywayMigrationFile file = migrationParser.parseContent("db/migration/V2__cleanup.sql", sql);

            List<DatabaseChangeFinding> findings = comparator.evaluateMigrations(Collections.emptyList(), List.of(file));

            assertThat(findings).anyMatch(f -> f.changeType() == DatabaseChangeType.TABLE_DROPPED
                    && f.severity() == DatabaseChangeSeverity.DESTRUCTIVE
                    && f.evidenceCategory() == EvidenceCategory.MIGRATION_BASED);

            assertThat(findings).anyMatch(f -> f.changeType() == DatabaseChangeType.DESTRUCTIVE_DML_OPERATION
                    && f.severity() == DatabaseChangeSeverity.DESTRUCTIVE);
        }

        @Test
        @DisplayName("Should detect Flyway version collisions")
        void shouldDetectVersionCollisions() {
            FlywayMigrationFile file1 = migrationParser.parseContent("db/migration/V1__init_users.sql", "CREATE TABLE users (id INT);");
            FlywayMigrationFile file2 = migrationParser.parseContent("db/migration/V1__init_orders.sql", "CREATE TABLE orders (id INT);");

            List<DatabaseChangeFinding> findings = comparator.evaluateMigrations(Collections.emptyList(), List.of(file1, file2));

            assertThat(findings).anyMatch(f -> f.changeType() == DatabaseChangeType.MIGRATION_VERSION_CONFLICT
                    && f.severity() == DatabaseChangeSeverity.POTENTIALLY_BREAKING);
        }

        @Test
        @DisplayName("Should detect modified existing migrations")
        void shouldDetectModifiedMigrationFiles() {
            FlywayMigrationFile base = migrationParser.parseContent("db/migration/V1__init.sql", "CREATE TABLE users (id INT);");
            FlywayMigrationFile target = migrationParser.parseContent("db/migration/V1__init.sql", "CREATE TABLE users (id BIGINT, name TEXT);");

            List<DatabaseChangeFinding> findings = comparator.evaluateMigrations(List.of(base), List.of(target));

            assertThat(findings).anyMatch(f -> f.changeType() == DatabaseChangeType.MIGRATION_FILE_MODIFIED
                    && f.severity() == DatabaseChangeSeverity.POTENTIALLY_BREAKING
                    && f.evidenceCategory() == EvidenceCategory.MIGRATION_METADATA);
        }

        @Test
        @DisplayName("Should classify unsupported SQL as UNKNOWN")
        void shouldClassifyUnsupportedSqlAsUnknown() {
            String sql = "DO $$ BEGIN PERFORM 1; END $$;";
            FlywayMigrationFile file = migrationParser.parseContent("db/migration/V3__procedure.sql", sql);

            List<DatabaseChangeFinding> findings = comparator.evaluateMigrations(Collections.emptyList(), List.of(file));

            assertThat(findings).anyMatch(f -> f.changeType() == DatabaseChangeType.UNSUPPORTED_SQL_STATEMENT
                    && f.severity() == DatabaseChangeSeverity.UNKNOWN
                    && f.evidenceCategory() == EvidenceCategory.MIGRATION_BASED);
        }
    }
}

