package com.aichangeguard.impact.database.service;

import com.aichangeguard.analysis.model.AnnotationMetadata;
import com.aichangeguard.analysis.model.FieldMetadata;
import com.aichangeguard.analysis.model.ParsedFileMetadata;
import com.aichangeguard.analysis.model.SourceAnalysisReport;
import com.aichangeguard.analysis.model.TypeKind;
import com.aichangeguard.analysis.model.TypeMetadata;
import com.aichangeguard.impact.database.model.JpaColumn;
import com.aichangeguard.impact.database.model.JpaEntity;
import com.aichangeguard.impact.database.model.JpaRelationship;
import com.aichangeguard.impact.database.model.JpaUniqueConstraint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Extracts normalized JPA Entity metadata from parsed Java source code.
 *
 * <p>Discovers entities annotated with {@code @Entity}, and extracts table mappings,
 * columns, primary keys, relationships, and constraints.</p>
 *
 * <h3>Limitations</h3>
 * <ul>
 *   <li>Only static syntactic definitions are extracted; runtime Hibernate naming strategies
 *       and Spring physical naming strategies are not evaluated.</li>
 *   <li>Table inheritance hierarchies (SINGLE_TABLE, JOINED, TABLE_PER_CLASS) across multiple files
 *       are extracted as declared without full cross-file resolution.</li>
 *   <li>Embeddable composite keys are extracted syntactically.</li>
 * </ul>
 */
@Service
public class JpaEntityExtractor {

    private static final Logger log = LoggerFactory.getLogger(JpaEntityExtractor.class);

    private static final Set<String> ENTITY_ANNOTATIONS = Set.of("Entity");
    private static final Set<String> ID_ANNOTATIONS = Set.of("Id", "EmbeddedId");
    private static final Set<String> TRANSIENT_ANNOTATIONS = Set.of("Transient");
    private static final Set<String> NOT_NULL_ANNOTATIONS = Set.of("NotNull", "NotBlank", "NotEmpty");
    private static final Set<String> PRIMITIVE_TYPES = Set.of("int", "long", "short", "byte", "boolean", "double", "float", "char");

    private static final Set<String> RELATIONSHIP_ANNOTATIONS = Set.of(
            "OneToOne", "OneToMany", "ManyToOne", "ManyToMany"
    );

    /**
     * Extracts all JPA entities from a source analysis report.
     */
    public List<JpaEntity> extractEntities(SourceAnalysisReport report) {
        if (report == null || report.parsedFiles() == null) {
            return Collections.emptyList();
        }

        List<JpaEntity> entities = new ArrayList<>();
        for (ParsedFileMetadata file : report.parsedFiles()) {
            if (!file.isSuccessful() || file.types() == null) {
                continue;
            }
            for (TypeMetadata type : file.types()) {
                extractFromType(type, file.relativePath(), entities);
            }
        }

        log.info("Extracted {} JPA entities from parsed source report", entities.size());
        return entities;
    }

    private void extractFromType(TypeMetadata type, String sourceFile, List<JpaEntity> result) {
        if (isEntity(type)) {
            JpaEntity entity = buildEntity(type, sourceFile);
            result.add(entity);
        }

        // Check nested classes if any
        if (type.nestedTypes() != null) {
            for (TypeMetadata nested : type.nestedTypes()) {
                extractFromType(nested, sourceFile, result);
            }
        }
    }

    private boolean isEntity(TypeMetadata type) {
        if (type.kind() != TypeKind.CLASS) {
            return false;
        }
        return safeList(type.annotations()).stream()
                .anyMatch(a -> ENTITY_ANNOTATIONS.contains(a.name()));
    }

    private JpaEntity buildEntity(TypeMetadata type, String sourceFile) {
        String entityName = type.simpleName();
        String qualifiedName = type.qualifiedName();

        // Check @Entity(name = "...")
        for (AnnotationMetadata a : safeList(type.annotations())) {
            if ("Entity".equals(a.name()) && a.attributes() != null) {
                String nameAttr = cleanValue(a.attributes().get("name"));
                if (!nameAttr.isEmpty()) {
                    entityName = nameAttr;
                }
            }
        }

        // Table mapping: @Table(name = "...", schema = "...")
        String tableName = entityName;
        String schemaName = null;
        boolean isTableExplicit = false;
        List<JpaUniqueConstraint> uniqueConstraints = new ArrayList<>();

        for (AnnotationMetadata a : safeList(type.annotations())) {
            if ("Table".equals(a.name()) && a.attributes() != null) {
                String nameAttr = cleanValue(a.attributes().get("name"));
                if (!nameAttr.isEmpty()) {
                    tableName = nameAttr;
                    isTableExplicit = true;
                }
                String schemaAttr = cleanValue(a.attributes().get("schema"));
                if (!schemaAttr.isEmpty()) {
                    schemaName = schemaAttr;
                }
                // Check uniqueConstraints attribute
                String ucAttr = a.attributes().get("uniqueConstraints");
                if (ucAttr != null && !ucAttr.isBlank()) {
                    uniqueConstraints.add(new JpaUniqueConstraint(
                            "table_unique_" + tableName,
                            List.of(cleanValue(ucAttr)),
                            type.range() != null && type.range().begin() != null ? type.range().begin().line() : 0
                    ));
                }
            }
        }

        List<String> primaryKeyFieldNames = new ArrayList<>();
        List<JpaColumn> columns = new ArrayList<>();
        List<JpaRelationship> relationships = new ArrayList<>();

        for (FieldMetadata field : safeList(type.fields())) {
            // Skip transient fields
            if (isTransient(field)) {
                continue;
            }

            boolean isPk = isPrimaryKey(field);
            if (isPk) {
                primaryKeyFieldNames.add(field.name());
            }

            // Check if relationship
            JpaRelationship rel = extractRelationship(field);
            if (rel != null) {
                relationships.add(rel);
                // In many relationships with @JoinColumn, a foreign key column is created
                if (rel.joinColumn() != null) {
                    columns.add(new JpaColumn(
                            field.name(),
                            rel.joinColumn(),
                            field.typeName(),
                            null,
                            true,
                            false,
                            false,
                            null,
                            field.range() != null && field.range().begin() != null ? field.range().begin().line() : 0
                    ));
                }
            } else {
                // Regular column
                JpaColumn column = extractColumn(field, isPk);
                columns.add(column);

                if (column.unique()) {
                    uniqueConstraints.add(new JpaUniqueConstraint(
                            "uk_" + tableName + "_" + column.columnName(),
                            List.of(column.columnName()),
                            column.sourceLine()
                    ));
                }
            }
        }

        int sourceLine = type.range() != null && type.range().begin() != null ? type.range().begin().line() : 0;

        return new JpaEntity(
                entityName,
                qualifiedName,
                tableName,
                schemaName,
                isTableExplicit,
                primaryKeyFieldNames,
                columns,
                relationships,
                uniqueConstraints,
                sourceFile,
                sourceLine
        );
    }

    private boolean isTransient(FieldMetadata field) {
        if (field.modifiers() != null && field.modifiers().contains("transient")) {
            return true;
        }
        return safeList(field.annotations()).stream()
                .anyMatch(a -> TRANSIENT_ANNOTATIONS.contains(a.name()));
    }

    private boolean isPrimaryKey(FieldMetadata field) {
        return safeList(field.annotations()).stream()
                .anyMatch(a -> ID_ANNOTATIONS.contains(a.name()));
    }

    private JpaColumn extractColumn(FieldMetadata field, boolean isPrimaryKey) {
        String columnName = field.name();
        String columnDefinition = null;
        boolean nullable = !isPrimaryKey && !PRIMITIVE_TYPES.contains(field.typeName());
        boolean unique = false;
        Integer length = null;

        for (AnnotationMetadata a : safeList(field.annotations())) {
            if ("Column".equals(a.name()) && a.attributes() != null) {
                String nameAttr = cleanValue(a.attributes().get("name"));
                if (!nameAttr.isEmpty()) {
                    columnName = nameAttr;
                }
                String colDef = cleanValue(a.attributes().get("columnDefinition"));
                if (!colDef.isEmpty()) {
                    columnDefinition = colDef;
                }
                String nullableAttr = a.attributes().get("nullable");
                if ("false".equalsIgnoreCase(nullableAttr)) {
                    nullable = false;
                } else if ("true".equalsIgnoreCase(nullableAttr) && !isPrimaryKey && !PRIMITIVE_TYPES.contains(field.typeName())) {
                    nullable = true;
                }
                String uniqueAttr = a.attributes().get("unique");
                if ("true".equalsIgnoreCase(uniqueAttr)) {
                    unique = true;
                }
                String lenAttr = a.attributes().get("length");
                if (lenAttr != null) {
                    try {
                        length = Integer.parseInt(cleanValue(lenAttr));
                    } catch (NumberFormatException ignored) {}
                }
            }

            if (NOT_NULL_ANNOTATIONS.contains(a.name())) {
                nullable = false;
            }
        }

        int sourceLine = field.range() != null && field.range().begin() != null ? field.range().begin().line() : 0;

        return new JpaColumn(
                field.name(),
                columnName,
                field.typeName(),
                columnDefinition,
                nullable,
                unique,
                isPrimaryKey,
                length,
                sourceLine
        );
    }

    private JpaRelationship extractRelationship(FieldMetadata field) {
        for (AnnotationMetadata a : safeList(field.annotations())) {
            if (RELATIONSHIP_ANNOTATIONS.contains(a.name())) {
                String relType = a.name().toUpperCase(); // ONE_TO_ONE, ONE_TO_MANY, etc.
                String mappedBy = null;
                String fetchType = "DEFAULT";
                List<String> cascades = new ArrayList<>();

                if (a.attributes() != null) {
                    mappedBy = cleanValue(a.attributes().get("mappedBy"));
                    if (mappedBy.isEmpty()) {
                        mappedBy = null;
                    }
                    String fetch = a.attributes().get("fetch");
                    if (fetch != null) {
                        fetchType = cleanValue(fetch);
                    }
                    String cascade = a.attributes().get("cascade");
                    if (cascade != null) {
                        cascades.add(cleanValue(cascade));
                    }
                }

                String joinColumn = null;
                for (AnnotationMetadata ja : safeList(field.annotations())) {
                    if ("JoinColumn".equals(ja.name()) && ja.attributes() != null) {
                        String nameAttr = cleanValue(ja.attributes().get("name"));
                        if (!nameAttr.isEmpty()) {
                            joinColumn = nameAttr;
                        }
                    }
                }

                int sourceLine = field.range() != null && field.range().begin() != null ? field.range().begin().line() : 0;

                return new JpaRelationship(
                        field.name(),
                        field.typeName(),
                        relType,
                        joinColumn,
                        mappedBy,
                        fetchType,
                        cascades,
                        sourceLine
                );
            }
        }
        return null;
    }

    private String cleanValue(String val) {
        if (val == null) {
            return "";
        }
        return val.trim()
                .replaceAll("^\"+|\"+$", "")
                .replaceAll("^'+|'+$", "")
                .trim();
    }

    private <T> List<T> safeList(List<T> list) {
        return list != null ? list : Collections.emptyList();
    }
}

