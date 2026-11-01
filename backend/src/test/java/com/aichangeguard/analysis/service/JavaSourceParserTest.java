package com.aichangeguard.analysis.service;

import com.aichangeguard.analysis.model.AnnotationMetadata;
import com.aichangeguard.analysis.model.FieldMetadata;
import com.aichangeguard.analysis.model.MethodInvocationMetadata;
import com.aichangeguard.analysis.model.MethodMetadata;
import com.aichangeguard.analysis.model.ParameterMetadata;
import com.aichangeguard.analysis.model.ParsedFileMetadata;
import com.aichangeguard.analysis.model.SourceAnalysisReport;
import com.aichangeguard.analysis.model.TypeKind;
import com.aichangeguard.analysis.model.TypeMetadata;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class JavaSourceParserTest {

    private JavaSourceParser parser;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        parser = new JavaSourceParser();
    }

    @Test
    @DisplayName("Extracts package, imports, class, inheritance, interfaces, fields, and annotations")
    void extractsClassDeclarationsAndMembers() {
        String code = """
                package com.sample.service;

                import java.util.List;
                import java.io.Serializable;
                import org.springframework.stereotype.Service;
                import org.springframework.transaction.annotation.Transactional;

                @Service
                @Transactional(readOnly = true)
                public class OrderService extends BaseService implements Serializable, AutoCloseable {

                    @Deprecated
                    private final String orderId;

                    protected int retryCount;

                    public OrderService(String orderId) {
                        super("OrderContext");
                        this.orderId = orderId;
                    }

                    @Override
                    public void close() {
                        System.out.println("Closing order");
                    }
                }
                """;

        ParsedFileMetadata result = parser.parseSource("OrderService.java", code);

        assertThat(result.isSuccessful()).isTrue();
        assertThat(result.packageName()).isEqualTo("com.sample.service");
        assertThat(result.imports()).contains(
                "import java.util.List;",
                "import org.springframework.stereotype.Service;"
        );

        assertThat(result.types()).hasSize(1);
        TypeMetadata type = result.types().get(0);
        assertThat(type.symbolId()).isEqualTo("com.sample.service.OrderService");
        assertThat(type.simpleName()).isEqualTo("OrderService");
        assertThat(type.kind()).isEqualTo(TypeKind.CLASS);
        assertThat(type.modifiers()).contains("public");
        assertThat(type.extendedTypes()).containsExactly("BaseService");
        assertThat(type.implementedInterfaces()).containsExactly("Serializable", "AutoCloseable");

        // Annotations
        assertThat(type.annotations()).hasSize(2);
        assertThat(type.annotations()).extracting(AnnotationMetadata::name).contains("Service", "Transactional");

        // Fields
        assertThat(type.fields()).hasSize(2);
        FieldMetadata orderIdField = type.fields().stream()
                .filter(f -> f.name().equals("orderId")).findFirst().orElseThrow();
        assertThat(orderIdField.symbolId()).isEqualTo("com.sample.service.OrderService#orderId");
        assertThat(orderIdField.typeName()).isEqualTo("String");
        assertThat(orderIdField.modifiers()).contains("private", "final");
        assertThat(orderIdField.annotations()).extracting(AnnotationMetadata::name).contains("Deprecated");

        // Constructors
        assertThat(type.constructors()).hasSize(1);
        MethodMetadata constructor = type.constructors().get(0);
        assertThat(constructor.symbolId()).isEqualTo("com.sample.service.OrderService#<init>(String)");
        assertThat(constructor.isConstructor()).isTrue();
        assertThat(constructor.parameters()).hasSize(1);
        assertThat(constructor.parameters().get(0).name()).isEqualTo("orderId");
        assertThat(constructor.parameters().get(0).typeName()).isEqualTo("String");

        // Methods
        assertThat(type.methods()).hasSize(1);
        MethodMetadata closeMethod = type.methods().get(0);
        assertThat(closeMethod.symbolId()).isEqualTo("com.sample.service.OrderService#close()");
        assertThat(closeMethod.returnTypeName()).isEqualTo("void");
        assertThat(closeMethod.isAbstract()).isFalse();
    }

    @Test
    @DisplayName("Handles overloaded methods with distinct stable identifiers")
    void handlesOverloadedMethods() {
        String code = """
                package com.sample.calc;

                public class Calculator {
                    public int calculate(int a, int b) {
                        return a + b;
                    }

                    public double calculate(double a, double b) {
                        return a + b;
                    }

                    public int calculate(int a) {
                        return a * 2;
                    }
                }
                """;

        ParsedFileMetadata result = parser.parseSource("Calculator.java", code);
        TypeMetadata type = result.types().get(0);

        assertThat(type.methods()).hasSize(3);
        List<String> symbolIds = type.methods().stream().map(MethodMetadata::symbolId).toList();

        assertThat(symbolIds).containsExactlyInAnyOrder(
                "com.sample.calc.Calculator#calculate(int,int)",
                "com.sample.calc.Calculator#calculate(double,double)",
                "com.sample.calc.Calculator#calculate(int)"
        );
    }

    @Test
    @DisplayName("Extracts interfaces, abstract methods, and default methods")
    void handlesInterfacesAndAbstractMethods() {
        String code = """
                package com.sample.contract;

                public interface Repository<T, ID> {
                    T findById(ID id);

                    default boolean exists(ID id) {
                        return findById(id) != null;
                    }
                }
                """;

        ParsedFileMetadata result = parser.parseSource("Repository.java", code);
        TypeMetadata type = result.types().get(0);

        assertThat(type.kind()).isEqualTo(TypeKind.INTERFACE);
        assertThat(type.methods()).hasSize(2);

        MethodMetadata findById = type.methods().stream()
                .filter(m -> m.name().equals("findById")).findFirst().orElseThrow();
        assertThat(findById.isAbstract()).isTrue();

        MethodMetadata exists = type.methods().stream()
                .filter(m -> m.name().equals("exists")).findFirst().orElseThrow();
        assertThat(exists.isAbstract()).isFalse();
        assertThat(exists.modifiers()).contains("default");
    }

    @Test
    @DisplayName("Handles nested classes, static nested classes, records, and enums")
    void handlesNestedTypesRecordsAndEnums() {
        String code = """
                package com.sample.domain;

                public class OuterEntity {
                    private Status status;

                    public enum Status {
                        ACTIVE, INACTIVE
                    }

                    public static record Payload(String key, int value) {}

                    public static class Builder {
                        public OuterEntity build() {
                            return new OuterEntity();
                        }
                    }
                }
                """;

        ParsedFileMetadata result = parser.parseSource("OuterEntity.java", code);
        TypeMetadata outer = result.types().get(0);

        assertThat(outer.symbolId()).isEqualTo("com.sample.domain.OuterEntity");
        assertThat(outer.nestedTypes()).hasSize(3);

        TypeMetadata enumType = outer.nestedTypes().stream()
                .filter(t -> t.kind() == TypeKind.ENUM).findFirst().orElseThrow();
        assertThat(enumType.symbolId()).isEqualTo("com.sample.domain.OuterEntity.Status");
        assertThat(enumType.simpleName()).isEqualTo("Status");

        TypeMetadata recordType = outer.nestedTypes().stream()
                .filter(t -> t.kind() == TypeKind.RECORD).findFirst().orElseThrow();
        assertThat(recordType.symbolId()).isEqualTo("com.sample.domain.OuterEntity.Payload");

        TypeMetadata builderType = outer.nestedTypes().stream()
                .filter(t -> t.simpleName().equals("Builder")).findFirst().orElseThrow();
        assertThat(builderType.symbolId()).isEqualTo("com.sample.domain.OuterEntity.Builder");
    }

    @Test
    @DisplayName("Extracts method invocations and explicitly marks them as unconfirmed/unresolved")
    void extractsMethodInvocationsAndMarksUnresolved() {
        String code = """
                package com.sample.workflow;

                public class Processor {
                    public void process(String data) {
                        String cleaned = data.trim();
                        validate(cleaned);
                        Sender sender = new Sender();
                        sender.send(cleaned, 42);
                    }

                    private void validate(String input) {}
                }
                """;

        ParsedFileMetadata result = parser.parseSource("Processor.java", code);
        MethodMetadata process = result.types().get(0).methods().stream()
                .filter(m -> m.name().equals("process")).findFirst().orElseThrow();

        assertThat(process.invocations()).isNotEmpty();

        // Method invocations should distinguish unresolved targets from confirmed calls
        for (MethodInvocationMetadata call : process.invocations()) {
            assertThat(call.isResolved()).isFalse();
            assertThat(call.unresolvedReason()).contains("unresolved without semantic symbol solver");
            assertThat(call.range().begin().line()).isGreaterThan(0);
        }

        assertThat(process.invocations()).extracting(MethodInvocationMetadata::methodName)
                .contains("trim", "validate", "<init>", "send");
    }

    @Test
    @DisplayName("Handles syntax errors and malformed files without crashing analysis")
    void handlesMalformedSourceGracefully() {
        String malformedCode = """
                package com.broken;

                public class BrokenClass {
                    // Syntax error: missing type and invalid tokens
                    public def brokenMethod( {
                        if (x == ) {
                    }
                }
                """;

        ParsedFileMetadata result = parser.parseSource("BrokenClass.java", malformedCode);

        // Analysis must not crash, must record isSuccessful = false and collect diagnostics
        assertThat(result.isSuccessful()).isFalse();
        assertThat(result.diagnostics()).isNotEmpty();
        assertThat(result.diagnostics().get(0).severity()).isEqualTo("ERROR");
        assertThat(result.diagnostics().get(0).filePath()).isEqualTo("BrokenClass.java");
        assertThat(result.diagnostics().get(0).position().line()).isGreaterThan(0);
    }

    @Test
    @DisplayName("Aggregates repository coverage report across valid and malformed files")
    void aggregatesRepositoryCoverage() throws IOException {
        Path repo = Files.createDirectories(tempDir.resolve("coverage-repo"));

        // Valid file
        Files.writeString(repo.resolve("Valid.java"), "public class Valid { public void ok() {} }");

        // Malformed file
        Files.writeString(repo.resolve("Invalid.java"), "public class Invalid { invalid syntax ??? ");

        SourceAnalysisReport report = parser.parseRepository(repo, List.of("Valid.java", "Invalid.java"));

        assertThat(report.totalFilesAttempted()).isEqualTo(2);
        assertThat(report.totalFilesSuccessful()).isEqualTo(1);
        assertThat(report.totalFilesFailed()).isEqualTo(1);
        assertThat(report.parserCoveragePercent()).isEqualTo(50.0);
        assertThat(report.diagnostics()).isNotEmpty();
        assertThat(report.limitations()).isNotEmpty();
    }
}
