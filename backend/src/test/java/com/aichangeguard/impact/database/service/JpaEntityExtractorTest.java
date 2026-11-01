package com.aichangeguard.impact.database.service;

import com.aichangeguard.analysis.model.AnnotationMetadata;
import com.aichangeguard.analysis.model.FieldMetadata;
import com.aichangeguard.analysis.model.ParsedFileMetadata;
import com.aichangeguard.analysis.model.SourceAnalysisReport;
import com.aichangeguard.analysis.model.SourceRange;
import com.aichangeguard.analysis.model.TypeKind;
import com.aichangeguard.analysis.model.TypeMetadata;
import com.aichangeguard.impact.database.model.JpaColumn;
import com.aichangeguard.impact.database.model.JpaEntity;
import com.aichangeguard.impact.database.model.JpaRelationship;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class JpaEntityExtractorTest {

    private JpaEntityExtractor extractor;

    @BeforeEach
    void setUp() {
        extractor = new JpaEntityExtractor();
    }

    private AnnotationMetadata annotation(String name) {
        return new AnnotationMetadata(name, Collections.emptyMap(), SourceRange.UNKNOWN);
    }

    private AnnotationMetadata annotation(String name, Map<String, String> attrs) {
        return new AnnotationMetadata(name, attrs, SourceRange.UNKNOWN);
    }

    private FieldMetadata field(String name, String type, List<AnnotationMetadata> annotations) {
        return new FieldMetadata(
                "com.example.User#" + name,
                name,
                type,
                Set.of("private"),
                annotations,
                SourceRange.of(10, 1, 10, 30)
        );
    }

    private TypeMetadata entityType(String simpleName, List<AnnotationMetadata> annotations, List<FieldMetadata> fields) {
        return new TypeMetadata(
                "com.example." + simpleName,
                "com.example." + simpleName,
                simpleName,
                TypeKind.CLASS,
                Set.of("public"),
                annotations,
                Collections.emptyList(),
                Collections.emptyList(),
                fields,
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                SourceRange.of(1, 1, 50, 1)
        );
    }

    private SourceAnalysisReport reportWithTypes(List<TypeMetadata> types) {
        ParsedFileMetadata file = new ParsedFileMetadata(
                "src/main/java/com/example/User.java",
                "com.example",
                Collections.emptyList(),
                types,
                Collections.emptyList(),
                true,
                SourceRange.UNKNOWN
        );
        return new SourceAnalysisReport(
                "/repo", 1, 1, 0, 100.0,
                List.of(file), Collections.emptyList(),
                Instant.now(), Collections.emptyList()
        );
    }

    @Nested
    @DisplayName("Entity and table mapping extraction")
    class EntityTableMappingTests {

        @Test
        @DisplayName("Should detect entity with explicit @Table annotation")
        void shouldDetectEntityWithExplicitTable() {
            TypeMetadata type = entityType("User",
                    List.of(
                            annotation("Entity"),
                            annotation("Table", Map.of("name", "\"app_users\"", "schema", "\"public\""))
                    ),
                    List.of(field("id", "Long", List.of(annotation("Id"))))
            );

            List<JpaEntity> entities = extractor.extractEntities(reportWithTypes(List.of(type)));

            assertThat(entities).hasSize(1);
            JpaEntity entity = entities.getFirst();
            assertThat(entity.entityName()).isEqualTo("User");
            assertThat(entity.tableName()).isEqualTo("app_users");
            assertThat(entity.schemaName()).isEqualTo("public");
            assertThat(entity.isTableExplicit()).isTrue();
        }

        @Test
        @DisplayName("Should default table name to entity name when @Table is missing")
        void shouldDefaultTableNameToEntityName() {
            TypeMetadata type = entityType("Order",
                    List.of(annotation("Entity")),
                    List.of(field("id", "Long", List.of(annotation("Id"))))
            );

            List<JpaEntity> entities = extractor.extractEntities(reportWithTypes(List.of(type)));

            assertThat(entities).hasSize(1);
            assertThat(entities.getFirst().tableName()).isEqualTo("Order");
            assertThat(entities.getFirst().isTableExplicit()).isFalse();
        }

        @Test
        @DisplayName("Should ignore non-entity classes")
        void shouldIgnoreNonEntityClasses() {
            TypeMetadata serviceType = new TypeMetadata(
                    "com.example.UserService", "com.example.UserService", "UserService",
                    TypeKind.CLASS, Set.of("public"),
                    List.of(annotation("Service")),
                    Collections.emptyList(), Collections.emptyList(),
                    Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
                    Collections.emptyList(), SourceRange.UNKNOWN
            );

            List<JpaEntity> entities = extractor.extractEntities(reportWithTypes(List.of(serviceType)));
            assertThat(entities).isEmpty();
        }
    }

    @Nested
    @DisplayName("Column extraction")
    class ColumnExtractionTests {

        @Test
        @DisplayName("Should extract columns, nullability, unique flag, and primary keys")
        void shouldExtractColumnsAndNullability() {
            List<FieldMetadata> fields = List.of(
                    field("id", "Long", List.of(annotation("Id"))),
                    field("email", "String", List.of(
                            annotation("Column", Map.of("name", "\"email_address\"", "nullable", "false", "unique", "true", "length", "100"))
                    )),
                    field("bio", "String", List.of(
                            annotation("Column", Map.of("nullable", "true"))
                    )),
                    field("activeCount", "int", Collections.emptyList()) // Primitive -> non-null
            );

            TypeMetadata type = entityType("User", List.of(annotation("Entity")), fields);
            List<JpaEntity> entities = extractor.extractEntities(reportWithTypes(List.of(type)));

            assertThat(entities).hasSize(1);
            JpaEntity entity = entities.getFirst();
            assertThat(entity.primaryKeyFieldNames()).containsExactly("id");

            JpaColumn emailCol = entity.findColumn("email_address");
            assertThat(emailCol).isNotNull();
            assertThat(emailCol.nullable()).isFalse();
            assertThat(emailCol.unique()).isTrue();
            assertThat(emailCol.length()).isEqualTo(100);

            JpaColumn bioCol = entity.findColumn("bio");
            assertThat(bioCol).isNotNull();
            assertThat(bioCol.nullable()).isTrue();

            JpaColumn countCol = entity.findColumn("activeCount");
            assertThat(countCol).isNotNull();
            assertThat(countCol.nullable()).isFalse(); // Primitive int is non-null
        }

        @Test
        @DisplayName("Should skip @Transient fields")
        void shouldSkipTransientFields() {
            List<FieldMetadata> fields = List.of(
                    field("id", "Long", List.of(annotation("Id"))),
                    field("tempToken", "String", List.of(annotation("Transient")))
            );

            TypeMetadata type = entityType("User", List.of(annotation("Entity")), fields);
            List<JpaEntity> entities = extractor.extractEntities(reportWithTypes(List.of(type)));

            assertThat(entities).hasSize(1);
            assertThat(entities.getFirst().findColumn("tempToken")).isNull();
        }
    }

    @Nested
    @DisplayName("Relationship extraction")
    class RelationshipExtractionTests {

        @Test
        @DisplayName("Should extract relationships with join columns and mappedBy")
        void shouldExtractRelationships() {
            List<FieldMetadata> fields = List.of(
                    field("id", "Long", List.of(annotation("Id"))),
                    field("orders", "List<Order>", List.of(
                            annotation("OneToMany", Map.of("mappedBy", "\"user\"", "cascade", "CascadeType.ALL"))
                    )),
                    field("profile", "Profile", List.of(
                            annotation("OneToOne"),
                            annotation("JoinColumn", Map.of("name", "\"profile_id\""))
                    ))
            );

            TypeMetadata type = entityType("User", List.of(annotation("Entity")), fields);
            List<JpaEntity> entities = extractor.extractEntities(reportWithTypes(List.of(type)));

            assertThat(entities).hasSize(1);
            JpaEntity entity = entities.getFirst();
            assertThat(entity.relationships()).hasSize(2);

            JpaRelationship oneToMany = entity.relationships().stream()
                    .filter(r -> "orders".equals(r.fieldName())).findFirst().orElseThrow();
            assertThat(oneToMany.relationshipType()).isEqualTo("ONETOMANY");
            assertThat(oneToMany.mappedBy()).isEqualTo("user");

            JpaRelationship oneToOne = entity.relationships().stream()
                    .filter(r -> "profile".equals(r.fieldName())).findFirst().orElseThrow();
            assertThat(oneToOne.relationshipType()).isEqualTo("ONETOONE");
            assertThat(oneToOne.joinColumn()).isEqualTo("profile_id");
        }
    }
}

