package com.aichangeguard.impact.database.service;

import com.aichangeguard.analysis.model.AnnotationMetadata;
import com.aichangeguard.analysis.model.MethodMetadata;
import com.aichangeguard.analysis.model.ParameterMetadata;
import com.aichangeguard.analysis.model.ParsedFileMetadata;
import com.aichangeguard.analysis.model.SourceAnalysisReport;
import com.aichangeguard.analysis.model.SourceRange;
import com.aichangeguard.analysis.model.TypeKind;
import com.aichangeguard.analysis.model.TypeMetadata;
import com.aichangeguard.impact.database.model.RepositoryMethod;
import com.aichangeguard.impact.database.model.SpringDataRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class SpringDataRepositoryExtractorTest {

    private SpringDataRepositoryExtractor extractor;

    @BeforeEach
    void setUp() {
        extractor = new SpringDataRepositoryExtractor();
    }

    private AnnotationMetadata annotation(String name, Map<String, String> attrs) {
        return new AnnotationMetadata(name, attrs, SourceRange.UNKNOWN);
    }

    private ParameterMetadata parameter(String name, String type) {
        return new ParameterMetadata(name, type, Collections.emptyList(), false);
    }

    private MethodMetadata method(String name, String returnType, List<ParameterMetadata> params, List<AnnotationMetadata> annotations) {
        return new MethodMetadata(
                "com.example.UserRepository#" + name + "()",
                name,
                returnType,
                params,
                Set.of("public"),
                annotations,
                Collections.emptyList(),
                false,
                false,
                SourceRange.of(10, 1, 10, 50),
                Collections.emptyList()
        );
    }

    @Test
    @DisplayName("Should extract Spring Data repository with domain entity, derived query, and @Query")
    void shouldExtractRepositoryAndQueries() {
        MethodMetadata derivedMethod = method("findByEmail", "Optional<User>",
                List.of(parameter("email", "String")),
                Collections.emptyList());

        MethodMetadata customQueryMethod = method("findActiveUsers", "List<User>",
                Collections.emptyList(),
                List.of(annotation("Query", Map.of("value", "\"SELECT u FROM User u WHERE u.active = true\""))));

        MethodMetadata nativeQueryMethod = method("hardDeleteOldUsers", "int",
                List.of(parameter("days", "int")),
                List.of(
                        annotation("Query", Map.of("value", "\"DELETE FROM users WHERE age > :days\"", "nativeQuery", "true")),
                        annotation("Modifying", Collections.emptyMap())
                ));

        TypeMetadata repoInterface = new TypeMetadata(
                "com.example.UserRepository",
                "com.example.UserRepository",
                "UserRepository",
                TypeKind.INTERFACE,
                Set.of("public"),
                Collections.emptyList(),
                List.of("JpaRepository<User, Long>"),
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                List.of(derivedMethod, customQueryMethod, nativeQueryMethod),
                Collections.emptyList(),
                SourceRange.of(1, 1, 30, 1)
        );

        ParsedFileMetadata file = new ParsedFileMetadata(
                "src/main/java/com/example/UserRepository.java",
                "com.example",
                Collections.emptyList(),
                List.of(repoInterface),
                Collections.emptyList(),
                true,
                SourceRange.UNKNOWN
        );

        SourceAnalysisReport report = new SourceAnalysisReport(
                "/repo", 1, 1, 0, 100.0,
                List.of(file), Collections.emptyList(),
                Instant.now(), Collections.emptyList()
        );

        List<SpringDataRepository> repos = extractor.extractRepositories(report);

        assertThat(repos).hasSize(1);
        SpringDataRepository repo = repos.getFirst();
        assertThat(repo.interfaceName()).isEqualTo("UserRepository");
        assertThat(repo.domainEntity()).isEqualTo("User");
        assertThat(repo.idType()).isEqualTo("Long");
        assertThat(repo.methods()).hasSize(3);

        RepositoryMethod derived = repo.findMethod("findByEmail");
        assertThat(derived).isNotNull();
        assertThat(derived.queryType()).isEqualTo("DERIVED");

        RepositoryMethod custom = repo.findMethod("findActiveUsers");
        assertThat(custom).isNotNull();
        assertThat(custom.queryType()).isEqualTo("JPQL_QUERY");
        assertThat(custom.queryString()).isEqualTo("SELECT u FROM User u WHERE u.active = true");
        assertThat(custom.isNative()).isFalse();

        RepositoryMethod nativeMethod = repo.findMethod("hardDeleteOldUsers");
        assertThat(nativeMethod).isNotNull();
        assertThat(nativeMethod.queryType()).isEqualTo("NATIVE_QUERY");
        assertThat(nativeMethod.isNative()).isTrue();
        assertThat(nativeMethod.isModifying()).isTrue();
    }
}

