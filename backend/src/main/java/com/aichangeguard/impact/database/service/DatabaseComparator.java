package com.aichangeguard.impact.database.service;

import com.aichangeguard.impact.database.model.DatabaseChangeFinding;
import com.aichangeguard.impact.database.model.DatabaseChangeSeverity;
import com.aichangeguard.impact.database.model.DatabaseChangeType;
import com.aichangeguard.impact.database.model.EvidenceCategory;
import com.aichangeguard.impact.database.model.FlywayMigrationFile;
import com.aichangeguard.impact.database.model.JpaColumn;
import com.aichangeguard.impact.database.model.JpaEntity;
import com.aichangeguard.impact.database.model.JpaRelationship;
import com.aichangeguard.impact.database.model.RepositoryMethod;
import com.aichangeguard.impact.database.model.SqlStatementInfo;
import com.aichangeguard.impact.database.model.SqlStatementType;
import com.aichangeguard.impact.database.model.SpringDataRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Compares database components (JPA entities, Spring Data repositories, Flyway migrations)
 * across revisions and generates classified, evidence-backed findings.
 */
@Service
public class DatabaseComparator {

    private static final Logger log = LoggerFactory.getLogger(DatabaseComparator.class);

    /**
     * Compares base and target JPA entities and generates inferred findings.
     */
    public List<DatabaseChangeFinding> compareEntities(List<JpaEntity> baseEntities, List<JpaEntity> targetEntities) {
        List<DatabaseChangeFinding> findings = new ArrayList<>();
        Map<String, JpaEntity> baseMap = indexEntities(safeList(baseEntities));
        Map<String, JpaEntity> targetMap = indexEntities(safeList(targetEntities));

        Set<String> allEntityNames = new HashSet<>();
        allEntityNames.addAll(baseMap.keySet());
        allEntityNames.addAll(targetMap.keySet());

        for (String entityName : allEntityNames) {
            JpaEntity base = baseMap.get(entityName);
            JpaEntity target = targetMap.get(entityName);

            if (base != null && target == null) {
                // Entity removed
                findings.add(new DatabaseChangeFinding(
                        DatabaseChangeType.ENTITY_REMOVED,
                        DatabaseChangeSeverity.POTENTIALLY_BREAKING,
                        EvidenceCategory.ENTITY_INFERRED,
                        "JPA Entity removed: " + entityName,
                        "Entity '" + entityName + "' mapped to table '" + base.tableName() + "' was removed from application code.",
                        base.tableName(),
                        null,
                        entityName,
                        null,
                        base.sourceFile(),
                        base.sourceLine(),
                        "Entity: " + base.qualifiedName(),
                        "Verify whether table '" + base.tableName() + "' should be retired or maintained."
                ));
            } else if (base == null && target != null) {
                // Entity added
                findings.add(new DatabaseChangeFinding(
                        DatabaseChangeType.ENTITY_ADDED,
                        DatabaseChangeSeverity.NON_BREAKING,
                        EvidenceCategory.ENTITY_INFERRED,
                        "JPA Entity added: " + entityName,
                        "New entity '" + entityName + "' mapped to table '" + target.tableName() + "' added.",
                        target.tableName(),
                        null,
                        null,
                        entityName,
                        target.sourceFile(),
                        target.sourceLine(),
                        "Entity: " + target.qualifiedName(),
                        "Ensure a corresponding migration exists to create table '" + target.tableName() + "' if not auto-generated."
                ));
            } else if (base != null && target != null) {
                // Both exist — compare details
                findings.addAll(compareMatchedEntity(base, target));
            }
        }

        return findings;
    }

    private List<DatabaseChangeFinding> compareMatchedEntity(JpaEntity base, JpaEntity target) {
        List<DatabaseChangeFinding> findings = new ArrayList<>();

        // Table mapping change
        if (!Objects.equals(base.tableName(), target.tableName())) {
            findings.add(new DatabaseChangeFinding(
                    DatabaseChangeType.ENTITY_TABLE_MAPPING_CHANGED,
                    DatabaseChangeSeverity.POTENTIALLY_BREAKING,
                    EvidenceCategory.ENTITY_INFERRED,
                    "Entity table mapping changed for " + target.entityName(),
                    "Table mapping changed from '" + base.tableName() + "' to '" + target.tableName() + "'.",
                    target.tableName(),
                    null,
                    base.tableName(),
                    target.tableName(),
                    target.sourceFile(),
                    target.sourceLine(),
                    "@Table(name = \"" + target.tableName() + "\")",
                    "Update database table names or migration scripts accordingly."
            ));
        }

        // Compare columns
        Map<String, JpaColumn> baseCols = indexColumns(safeList(base.columns()));
        Map<String, JpaColumn> targetCols = indexColumns(safeList(target.columns()));

        Set<String> allCols = new HashSet<>();
        allCols.addAll(baseCols.keySet());
        allCols.addAll(targetCols.keySet());

        for (String colName : allCols) {
            JpaColumn bc = baseCols.get(colName);
            JpaColumn tc = targetCols.get(colName);

            if (bc != null && tc == null) {
                // Column removed in entity
                findings.add(new DatabaseChangeFinding(
                        DatabaseChangeType.ENTITY_COLUMN_REMOVED,
                        DatabaseChangeSeverity.POTENTIALLY_BREAKING,
                        EvidenceCategory.ENTITY_INFERRED,
                        "Column removed in entity: " + target.entityName() + "." + colName,
                        "Column '" + colName + "' was removed from entity '" + target.entityName() + "'.",
                        target.tableName(),
                        colName,
                        bc.javaType(),
                        null,
                        target.sourceFile(),
                        target.sourceLine(),
                        "Field: " + bc.fieldName(),
                        "Ensure database column removal is safely coordinated via migration."
                ));
            } else if (bc == null && tc != null) {
                // Column added in entity
                DatabaseChangeSeverity severity = !tc.nullable()
                        ? DatabaseChangeSeverity.POTENTIALLY_BREAKING
                        : DatabaseChangeSeverity.NON_BREAKING;
                String desc = tc.nullable()
                        ? "Nullable column '" + colName + "' added to entity '" + target.entityName() + "'."
                        : "Non-nullable column '" + colName + "' added to entity '" + target.entityName() + "'. May fail if database rows exist without default.";

                findings.add(new DatabaseChangeFinding(
                        DatabaseChangeType.ENTITY_COLUMN_ADDED,
                        severity,
                        EvidenceCategory.ENTITY_INFERRED,
                        "Column added to entity: " + target.entityName() + "." + colName,
                        desc,
                        target.tableName(),
                        colName,
                        null,
                        tc.javaType(),
                        target.sourceFile(),
                        tc.sourceLine(),
                        "Field: " + tc.fieldName() + " (" + tc.javaType() + ")",
                        !tc.nullable() ? "Ensure new non-null column has database DEFAULT or migration backfill." : null
                ));
            } else if (bc != null && tc != null) {
                // Type change
                if (!Objects.equals(bc.javaType(), tc.javaType())) {
                    findings.add(new DatabaseChangeFinding(
                            DatabaseChangeType.ENTITY_COLUMN_TYPE_CHANGED,
                            DatabaseChangeSeverity.POTENTIALLY_BREAKING,
                            EvidenceCategory.ENTITY_INFERRED,
                            "Column type changed in entity: " + target.entityName() + "." + colName,
                            "Type changed from '" + bc.javaType() + "' to '" + tc.javaType() + "'.",
                            target.tableName(),
                            colName,
                            bc.javaType(),
                            tc.javaType(),
                            target.sourceFile(),
                            tc.sourceLine(),
                            tc.fieldName() + ": " + bc.javaType() + " -> " + tc.javaType(),
                            "Verify column type compatibility in database."
                    ));
                }

                // Nullability change
                if (bc.nullable() != tc.nullable()) {
                    DatabaseChangeSeverity severity = !tc.nullable()
                            ? DatabaseChangeSeverity.POTENTIALLY_BREAKING
                            : DatabaseChangeSeverity.NON_BREAKING;
                    findings.add(new DatabaseChangeFinding(
                            DatabaseChangeType.ENTITY_COLUMN_NULLABILITY_CHANGED,
                            severity,
                            EvidenceCategory.ENTITY_INFERRED,
                            "Column nullability changed in entity: " + target.entityName() + "." + colName,
                            "Nullability changed from " + (bc.nullable() ? "nullable" : "not null") +
                                    " to " + (tc.nullable() ? "nullable" : "not null") + ".",
                            target.tableName(),
                            colName,
                            String.valueOf(bc.nullable()),
                            String.valueOf(tc.nullable()),
                            target.sourceFile(),
                            tc.sourceLine(),
                            "nullable = " + tc.nullable(),
                            !tc.nullable() ? "Backfill null database records before making column non-nullable." : null
                    ));
                }
            }
        }

        // Compare relationships
        Map<String, JpaRelationship> baseRels = indexRelationships(safeList(base.relationships()));
        Map<String, JpaRelationship> targetRels = indexRelationships(safeList(target.relationships()));

        for (String relField : targetRels.keySet()) {
            JpaRelationship tr = targetRels.get(relField);
            JpaRelationship br = baseRels.get(relField);
            if (br == null) {
                findings.add(new DatabaseChangeFinding(
                        DatabaseChangeType.ENTITY_RELATIONSHIP_CHANGED,
                        DatabaseChangeSeverity.NON_BREAKING,
                        EvidenceCategory.ENTITY_INFERRED,
                        "Relationship added: " + target.entityName() + "." + relField,
                        "Added " + tr.relationshipType() + " relationship to " + tr.targetEntity() + ".",
                        target.tableName(),
                        tr.joinColumn(),
                        null,
                        tr.relationshipType() + " -> " + tr.targetEntity(),
                        target.sourceFile(),
                        tr.sourceLine(),
                        "@" + tr.relationshipType() + " " + tr.targetEntity() + " " + relField,
                        null
                ));
            } else if (!Objects.equals(br.relationshipType(), tr.relationshipType())) {
                findings.add(new DatabaseChangeFinding(
                        DatabaseChangeType.ENTITY_RELATIONSHIP_CHANGED,
                        DatabaseChangeSeverity.POTENTIALLY_BREAKING,
                        EvidenceCategory.ENTITY_INFERRED,
                        "Relationship type changed: " + target.entityName() + "." + relField,
                        "Relationship type changed from " + br.relationshipType() + " to " + tr.relationshipType() + ".",
                        target.tableName(),
                        tr.joinColumn(),
                        br.relationshipType(),
                        tr.relationshipType(),
                        target.sourceFile(),
                        tr.sourceLine(),
                        br.relationshipType() + " -> " + tr.relationshipType(),
                        "Verify foreign key constraints in schema."
                ));
            }
        }

        return findings;
    }

    /**
     * Compares Spring Data repositories and queries across revisions.
     */
    public List<DatabaseChangeFinding> compareRepositories(List<SpringDataRepository> baseRepos, List<SpringDataRepository> targetRepos) {
        List<DatabaseChangeFinding> findings = new ArrayList<>();
        Map<String, SpringDataRepository> baseMap = indexRepos(safeList(baseRepos));
        Map<String, SpringDataRepository> targetMap = indexRepos(safeList(targetRepos));

        Set<String> allRepos = new HashSet<>();
        allRepos.addAll(baseMap.keySet());
        allRepos.addAll(targetMap.keySet());

        for (String repoName : allRepos) {
            SpringDataRepository base = baseMap.get(repoName);
            SpringDataRepository target = targetMap.get(repoName);

            if (base != null && target == null) {
                findings.add(new DatabaseChangeFinding(
                        DatabaseChangeType.REPOSITORY_REMOVED,
                        DatabaseChangeSeverity.POTENTIALLY_BREAKING,
                        EvidenceCategory.REPOSITORY_QUERY,
                        "Spring Data repository removed: " + repoName,
                        "Repository interface '" + repoName + "' was removed.",
                        base.domainEntity(),
                        null,
                        repoName,
                        null,
                        base.sourceFile(),
                        base.sourceLine(),
                        "Interface: " + base.qualifiedName(),
                        "Check for broken references in service layers."
                ));
            } else if (base == null && target != null) {
                findings.add(new DatabaseChangeFinding(
                        DatabaseChangeType.REPOSITORY_ADDED,
                        DatabaseChangeSeverity.NON_BREAKING,
                        EvidenceCategory.REPOSITORY_QUERY,
                        "Spring Data repository added: " + repoName,
                        "Repository interface '" + repoName + "' added for domain entity " + target.domainEntity() + ".",
                        target.domainEntity(),
                        null,
                        null,
                        repoName,
                        target.sourceFile(),
                        target.sourceLine(),
                        "Interface: " + target.qualifiedName(),
                        null
                ));
            } else if (base != null && target != null) {
                findings.addAll(compareMatchedRepository(base, target));
            }
        }

        return findings;
    }

    private List<DatabaseChangeFinding> compareMatchedRepository(SpringDataRepository base, SpringDataRepository target) {
        List<DatabaseChangeFinding> findings = new ArrayList<>();

        Map<String, RepositoryMethod> baseMethods = indexMethods(safeList(base.methods()));
        Map<String, RepositoryMethod> targetMethods = indexMethods(safeList(target.methods()));

        for (String mName : baseMethods.keySet()) {
            if (!targetMethods.containsKey(mName)) {
                RepositoryMethod bm = baseMethods.get(mName);
                findings.add(new DatabaseChangeFinding(
                        DatabaseChangeType.REPOSITORY_METHOD_REMOVED,
                        DatabaseChangeSeverity.POTENTIALLY_BREAKING,
                        EvidenceCategory.REPOSITORY_QUERY,
                        "Repository method removed: " + target.interfaceName() + "." + mName,
                        "Method '" + mName + "' was removed from " + target.interfaceName() + ".",
                        target.domainEntity(),
                        null,
                        bm.returnType() + " " + mName + "()",
                        null,
                        target.sourceFile(),
                        target.sourceLine(),
                        bm.queryString() != null ? bm.queryString() : bm.methodName(),
                        "Verify whether caller services still expect this query method."
                ));
            }
        }

        for (String mName : targetMethods.keySet()) {
            RepositoryMethod tm = targetMethods.get(mName);
            RepositoryMethod bm = baseMethods.get(mName);

            if (bm == null) {
                findings.add(new DatabaseChangeFinding(
                        DatabaseChangeType.REPOSITORY_METHOD_ADDED,
                        DatabaseChangeSeverity.NON_BREAKING,
                        EvidenceCategory.REPOSITORY_QUERY,
                        "Repository method added: " + target.interfaceName() + "." + mName,
                        "Method '" + mName + "' added to " + target.interfaceName() + ".",
                        target.domainEntity(),
                        null,
                        null,
                        tm.returnType() + " " + mName + "()",
                        target.sourceFile(),
                        tm.sourceLine(),
                        tm.queryString() != null ? tm.queryString() : tm.methodName(),
                        null
                ));
            } else {
                // Compare queries
                if (!Objects.equals(bm.queryString(), tm.queryString())) {
                    findings.add(new DatabaseChangeFinding(
                            DatabaseChangeType.REPOSITORY_QUERY_CHANGED,
                            DatabaseChangeSeverity.POTENTIALLY_BREAKING,
                            EvidenceCategory.REPOSITORY_QUERY,
                            "Repository query string changed: " + target.interfaceName() + "." + mName,
                            "Query string modified in method '" + mName + "'.",
                            target.domainEntity(),
                            null,
                            bm.queryString(),
                            tm.queryString(),
                            target.sourceFile(),
                            tm.sourceLine(),
                            "@Query(\"" + tm.queryString() + "\")",
                            "Verify query syntax and indexing for performance."
                    ));
                }

                if (bm.isNative() != tm.isNative()) {
                    findings.add(new DatabaseChangeFinding(
                            DatabaseChangeType.REPOSITORY_NATIVE_QUERY_FLAG_CHANGED,
                            DatabaseChangeSeverity.POTENTIALLY_BREAKING,
                            EvidenceCategory.REPOSITORY_QUERY,
                            "Repository nativeQuery flag changed: " + target.interfaceName() + "." + mName,
                            "nativeQuery changed from " + bm.isNative() + " to " + tm.isNative() + ".",
                            target.domainEntity(),
                            null,
                            String.valueOf(bm.isNative()),
                            String.valueOf(tm.isNative()),
                            target.sourceFile(),
                            tm.sourceLine(),
                            "nativeQuery = " + tm.isNative(),
                            "Native SQL bypasses JPQL type checking."
                    ));
                }
            }
        }

        return findings;
    }

    /**
     * Evaluates Flyway migration files across revisions or inspects new migration files.
     */
    public List<DatabaseChangeFinding> evaluateMigrations(List<FlywayMigrationFile> baseMigrations,
                                                           List<FlywayMigrationFile> targetMigrations) {
        List<DatabaseChangeFinding> findings = new ArrayList<>();

        Map<String, FlywayMigrationFile> baseMap = indexMigrations(safeList(baseMigrations));
        Map<String, FlywayMigrationFile> targetMap = indexMigrations(safeList(targetMigrations));

        // Check for version collisions in target
        Map<String, List<FlywayMigrationFile>> versionMap = new HashMap<>();
        for (FlywayMigrationFile file : safeList(targetMigrations)) {
            if (file.version() != null) {
                versionMap.computeIfAbsent(file.version(), k -> new ArrayList<>()).add(file);
            }
        }

        for (Map.Entry<String, List<FlywayMigrationFile>> entry : versionMap.entrySet()) {
            if (entry.getValue().size() > 1) {
                List<String> files = entry.getValue().stream().map(FlywayMigrationFile::fileName).toList();
                findings.add(new DatabaseChangeFinding(
                        DatabaseChangeType.MIGRATION_VERSION_CONFLICT,
                        DatabaseChangeSeverity.POTENTIALLY_BREAKING,
                        EvidenceCategory.MIGRATION_METADATA,
                        "Flyway version collision: V" + entry.getKey(),
                        "Multiple migration files share version '" + entry.getKey() + "': " + files + ". Flyway startup will fail.",
                        null,
                        null,
                        null,
                        entry.getKey(),
                        entry.getValue().getFirst().relativePath(),
                        1,
                        "Conflicting files: " + files,
                        "Assign distinct version numbers to each migration script."
                ));
            }
        }

        // Identify new or modified migration files
        for (FlywayMigrationFile targetFile : safeList(targetMigrations)) {
            FlywayMigrationFile baseFile = baseMap.get(targetFile.fileName());

            if (baseFile == null) {
                // New migration file added
                findings.add(new DatabaseChangeFinding(
                        DatabaseChangeType.NEW_MIGRATION_FILE,
                        DatabaseChangeSeverity.INFORMATIONAL,
                        EvidenceCategory.MIGRATION_METADATA,
                        "New Flyway migration file: " + targetFile.fileName(),
                        "Discovered new migration file '" + targetFile.fileName() + "' with " + targetFile.totalStatements() + " statement(s).",
                        null,
                        null,
                        null,
                        targetFile.fileName(),
                        targetFile.relativePath(),
                        1,
                        "File: " + targetFile.relativePath(),
                        null
                ));

                // Analyze statements inside the new migration
                for (SqlStatementInfo stmt : safeList(targetFile.statements())) {
                    findings.addAll(analyzeStatement(stmt, targetFile.relativePath()));
                }
            } else {
                // Check if existing migration was modified! (Flyway checksum failure risk)
                if (targetFile.statements().size() != baseFile.statements().size() ||
                        !targetFile.statements().equals(baseFile.statements())) {
                    findings.add(new DatabaseChangeFinding(
                            DatabaseChangeType.MIGRATION_FILE_MODIFIED,
                            DatabaseChangeSeverity.POTENTIALLY_BREAKING,
                            EvidenceCategory.MIGRATION_METADATA,
                            "Released Flyway migration modified: " + targetFile.fileName(),
                            "Migration file '" + targetFile.fileName() + "' has been modified. Flyway validates checksums and will reject modified migrations on startup.",
                            null,
                            null,
                            "statements=" + baseFile.totalStatements(),
                            "statements=" + targetFile.totalStatements(),
                            targetFile.relativePath(),
                            1,
                            "Checksum mismatch risk for " + targetFile.fileName(),
                            "Do not modify released migrations. Create a new forward migration script."
                    ));
                }
            }
        }

        return findings;
    }

    private List<DatabaseChangeFinding> analyzeStatement(SqlStatementInfo stmt, String sourceFile) {
        List<DatabaseChangeFinding> findings = new ArrayList<>();

        if (stmt.statementType() == SqlStatementType.UNSUPPORTED) {
            findings.add(new DatabaseChangeFinding(
                    DatabaseChangeType.UNSUPPORTED_SQL_STATEMENT,
                    DatabaseChangeSeverity.UNKNOWN,
                    EvidenceCategory.MIGRATION_BASED,
                    "Unsupported SQL construct in migration",
                    "Statement could not be parsed deterministically: " + stmt.unsupportedReason() + ". Static risk cannot be guaranteed.",
                    stmt.targetTable(),
                    null,
                    null,
                    stmt.rawSql(),
                    sourceFile,
                    stmt.lineNumber(),
                    stmt.rawSql(),
                    "Manually review procedural or vendor-specific SQL scripts."
            ));
            return findings;
        }

        switch (stmt.statementType()) {
            case DROP_TABLE -> findings.add(new DatabaseChangeFinding(
                    DatabaseChangeType.TABLE_DROPPED,
                    DatabaseChangeSeverity.DESTRUCTIVE,
                    EvidenceCategory.MIGRATION_BASED,
                    "Destructive SQL: DROP TABLE " + stmt.targetTable(),
                    "Table '" + stmt.targetTable() + "' will be dropped permanently. Irreversible data loss.",
                    stmt.targetTable(),
                    null,
                    stmt.targetTable(),
                    null,
                    sourceFile,
                    stmt.lineNumber(),
                    stmt.rawSql(),
                    "Confirm backup procedures before dropping tables."
            ));
            case CREATE_TABLE -> findings.add(new DatabaseChangeFinding(
                    DatabaseChangeType.TABLE_CREATED,
                    DatabaseChangeSeverity.NON_BREAKING,
                    EvidenceCategory.MIGRATION_BASED,
                    "Table created: " + stmt.targetTable(),
                    "New table '" + stmt.targetTable() + "' created.",
                    stmt.targetTable(),
                    null,
                    null,
                    stmt.targetTable(),
                    sourceFile,
                    stmt.lineNumber(),
                    stmt.rawSql(),
                    null
            ));
            case TRUNCATE_TABLE -> findings.add(new DatabaseChangeFinding(
                    DatabaseChangeType.DESTRUCTIVE_DML_OPERATION,
                    DatabaseChangeSeverity.DESTRUCTIVE,
                    EvidenceCategory.MIGRATION_BASED,
                    "Destructive SQL: TRUNCATE " + stmt.targetTable(),
                    "All rows in table '" + stmt.targetTable() + "' will be truncated. Irreversible data loss.",
                    stmt.targetTable(),
                    null,
                    stmt.targetTable(),
                    null,
                    sourceFile,
                    stmt.lineNumber(),
                    stmt.rawSql(),
                    "Avoid TRUNCATE in forward migrations."
            ));
            case ALTER_TABLE_DROP_COLUMN -> findings.add(new DatabaseChangeFinding(
                    DatabaseChangeType.COLUMN_DROPPED,
                    DatabaseChangeSeverity.DESTRUCTIVE,
                    EvidenceCategory.MIGRATION_BASED,
                    "Destructive SQL: DROP COLUMN " + stmt.targetTable() + "." + stmt.targetColumn(),
                    "Column '" + stmt.targetColumn() + "' in table '" + stmt.targetTable() + "' will be dropped permanently.",
                    stmt.targetTable(),
                    stmt.targetColumn(),
                    stmt.targetColumn(),
                    null,
                    sourceFile,
                    stmt.lineNumber(),
                    stmt.rawSql(),
                    "Deprecate column usage before dropping in production."
            ));
            case ALTER_TABLE_ADD_COLUMN -> {
                DatabaseChangeSeverity sev = Boolean.FALSE.equals(stmt.nullable()) && stmt.defaultValue() == null
                        ? DatabaseChangeSeverity.POTENTIALLY_BREAKING
                        : DatabaseChangeSeverity.NON_BREAKING;
                String desc = sev == DatabaseChangeSeverity.POTENTIALLY_BREAKING
                        ? "Column '" + stmt.targetColumn() + "' added as NOT NULL without DEFAULT on table '" + stmt.targetTable() + "'. Will fail if existing rows exist."
                        : "Column '" + stmt.targetColumn() + "' (" + stmt.dataType() + ") added to table '" + stmt.targetTable() + "'.";
                findings.add(new DatabaseChangeFinding(
                        DatabaseChangeType.COLUMN_ADDED,
                        sev,
                        EvidenceCategory.MIGRATION_BASED,
                        "Column added: " + stmt.targetTable() + "." + stmt.targetColumn(),
                        desc,
                        stmt.targetTable(),
                        stmt.targetColumn(),
                        null,
                        stmt.dataType(),
                        sourceFile,
                        stmt.lineNumber(),
                        stmt.rawSql(),
                        sev == DatabaseChangeSeverity.POTENTIALLY_BREAKING ? "Add DEFAULT or make column NULLABLE during initial migration." : null
                ));
            }
            case ALTER_TABLE_ALTER_COLUMN_TYPE -> findings.add(new DatabaseChangeFinding(
                    DatabaseChangeType.COLUMN_TYPE_CHANGED,
                    DatabaseChangeSeverity.POTENTIALLY_BREAKING,
                    EvidenceCategory.MIGRATION_BASED,
                    "Column type altered: " + stmt.targetTable() + "." + stmt.targetColumn(),
                    "Column '" + stmt.targetColumn() + "' altered to type '" + stmt.dataType() + "'. Potential cast error for existing rows.",
                    stmt.targetTable(),
                    stmt.targetColumn(),
                    null,
                    stmt.dataType(),
                    sourceFile,
                    stmt.lineNumber(),
                    stmt.rawSql(),
                    "Ensure explicit USING clause or data conversion script is provided."
            ));
            case ALTER_TABLE_SET_NOT_NULL -> findings.add(new DatabaseChangeFinding(
                    DatabaseChangeType.COLUMN_NULLABILITY_CHANGED,
                    DatabaseChangeSeverity.POTENTIALLY_BREAKING,
                    EvidenceCategory.MIGRATION_BASED,
                    "Column set NOT NULL: " + stmt.targetTable() + "." + stmt.targetColumn(),
                    "Column '" + stmt.targetColumn() + "' altered to NOT NULL. Migration will fail if table contains NULL rows.",
                    stmt.targetTable(),
                    stmt.targetColumn(),
                    "NULL",
                    "NOT NULL",
                    sourceFile,
                    stmt.lineNumber(),
                    stmt.rawSql(),
                    "Backfill existing NULL values prior to applying NOT NULL constraint."
            ));
            case ALTER_TABLE_DROP_NOT_NULL -> findings.add(new DatabaseChangeFinding(
                    DatabaseChangeType.COLUMN_NULLABILITY_CHANGED,
                    DatabaseChangeSeverity.NON_BREAKING,
                    EvidenceCategory.MIGRATION_BASED,
                    "Column NOT NULL constraint dropped: " + stmt.targetTable() + "." + stmt.targetColumn(),
                    "Column '" + stmt.targetColumn() + "' relaxed to allow NULL values.",
                    stmt.targetTable(),
                    stmt.targetColumn(),
                    "NOT NULL",
                    "NULL",
                    sourceFile,
                    stmt.lineNumber(),
                    stmt.rawSql(),
                    null
            ));
            case ALTER_TABLE_ADD_CONSTRAINT -> findings.add(new DatabaseChangeFinding(
                    DatabaseChangeType.UNIQUE_CONSTRAINT_ADDED,
                    DatabaseChangeSeverity.POTENTIALLY_BREAKING,
                    EvidenceCategory.MIGRATION_BASED,
                    "Constraint added: " + stmt.targetTable() + " (" + stmt.constraintName() + ")",
                    "Constraint '" + stmt.constraintName() + "' added to '" + stmt.targetTable() + "'. Will fail if duplicate data exists.",
                    stmt.targetTable(),
                    null,
                    null,
                    stmt.constraintName(),
                    sourceFile,
                    stmt.lineNumber(),
                    stmt.rawSql(),
                    "Deduplicate records before applying unique constraint."
            ));
            case ALTER_TABLE_DROP_CONSTRAINT -> findings.add(new DatabaseChangeFinding(
                    DatabaseChangeType.UNIQUE_CONSTRAINT_REMOVED,
                    DatabaseChangeSeverity.POTENTIALLY_BREAKING,
                    EvidenceCategory.MIGRATION_BASED,
                    "Constraint dropped: " + stmt.targetTable() + " (" + stmt.constraintName() + ")",
                    "Constraint '" + stmt.constraintName() + "' dropped from '" + stmt.targetTable() + "'. Relaxes schema guarantee.",
                    stmt.targetTable(),
                    null,
                    stmt.constraintName(),
                    null,
                    sourceFile,
                    stmt.lineNumber(),
                    stmt.rawSql(),
                    null
            ));
            case ALTER_TABLE_RENAME_COLUMN -> findings.add(new DatabaseChangeFinding(
                    DatabaseChangeType.TABLE_RENAMED,
                    DatabaseChangeSeverity.DESTRUCTIVE,
                    EvidenceCategory.MIGRATION_BASED,
                    "Column renamed: " + stmt.targetTable() + "." + stmt.targetColumn() + " -> " + stmt.defaultValue(),
                    "Column renamed. Breaks existing queries and applications referring to old column name.",
                    stmt.targetTable(),
                    stmt.targetColumn(),
                    stmt.targetColumn(),
                    stmt.defaultValue(),
                    sourceFile,
                    stmt.lineNumber(),
                    stmt.rawSql(),
                    "Use expand-contract migration pattern (add new column, copy, deprecate old)."
            ));
            case ALTER_TABLE_RENAME_TABLE -> findings.add(new DatabaseChangeFinding(
                    DatabaseChangeType.TABLE_RENAMED,
                    DatabaseChangeSeverity.DESTRUCTIVE,
                    EvidenceCategory.MIGRATION_BASED,
                    "Table renamed: " + stmt.targetTable() + " -> " + stmt.defaultValue(),
                    "Table renamed. Breaks existing queries and applications referring to old table name.",
                    stmt.targetTable(),
                    null,
                    stmt.targetTable(),
                    stmt.defaultValue(),
                    sourceFile,
                    stmt.lineNumber(),
                    stmt.rawSql(),
                    "Use expand-contract migration pattern."
            ));
            case CREATE_INDEX -> findings.add(new DatabaseChangeFinding(
                    DatabaseChangeType.INDEX_ADDED,
                    DatabaseChangeSeverity.NON_BREAKING,
                    EvidenceCategory.MIGRATION_BASED,
                    "Index created: " + stmt.indexName() + " on " + stmt.targetTable(),
                    "Index '" + stmt.indexName() + "' created.",
                    stmt.targetTable(),
                    null,
                    null,
                    stmt.indexName(),
                    sourceFile,
                    stmt.lineNumber(),
                    stmt.rawSql(),
                    null
            ));
            case DROP_INDEX -> findings.add(new DatabaseChangeFinding(
                    DatabaseChangeType.INDEX_DROPPED,
                    DatabaseChangeSeverity.POTENTIALLY_BREAKING,
                    EvidenceCategory.MIGRATION_BASED,
                    "Index dropped: " + stmt.indexName(),
                    "Index '" + stmt.indexName() + "' dropped. May cause performance regressions.",
                    stmt.targetTable(),
                    null,
                    stmt.indexName(),
                    null,
                    sourceFile,
                    stmt.lineNumber(),
                    stmt.rawSql(),
                    "Verify query execution plans without this index."
            ));
            case DELETE_DML -> {
                if (stmt.isDestructive()) {
                    findings.add(new DatabaseChangeFinding(
                            DatabaseChangeType.DESTRUCTIVE_DML_OPERATION,
                            DatabaseChangeSeverity.DESTRUCTIVE,
                            EvidenceCategory.MIGRATION_BASED,
                            "Destructive SQL: Unconditional DELETE on " + stmt.targetTable(),
                            "Unconditional DELETE statement will wipe all data from '" + stmt.targetTable() + "'.",
                            stmt.targetTable(),
                            null,
                            stmt.targetTable(),
                            null,
                            sourceFile,
                            stmt.lineNumber(),
                            stmt.rawSql(),
                            "Add explicit WHERE clause to DELETE statements."
                    ));
                }
            }
            case UPDATE_DML -> {
                if (stmt.isDestructive()) {
                    findings.add(new DatabaseChangeFinding(
                            DatabaseChangeType.DESTRUCTIVE_DML_OPERATION,
                            DatabaseChangeSeverity.DESTRUCTIVE,
                            EvidenceCategory.MIGRATION_BASED,
                            "Destructive SQL: Unconditional UPDATE on " + stmt.targetTable(),
                            "Unconditional UPDATE statement modifies all rows in '" + stmt.targetTable() + "'.",
                            stmt.targetTable(),
                            null,
                            stmt.targetTable(),
                            null,
                            sourceFile,
                            stmt.lineNumber(),
                            stmt.rawSql(),
                            "Add explicit WHERE clause to UPDATE statements."
                    ));
                }
            }
            default -> {}
        }

        return findings;
    }

    private Map<String, JpaEntity> indexEntities(List<JpaEntity> entities) {
        Map<String, JpaEntity> map = new HashMap<>();
        for (JpaEntity e : entities) {
            map.put(e.entityName(), e);
        }
        return map;
    }

    private Map<String, JpaColumn> indexColumns(List<JpaColumn> columns) {
        Map<String, JpaColumn> map = new HashMap<>();
        for (JpaColumn c : columns) {
            map.put(c.columnName(), c);
        }
        return map;
    }

    private Map<String, JpaRelationship> indexRelationships(List<JpaRelationship> rels) {
        Map<String, JpaRelationship> map = new HashMap<>();
        for (JpaRelationship r : rels) {
            map.put(r.fieldName(), r);
        }
        return map;
    }

    private Map<String, SpringDataRepository> indexRepos(List<SpringDataRepository> repos) {
        Map<String, SpringDataRepository> map = new HashMap<>();
        for (SpringDataRepository r : repos) {
            map.put(r.interfaceName(), r);
        }
        return map;
    }

    private Map<String, RepositoryMethod> indexMethods(List<RepositoryMethod> methods) {
        Map<String, RepositoryMethod> map = new HashMap<>();
        for (RepositoryMethod m : methods) {
            map.put(m.methodName(), m);
        }
        return map;
    }

    private Map<String, FlywayMigrationFile> indexMigrations(List<FlywayMigrationFile> migrations) {
        Map<String, FlywayMigrationFile> map = new HashMap<>();
        for (FlywayMigrationFile f : migrations) {
            map.put(f.fileName(), f);
        }
        return map;
    }

    private <T> List<T> safeList(List<T> list) {
        return list != null ? list : Collections.emptyList();
    }
}

